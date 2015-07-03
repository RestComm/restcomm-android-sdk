package org.mobicents.restcomm.android.sipua.impl;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.content.Context;
import android.media.AudioManager;
import android.net.rtp.AudioCodec;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.net.rtp.RtpStream;
import android.util.Log;

public class SoundManager implements AudioManager.OnAudioFocusChangeListener {
	Context appContext;
	AudioManager audio;
	AudioStream audioStream;
	AudioGroup audioGroup;
	InetAddress localAddress;
	private static final String TAG = "SoundManager";

	public SoundManager(Context appContext, String ip){
		this.appContext = appContext;
		audio = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
		try {
			localAddress = InetAddress.getByName(ip);
			audioGroup = new AudioGroup();
			audioGroup.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);
			//audioGroup.setMode(AudioGroup.MODE_NORMAL);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	public int setupAudioStream() {
		Log.i(TAG, "Setting up Audio Stream");
		try {
			audioStream = new AudioStream(localAddress);
			audioStream.setCodec(AudioCodec.PCMU);
			audioStream.setMode(RtpStream.MODE_NORMAL);
		}
		catch (SocketException e) {
			e.printStackTrace();
		}

		return audioStream.getLocalPort();
	}

	// Start sending/receiving media
	public void startStreaming(int remoteRtpPort, String remoteIp) {

		Log.i(TAG, "Starting streaming: " + remoteIp + "/" + remoteRtpPort);

		audio.setMode(AudioManager.MODE_IN_COMMUNICATION);

		// Request audio focus for playback
		int result = audio.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

		//////// DEBUG
		if (audio.isBluetoothA2dpOn()) {
			// Adjust output for Bluetooth.
			Log.i(TAG, "Using Bluetooth");
		} else if (audio.isSpeakerphoneOn()) {
			// Adjust output for Speakerphone.
			Log.i(TAG, "Using Speaker");
		} else if (audio.isMicrophoneMute()) {
			// Adjust output for headsets
			Log.i(TAG, "Using Microphone is mute");
		} else {
			// If audio plays and noone can hear it, is it still playing?
			Log.i(TAG, "Using None ??");
			//audio.setSpeakerphoneOn(true);
		}
		//audio.setSpeakerphoneOn(false);

		Log.i(TAG, "Vol/max: " + audio.getStreamVolume(audio.STREAM_VOICE_CALL) + "/" + audio.getStreamMaxVolume(audio.STREAM_VOICE_CALL));

		if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			try {
				audioStream.associate(
						InetAddress.getByName(remoteIp),
						remoteRtpPort);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			}

			try {
				audioStream.join(audioGroup);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			}
		}
		else {
			Log.e(TAG, "Cannot receive audio focus; media stream not setup");

		}

	}

	public void stopStreaming() {
		AudioStream streams[] = audioGroup.getStreams();
		for (int i = 0; i < streams.length; i++) {
			Log.i(TAG, "-------- Stream: " + i);
			AudioCodec codec = streams[i].getCodec();
			Log.i(TAG, "fmtp: " + codec.fmtp + ", rtpmap: " + codec.rtpmap + ", type: " + codec.type);
		}

		System.out.println("Releasing Audio: ");
		try {
			audioStream.join(null);
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}

		audioGroup.clear();
		if (audioStream.isBusy()) {
			Log.i(TAG, "AudioStream is busy");
		}
		//audioStream.release();
		audioStream = null;
		audio.setMode(AudioManager.MODE_NORMAL);

		// Abandon audio focus when playback complete
		audio.abandonAudioFocus(this);
	}

	public void muteAudio(boolean muted)
	{
		System.out.println("Muting audio: " + muted);
		if (muted) {
			if (audioGroup.getMode() != audioGroup.MODE_MUTED) {
				audioGroup.setMode(audioGroup.MODE_MUTED);
			}
		}
		else {
			if (audioGroup.getMode() == audioGroup.MODE_MUTED) {
				audioGroup.setMode(audioGroup.MODE_NORMAL);
			}
		}
	}

	// Callbacks for auio focus change events
	public void onAudioFocusChange(int focusChange)
	{
		Log.i(TAG, "onAudioFocusChange: " + focusChange);
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
