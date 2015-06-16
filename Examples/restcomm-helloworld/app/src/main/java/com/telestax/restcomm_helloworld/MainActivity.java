package com.telestax.restcomm_helloworld;

//import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.view.View.OnClickListener;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;
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

        /*
        // set AOR and registrar
        params.put("aor", "sip:bob@telestax.com");
        params.put("registrar", "192.168.2.32");
        */

        params.put("pref_proxy_ip", "192.168.2.32");
        params.put("pref_proxy_port", "5080");
        params.put("pref_sip_user", "bob");
        params.put("pref_sip_password", "1234");

        // register on startup
        device.updateParams(params);
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
    public void onClick(View view)
    {
        if (view.getId() == R.id.button_dial) {
            if (connection != null) {
                Log.e(TAG, "Error: already connected");
                return;
            }
            HashMap<String, String> connectParams = new HashMap<String, String>();
            connectParams.put("username", "sip:1235@192.168.2.32:5080");
            connection = device.connect(connectParams, this);
            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                return;
            }
        }
        else if (view.getId() == R.id.button_hangup) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
                return;
            }
            connection.disconnect();
        }
        else if (view.getId() == R.id.button_register) {
            device.updateParams(params);
        }
        else if (view.getId() == R.id.button_answer) {
            this.pendingConnection.accept();
        }
        else if (view.getId() == R.id.button_decline) {
            this.pendingConnection.reject();
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

    public void onIncomingMessage(RCDevice device, String message)
    {
        Log.i(TAG, "Message arrived: message");
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
