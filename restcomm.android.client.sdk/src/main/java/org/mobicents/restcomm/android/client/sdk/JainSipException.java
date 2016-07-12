package org.mobicents.restcomm.android.client.sdk;

public class JainSipException extends Exception {
   RCClient.ErrorCodes errorCode;
   String errorText;

   JainSipException(RCClient.ErrorCodes errorCode, String errorText)
   {
      this.errorCode = errorCode;
      this.errorText = errorText;
   }
}
