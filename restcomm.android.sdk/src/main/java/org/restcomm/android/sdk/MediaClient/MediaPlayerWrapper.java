package org.restcomm.android.sdk.MediaClient;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

import org.restcomm.android.sdk.util.RCLogger;

/*
 * Notice that I'm doing everything in main thread right now, which is bad practice, but since all audio files are so small it doesn't
 * seem to cause any issues and I would like to avoid spending the time to make all this work properly asynchronously.
 *
 * Since only one sound can play at a given time what I did was to take a very simplistic approach where a single MediaPlayer is used
 * and re-initiated with each call
 *
 * Credits for the sounds used:
 *
 * The SDK uses these sounds from freesound:
 * - 'Call Busy.wav' by 'henrique85n' ( http://www.freesound.org/people/henrique85n/ )
 * - 'PhoneRinging.mp3' by 'acclivity' ( http://www.freesound.org/people/acclivity/ )
 */

public class MediaPlayerWrapper implements MediaPlayer.OnCompletionListener {
   private final Context androidContext;
   private MediaPlayer mediaPlayer = null;
   private static final String TAG = "MediaPlayerWrapper";

   MediaPlayerWrapper(Context androidContext)
   {
      RCLogger.v(TAG, "MediaPlayerWrapper()");
      this.androidContext = androidContext;
   }

   void play(int resid, boolean loop)
   {
      RCLogger.v(TAG, "MediaPlayerWrapper.play(): " + resid);
      if (mediaPlayer != null) {
         RCLogger.v(TAG, "MediaPlayerWrapper.play(): reset");
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;
      }

      // Request audio focus before making any device switch.
      ((AudioManager) androidContext.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(null, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

      mediaPlayer = MediaPlayer.create(androidContext, resid);
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      mediaPlayer.setLooping(loop);
      mediaPlayer.start();
      if (!loop) {
         // For non-looping sounds we want an event when they are done so that we can abandon focus
         mediaPlayer.setOnCompletionListener(this);
      }
   }

   void stop()
   {
      RCLogger.v(TAG, "MediaPlayerWrapper.stop()");
      if (mediaPlayer != null) {
         RCLogger.v(TAG, "MediaPlayerWrapper.stop(): stopping");
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;

         ((AudioManager) androidContext.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(null);
      }
   }

   void close()
   {
      RCLogger.v(TAG, "MediaPlayerWrapper.close()");
      if (mediaPlayer != null) {
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;

         ((AudioManager) androidContext.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(null);
      }
   }

   public void onCompletion(MediaPlayer mediaPlayer)
   {
      RCLogger.v(TAG, "onCompletion()");
      ((AudioManager) androidContext.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(null);

      if (this.mediaPlayer != null) {
         RCLogger.v(TAG, "onCompletion(): reset");
         this.mediaPlayer.stop();
         //   mediaPlayer.reset();
         this.mediaPlayer.release();
         this.mediaPlayer = null;
      }
   }
}
