/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.util;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;

import java.util.HashMap;

/*
 * Various internal SDK utilities not to be directly used by App
 */
public class RCUtils {
   private static final String TAG = "RCUtils";
   public static void validateDeviceParms(HashMap<String, Object> parameters) throws RCException
   {
      validatePreferenceParms(parameters);

      if (!parameters.containsKey(RCDevice.ParameterKeys.INTENT_INCOMING_CALL)) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_CALL_INTENT);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE)) {
         RCLogger.w(TAG, "validateDeviceParms(): Intent missing for incoming text messages, your App will work but won't be able to be notified on such event");
      }


      //return new ErrorStruct(RCClient.ErrorCodes.SUCCESS);
   }

   public static void validatePreferenceParms(HashMap<String, Object> parameters) throws RCException
   {
      /*
      if (parameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED) &&
            ((Boolean)parameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED))) {
       */

      if (!parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_USERNAME) ||
              parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
         //return new ErrorStruct(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
      }

      // all those fields are mandatory irrespective if we use TURN or not. Even to only get the STUN url, we need all of them
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_URL) ||
              parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_URL);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME) ||
              parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_USERNAME);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD) ||
              parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_PASSWORD);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN) ||
              parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_DOMAIN);
      }
   }

   public static void validateConnectionParms(HashMap<String, Object> parameters) throws RCException
   {
      if (!parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_PEER) ||
              parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER).equals("")) {
         throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_MISSING_PEER);
      }
      if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED) &&
              (boolean)parameters.get(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED)) {
         // video call

         if (!parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO) ||
                 parameters.get(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO) == null ||
                 !parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO) ||
                 parameters.get(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO) == null) {
            throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_VIDEO_CALL_VIEWS_MANDATORY);
         }
      }
      else {
         // audio-only call
         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO)) {
            RCLogger.w(TAG, "validateConnectionParms(): WARN, local video  doesn't take effect since the call is audio-only" + parameters.toString());
         }

         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO)) {
            RCLogger.w(TAG, "validateConnectionParms(): WARN, remote video doesn't take effect since the call is audio-only" + parameters.toString());
         }

         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC)) {
            // should not throw exception because we 'll be breaking backwards compatibility pretty badly. Let's instead log this
            //throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_CODEC_FORBIDDEN);
            RCLogger.w(TAG, "validateConnectionParms(): WARN, video codec doesn't take effect since the call is audio-only" + parameters.toString());
         }
         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION)) {
            //throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_RESOLUTION_FORBIDDEN);
            RCLogger.w(TAG, "validateConnectionParms(): WARN, video resolution doesn't take effect since the call is audio-only" + parameters.toString());
         }
         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE)) {
            //throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_FRAME_RATE_FORBIDDEN);
            RCLogger.w(TAG, "validateConnectionParms(): WARN, video frame rate doesn't take effect since the call is audio-only" + parameters.toString());
         }

      }

   }
}
