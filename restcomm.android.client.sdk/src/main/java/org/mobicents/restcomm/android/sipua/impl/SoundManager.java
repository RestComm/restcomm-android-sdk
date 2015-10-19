package org.mobicents.restcomm.android.sipua.impl;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.os.Handler;
import android.util.Log;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.client.sdk.R;

public class SoundManager implements AudioManager.OnAudioFocusChangeListener {
	Context appContext;

	AudioManager audioManager;
	MediaPlayer ringingPlayer;
	MediaPlayer callingPlayer;
	MediaPlayer messagePlayer;
	boolean incomingOn = true, outgoingOn = true, disconnectOn = true;
	private static final String TAG = "SoundManager";

	public SoundManager(Context appContext, String ip){
		this.appContext = appContext;
		audioManager = (AudioManager)this.appContext.getSystemService(Context.AUDIO_SERVICE);
		// Setup Media (notice that I'm not preparing the media as create does that implicitly plus
		// I'm not ever stopping a player -instead I'm pausing so no additional preparation is needed
		// there either. We might need to revisit this at some point though
		ringingPlayer = MediaPlayer.create(this.appContext, R.raw.ringing);
		ringingPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		ringingPlayer.setLooping(true);
		callingPlayer = MediaPlayer.create(this.appContext, R.raw.calling);
		callingPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		callingPlayer.setLooping(true);
		messagePlayer = MediaPlayer.create(this.appContext, R.raw.message);
		messagePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		audioManager = (AudioManager)this.appContext.getSystemService(Context.AUDIO_SERVICE);
	}

	// handle mutes for the sounds
	public void setIncoming(boolean on)
	{
		incomingOn = on;
	}
	public void setOutgoing(boolean on)
	{
		outgoingOn = on;
	}
	public void setDisconnect(boolean on)
	{
		disconnectOn = on;
	}
	public boolean getIncoming()
	{
		return incomingOn;
	}
	public boolean getOutgoing()
	{
		return outgoingOn;
	}
	public boolean getDisconnect()
	{
		return disconnectOn;
	}

	public void startRinging()
	{
		if (incomingOn == false) {
			return;
		}
		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				int result = audioManager.requestAudioFocus(sm, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
				if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					ringingPlayer.start();
					RCLogger.i(TAG, "startRinging start()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}
	public void stopRinging()
	{
		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				if (ringingPlayer.isPlaying()) {
					ringingPlayer.pause();
					// Abandon audio focus when playback complete
					audioManager.abandonAudioFocus(sm);
					RCLogger.i(TAG, "stopRinging pause()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}
	public void startCalling()
	{
		if (!outgoingOn) {
			return;
		}

		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				int result = audioManager.requestAudioFocus(sm, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
				if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					callingPlayer.start();
					RCLogger.i(TAG, "startCalling start()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}
	public void stopCalling()
	{
		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				if (callingPlayer.isPlaying()) {
					callingPlayer.pause();
					// Abandon audio focus when playback complete
					audioManager.abandonAudioFocus(sm);
					RCLogger.i(TAG, "stopCalling pause()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}
	public void incomingMessage()
	{
		if (!incomingOn) {
			return;
		}

		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				int result = audioManager.requestAudioFocus(sm, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
				if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					messagePlayer.start();
					RCLogger.i(TAG, "incomingMessage start()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}

	public void outgoingMessage()
	{
		if (!outgoingOn) {
			return;
		}

		final SoundManager sm = this;
		// Important: need to fire the event in UI context cause we might be in JAIN SIP thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				int result = audioManager.requestAudioFocus(sm, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
				if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					messagePlayer.start();
					RCLogger.i(TAG, "outgoingMessage start()");
				}
			}
		};
		mainHandler.post(myRunnable);
	}

	// Callbacks for auio focus change events
	public void onAudioFocusChange(int focusChange)
	{
		RCLogger.i(TAG, "onAudioFocusChange: " + focusChange);
		/*
		if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
			// Pause playback
		}
		else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			// Resume playback or raise it back to normal if we were ducked
		}
		else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
			//am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
			audio.abandonAudioFocus(this);
			// Stop playback
		}
		else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // Lower the volume
        }
		*/
	}
}
