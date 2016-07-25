package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.os.Handler;
import android.os.HandlerThread;

// SignalingHandlerThread encapsulates the signaling thread (separate from UI thread) and facilitates asynchronous communication between he two.
// It installs an Android Handler and takes care of all signaling actions from UI thread -> JainSipClient and all responses/events from JainSipClient -> UI thread
class SignalingHandlerThread extends HandlerThread {
   Handler signalingHandler;
   private static final String TAG = "SignalingHandlerThread";
   //Handler uiHandler;

   SignalingHandlerThread(SignalingClient uiHandler)
   {
      super("signaling-handler-thread");
      //this.uiHandler = uiHandler;

      start();
      signalingHandler = new SignalingHandler(this.getLooper(), uiHandler);
   }

   Handler getHandler()
   {
      return signalingHandler;
   }

    /*
    public void start()
    {
        super.start();
    }
    */
}
