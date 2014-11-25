package org.mobicents.restcomm.android.sdk.impl;

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
	public SoundManager(Context appContext){
		this.appContext = appContext;
	}
	public void releaseAudioResources() {
		audio.setMode(AudioManager.MODE_NORMAL);
		audioStream.release();
		audioGroup.clear();
	}
	public int setupAudioStream(String localIp) {
		audio = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
		audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioGroup = new AudioGroup();
		audioGroup.setMode(AudioGroup.MODE_ECHO_SUPPRESSION);
		try {
			audioStream = new AudioStream(InetAddress.getByName(localIp));
			audioStream.setCodec(AudioCodec.PCMU);
			audioStream.setMode(RtpStream.MODE_NORMAL);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return audioStream.getLocalPort();
	}
	public void setupAudio(int remoteRtpPort, String remoteIp) {

		try {
			audioStream.associate(
					InetAddress.getByName(remoteIp),
					remoteRtpPort);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		audioStream.join(audioGroup);

	}
}
