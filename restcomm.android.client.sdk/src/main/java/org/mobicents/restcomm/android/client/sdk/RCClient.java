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

package org.mobicents.restcomm.android.client.sdk;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;

import org.mobicents.restcomm.android.client.sdk.util.ErrorStruct;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;
import org.mobicents.restcomm.android.client.sdk.util.RCUtils;


/**
 * Top level singleton to initialize and shut down the Restcomm Client SDK. RCClient is also responsible
 * for creating the RCDevice object that represents a virtual device that can create connections and
 * send text messages.
 *
 * @see RCDevice
 * @see RCConnection
 */
public class RCClient {
   private static RCClient instance = null;
   private static boolean initialized = false;

   public enum ErrorCodes {
      SUCCESS,
      ERROR_DEVICE_MISSING_ICE_URL,
      ERROR_DEVICE_MISSING_ICE_USERNAME,
      ERROR_DEVICE_MISSING_ICE_PASSWORD,
      ERROR_DEVICE_NO_CONNECTIVITY,
      ERROR_DEVICE_ALREADY_OPEN,
      ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN,
      ERROR_DEVICE_REGISTER_TIMEOUT,
      ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT,
      ERROR_DEVICE_REGISTER_URI_INVALID,
      ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE,
      ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER,

      ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN,
      ERROR_CONNECTION_URI_INVALID,
      ERROR_CONNECTION_PEER_UNAVAILABLE,
      ERROR_CONNECTION_TIMEOUT,
      ERROR_CONNECTION_COULD_NOT_CONNECT,
      ERROR_CONNECTION_PEER_NOT_FOUND,
      ERROR_CONNECTION_SERVICE_UNAVAILABLE,
      ERROR_CONNECTION_PARSE_CUSTOM_SIP_HEADERS,
      ERROR_CONNECTION_ACCEPT_FAILED,
      ERROR_CONNECTION_ACCEPT_WRONG_STATE,
      ERROR_CONNECTION_IGNORE_WRONG_STATE,
      ERROR_CONNECTION_REJECT_WRONG_STATE,
      ERROR_CONNECTION_DISCONNECT_WRONG_STATE,
      ERROR_CONNECTION_DISCONNECT_FAILED,
      ERROR_CONNECTION_DECLINE_FAILED,
      ERROR_CONNECTION_DTMF_DIGITS_FAILED,
      ERROR_CONNECTION_DTMF_DIGITS_WRONG_STATE,
      ERROR_CONNECTION_REGISTRARLESS_FULL_URI_REQUIRED,
      ERROR_CONNECTION_WEBRTC_PEERCONNECTION_ERROR,
      ERROR_CONNECTION_WEBRTC_TURN_ERROR,
      ERROR_CONNECTION_UNTRUSTED_SERVER,

      ERROR_MESSAGE_AUTHENTICATION_FORBIDDEN,
      ERROR_MESSAGE_URI_INVALID,
      ERROR_MESSAGE_PEER_UNAVAILABLE,
      ERROR_MESSAGE_TIMEOUT,
      ERROR_MESSAGE_COULD_NOT_CONNECT,
      ERROR_MESSAGE_PEER_NOT_FOUND,
      ERROR_MESSAGE_SERVICE_UNAVAILABLE,
      ERROR_MESSAGE_UNTRUSTED_SERVER,
   }

   public static String errorText(ErrorCodes errorCode)
   {
      if (errorCode == ErrorCodes.SUCCESS) {
         return "Success";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_ICE_URL) {
         return "Device parameter validation error; ICE URL is mandatory";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_ICE_USERNAME) {
         return "Device parameter validation error; ICE username is mandatory";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_ICE_PASSWORD) {
         return "Device parameter validation error; ICE password is mandatory";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY) {
         return "Device has no connectivity";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_ALREADY_OPEN) {
         return "Device initialization failed; device is already open";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT) {
         return "Device registration with Restcomm timed out";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN) {
         return "Device failed to authenticate with Service";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT) {
         // returned when there is an issue connecting to the domain URI, like no process listening to the server port,
         // wrong server domain/ip is used, or domain is not resolvable
         return "Device could not connect to Service";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_URI_INVALID) {
         return "Register Domain URI is invalid";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE) {
         return "Device failed to register; service unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER) {
         return "Device failed to register; server is not trusted";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN) {
         return "Connection failed to authenticate with Service";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_URI_INVALID) {
         return "Connection URI is invalid";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_PEER_UNAVAILABLE) {
         return "Failed to initiate connection; peer is unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_TIMEOUT) {
         return "Connection timed out";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_COULD_NOT_CONNECT) {
         // returned when there is an issue connecting to the destination URI, like no process listening to the server port,
         // wrong server domain/ip is used, or domain is not resolvable
         return "Failed to initiate connection; could not connect to service";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_PEER_NOT_FOUND) {
         return "Failed to initiate connection; peer not found";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_SERVICE_UNAVAILABLE) {
         return "Failed to initiate connection; service is unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_PARSE_CUSTOM_SIP_HEADERS) {
         return "Failed to initiate connection; error parsing custom SIP headers";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED) {
         return "Failed to disconnect connection";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_ACCEPT_FAILED) {
         return "Failed to accept connection";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_ACCEPT_WRONG_STATE) {
         return "Failed to accept connection; connection state should be CONNECTING";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_IGNORE_WRONG_STATE) {
         return "Failed to ignore connection; connection state should be CONNECTING";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_REJECT_WRONG_STATE) {
         return "Failed to reject connection; connection state should be CONNECTING";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE) {
         return "Failed to disconnect connection; connection state is already DISCONNECTED";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DECLINE_FAILED) {
         return "Failed to decline connection";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_FAILED) {
         return "Failed to send DTMF digits over connection";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_WRONG_STATE) {
         return "Failed to send DTMF digits; connection state should be CONNECTED";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_REGISTRARLESS_FULL_URI_REQUIRED) {
         return "Failed to initiate connection: when RCDevice is configured with no domain you need to provide full SIP URI in connection peer";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_WEBRTC_PEERCONNECTION_ERROR) {
         return "Webrtc Peer Connection error";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_WEBRTC_TURN_ERROR) {
         return "Error retrieving TURN servers";
      }

      else if (errorCode == ErrorCodes.ERROR_MESSAGE_AUTHENTICATION_FORBIDDEN) {
         return "Message failed to authenticate with Service";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_URI_INVALID) {
         return "Message URI is invalid";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_PEER_UNAVAILABLE) {
         return "Failed to send message; peer is unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_TIMEOUT) {
         return "Message timed out";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_COULD_NOT_CONNECT) {
         // returned when there is an issue connecting to the destination URI, like no process listening to the server port,
         // wrong server domain/ip is used, or domain is not resolvable
         return "Failed to send message; could not connect to service";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_PEER_NOT_FOUND) {
         return "Failed to send message; peer not found";
      }
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_SERVICE_UNAVAILABLE) {
         return "Failed to send message; service is unavailable";
      }

