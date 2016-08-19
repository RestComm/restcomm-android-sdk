/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.telestax.restcomm_olympus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.mobicents.restcomm.android.client.sdk.RCDevice;

import java.util.ArrayList;
import java.util.Map;

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
   private static final String PREFS_NAME = "general-prefs.xml";
   private static final String PREFS_SIGNED_UP_KEY = "user-signed-up";
   private Context context;

   SharedPreferences prefsGeneral = null;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_signin);

      // Check if
      prefsGeneral = this.getSharedPreferences(PREFS_NAME, 0);
      boolean signedUp = prefsGeneral.getBoolean(PREFS_SIGNED_UP_KEY, false);
      if (signedUp) {
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
         // note down the fact that we are signing up
         SharedPreferences.Editor prefEdit = prefsGeneral.edit();
         prefEdit.putBoolean(PREFS_SIGNED_UP_KEY, true);
         prefEdit.apply();

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
         // values are valid let's update prefs
         updatePrefs();
         Intent intent = new Intent(this, MainActivity.class);
         //intent.setAction(RCDevice.OUTGOING_CALL);
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

