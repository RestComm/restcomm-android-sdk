package org.restcomm.android.olympus;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.restcomm.android.olympus.Util.Utils;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.TimeZone;

public class BugReportActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener,
        RCDeviceListener, ServiceConnection {
   private static final String TAG = "BugReportActivity";
   public static final String MOST_RECENT_CALL_PEER = "most-recent-call-peer";
   private Spinner spinner;
   SharedPreferences prefs;
   EditText noteEditText;
   private RCDevice device;
   boolean serviceBound = false;
   private AlertDialog alertDialog;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_bug_report);
      Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
      setSupportActionBar(toolbar);

      FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
      fab.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view)
         {
            sendBugReport();
         }
      });
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);

      // Handle issue spinner
      spinner = (Spinner) findViewById(R.id.issue_spinner);
      // Create an ArrayAdapter using the string array and a default spinner layout
      ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.bug_report_issue_values, R.layout.custom_spinner_item);
      // Specify the layout to use when the list of choices appears
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      // Apply the adapter to the spinner
      spinner.setAdapter(adapter);

      // so that we get callbacks on the Activity that implements AdapterView.OnItemSelectedListener
      spinner.setOnItemSelectedListener(this);

      // preferences
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      prefs = PreferenceManager.getDefaultSharedPreferences(this);

      EditText contextEditText = (EditText) findViewById(R.id.contextEditText);
      contextEditText.setText(prefs.getString(MOST_RECENT_CALL_PEER, ""));

      noteEditText = (EditText) findViewById(R.id.noteEditText);

      alertDialog = new AlertDialog.Builder(BugReportActivity.this, R.style.SimpleAlertStyle).create();
   }

   @Override
   protected void onStart()
   {
      super.onStart();
      // The activity is about to become visible.
      Log.i(TAG, "%% onStart");

      bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);
   }

   @Override
   protected void onResume()
   {
      super.onResume();
      Log.i(TAG, "%% onResume");

      if (device != null) {
         // needed if we are returning from Message screen that becomes the Device listener
         device.setDeviceListener(this);
      }
   }

   @Override
   protected void onPause()
   {
      super.onPause();
      // Another activity is taking focus (this activity is about to be "paused").
      Log.i(TAG, "%% onPause");
      Intent intent = getIntent();
      // #129: clear the action so that on subsequent indirect activity open (i.e. via lock & unlock) we don't get the old action firing again
      intent.setAction("CLEAR_ACTION");
      setIntent(intent);
   }

   @Override
   protected void onStop()
   {
      super.onStop();
      // The activity is no longer visible (it is now "stopped")
      Log.i(TAG, "%% onStop");

      // Unbind from the service
      if (serviceBound) {
         unbindService(this);
         serviceBound = false;
      }
   }

   @Override
   protected void onDestroy()
   {
      super.onDestroy();
      // The activity is about to be destroyed.
      Log.i(TAG, "%% onDestroy");
   }


   public void onItemSelected(AdapterView<?> parent, View view,
                              int pos, long id) {
      // An item was selected. You can retrieve the selected item using
      // parent.getItemAtPosition(pos)
   }

   public void onNothingSelected(AdapterView<?> parent) {
      // Another interface callback
   }

   public void sendBugReport()
   {
      try {
         // retrieve all log entries from logcat in a string builder
         Process process = Runtime.getRuntime().exec("logcat -d *:V");
         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

         StringBuilder log = new StringBuilder();
         String line;
         while ((line = bufferedReader.readLine()) != null) {
            log.append(line);
            log.append(System.getProperty("line.separator"));
         }
         bufferedReader.close();
         View parentLayout = findViewById(R.id.content_bug_report);
         String domain = prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "");

         if (!domain.contains(".restcomm.com")) {
            Snackbar.make(parentLayout, "Bug reports are only applicable to Restcomm Cloud domain.", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
            return;
         }
         if (spinner.getSelectedItemPosition() == 0) {
            Snackbar.make(parentLayout, "Please select issue before submitting.", Snackbar.LENGTH_LONG)
                  .setAction("Action", null).show();
            spinner.requestFocus();
            return;
         }
         // if the last element is selected in the spinner, which is other, then providing additional noteEditText is mandatory
         if (spinner.getSelectedItemPosition() == spinner.getAdapter().getCount() - 1 && TextUtils.isEmpty(noteEditText.getText())) {
            noteEditText.setError(getString(R.string.bug_report_description_mandatory_error));
            noteEditText.requestFocus();
            return;
         }

         TimeZone timezone = TimeZone.getDefault();
         //String logs ="<HTML><BODY><p style=\"font-family: 'Courier New', Courier, monospace\">";
         String logs = log.toString();
         //logs += "</p></BODY><HTML>";
         String emailBody = "";
         emailBody += "Client: " + prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk") + "\n";
         emailBody += "Issue: " + spinner.getSelectedItem().toString() + "\n";
         emailBody += "Additional Note: " + noteEditText.getText() + "\n";
         emailBody += "Peer: " + prefs.getString(MOST_RECENT_CALL_PEER, "") + "\n";
         emailBody += "Domain: " + domain + "\n";
         emailBody += "Timezone: " + timezone.getDisplayName(false, TimeZone.SHORT) + "\n";
         emailBody += "Olympus Version: " + BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE + "\n";
         emailBody += "Logs: \n" + logs;

         // Send logs via email and add timezone in the subject so that we can exactly correlate
         Intent i = new Intent(Intent.ACTION_SEND);
         i.setType("message/rfc822");
         i.putExtra(Intent.EXTRA_EMAIL, new String[]{"mobile-sdks-squad@telestax.com"});
         i.putExtra(Intent.EXTRA_SUBJECT, "[restcomm-android-sdk] User bug report for Olympus");
         i.putExtra(Intent.EXTRA_TEXT, emailBody);
         //i.putExtra(Intent.EXTRA_HTML_TEXT, logs);
         try {
            startActivity(Intent.createChooser(i, "Send mail..."));
         }
         catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(BugReportActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
         }
         Snackbar.make(parentLayout, "Sending bug report...", Snackbar.LENGTH_LONG)
               .setAction("Action", null).show();

      }
      catch (IOException e) {
         Log.e(TAG, "IOException when gathering logcat");
      }

   }

   // Callbacks for service binding, passed to bindService()
   @Override
   public void onServiceConnected(ComponentName className, IBinder service)
   {
      Log.i(TAG, "%% onServiceConnected");
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      RCDevice.RCDeviceBinder binder = (RCDevice.RCDeviceBinder) service;
      device = binder.getService();

      if (device.getState() == RCDevice.DeviceState.OFFLINE) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      if (!device.isInitialized()) {
         Log.i(TAG, "RCDevice not initialized; initializing");
         PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
         SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

         HashMap<String, Object> params = Utils.createParameters(prefs, this);

         // If exception is raised, we will close activity only if it comes from login
         // otherwise we will just show the error dialog
         device.setLogLevel(Log.VERBOSE);
         try {
            device.initialize(getApplicationContext(), params, this);
         } catch (RCException e) {
            showOkAlert("RCDevice Initialization Error", e.errorText);
         }
      }

      // needed if we are returning from Message screen that becomes the Device listener
      device.setDeviceListener(this);

      serviceBound = true;
   }

   @Override
   public void onServiceDisconnected(ComponentName arg0)
   {
      Log.i(TAG, "%% onServiceDisconnected");
      serviceBound = false;
   }

   /**
    * RCDeviceListener callbacks
    */
   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      Log.i(TAG, "%% onInitialized");
      if (statusCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(connectivityStatus, "RCDevice successfully initialized, using: " + connectivityStatus);
      }
      else if (statusCode == RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY.ordinal()) {
         // This is not really an error, since if connectivity comes back the RCDevice will resume automatically
         handleConnectivityUpdate(connectivityStatus, null);
      }
      else {
         if (!isFinishing()) {
            showOkAlert("RCDevice Initialization Error", statusText);
         }
      }
   }

   public void onReconfigured(RCDevice device, RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      Log.i(TAG, "%% onReconfigured");
   }

   public void onError(RCDevice device, int errorCode, String errorText)
   {
      if (errorCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice: " + errorText);
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Error: " + errorText);
      }
   }

   public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
   {
      Log.i(TAG, "%% onConnectivityUpdate");

      handleConnectivityUpdate(connectivityStatus, null);
   }

   public void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId)
   {
      Log.i(TAG, "onMessageSent(): statusCode: " + statusCode + ", statusText: " + statusText);
   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
      if (statusCode != RCClient.ErrorCodes.SUCCESS.ordinal()) {
         showOkAlert("RCDevice Error", statusText);
      }

      //maybe we stopped the activity before onReleased is called
      if (serviceBound) {
         unbindService(this);
         serviceBound = false;
      }
   }

   public void handleConnectivityUpdate(RCConnectivityStatus connectivityStatus, String text)
   {

      if (text == null) {
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            text = "RCDevice connectivity change: Lost connectivity";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusWiFi) {
            text = "RCDevice connectivity change: Reestablished connectivity (Wifi)";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusCellular) {
            text = "RCDevice connectivity change: Reestablished connectivity (Cellular)";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusEthernet) {
            text = "RCDevice connectivity change: Reestablished connectivity (Ethernet)";
         }

      }

      if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
   }

   /**
    * Helpers
    */
   private void showOkAlert(final String title, final String detail)
   {
      Log.d(TAG, "Showing alert: " + title + ", detail: " + detail);

      if (alertDialog.isShowing()) {
         Log.w(TAG, "Alert already showing, hiding to show new alert");
         alertDialog.hide();
      }

      alertDialog.setTitle(title);
      alertDialog.setMessage(detail);
      alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int which)
         {
            dialog.dismiss();
         }
      });
      alertDialog.show();
   }

}
