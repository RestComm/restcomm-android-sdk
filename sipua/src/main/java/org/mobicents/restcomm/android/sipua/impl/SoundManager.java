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
	private static final String TAG = "SoundManager";

	public SoundManager(Context appContext, String ip){
		this.appContext = appContext;
		audio = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
		try {
			audioStream = new AudioStream(InetAddress.getByName(ip));
			audioStream.setCodec(AudioCodec.PCMU);
			audioStream.setMode(RtpStream.MODE_NORMAL);
			audioGroup = new AudioGroup();
			audioGroup.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);

		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	public void releaseAudioResources() {
		System.out.println("@@@@ Releasing Audio: ");
		try {
			audioStream.join(null);
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}

		//audioStream.release();
		//audioGroup.clear();
		audio.setMode(AudioManager.MODE_NORMAL);

		// Abandon audio focus when playback complete
		audio.abandonAudioFocus(this);
	}

	public int setupAudioStream(String localIp) {
		int localPort = audioStream.getLocalPort();
		System.out.println("@@@@ Updating audioManager Mode, localport: " + localPort);

		audio.setMode(AudioManager.MODE_IN_COMMUNICATION);

		return localPort;
	}

	public int getLocalPort() {
		return audioStream.getLocalPort();
	}

	// Start sending/receiving media
	public void setupAudio(int remoteRtpPort, String remoteIp) {

		System.out.println("@@@@ Setting up Audio: " + remoteIp + "/" + remoteRtpPort);

		// Request audio focus for playback
		int result = audio.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

		//////// DEBUG
		if (audio.isBluetoothA2dpOn()) {
			// Adjust output for Bluetooth.
			Log.i(TAG, "@@@@ Bluetooth");
		} else if (audio.isSpeakerphoneOn()) {
			// Adjust output for Speakerphone.
			Log.i(TAG, "@@@@ Speaker");
		} else if (audio.isMicrophoneMute()) {
			// Adjust output for headsets
			Log.i(TAG, "@@@@ Microphone is mute");
		} else {
			// If audio plays and noone can hear it, is it still playing?
			Log.i(TAG, "@@@@ None ??");
			//audio.setSpeakerphoneOn(true);
		}

		Log.i(TAG, "@@@@: vol/max: " + audio.getStreamVolume(audio.STREAM_VOICE_CALL) + "/" + audio.getStreamMaxVolume(audio.STREAM_VOICE_CALL));

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
	public void muteAudio(boolean muted)
	{
		System.out.println("#### Muting audio: " + muted);
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
		Log.i(TAG, "@@@@ onAudioFocusChange: " + focusChange);
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
