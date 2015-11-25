package com.telestax.restcomm_olympus;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a ItemDetailActivity representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link MainFragment} and the item details
 * (if present) is a ItemDetailFragment.
 * <p/>
 * This activity also implements the required
 * {@link MainFragment.Callbacks} interface
 * to listen for item selections.
 */
public class MainActivity extends AppCompatActivity
        implements MainFragment.Callbacks, RCDeviceListener,
        View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        AddUserDialogFragment.ContactDialogListener, ActionFragment.ActionListener {

    private static final String TAG = "MainActivity";
    SharedPreferences prefs;
    private RCDevice device;
    private HashMap<String, Object> params;
    private MainFragment listFragment;
    private AlertDialog alertDialog;
    private RCConnectivityStatus previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;

    ImageButton btnAdd;

    private static final int CONNECTION_REQUEST = 1;
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        listFragment = (MainFragment)getSupportFragmentManager().findFragmentById(R.id.item_list);

        btnAdd = (ImageButton)findViewById(R.id.imageButton_add);
        btnAdd.setOnClickListener(this);
        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        //fab.setOnClickListener(this);

        alertDialog = new AlertDialog.Builder(MainActivity.this).create();

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        RCClient.setLogLevel(Log.VERBOSE);
        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener() {
            public void onInitialized() {
                Log.i(TAG, "RCClient initialized");
            }

            public void onError(Exception exception) {
                Log.e(TAG, "RCClient initialization error");
            }
        });

        params = new HashMap<String, Object>();
        params.put("pref_proxy_domain", prefs.getString("pref_proxy_domain", "sip:cloud.restcomm.com:5060"));
        //params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5080"));
        params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
        params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
        device = RCClient.createDevice(params, this);
        device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                new Intent(getApplicationContext(), MessageActivity.class));

        // preferences
        prefs.registerOnSharedPreferenceChangeListener(this);

        // set it to wifi by default to avoid the status message when starting with wifi
        previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;
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
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        }
        else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        }

        /*
        if (device.getState() == RCDevice.DeviceState.OFFLINE) {
            showOkAlert("No Connectivity", "No Wifi connectivity");
        }
        */

        // The activity has become visible (it is now "resumed").
        Log.i(TAG, "%% onResume");
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

    /**
     * MainFragment Callbacks
     */
    @Override
    public void onItemSelected(HashMap<String, String> contact, MainFragment.ContactSelectionType type) {
        // forward to onActionClicked
        onActionClicked(ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL, contact.get("username"), contact.get("sipuri"));
    }

    public void onContactUpdate(HashMap<String, String> contact, int type)
    {
        DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get("username"), contact.get("sipuri"));
        newFragment.show(getFragmentManager(), "dialog");
    }

    public void onAccessoryClicked(HashMap<String, String> contact)
    {
        DialogFragment actionFragment = ActionFragment.newInstance(contact.get("username"), contact.get("sipuri"));
        actionFragment.show(getFragmentManager(), "dialog-accessory");
    }


    /**
     * Callbacks for AddUserDialogFragment
     */
    public void onDialogPositiveClick(int type, String username, String sipuri)
    {
        listFragment.updateContact(type, username, sipuri);
    }

    public void onDialogNegativeClick()
    {

    }

    /**
     * Callbacks for ActionFragment
     */
    public void onActionClicked(ActionFragment.ActionType action, String username, String sipuri)
    {
        if (action == ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (action == ActionFragment.ActionType.ACTION_TYPE_AUDIO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (action == ActionFragment.ActionType.ACTION_TYPE_TEXT_MESSAGE) {
            Intent intent = new Intent(this, MessageActivity.class);
            intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            startActivity(intent);
        }
    }

    /**
     * Main Activity onClick
     */
    public void onClick(View view) {
        if (view.getId() == R.id.imageButton_add) {
            DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
            newFragment.show(getFragmentManager(), "dialog");
        }
    }

    /**
     * RCDeviceListener callbacks
     */
    public void onStartListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device, int errorCode, String errorText)
    {
        showOkAlert("RCDevice Error", errorText);
        /*
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Wifi Connectivity", errorText);
        }
        else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        }
        else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
        */
    }

    public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
    {
        String text = "";
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            text = "Lost connectivity";
        }
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusWiFi) {
            text = "Reestablished connectivity (Wifi)";
        }
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusCellular) {
            text = "Reestablished connectivity (Cellular)";
        }

        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            //getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.rgb(109, 109, 109)));
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        }
        else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        }

        if (connectivityStatus != this.previousConnectivityStatus) {
            showOkAlert("RCDevice connectivity change", text);
            this.previousConnectivityStatus = connectivityStatus;
        }
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    /**
     * Settings Menu callbacks
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
        if (id == R.id.action_about) {
            DialogFragment newFragment = AboutFragment.newInstance();
            newFragment.show(getFragmentManager(), "dialog-about");
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        boolean updated = false;
        if (key.equals("pref_proxy_domain")) {
            params.put("pref_proxy_domain", prefs.getString("pref_proxy_domain", "sip:cloud.restcomm.com:5060"));
            updated = true;
        }
        /*
        else if (key.equals("pref_proxy_port")) {
            params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5060"));
            updated = true;
        }
         */
        else if (key.equals("pref_sip_user")) {
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

    /**
     * Helpers
     */
    private void showOkAlert(final String title, final String detail) {
        if (alertDialog.isShowing()) {
            Log.w(TAG, "Alert already showing, hiding to show new alert");
            alertDialog.hide();
        }

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

