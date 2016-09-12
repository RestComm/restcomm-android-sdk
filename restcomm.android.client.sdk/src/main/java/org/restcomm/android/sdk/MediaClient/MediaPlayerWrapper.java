package org.restcomm.android.sdk.MediaClient;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

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

public class MediaPlayerWrapper {
   private final Context androidContext;
   private MediaPlayer mediaPlayer = null;

   MediaPlayerWrapper(Context androidContext)
   {
      this.androidContext = androidContext;
   }

   void play(int resid, boolean loop)
   {
      if (mediaPlayer != null) {
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;
      }

      mediaPlayer = MediaPlayer.create(androidContext, resid);
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      mediaPlayer.setLooping(loop);
      mediaPlayer.start();
   }

   void stop()
   {
      if (mediaPlayer != null) {
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;
      }
   }

   void close()
   {
      if (mediaPlayer != null) {
         mediaPlayer.stop();
         mediaPlayer.release();
         mediaPlayer = null;
      }
   }

}
