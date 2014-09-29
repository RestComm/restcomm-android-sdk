package com.example.sipmessagetest;

import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity implements OnClickListener,ISipEventListener {

	Button btnSubmit;
	EditText editTextUser;
	EditText editTextPassword;
	EditText editTextDomain;
	EditText editTextTo;
	EditText editTextMessage;
	TextView textViewChat;
	String chatText="";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		new SipStackAndroid().execute();
		SipStackAndroid.getInstance().addSipListener(this);
		Button btnRegister = (Button) findViewById(R.id.btnSubmit);
		btnRegister.setOnClickListener(this);
		Button btnSend = (Button) findViewById(R.id.btnSend);
		btnSend.setOnClickListener(this);
		editTextUser = (EditText)findViewById(R.id.editTextUser);
		editTextPassword = (EditText)findViewById(R.id.editTextPassword);
		editTextDomain = (EditText)findViewById(R.id.editTextDomain);
		editTextTo = (EditText)findViewById(R.id.editTextTo);
		editTextMessage = (EditText) findViewById(R.id.editTextMessage); 
		textViewChat =(TextView)findViewById(R.id.textViewChat); 
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
			new SipRegister().execute(editTextUser.getText().toString(),editTextPassword.getText().toString(),editTextDomain.getText().toString());
			break;
		case (R.id.btnSend):
			this.chatText += editTextUser.getText().toString() + ":" + editTextMessage.getText().toString()+"\r\n";
			textViewChat.setText(this.chatText );
			
			new SipSendMessage().execute(editTextTo.getText().toString(),editTextMessage.getText().toString());
			break;
		}
	}

	@Override
	public void onSipMessage(SipEvent sipEventObject) {
		chatText +=sipEventObject.from +":"+ sipEventObject.content + "\r\n";
		this.runOnUiThread(new Runnable() {
			  public void run() {
					textViewChat.append(chatText);
					
			  }
			});
	
		System.out.println("Sip Event fired");
		
		
	}

}
