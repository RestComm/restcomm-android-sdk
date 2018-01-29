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

package org.restcomm.android.olympus.Util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.restcomm.android.olympus.BuildConfig;
import org.restcomm.android.olympus.CallActivity;
import org.restcomm.android.olympus.MessageActivity;
import org.restcomm.android.sdk.RCDevice;

import java.util.HashMap;

public class Utils {

    public final static boolean isValidEmail(CharSequence target) {
        if (target == null) {
            return false;
        }

        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public static HashMap<String, Object> createParameters(SharedPreferences prefs, Context context)
    {
        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, context, CallActivity.class));
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, context, MessageActivity.class));
        params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, ""));
        params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk"));
        params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, prefs.getString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234"));

        // Choose an ICE discovery type
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE,
                RCDevice.MediaIceServersDiscoveryType.values()[Integer.parseInt(prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, "0"))]
        );

        //params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
        //params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
        //params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CUSTOM);

        // Media ICE url is a bit different between V2 and V3. Here are some examples:
        // - For Xirsys V2: https://service.xirsys.com/ice
        // - For Xirsys V3: https://es.xirsys.com/_turn/
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_URL, ""));
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ""));
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ""));
        // V2 Domains are called Channels in V3 organization, but we use the same key in both of them: MEDIA_ICE_DOMAIN
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ""));
        params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));

         /*
         // If MEDIA_ICE_SERVERS_DISCOVERY_TYPE is ICE_SERVERS_CUSTOM the App needs to discover the ICE urls on its own and provide them in a list to the SDK
         List<Map<String, String>> iceServers = new ArrayList<Map<String, String>>();

         // The ICE credentials below are fictional, not to be used
         iceServers.add(RCConnection.createIceServerHashMap("stun:turn01.uswest.xirsys.com", "", ""));
         iceServers.add(RCConnection.createIceServerHashMap("turn:turn01.uswest.xirsys.com:80?transport=udp", "412ff434-0c12-31b7-c722-3b25112266a1", "412ff434-0c12-31b7-c722-2aaf653ab121"));
         // ...
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS, iceServers);
         */

        params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true));


        // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
        // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.user_provided_calling_sound
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.user_provided_calling_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.user_provided_ringing_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.user_provided_declined_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.user_provided_message_sound);

        // WARNING: These are for debugging purposes, NOT for production builds!
        // DEBUG_DISABLE_CERTIFICATE_VERIFICATION is handy when connecting to a testing/staging Restcomm Connect instance that typically has a self-signed certificate which is not acceptable by the client by default.
        // With this setting we override that behavior to accept it. NOT for production!
        params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, prefs.getBoolean(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true));
        //params.put(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, true));

        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME , ""));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL , ""));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD , ""));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, prefs.getBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT , true));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN ,""));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN ,""));
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY ,""));

        return params;
    }
}
