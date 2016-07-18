package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import org.mobicents.restcomm.android.client.sdk.RCClient;

class JainSipException extends Exception {
   RCClient.ErrorCodes errorCode;
   String errorText;

   JainSipException(RCClient.ErrorCodes errorCode, String errorText)
   {
      this.errorCode = errorCode;
      this.errorText = errorText;
   }
}
