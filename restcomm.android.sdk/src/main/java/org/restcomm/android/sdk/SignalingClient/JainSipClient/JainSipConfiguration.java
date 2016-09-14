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

package org.restcomm.android.sdk.SignalingClient.JainSipClient;

import java.util.HashMap;
import java.util.Map;

public class JainSipConfiguration {
   private static final String TAG = "JainSipConfiguration";

   // compares old and new parameters and returns a map with new keys as well as modified keys
   static HashMap<String, Object> modifiedParameters(HashMap<String, Object> oldParameters, HashMap<String, Object> newParameters)
   {
      HashMap<String, Object> modifiedParameters = new HashMap<String, Object>();
      for (Map.Entry<String, Object> entry : newParameters.entrySet()) {
         if (!oldParameters.containsKey(entry.getKey()) ||
               (oldParameters.containsKey(entry.getKey()) && !oldParameters.get(entry.getKey()).equals(newParameters.get(entry.getKey())))) {
            modifiedParameters.put(entry.getKey(), entry.getValue());
         }
      }
      return modifiedParameters;
   }

   public static HashMap<String, Object> mergeParameters(HashMap<String, Object> baseParameters, HashMap<String, Object> newParameters)
   {
      HashMap<String, Object> mergedParameters = new HashMap<String, Object>();
      mergedParameters.putAll(baseParameters);
      mergedParameters.putAll(newParameters);
      return mergedParameters;
   }

   static boolean getBoolean(HashMap<String, Object> parameters, String key)
   {
      if (parameters.containsKey(key) && (Boolean) parameters.get(key)) {
         return true;
      }
      return false;
   }

}
