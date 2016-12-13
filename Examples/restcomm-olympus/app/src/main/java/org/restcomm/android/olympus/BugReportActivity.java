package org.restcomm.android.olympus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.restcomm.android.sdk.RCDevice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TimeZone;

public class BugReportActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
   private static final String TAG = "BugReportActivity";
   public static final String MOST_RECENT_CALL_PEER = "most-recent-call-peer";
   private Spinner spinner;
   SharedPreferences prefs;

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

      EditText context = (EditText) findViewById(R.id.contextEditText);
      context.setText(prefs.getString(MOST_RECENT_CALL_PEER, ""));
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
         }
         else {
            EditText note = (EditText) findViewById(R.id.noteEditText);
            TimeZone timezone = TimeZone.getDefault();
            //String logs ="<HTML><BODY><p style=\"font-family: 'Courier New', Courier, monospace\">";
            String logs = log.toString();
            //logs += "</p></BODY><HTML>";
            String emailBody = "";
            emailBody += "Client: " + prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk") + "\n";
            emailBody += "Issue: " + spinner.getSelectedItem().toString() + "\n";
            emailBody += "Additional Note: " + note.getText() + "\n";
            emailBody += "Peer: " + prefs.getString(MOST_RECENT_CALL_PEER, "") + "\n";
            emailBody += "Domain: " + domain + "\n";
            emailBody += "Timezone: " + timezone.getDisplayName(false, TimeZone.SHORT) + "\n";
            emailBody += "Olympus Version: " + BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE + "\n";
            emailBody += "Logs: \n" + logs;

            // Send logs via email and add timezone in the subject so that we can exactly correlate
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("message/rfc822");
            i.putExtra(Intent.EXTRA_EMAIL, new String[]{"antonis.tsakiridis@telestax.com"});
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

      }
      catch (IOException e) {
         Log.e(TAG, "IOException when gathering logcat");
      }

   }

}
