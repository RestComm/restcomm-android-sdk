package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import org.mobicents.restcomm.android.client.sdk.RCClient;

class JainSipException extends Exception {
   RCClient.ErrorCodes errorCode;
   String errorText;

   JainSipException(RCClient.ErrorCodes errorCode, String errorText)
   {
      this.errorCode = errorCode;
      this.errorText = errorText;
   }

   // initialize an exception, but also chain another exception to it
   JainSipException(RCClient.ErrorCodes errorCode, String errorText, Throwable throwable)
   {
      this.errorCode = errorCode;
      this.errorText = errorText;

      this.initCause(throwable);
   }


}
