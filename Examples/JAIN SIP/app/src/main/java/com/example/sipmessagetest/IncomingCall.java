package com.example.sipmessagetest;

// ISSUE#17: commented the class after moving it from the SDK to the Application. Let's keep them commented until they are needed
/*
import org.mobicents.restcomm.android.sdk.IDevice;
import org.mobicents.restcomm.android.sdk.impl.DeviceImpl;
import org.mobicents.restcomm.android.sdk.impl.SipManager;

import com.example.sipmessagetest.MainActivity;
import com.example.sipmessagetest.R;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;

public class IncomingCall extends ActionBarActivity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setFinishOnTouchOutside(false);
		Bundle b = getIntent().getExtras();
		setContentView(R.layout.activity_incoming_call);
		ImageButton b1 = (ImageButton) findViewById(R.id.btnAccept);
		b1.setOnClickListener(this);

		ImageButton b2 = (ImageButton) findViewById(R.id.btnHangup);
		b2.setOnClickListener(this);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.incoming_call, menu);
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
		case (R.id.btnAccept):
			DeviceImpl.GetInstance().GetSipManager().AcceptCall(
					DeviceImpl.GetInstance().getSoundManager().setupAudioStream(
							DeviceImpl.GetInstance().GetSipManager().getSipProfile()
									.getLocalIp()));
			this.finish();
			break;

		case (R.id.btnHangup):
			DeviceImpl.GetInstance().GetSipManager().RejectCall();
			this.finish();
			break;
		}

	}
}
*/
