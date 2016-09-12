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
import org.restcomm.android.sdk.RCDevice;

import java.util.HashMap;

/*
 * Various internal SDK utilities not to be directly used by App
 */
public class RCUtils {
   public static ErrorStruct validateParms(HashMap<String, Object> parameters)
   {
      /*
      if (parameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED) &&
            ((Boolean)parameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED))) {
            */

      if (!parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_USERNAME) ||
            parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME).equals("")) {
         return new ErrorStruct(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
      }

      // all those fields are mandatory irrespective if we use TURN or not. Even to only get the STUN url, we need all of them
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_URL) ||
            parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL).equals("")) {
         return new ErrorStruct(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_URL);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME) ||
            parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME).equals("")) {
         return new ErrorStruct(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_USERNAME);
      }
      if (!parameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD) ||
            parameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD).equals("")) {
         return new ErrorStruct(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_PASSWORD);
      }

      return new ErrorStruct(RCClient.ErrorCodes.SUCCESS);
   }

}
