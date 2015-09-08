package com.telestax.restcomm_messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.view.View.OnClickListener;
import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;


public class MainActivity extends Activity implements RCDeviceListener,
        OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        MediaPlayer.OnPreparedListener {

    SharedPreferences prefs;
    private RCDevice device;
    private HashMap<String, Object> params;
    private static final String TAG = "MainActivity";

    // UI elements
    Button btnRegister, btnMessage;
    Button btnDial, btnDialAudio;
    EditText txtUri;
    // debug
    Button btnListen, btnUnlisten, btnInit, btnShutdown;

    // #webrtc
    private static final int CONNECTION_REQUEST = 1;

    // Activity lifetime methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "%% onCreate");

        // initialize UI
        btnRegister = (Button)findViewById(R.id.button_register);
        btnRegister.setOnClickListener(this);
        btnDial = (Button)findViewById(R.id.button_dial);
        btnDial.setOnClickListener(this);
        btnDialAudio= (Button)findViewById(R.id.button_dial_audio);
        btnDialAudio.setOnClickListener(this);
        txtUri = (EditText)findViewById(R.id.text_uri);
        btnMessage = (Button)findViewById(R.id.button_message);
        btnMessage.setOnClickListener(this);

        // debug
        btnListen = (Button)findViewById(R.id.button_listen);
        btnListen.setOnClickListener(this);
        btnUnlisten = (Button)findViewById(R.id.button_unlisten);
        btnUnlisten.setOnClickListener(this);
        btnInit = (Button)findViewById(R.id.button_init);
        btnInit.setOnClickListener(this);
        btnShutdown = (Button)findViewById(R.id.button_shutdown);
        btnShutdown.setOnClickListener(this);

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener() {
            public void onInitialized() {
                Log.i(TAG, "RCClient initialized");
            }

            public void onError(Exception exception) {
                Log.e(TAG, "RCClient initialization error");
            }
        });

        params = new HashMap<String, Object>();
        params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "23.23.228.238"));
        params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5080"));
        params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
        params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
        device = RCClient.createDevice(params, this);
        device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                new Intent(getApplicationContext(), MessageActivity.class));

        // preferences
        prefs.registerOnSharedPreferenceChangeListener(this);

        txtUri.setText("sip:1235@23.23.228.238:5080");
        //txtUri.setText("sip:alice@192.168.2.32:5080");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The activity is about to become visible.
        Log.i(TAG, "%% onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (device.getState() == RCDevice.DeviceState.OFFLINE) {
            showOkAlert("No Connectivity", "No Wifi connectivity");
        }
        // The activity has become visible (it is now "resumed").
        Log.i(TAG, "%% onResume");
        Intent intent = getIntent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
        Log.i(TAG, "%% onPause");
    }
    @Override
    protected void onStop() {
        super.onStop();
        // The activity is no longer visible (it is now "stopped")
        Log.i(TAG, "%% onStop");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The activity is about to be destroyed.
        Log.i(TAG, "%% onDestroy");
        RCClient.shutdown();
        device = null;
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onPrepared(MediaPlayer mp)
    {
        Log.i(TAG, "Media Player prepared");
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_dial || view.getId() == R.id.button_dial_audio) {
            //WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
            //if (wifi.isWifiEnabled()) {
                Intent intent = new Intent(this, CallActivity.class);
                intent.setAction(RCDevice.OUTGOING_CALL);
                intent.putExtra(RCDevice.EXTRA_DID, txtUri.getText().toString());
                if (view.getId() == R.id.button_dial_audio) {
                    intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
                }
                else {
                    intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
                }
                startActivityForResult(intent, CONNECTION_REQUEST);
            //}
            //else {
            //    showOkAlert("No Connectivity", "No network connectivity");
            //}
        }
        else if (view.getId() == R.id.button_register) {
            if (!device.updateParams(params)) {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }

        }
        else if (view.getId() == R.id.button_message) {
            //WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
            //if (wifi.isWifiEnabled()) {
                Intent intent = new Intent(this, MessageActivity.class);
                intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
                intent.putExtra(RCDevice.EXTRA_DID, txtUri.getText().toString());
                startActivity(intent);
            //}
            //else {
            //    showOkAlert("No Connectivity", "No network connectivity");
            //}
        }
        else if (view.getId() == R.id.button_listen) {
            device.listen();
        }
        else if (view.getId() == R.id.button_unlisten) {
            device.unlisten();
        }
        else if (view.getId() == R.id.button_init) {
            device = RCClient.createDevice(params, this);
            device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                    new Intent(getApplicationContext(), MessageActivity.class));
        }
        else if (view.getId() == R.id.button_shutdown) {
            RCClient.shutdown();
            device = null;
        }
    }

    // RCDevice Listeners
    public void onStartListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device, int errorCode, String errorText)
    {
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Wifi Connectivity", errorText);
        }
        else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        }
        else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // menu stuff
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
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        boolean updated = false;
        if (key.equals("pref_proxy_ip")) {
            params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "23.23.228.238"));
            updated = true;
        } else if (key.equals("pref_proxy_port")) {
            params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5060"));
            updated = true;
        } else if (key.equals("pref_sip_user")) {
            params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
            updated = true;
        } else if (key.equals("pref_sip_password")) {
            params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
            updated = true;
        }
        if (updated) {
            if (!device.updateParams(params)) {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }
        }
    }

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(detail);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alertDialog.show();
    }
}
