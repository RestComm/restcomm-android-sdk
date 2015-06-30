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

public class SoundManager {
	Context appContext;
	AudioManager audio;
	AudioStream audioStream;
	AudioGroup audioGroup;
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
	public void setupAudio(int remoteRtpPort, String remoteIp) {

		System.out.println("@@@@ Setting up Audio: " + remoteIp + "/" + remoteRtpPort);
		try {
			audioStream.associate(
					InetAddress.getByName(remoteIp),
					remoteRtpPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}  catch (IllegalStateException e) {
			e.printStackTrace();
		}

		try {
			audioStream.join(audioGroup);
		}
		catch (IllegalStateException e) {
			e.printStackTrace();
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
}
