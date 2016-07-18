package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.os.Handler;
import android.os.HandlerThread;

class SignalingHandlerThread extends HandlerThread {
   Handler signalingHandler;
   private static final String TAG = "SignalingHandlerThread";
   //Handler uiHandler;

   SignalingHandlerThread(Handler uiHandler)
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
