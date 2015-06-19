package com.telestax.restcomm_messenger;

//import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.net.wifi.WifiManager;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;
import java.util.Map;
//import java.util.Map;


public class MainActivity extends Activity implements RCDeviceListener, RCConnectionListener, OnClickListener {

    private RCDevice device;
    private RCConnection connection, pendingConnection;
    private HashMap<String, String> params;
    private static final String TAG = "MainActivity";

    // UI elements
    Button btnRegister;
    Button btnDial;
    Button btnHangup;
    Button btnAnswer;
    Button btnDecline;
    Button btnCancel;
    Button btnSend;
    EditText txtUri;
    EditText txtMessage;
    EditText txtWall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize UI
        btnRegister = (Button)findViewById(R.id.button_register);
        btnRegister.setOnClickListener(this);
        btnDial = (Button)findViewById(R.id.button_dial);
        btnDial.setOnClickListener(this);
        btnHangup = (Button)findViewById(R.id.button_hangup);
        btnHangup.setOnClickListener(this);
        btnAnswer = (Button)findViewById(R.id.button_answer);
        btnAnswer.setOnClickListener(this);
        btnDecline = (Button)findViewById(R.id.button_decline);
        btnDecline.setOnClickListener(this);
        btnCancel = (Button)findViewById(R.id.button_cancel);
        btnCancel.setOnClickListener(this);
        btnSend = (Button)findViewById(R.id.button_send);
        btnSend.setOnClickListener(this);
        txtUri = (EditText)findViewById(R.id.text_uri);
        txtMessage = (EditText)findViewById(R.id.text_message);
        txtWall = (EditText)findViewById(R.id.text_wall);

        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener()
        {
            public void onInitialized()
            {
                Log.i(TAG, "RCClient initialized");

            }

            public void onError(Exception exception)
            {
                Log.e(TAG, "RCClient initialization error");
            }

        });

        // TODO: we don't support capability tokens yet so let's use an empty string
        device = RCClient.createDevice("", this);
        connection = null;
        params = new HashMap<String, String>();

        // set AOR and registrar
        params.put("pref_proxy_ip", "192.168.2.32");
        params.put("pref_proxy_port", "5080");
        params.put("pref_sip_user", "bob");
        params.put("pref_sip_password", "1234");

        WifiManager wifi = (WifiManager)getSystemService(getApplicationContext().WIFI_SERVICE);
        if (wifi.isWifiEnabled()) {
            // register on startup
            device.updateParams(params);
        }
        else {
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("No Connectivity");
            alertDialog.setMessage("Please turn on Network Connectivity");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        }

        txtUri.setText("sip:1234@192.168.2.32:5080");
        txtMessage.setText("Hello there!");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_dial) {
            if (connection != null) {
                Log.e(TAG, "Error: already connected");
                return;
            }
            HashMap<String, String> connectParams = new HashMap<String, String>();
            connectParams.put("username", txtUri.getText().toString());
            connection = device.connect(connectParams, this);
            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                return;
            }
        } else if (view.getId() == R.id.button_hangup) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
                return;
            }
            connection.disconnect();
        } else if (view.getId() == R.id.button_register) {
            device.updateParams(params);
        } else if (view.getId() == R.id.button_answer) {
            pendingConnection.accept();
            connection = this.pendingConnection;
        } else if (view.getId() == R.id.button_decline) {
            pendingConnection.reject();
            pendingConnection = null;
        } else if (view.getId() == R.id.button_cancel) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
                return;
            }
            connection.disconnect();
        } else if (view.getId() == R.id.button_send) {
            HashMap<String, String> sendParams = new HashMap<String, String>();
            sendParams.put("username", txtUri.getText().toString());
            device.sendMessage(txtMessage.getText().toString(), sendParams);

            // also output the message in the wall
            String text = txtWall.getText().toString();
            String newText = "Me: " + txtMessage.getText().toString() + "\n" + text;
            txtWall.setText(newText, TextView.BufferType.EDITABLE);
        }
    }


    // RCDevice Listeners
    public void onStartListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device)
    {

    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    public void onIncomingConnection(RCDevice device, RCConnection connection)
    {
        Log.i(TAG, "Connection arrived");
        this.pendingConnection = connection;
    }

    public void onIncomingMessage(RCDevice device, String message, HashMap<String, String> parameters)
    {
        final HashMap<String, String> finalParameters = parameters;
        final String finalMessage = message;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String text = txtWall.getText().toString();
                String newText = finalParameters.get("username") + ": " + finalMessage + "\n" + text;
                txtWall.setText(newText, TextView.BufferType.EDITABLE);
                Log.i(TAG, "Message arrived: message");
            }
        });
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {
        Log.i(TAG, "RCConnection connecting");
    }

    public void onConnected(RCConnection connection)
    {
        Log.i(TAG, "RCConnection connected");
    }

    public void onDisconnected(RCConnection connection)
    {
        Log.i(TAG, "RCConnection disconnected");
    }


}
