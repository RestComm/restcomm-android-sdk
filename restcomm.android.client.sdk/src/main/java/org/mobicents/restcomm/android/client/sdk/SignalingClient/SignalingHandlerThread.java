package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.os.Handler;
import android.os.HandlerThread;

// SignalingHandlerThread facilitates a separate thread that installs a handler and takes care of all signaling actions
// and responses towards and from JainSipClient
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
