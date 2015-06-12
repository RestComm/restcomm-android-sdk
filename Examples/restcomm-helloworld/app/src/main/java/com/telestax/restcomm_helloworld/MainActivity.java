package com.telestax.restcomm_helloworld;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;
import java.util.Map;



public class MainActivity extends ActionBarActivity implements RCDeviceListener, RCConnectionListener {

    private RCDevice device;
    private RCConnection connection;
    private HashMap<String, String> params;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener()
        {
            public void onInitialized()
            {

            }

            public void onError(Exception exception)
            {

            }

        });

        // TODO: we don't support capability tokens yet so let's use an empty string
        device = RCClient.createDevice("", this);
        connection = null;
        params = new HashMap<String, String>();

        // set AOR and register
        params.put("aor", "sip:bob@telestax.com");
        params.put("registrar", "192.168.2.32");
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
            connection = device.connect(connectParams, this);
            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                return;
            }
            /*
            if (!phone.isConnected()) {
                Map<String, String> params = new HashMap<String, String>();
                if (outgoingTextBox.getText().length() > 0) {
                    String number = outgoingTextBox.getText().toString();
                    if (inputSelect.getCheckedRadioButtonId() == R.id.input_text) {
                        number = "client:" + number;
                    }
                    params.put("To", number);
                }
                phone.connect(params);
            }
            else
                phone.disconnect();
                */
        }
        else if (view.getId() == R.id.button_hangup) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
                return;
            }
            connection.disconnect();
            /*
            phone.disconnect();
            phone.login(clientNameTextBox.getText().toString(),
                    outgoingCheckBox.isChecked(),
                    incomingCheckBox.isChecked());
                    */
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


    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {

    }

    public void onConnected(RCConnection connection)
    {

    }

    public void onDisconnected(RCConnection connection)
    {

    }


}
