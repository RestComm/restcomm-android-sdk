package com.example.sipmessagetest;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import com.example.sipmessagetest.SipEvent.SipEventType;

import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.content.Context;
import android.media.AudioManager;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity implements OnClickListener,
		ISipEventListener {

	Button btnSubmit;
	EditText editTextUser;
	EditText editTextPassword;
	EditText editTextDomain;
	EditText editTextTo;
	EditText editTextMessage;
	TextView textViewChat;
	String chatText = "";
	AudioManager audio;
	AudioStream audioStream;
	AudioGroup audioGroup;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Button btnRegister = (Button) findViewById(R.id.btnSubmit);
		btnRegister.setOnClickListener(this);
		Button btnSend = (Button) findViewById(R.id.btnSend);
		btnSend.setOnClickListener(this);
		Button btnCall = (Button) findViewById(R.id.btnCall);
		btnCall.setOnClickListener(this);
		editTextUser = (EditText) findViewById(R.id.editTextUser);
		editTextPassword = (EditText) findViewById(R.id.editTextPassword);
		editTextDomain = (EditText) findViewById(R.id.editTextDomain);
		editTextTo = (EditText) findViewById(R.id.editTextTo);
		editTextMessage = (EditText) findViewById(R.id.editTextMessage);
		textViewChat = (TextView) findViewById(R.id.textViewChat);
		textViewChat.setMovementMethod(new ScrollingMovementMethod());

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
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case (R.id.btnSubmit):
			new SipStackAndroid().execute(editTextUser.getText().toString(),
					editTextPassword.getText().toString(), editTextDomain
							.getText().toString());
			SipStackAndroid.getInstance().addSipListener(this);
			new SipRegister().execute(editTextUser.getText().toString(),
					editTextPassword.getText().toString(), editTextDomain
							.getText().toString());
			break;
		case (R.id.btnCall):
		

			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
					.permitAll().build();
			StrictMode.setThreadPolicy(policy);
			try {
				audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
				audioGroup = new AudioGroup();
				audioGroup.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);
				audioStream = new AudioStream(
						InetAddress.getByAddress(getLocalIPAddress()));
				audioStream.setCodec(AudioCodec.PCMU);
				audioStream.setMode(RtpStream.MODE_NORMAL);
				audioStream.associate(
						InetAddress.getByName(SipStackAndroid.getRemoteIp()),
						7078);
				audioStream.join(audioGroup);

				// Stream is setup now we initiate a call and specify our
				// listening RTP port
				new SipCall().execute(editTextTo.getText().toString(),
						editTextMessage.getText().toString(),
						String.valueOf(audioStream.getLocalPort()));

			} catch (Exception e) {
				e.printStackTrace();
			}

			break;
		case (R.id.btnSend):
			this.chatText += editTextUser.getText().toString() + ":"
					+ editTextMessage.getText().toString() + "\r\n";
			textViewChat.setText(this.chatText);
			new SipSendMessage().execute(editTextTo.getText().toString(),editTextMessage.getText().toString());
			break;
		}
	}

	public static byte[] getLocalIPAddress() {
		byte ip[] = null;
		try {
			for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en
					.hasMoreElements();) {
				NetworkInterface intf = (NetworkInterface) en.nextElement();
				for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr
						.hasMoreElements();) {
					InetAddress inetAddress = (InetAddress) enumIpAddr
							.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						ip = inetAddress.getAddress();
					}
				}
			}
		} catch (SocketException ex) {
		}
		return ip;

	}

	@Override
	public void onSipMessage(SipEvent sipEventObject) {
		System.out.println("Sip Event fired");
		if (sipEventObject.type == SipEventType.MESSAGE) {
			chatText += sipEventObject.from + ":" + sipEventObject.content
					+ "\r\n";
			this.runOnUiThread(new Runnable() {
				public void run() {
					textViewChat.append(chatText);

				}
			});
		} else if (sipEventObject.type == SipEventType.BYE) {
			audio.setMode(AudioManager.RINGER_MODE_NORMAL);
			audioStream.release();
			audioGroup.clear();
		}

	}

}