      return "Unmapped Restcomm Client error";
   }

   static ArrayList<RCDevice> list;
   static Context context;
   private static final String TAG = "RCClient";


   protected RCClient()
   {
      // Exists to defeat instantiation.
   }

   // SDK users need to use initialize()
   private static RCClient getInstance()
   {
      if (instance == null) {
         instance = new RCClient();
      }
      return instance;
   }

   public static Context getContext()
   {
      return context;
   }

   /**
    * Initialize the Restcomm Client SDK
    *
    * @param context  The Android Activity context
    * @param listener The listener for upcoming events from Restcomm Client
    */
   public static void initialize(Context context, final RCInitListener listener)
   {
      if (context == null) {
         throw new IllegalArgumentException("Error: Context cannot be null");
      }
      else if (listener == null) {
         throw new IllegalArgumentException("Error: Listener cannot be null");
      }
      else {
         RCClient.getInstance();
         RCClient.context = context;
         list = new ArrayList<RCDevice>();
         initialized = true;

         listener.onInitialized();

            /*
            TODO: this would probably make more sense at some point
            Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    // notify that we are initialized
                    listener.onInitialized();
                }
            };
            mainHandler.post(myRunnable);
            */
      }
   }

   /**
    * Shut down the Restcomm Client
    */
   public static void shutdown()
   {
      if (!initialized) {
         return;
      }

      if (list.size() > 0) {
         RCDevice device = list.get(0);
         // remove the reference so that RCDevice instance is removed
         list.clear();
         list = null;
         // Need to make sure that shutdown() will finish its job synchronously.
         // Keep in mind that once this block is left device can be claimed by GC
         device.release();
      }
      else {
         RCLogger.e(TAG, "shutdown(): Warning Restcomm Client already shut down, skipping");
      }
      // allow the singleton to be GC'd
      instance = null;
      initialized = false;
   }

   /**
    * Retrieve whether Restcomm Client is initialized
    *
    * @return
    */
   public static boolean isInitialized()
   {
      return initialized;
   }

   /**
    *  Initialize a new RCDevice object with parameters
    *
    * @param parameters  Parameters for the Device entity (prefer using the string constants shown below, i.e. RCDevice.ParameterKeys.*, instead of
    *                    using strings like 'signaling-secure', etc. Possible keys: <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_USERNAME</b>: Identity for the client, like <i>'bob'</i> (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm instance to use, like <i>'cloud.restcomm.com'</i>. Leave empty for registrar-less mode<br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use, like <i>'https://turn.provider.com/turn'</i> (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? If this is the case, then a key pair is generated when
    *                signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *                the System Wide Android CA Store, so that we properly accept legit server certificates (optional) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    * @param deviceListener  The listener for upcoming RCDevice events
    * @return The newly created RCDevice
    * @see RCDevice
    */
   public static RCDevice createDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener)
   {
      if (!initialized) {
         RCLogger.i(TAG, "Attempting to create RCDevice without first initializing RCClient");
         return null;
      }

      if (list.size() == 0) {
         // check if RCDevice parameters are ok
         ErrorStruct errorStruct = RCUtils.validateParms(parameters);
         if (errorStruct.statusCode != RCClient.ErrorCodes.SUCCESS) {
            throw new RuntimeException(errorStruct.statusText);
         }

         RCDevice device = new RCDevice(parameters, deviceListener);
         list.add(device);
      }
      else {
         RCLogger.e(TAG, "Device already exists, so we 're returning this one");
      }

      return list.get(0);
   }

   /**
    * Retrieve a list of active Devices
    *
    * @return List of Devices
    */
   public static ArrayList<RCDevice> listDevices()
   {
      if (!initialized) {
         RCLogger.w(TAG, "RCClient uninitialized");
         return null;
      }
      if (list.size() == 0) {
         RCLogger.e(TAG, "Warning: RCDevice list size is 0");
      }

      return list;
   }

   public static void setLogLevel(int level)
   {
      RCLogger.setLogLevel(level);
   }

    /*
    // TODO: implement
    public static String getVersion()
    {
        return "";
    }
    */

   /**
    * Interface defining callbacks for RCClient, such as when it is fully initialized and in case of error
    */
   public interface RCInitListener {
      /**
       * Callback that is called when RCClient is fully initialized
       */
      void onInitialized();

      /**
       * Callback that is called if there's an error
       */
      void onError(Exception exception);
   }
}
