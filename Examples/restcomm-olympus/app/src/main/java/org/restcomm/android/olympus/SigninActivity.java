/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.olympus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.restcomm.android.olympus.Util.Utils;
import org.restcomm.android.sdk.RCDevice;

/**
 * A login screen that offers Restcomm login.
 */
public class SigninActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

   SharedPreferences prefs;
   // UI references.
   private EditText txtUsername;
   private EditText txtPassword;
   private EditText txtDomain;
   private static final String TAG = "ContactsController";
   //private static final String PREFS_NAME = "general-prefs.xml";
   //private static final String PREFS_SIGNED_UP_KEY = "user-signed-up";
   //private static final String PREFS_EXTERNAL_CALL_URI = "external-call-uri";
   private Context context;
   GlobalPreferences globalPreferences;

   //SharedPreferences prefsGeneral = null;

   //push notifications
   private SwitchCompat switchCompat;
   private EditText txtPushAccount;
   private EditText txtPushPassword;
   private EditText txtPushDomain;
   private EditText txtHttpDomain;
   private LinearLayout llPushContainer;


   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      Log.i(TAG, "%% onCreate");

      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_signin);

      globalPreferences = new GlobalPreferences(getApplicationContext());
      // Check if
      //prefsGeneral = this.getSharedPreferences(PREFS_NAME, 0);
      //boolean signedUp = prefsGeneral.getBoolean(PREFS_SIGNED_UP_KEY, false);

      // see if we are called from an external App trying to make a call
      // Notice that we also add the ACTION_VIEW, so that it also works for
      // numbers inside text that we tap on for example in default Android SMS App.
      // For the ACTION_VIEW, the number comes prepended with tel, like 'tel:XXXXXXXXX'
      if ((getIntent().getAction().equals(Intent.ACTION_CALL) || getIntent().getAction().equals(Intent.ACTION_VIEW)) &&
              getIntent().getData() != null) {
         // note down the fact that we are signed up so that
         //SharedPreferences.Editor prefEdit = prefsGeneral.edit();
         //prefEdit.putString(PREFS_EXTERNAL_CALL_URI, getIntent().getData().getHost());
         //prefEdit.apply();
         globalPreferences.setExternalCallUri(getIntent().getData().toString());
      }

      if (globalPreferences.haveSignedUp()) {
         // we have already sign up, skip this activity and fire up MainActivity
         Intent intent = new Intent(this, MainActivity.class);
         // needed to avoid extreme flashing when the App starts up without signing up
         intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
         finish();
         startActivity(intent);
         // needed to avoid extreme flashing when the App starts up without signing up
         overridePendingTransition(0, 0);
      }
      else {
         txtUsername = (EditText) findViewById(R.id.signin_username);
         txtPassword = (EditText) findViewById(R.id.signin_password);
         txtDomain = (EditText) findViewById(R.id.signin_domain);
         Button mSigninButton = (Button) findViewById(R.id.signin_button);

         txtPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent)
            {
               if (id == R.id.login || id == EditorInfo.IME_NULL) {
                  attemptLogin();
                  return true;
               }
               return false;
            }
         });

         mSigninButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view)
            {
               attemptLogin();
            }
         });

         PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
         prefs = PreferenceManager.getDefaultSharedPreferences(this);

         txtUsername.setText(prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, ""));
         txtDomain.setText(prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, ""));
         txtPassword.setText(prefs.getString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, ""));

         llPushContainer = (LinearLayout) findViewById(R.id.ll_push_container);
         switchCompat = (SwitchCompat) findViewById(R.id.switch_enable_push);
         txtPushAccount = (EditText) findViewById(R.id.push_account_email);
         txtPushPassword = (EditText) findViewById(R.id.push_password);
         txtPushDomain = (EditText) findViewById(R.id.push_domain);
         txtHttpDomain = (EditText) findViewById(R.id.http_domain);

         switchCompat.setChecked(prefs.getBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true));
         txtPushAccount.setText(prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, ""));
         txtPushPassword.setText(prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, ""));
         txtPushDomain.setText(prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, ""));
         txtHttpDomain.setText(prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, ""));

         switchCompat.setOnCheckedChangeListener(this);

      }
   }

   private void attemptLogin()
   {
      // Reset errors.
      txtUsername.setError(null);
      txtPassword.setError(null);
      txtDomain.setError(null);

      // Store values at the time of the login attempt.
      String username = txtUsername.getText().toString();
      String domain = txtDomain.getText().toString();
      boolean pushEnabled = switchCompat.isChecked();
      String pushDomain = txtPushDomain.getText().toString();
      String httpDomain = txtHttpDomain.getText().toString();

      boolean cancel = false;
      View focusView = null;

      // Check for a valid email address.
      if (TextUtils.isEmpty(username)) {
         txtUsername.setError(getString(R.string.error_field_required));
         focusView = txtUsername;
         cancel = true;
      }
      else if (TextUtils.isEmpty(domain)) {
         txtDomain.setError(getString(R.string.error_invalid_email));
         focusView = txtDomain;
         cancel = true;
      }
      else if (username.contains(" ")) {
         txtUsername.setError(getString(R.string.error_field_no_whitespace));
         focusView = txtUsername;
         cancel = true;
      }
      else if (domain.contains(" ")) {
         txtDomain.setError(getString(R.string.error_field_no_whitespace));
         focusView = txtDomain;
         cancel = true;
      }

      if (pushEnabled && !cancel){
         if (!Utils.isValidEmail(txtPushAccount.getText())){
            txtPushAccount.setError(getString(R.string.error_invalid_email));
            focusView = txtPushAccount;
            cancel = true;
         } else if (TextUtils.isEmpty(pushDomain)){
            txtPushDomain.setError(getString(R.string.error_field_required));
            focusView = txtPushDomain;
            cancel = true;
         } else if (TextUtils.isEmpty(httpDomain)){
            txtHttpDomain.setError(getString(R.string.error_field_required));
            focusView = txtHttpDomain;
            cancel = true;
         } else if (pushDomain.contains(" ")) {
            txtPushDomain.setError(getString(R.string.error_field_no_whitespace));
            focusView = txtPushDomain;
            cancel = true;
         } else if (httpDomain.contains(" ")) {
            txtHttpDomain.setError(getString(R.string.error_field_no_whitespace));
            focusView = txtHttpDomain;
            cancel = true;
         }
      }

      if (cancel) {
         // There was an error; don't attempt login and focus the first
         // form field with an error.
         focusView.requestFocus();
      }
      else {

         // note down the fact that we are signed up so that
         globalPreferences.setSignedUp(true);

         // values are valid let's update prefs
         updatePrefs();
         Intent intent = new Intent(this, MainActivity.class);
         //intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
         //intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         //intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
         startActivity(intent);
         //startActivityForResult(intent, CONNECTION_REQUEST);
      }
   }

   private void updatePrefs()
   {
      SharedPreferences.Editor prefEdit = prefs.edit();

      prefEdit.putString(RCDevice.ParameterKeys.SIGNALING_USERNAME, txtUsername.getText().toString());
      prefEdit.putString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, txtPassword.getText().toString());
      prefEdit.putString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, txtDomain.getText().toString());

      //push settings
      /*** IMPORTANT ***/
      /** Push notifications will not work if these parameters are replaced with real values: **/
      /** PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_NOTIFICATIONS_FCM_SERVER_KEY **/
      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "Olympus");
      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, BuildConfig.TEST_PUSH_FCM_KEY);

      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, txtPushAccount.getText().toString());
      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, txtPushPassword.getText().toString());
      prefEdit.putBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, switchCompat.isChecked());
      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, txtPushDomain.getText().toString());
      prefEdit.putString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, txtHttpDomain.getText().toString());



      prefEdit.apply();
   }

   @Override
   public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
         if (isChecked){
            llPushContainer.setVisibility(View.VISIBLE);
         } else {
            llPushContainer.setVisibility(View.GONE);
         }
   }
}

