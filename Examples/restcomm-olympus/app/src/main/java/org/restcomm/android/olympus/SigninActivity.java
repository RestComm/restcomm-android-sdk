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
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.restcomm.android.sdk.RCDevice;

/**
 * A login screen that offers Restcomm login.
 */
public class SigninActivity extends AppCompatActivity {

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
      if (getIntent().getAction().equals(Intent.ACTION_CALL) && getIntent().getData() != null) {
         if (getIntent().getData().getHost() != null) {
            // note down the fact that we are signed up so that
            //SharedPreferences.Editor prefEdit = prefsGeneral.edit();
            //prefEdit.putString(PREFS_EXTERNAL_CALL_URI, getIntent().getData().getHost());
            //prefEdit.apply();
            globalPreferences.setExternalCallUri(getIntent().getData().toString());
         }
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
      String password = txtPassword.getText().toString();
      String domain = txtDomain.getText().toString();

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

      prefEdit.apply();
   }
}

