package com.example.simon.androidweardatalayer;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;

import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;
import java.util.concurrent.TimeUnit;

//Wearable Layout
public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        SensorEventListener,
        MessageApi.MessageListener {
    private final static String TAG = "MiBand";


    private GoogleApiClient googleClient;

    private LinearLayout mainContainer;
    private TextView accelerometerView;
    private TextView gyroscopeView;
    private TextView resultView;

    private String nodeId;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private static final int THRESHOLD = 32;
    private static final int SCALE = 8;
    private SyncQueueBuffer AcceleratorBuffer;
    private SyncQueueBuffer GyroBuffer;

    private RelativeLayout overlay;
    private Vibrator vibrator;
    private static final long[] vibrationPattern = {5}; // TODO: Test!
    private boolean onAction = false;

    //on successful connection to play services, add data listener
    public void onConnected(Bundle connectionHint) {
        Wearable.MessageApi.addListener(googleClient, this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                googleClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
                NodeApi.GetConnectedNodesResult result =
                        Wearable.NodeApi.getConnectedNodes(googleClient).await();
                for (Node node: result.getNodes()) {
                    if (node.isNearby()) {
                        Log.w(TAG, "Nearby node name: " + node.getDisplayName());
                        nodeId = node.getId();
                    }
                }
                Log.w(TAG, "Finally connected to node: " + nodeId);
            }
        }).start();
    }

    //on resuming activity, reconnect play services
    public void onResume(){
        super.onResume();
        googleClient.connect();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST); // TODO: can adjust!!
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST); // TODO: adjust this too!
    }

    //on suspended connection, remove play services
    public void onConnectionSuspended(int cause) {
        Wearable.MessageApi.removeListener(googleClient, this);
    }

    //pause listener, disconnect play services
    public void onPause(){
        super.onPause();
        googleClient.disconnect();
        sensorManager.unregisterListener(this);
        Wearable.MessageApi.removeListener(googleClient, this);
    }

    //On failed connection to play services, remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
        Wearable.MessageApi.removeListener(googleClient, this);
    }


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //set up our google play services client
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Creates buffer

        AcceleratorBuffer = new SyncQueueBuffer(SCALE);
        GyroBuffer = new SyncQueueBuffer(SCALE);


        //find all of our UI element
        accelerometerView = (TextView) findViewById(R.id.accelerometer);
        gyroscopeView = (TextView) findViewById(R.id.gyroscope);
        mainContainer = (LinearLayout) findViewById(R.id.mainContainer);
        resultView = (TextView) findViewById(R.id.result);

        // Gets all sensors.
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Log.v(TAG, "on create!");

        overlay = (RelativeLayout) findViewById(R.id.overlay);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }


    // MessageAPI :: WearableListenerService
    @Override
    public void onMessageReceived(MessageEvent event) {
        nodeId = event.getSourceNodeId();
        String url = event.getPath();
        Log.v(TAG, "Receive: " + url);

        if (url.equalsIgnoreCase("/ok")) { // TODO: This can be removed to speedup.

        } else if (url.equalsIgnoreCase("/start")) {
            vibrator.vibrate(vibrationPattern, -1 /* not repeating */);
            overlay.setVisibility(View.VISIBLE);
            // TODO: What if vibration affects signals?
            onAction = true;
        } else { // Url: /end
            onAction = false;
            overlay.setVisibility(View.INVISIBLE);

            // Clears buffer!!
            byte[] res;
            if ((res = AcceleratorBuffer.getData(-1)) != null) { // -1 indicates to get all.
                sendData(accelerometer.getType(), accelerometerView, res);
            }

            if ((res = GyroBuffer.getData(-1)) != null) { // -1 indicates to get all.
                sendData(gyroscope.getType(), gyroscopeView, res);
            }
        }

        url += " " + ((new Date()).getTime() % 100000);
        resultView.setText(url);
    }


    // ---------------- SensorEventListener ----------------- //

    @Override
    public void onSensorChanged(SensorEvent event) {
        TextView sensorText = (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) ?
                accelerometerView : gyroscopeView;
        SyncQueueBuffer sensorBuffer = (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) ?
                AcceleratorBuffer : GyroBuffer;

        // If sensor is unreliable, then just return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            sensorText.setText("Unreliable now.");
            return;
        }

        Long time = new Date().getTime();

        // Returns if not recording action!
        if (!onAction) {
            return;
        }

        // Puts data into sensor buffer.
        sensorBuffer.putData(time, event.values);

        // Tries to read data out if available.
        byte[] res;
        if ((res = sensorBuffer.getData(THRESHOLD)) != null) {
            sendData(event.sensor.getType(), sensorText, res);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void sendData(int type, TextView sensorText, @NonNull byte[] res) {
        sensorText.setText(type + ": " + (new Date()).toString()); // TODO: test here.

        PendingResult<MessageApi.SendMessageResult> result =
                Wearable.MessageApi.sendMessage(googleClient, nodeId, "/raw/" + type, res);
        result.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                if (sendMessageResult.getStatus().isSuccess()) {
                    Log.v(TAG, "Successfully send message API.");
                } else {
                    Log.v(TAG, "Send messageAPI " + sendMessageResult.getStatus().toString());
                }
            }
        });
    }
}
