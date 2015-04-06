package com.example.sipmessagetest;

import org.mobicents.restcomm.android.sdk.IDevice;
import org.mobicents.restcomm.android.sdk.NotInitializedException;
import org.mobicents.restcomm.android.sdk.SipProfile;
import org.mobicents.restcomm.android.sdk.impl.DeviceImpl;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;

public class MainActivity extends ActionBarActivity implements OnClickListener,
		 OnSharedPreferenceChangeListener {
	SharedPreferences prefs;
	Button btnSubmit;
	EditText editTextUser;
	EditText editTextDomain;
	EditText editTextTo;
	EditText editTextMessage;
	TextView textViewChat;
	String chatText = "";
	SipProfile sipProfile;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		sipProfile = new SipProfile();
        HashMap<String, String> customHeaders = new HashMap<>();
        customHeaders.put("customHeader1","customValue1");
        customHeaders.put("customHeader2","customValue2");

        DeviceImpl.GetInstance().Initialize(getApplicationContext(), sipProfile,customHeaders);
		
		Button btnRegister = (Button) findViewById(R.id.btnSubmit);
		btnRegister.setOnClickListener(this);
		Button btnSend = (Button) findViewById(R.id.btnSend);
		btnSend.setOnClickListener(this);
		Button btnCall = (Button) findViewById(R.id.btnCall);
		btnCall.setOnClickListener(this);

		editTextTo = (EditText) findViewById(R.id.editTextTo);
		editTextMessage = (EditText) findViewById(R.id.editTextMessage);
		textViewChat = (TextView) findViewById(R.id.textViewChat);
		textViewChat.setMovementMethod(new ScrollingMovementMethod());
		// ////////////////////////////////////////////////////////////

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// register preference change listener
		prefs.registerOnSharedPreferenceChangeListener(this);
		initializeSipFromPreferences();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
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
	public void onClick(View v) {
		switch (v.getId()) {
		case (R.id.btnSubmit):
			DeviceImpl.GetInstance().Register();
			break;
		case (R.id.btnCall):
		
			DeviceImpl.GetInstance().Call(editTextTo.getText().toString());
		
			break;
		case (R.id.btnSend):
			
			DeviceImpl.GetInstance().SendMessage(editTextTo.getText().toString(), editTextMessage.getText().toString() );
			
			break;
		}
	}





	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("pref_proxy_ip")) {
			sipProfile.setRemoteIp((prefs.getString("pref_proxy_ip", "")));
		} else if (key.equals("pref_proxy_port")) {
			sipProfile.setRemotePort(Integer.parseInt(prefs.getString(
					"pref_proxy_port", "5060")));
		}  else if (key.equals("pref_sip_user")) {
			sipProfile.setSipUserName(prefs.getString("pref_sip_user",
					"alice"));
		} else if (key.equals("pref_sip_password")) {
			sipProfile.setSipPassword(prefs.getString("pref_sip_password",
					"1234"));
		}

	}

	@SuppressWarnings("static-access")
	private void initializeSipFromPreferences() {
		sipProfile.setRemoteIp((prefs.getString("pref_proxy_ip", "")));
		sipProfile.setRemotePort(Integer.parseInt(prefs.getString(
				"pref_proxy_port", "5060")));
		sipProfile.setSipUserName(prefs.getString("pref_sip_user", "alice"));
		sipProfile.setSipPassword(prefs
				.getString("pref_sip_password", "1234"));

	}

}
