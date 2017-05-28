package com.example.simon.androidweardatalayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

//Mobile Profile
public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener {
//        DataApi.DataListener,

    private final static String TAG = "MiBand";
    private Activity activity;
    private GoogleApiClient googleClient;
    private TextView messageContainer;

    private String IP = "192.168.1.104";
    private int PORT = 3000;

    private boolean onAction;
    private String action;
    private int actionTag;
    private RelativeLayout overlay;
    private RelativeLayout mainContainer;
    private TextView ipView;

    private static final int FLOAT = Float.SIZE / 8;
    private static final int LONG = Long.SIZE / 8;
    private static final int DATASIZE = FLOAT * 3 + LONG * 1;

    private String nodeId;


    // All buttons onClick function.
    public void defineAction(View view) {
        sendMessage("/start");
        action = ((Button)view).getText().toString();
        Log.v(TAG, "Define action " + action);
        onAction = true;
        actionTag = Integer.parseInt((String) view.getTag());
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
    }

    // Triggered when overlay is clicked.
    public void endAction(View view) {
        sendMessage("/end");
        overlay.setVisibility(View.INVISIBLE);
        mainContainer.bringToFront();
        Log.v(TAG, "Action " + action + " ends.");
        onAction = false;
    }

    //on successful connection to play services, add data listener
    public void onConnected(Bundle connectionHint) {
        Wearable.MessageApi.addListener(googleClient, this);
    }

    //on resuming activity, reconnect play services
    public void onResume(){
        super.onResume();
        googleClient.connect();
    }

    //on suspended connection, remove play services
    public void onConnectionSuspended(int cause) {
        Wearable.MessageApi.removeListener(googleClient, this);
    }

    //pause listener, disconnect play services
    public void onPause(){
        super.onPause();
        Wearable.MessageApi.removeListener(googleClient, this);
        googleClient.disconnect();
    }

    //On failed connection to play services, remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.activity = this;

        onAction = false;
        overlay = (RelativeLayout) findViewById(R.id.overlay);

        //data layer
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        messageContainer = (TextView) findViewById(R.id.messageContainer);
        mainContainer = (RelativeLayout) findViewById(R.id.mainContainer);
        ipView = (TextView) findViewById(R.id.ip);
        ipView.setText(IP + ":" + PORT);

        new Thread(new Runnable() {
            @Override
            public void run() {
                googleClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
                NodeApi.GetConnectedNodesResult result =
                        Wearable.NodeApi.getConnectedNodes(googleClient).await();
                for (Node node: result.getNodes()) {
                    Log.w(TAG, "Get node " + nodeId);
                    if (node.isNearby()) {
                        Log.w(TAG, "Nearby node name: " + node.getDisplayName());
                        nodeId = node.getId();
                    }
                }
            }
        }).start();

        Log.v(TAG, "Mobile on create");
        setIPDialog(null);
    }



    //checks to see if we are online (and can access the net)
    protected boolean isOnline(){
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        boolean connected = false;
        if((networkInfo != null) && (networkInfo.isConnected())){
            connected = true;
        }

        return connected;
    }

    //populates information for our API
    protected APIInformation setUpAPIInformation(String type, String time, String[] values){

        APIInformation apiInformation = new APIInformation();

        apiInformation.setAPIEndpoint(IP + PORT);
        HashMap arguments = new HashMap<String, String>();

        arguments.put("act", action);
        arguments.put("type", type);
        arguments.put("time", time);
        arguments.put("x", values[0]);
        arguments.put("y", values[1]);
        arguments.put("z", values[2]);

        apiInformation.setAPIArguments(arguments);
        apiInformation.setAPIUrl();

        return apiInformation;
    }

    // Sets IP dialog.
    private void setIPDialog(View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(this);

        alert.setMessage("Message...");
        alert.setTitle("Enter the server IP:Port");

        alert.setView(edittext);

        alert.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String address = edittext.getText().toString();
                String segs[] = address.split(":");
                Log.v(TAG, "Set IP to" + address);
                IP = segs[0];
                if (segs.length == 2) {
                    PORT = Integer.parseInt(segs[1]);
                }
                ipView.setText(IP + ":" + PORT);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.v(TAG, "IP remained:" + IP + ":" + PORT);
            }
        });

        alert.show();
    }

        @Override
        public void onMessageReceived(MessageEvent event) {
            Log.v(TAG, "Received " + event.getPath());
            if (!onAction) {
                return;
            }
            String url = event.getPath();
            if (!url.startsWith("/raw")) {
                Log.v(TAG, "Received weird URL:" + url);
                return;
            }

            int type = Integer.parseInt(url.split("/")[2]);
//            ByteBuffer data = ByteBuffer.wrap(event.getData())
//            String type = data[2];
//            String time = data[3];
//            //Log.v(TAG, type + ";time: " + time);
//            String values[] = {data[4], data[5], data[6]};

//            messageContainer.setText(type + ": " + time);

            // Passes data only when action on!
                //Log.v(TAG, "Sending action " + type + " : " + time);
                //BUILD API ARGUMENTS
                //populate our API information, in preparation for our API call
//                APIInformation apiInformation = setUpAPIInformation(type, time, values);

                //EXECUTE ASYNC TASK
//                APIAsyncTask asyncTask = new APIAsyncTask();
//                asyncTask.execute(apiInformation);

            SocketAsyncTask task = new SocketAsyncTask(type);
            //task.execute(event.getData());

            // By this, we can get thread in pool to work for us.
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, event.getData());
        }

    private void sendMessage(final String url) {
        PendingResult<MessageApi.SendMessageResult> result =
                Wearable.MessageApi.sendMessage(googleClient, nodeId, url, null);
        result.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                if (sendMessageResult.getStatus().isSuccess()) {
                    Log.v(TAG, "Successfully send message API to " + url);
                } else {
                    Log.v(TAG, "Send messageAPI to " + url + " : " + sendMessageResult.getStatus().toString());
                }
            }
        });
    }

    public class SocketAsyncTask extends AsyncTask<byte[], String, Exception> {
        Socket socket;
        DataOutputStream out;
        int sensorType;

        public SocketAsyncTask(int sensor_type) {
            sensorType = sensor_type;
        }

        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected Exception doInBackground(byte[]... data) {
            Log.v(TAG, "Start doing background");
            try {
                socket = new Socket(IP, PORT);
                out = new DataOutputStream(socket.getOutputStream());
                out.writeInt(actionTag);
                out.writeInt(sensorType);
                out.writeInt(data[0].length / DATASIZE);
                out.write(data[0]);
                out.close();
                socket.close();
            } catch (IOException e) {
                Log.v(TAG, e.getMessage());
                return e;
            }

            Log.v(TAG, "Writing: " + data[0].length);
            return null;
        }

        // TODO: onProgressUpdate(Progress...),
        //          invoked on the UI thread after a call to publishProgress(Progress...).
        protected void onPostExecute(Exception err) {
            super.onPostExecute(err);
        }
    }


    //main async task to connect to our API and collect a response
    public class APIAsyncTask extends AsyncTask<APIInformation, String, HashMap> {

        //execute before we start
        protected void onPreExecute() {
            super.onPreExecute();
        }

        //execute background task
        protected HashMap doInBackground(APIInformation... params) {

            APIInformation apiInformation = params[0];
            boolean isOnline = isOnline();
            HashMap result;

            if(isOnline){
                //perform a HTTP request
                APIUrlConnection apiUrlConnection = new APIUrlConnection();

                //get the result back and process
                result = apiUrlConnection.GetData(apiInformation.getAPIUrl());

            }else{
                //we're not online, flag the error
                result = new HashMap();
                result.put("type", "failure");
                result.put("data", "Not currrently online, can't connect to API");
            }
            return result;
        }

        //update progress
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
        }

        //Execute once we're done
        protected void onPostExecute(HashMap result) {
            super.onPostExecute(result);

            Wearable.MessageApi.sendMessage(googleClient, nodeId, "/ok", null);
//            //build our message back to the wearable (either with data or a failure message)
//            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/analysis");
//            putDataMapRequest.getDataMap().putString("result", (String) result.get("type"));
//
//
//            //finalise our message and send it off (either success or failure)
//            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
//            putDataRequest.setUrgent();
//            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(googleClient, putDataRequest);
        }
    }


}
