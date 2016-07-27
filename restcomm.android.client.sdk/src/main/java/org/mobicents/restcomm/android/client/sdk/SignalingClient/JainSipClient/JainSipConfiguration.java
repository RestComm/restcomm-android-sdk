package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

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
