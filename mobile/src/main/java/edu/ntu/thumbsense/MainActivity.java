package edu.ntu.thumbsense;

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

import com.google.android.gms.appindexing.Action;
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

    private RelativeLayout overlay;
    private RelativeLayout endingOverlay;
    private RelativeLayout mainContainer;
    private TextView ipView;

    private static final int FLOAT = Float.SIZE / 8;
    private static final int LONG = Long.SIZE / 8;
    private static final int DATASIZE = FLOAT * 3 + LONG;

    private String nodeId;

    private ActionList actionList;
    private static final int MaxTrialPerAction = 3;


    // All buttons onClick function.
    public void startAction(View view) {
        sendMessage("/start");
        actionList.updateSend();
        Log.v(TAG, "Define action " + actionList.getAction());
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
    }

    // Triggered when overlay is clicked.
    public void endAction(View view) {
        sendMessage("/end");
        overlay.setVisibility(View.INVISIBLE);
        mainContainer.bringToFront();
        Log.v(TAG, "Action " + actionList.getAction() + " ends.");
        if (!actionList.checkAndNext()) {
            endingOverlay.setVisibility(View.VISIBLE);
            endingOverlay.bringToFront();
            Log.v(TAG, "All actions done.");
        }
    }

    // Resets after all trials done.
    public void resetActions(View view) {
        endingOverlay.setVisibility(View.INVISIBLE);
        mainContainer.bringToFront();
        actionList.reset();
    }

    // --------------- Activity & Play services methods ------------- //

    //on successful connection to play services, add data listener
    public void onConnected(Bundle connectionHint) {
        messageContainer.setText("Play connected.");
        Wearable.MessageApi.addListener(googleClient, this);
    }

    //on resuming activity, reconnect play services
    public void onResume(){
        messageContainer.setText("Activity resumed.");
        super.onResume();
        googleClient.connect();
    }

    //on suspended connection, remove play services
    public void onConnectionSuspended(int cause) {
        messageContainer.setText("Connection suspended.");
        Wearable.MessageApi.removeListener(googleClient, this);
    }

    //pause listener, disconnect play services
    public void onPause(){
        messageContainer.setText("Play paused.");
        super.onPause();
        Wearable.MessageApi.removeListener(googleClient, this);
        googleClient.disconnect();
    }

    //On failed connection to play services, remove the data listener
    public void onConnectionFailed(ConnectionResult result) {
        messageContainer.setText("Connection failed.");
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.activity = this;

        overlay = (RelativeLayout) findViewById(R.id.overlay);
        endingOverlay = (RelativeLayout) findViewById(R.id.endingOverlay);

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

        actionList = new ActionList();

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

        messageContainer.setText(isOnline() ? "Network connected." : "Network disconnected.");
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

    // Sets IP dialog.
    public void setIPDialog(View view) {
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

            String url = event.getPath();
            if (!url.startsWith("/raw")) {
                Log.v(TAG, "Received weird URL:" + url);
                return;
            }

            int type = Integer.parseInt(url.split("/")[2]);

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
                out.writeInt(actionList.sendGesture);
                out.writeInt(actionList.sendCount);
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


    private class ActionList {
        String[] actions;
        int cur = 0, count = 1;
        int sendGesture = 0, sendCount = 0;
        TextView previousAction, currentAction, nextAction;
        Button startButton;

        private ActionList() {
            actions = getResources().getStringArray(R.array.interactions);

            previousAction = (TextView) findViewById(R.id.previousAction);
            currentAction = (TextView) findViewById(R.id.currentAction);
            nextAction = (TextView) findViewById(R.id.nextAction);

            startButton = (Button) findViewById(R.id.start);
            startButton.setText("Action! #" + count);

            updateWorking();
        }

        private int updateWorking() {
            previousAction.setText((cur == 0) ? "---" : actions[cur - 1]);
            currentAction.setText(actions[cur]);
            nextAction.setText((cur == (actions.length - 1)) ? "---" : actions[cur + 1]);

            return cur;
        }

        private boolean checkAndNext() {
            count ++;
            if (count > MaxTrialPerAction) {
                cur ++;
                count = 1;

                Log.v(TAG, "Status: action " + cur + "/" + actions.length + ", index " + count);
                if (actions.length == cur) {
                    reset();
                    startButton.setText("Action! #" + count);
                    updateWorking();
                    return false;
                } else {
                    updateWorking();
                }
            }

            startButton.setText("Action! #" + count);
            return true;
        }

        private String getAction() {
            return actions[cur];
        }

        private void reset() {
            cur = 0;
            count = 1;
        }

        // Update the data sent only when next action is started,
        // and avoids sending next round accidentally (if transmission takes long time).
        private void updateSend() {
            sendGesture = cur;
            sendCount = count;
        }
    }


}
