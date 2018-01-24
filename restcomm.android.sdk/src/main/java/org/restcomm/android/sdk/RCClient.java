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

package org.restcomm.android.sdk;


/**
 * Facility class to publish library's Error Codes and mapping with Error Text
 *
 * @see RCDevice
 * @see RCConnection
 */
public final class RCClient {
   private static RCClient instance = null;
   private static boolean initialized = false;

   /**
    * Error codes for the SDK. They can be broken into three categories: 1. RCDevice related, starting with ERROR_DEVICE_*,
    * 2. RCConnection related, starting with ERROR_CONNECTION_*, and 3. Text message related, starting with ERROR_MESSAGE_*,
    */
   public enum ErrorCodes {
      SUCCESS,
      ERROR_DEVICE_MISSING_CALL_INTENT,
      ERROR_DEVICE_MISSING_USERNAME,
      ERROR_DEVICE_MISSING_ICE_URL,
      ERROR_DEVICE_MISSING_ICE_USERNAME,
      ERROR_DEVICE_MISSING_ICE_PASSWORD,
      ERROR_DEVICE_MISSING_ICE_DOMAIN,
      ERROR_DEVICE_MISSING_CUSTOM_DISCOVERY_ICE_SERVER,
      ERROR_DEVICE_INVALID_ICE_SERVER_DISCOVERY_TYPE,
      ERROR_DEVICE_INVALID_ICE_SERVERS_NOT_CUSTOM_DISCOVERY,
      ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY_NO_ICE_SERVERS,
      ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY,
      ERROR_DEVICE_NO_CONNECTIVITY,
      ERROR_DEVICE_ALREADY_INITIALIZED,
      ERROR_DEVICE_ALREADY_OPEN,
      ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN,
      ERROR_DEVICE_REGISTER_TIMEOUT,
      ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT,
      ERROR_DEVICE_REGISTER_URI_INVALID,
      ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE,
      ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER,
      ERROR_DEVICE_FAILED_TO_START_NETWORKING,
      ERROR_DEVICE_REGISTER_INTENT_CALL_MISSING,
      ERROR_DEVICE_REGISTER_INTENT_MESSAGE_MISSING,
      ERROR_DEVICE_SIGNALING_FACILITIES_ALREADY_INITIALIZED,
      ERROR_DEVICE_SIGNALING_DOMAIN_INVALID,

      ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN,
      ERROR_CONNECTION_DEVICE_NOT_READY,
      ERROR_CONNECTION_URI_INVALID,
      ERROR_CONNECTION_PEER_UNAVAILABLE,
      ERROR_CONNECTION_SIGNALING_TIMEOUT,
      ERROR_CONNECTION_MEDIA_TIMEOUT,
      ERROR_CONNECTION_COULD_NOT_CONNECT,
      ERROR_CONNECTION_PEER_NOT_FOUND,
      ERROR_CONNECTION_SERVICE_UNAVAILABLE,
      ERROR_CONNECTION_SERVICE_INTERNAL_ERROR,
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
      ERROR_CONNECTION_PERMISSION_DENIED,
      ERROR_CONNECTION_UNTRUSTED_SERVER,
      ERROR_CONNECTION_MISSING_PEER,
      ERROR_CONNECTION_VIDEO_CALL_VIEWS_MANDATORY,
      ERROR_CONNECTION_AUDIO_CALL_VIDEO_CODEC_FORBIDDEN,
      ERROR_CONNECTION_AUDIO_CALL_VIDEO_RESOLUTION_FORBIDDEN,
      ERROR_CONNECTION_AUDIO_CALL_VIDEO_FRAME_RATE_FORBIDDEN,
      ERROR_CONNECTION_WEBRTC_CANDIDATES_TIMED_OUT,

      ERROR_MESSAGE_AUTHENTICATION_FORBIDDEN,
      ERROR_MESSAGE_URI_INVALID,
      ERROR_MESSAGE_PEER_UNAVAILABLE,
      ERROR_MESSAGE_TIMEOUT,
      ERROR_MESSAGE_COULD_NOT_CONNECT,
      ERROR_MESSAGE_PEER_NOT_FOUND,
      ERROR_MESSAGE_SERVICE_UNAVAILABLE,
      ERROR_MESSAGE_UNTRUSTED_SERVER,
      ERROR_MESSAGE_SEND_FAILED_DEVICE_OFFLINE,

      ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_SID_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_CLIENT_SID_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_CREDENTIALS_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_BINDING_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_EMAIL_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_PASSWORD_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_PUSH_DOMAIN_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_RESTCOMM_DOMAIN_MISSING,
      ERROR_DEVICE_PUSH_NOTIFICATION_ENABLE_DISABLE_PUSH_NOTIFICATION,
      ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_HTTP_DOMAIN,
      ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_PUSH_DOMAIN,
      ERROR_DEVICE_PUSH_NOTIFICATION_UNKNOWN_ERROR,
      ERROR_DEVICE_PUSH_NOTIFICATION_AUTHENTICATION_FORBIDDEN,
      ERROR_DEVICE_PUSH_NOTIFICATION_HTTP_NOT_FOUND,
      ERROR_DEVICE_PUSH_NOTIFICATION_TIMED_OUT
   }

   /**
    * Maps the error codes above with an error description, to get more detailed information on what happened
    * @param errorCode: The error code
    * @return The error text corresponding to the passed errorCode
    */
   public static String errorText(ErrorCodes errorCode)
   {
      if (errorCode == ErrorCodes.SUCCESS) {
         return "Success";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_CALL_INTENT) {
         return "Device parameter validation error; call intent is missing";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_USERNAME) {
         return "Device parameter validation error; username is mandatory";
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
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_ICE_DOMAIN) {
         return "Device parameter validation error; ICE domain is mandatory";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_MISSING_CUSTOM_DISCOVERY_ICE_SERVER) {
         return "Device parameter validation error; ICE server is missing url";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_INVALID_ICE_SERVER_DISCOVERY_TYPE) {
         return "Device parameter validation error; ICE server discovery type out of range";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_INVALID_ICE_SERVERS_NOT_CUSTOM_DISCOVERY) {
         return "Device parameter validation error; ICE servers list shouldn't be passed when media discovery type is not ICE_SERVERS_CUSTOM";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY_NO_ICE_SERVERS) {
         return "Device parameter validation error; media discovery type is ICE_SERVERS_CUSTOM, but no ICE servers list provided";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY) {
         return "Device parameter validation error; media discovery type is ICE_SERVERS_CUSTOM so none of MEDIA_ICE_URL, MEDIA_ICE_USERNAME, MEDIA_ICE_PASSWORD, MEDIA_ICE_DOMAIN should be provided";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY) {
         return "Device has no connectivity";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_ALREADY_INITIALIZED) {
         return "Device initialization failed; device is already initialized";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_ALREADY_OPEN) {
         return "Device initialization failed; device's signaling facilities already open";
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
         return "Device register domain URI is invalid";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE) {
         return "Device failed to register; service unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER) {
         return "Device failed to register; server is not trusted";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_FAILED_TO_START_NETWORKING) {
         return "Device networking facilities failed to start; please check if signaling port is already in use";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_INTENT_CALL_MISSING) {
         return "Device networking facilities failed to start; please check if intent for call is missing.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_REGISTER_INTENT_MESSAGE_MISSING) {
         return "Device networking facilities failed to start; please check if intent for message is missing.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_SIGNALING_FACILITIES_ALREADY_INITIALIZED) {
         return "Device initialization failed; siginaling facilities already intialized";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_SIGNALING_DOMAIN_INVALID) {
         return "Device signaling domain is invalid";
      }

      else if (errorCode == ErrorCodes.ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN) {
         return "Connection failed to authenticate with Service";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_DEVICE_NOT_READY) {
         return "Failed to initiate connection; Device is not in READY state";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_URI_INVALID) {
         return "Connection URI is invalid";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_PEER_UNAVAILABLE) {
         return "Failed to initiate connection; peer is unavailable";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_SIGNALING_TIMEOUT) {
         return "Connection timed out (signaling)";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_MEDIA_TIMEOUT) {
         return "Connection timed out (media)";
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
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_SERVICE_INTERNAL_ERROR) {
         return "Failed to initiate connection; service internal error";
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
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_PERMISSION_DENIED) {
         return "Failed to initiate connection; missing Android permissions";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_UNTRUSTED_SERVER) {
          return "Failed to initiate connection; server not trusted";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_MISSING_PEER) {
          return "Failed to initiate connection due to parameter validation error; missing peer";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_VIDEO_CALL_VIEWS_MANDATORY) {
         return "Failed to initiate connection due to parameter validation error; video call made without passing local and/or remote views";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_CODEC_FORBIDDEN) {
         return "Failed to initiate connection due to parameter validation error; video codec not allowed to be specified in an audio call";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_RESOLUTION_FORBIDDEN) {
         return "Failed to initiate connection due to parameter validation error; video resolution not allowed to be specified in an audio call";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_AUDIO_CALL_VIDEO_FRAME_RATE_FORBIDDEN) {
         return "Failed to initiate connection due to parameter validation error; video frame rate not allowed to be specified in an audio call";
      }
      else if (errorCode == ErrorCodes.ERROR_CONNECTION_WEBRTC_CANDIDATES_TIMED_OUT) {
         return "Failed to collect any candidates on time; please check your network settings and connectivity or consider increasing candidate timeout";
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
      else if (errorCode == ErrorCodes.ERROR_MESSAGE_SEND_FAILED_DEVICE_OFFLINE) {
         return "Failed to send message; RCDevice is offline";
      }
      else  if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_SID_MISSING) {
          return "Failed to register/update for push notification; Account sid cannot be found";
      }
      else  if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_CLIENT_SID_MISSING) {
          return "Failed to register/update for push notification; Client sid cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_CREDENTIALS_MISSING){
          return "Failed to register/update for push notification; Credentials cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_BINDING_MISSING) {
         return "Failed to register/update for push notification; Binding cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_MISSING){
         return "Failed to register/update for push notification; Application cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING){
         return "Failed to register/update for push notification; fcm server key cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING){
         return "Failed to register/update for push notification; Application name cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_EMAIL_MISSING){
         return "Failed to register/update for push notification; Account email cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_PASSWORD_MISSING){
         return "Failed to register/update for push notification; Account password cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_PUSH_DOMAIN_MISSING){
         return "Failed to register/update for push notification; Push domain cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_RESTCOMM_DOMAIN_MISSING){
         return "Failed to register/update for push notification; Restcomm Connect Domain cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ENABLE_DISABLE_PUSH_NOTIFICATION){
         return "Failed to register/update for push notification; Enable/Disable for push cannot be found";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_HTTP_DOMAIN){
         return "Failed to register/update for push notification; Invalid http domain.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_PUSH_DOMAIN){
         return "Failed to register/update for push notification; Invalid push domain.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_UNKNOWN_ERROR){
         return "Failed to register/update for push notification; Unknown error.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_AUTHENTICATION_FORBIDDEN){
         return "Failed to register/update for push notification; Could not authenticate.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_HTTP_NOT_FOUND){
         return "Failed to register/update for push notification; Http 404 not found.";
      }
      else if (errorCode == ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_TIMED_OUT){
         return "Failed to register/update for push notification; Request timed out.";
      }

      return "Unmapped Restcomm Client error: " + errorCode;
   }

   protected RCClient()
   {
      // Exists to defeat instantiation.
   }

   /*
   static ArrayList<RCDevice> list;
   static Context context;


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
      }
   }

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

   public static boolean isInitialized()
   {
      return initialized;
   }

   public static RCDevice createDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener)
   {
      if (!initialized) {
         RCLogger.i(TAG, "Attempting to create RCDevice without first initializing RCClient");
         return null;
      }

      if (list.size() == 0) {
         // check if RCDevice parameters are ok
         ErrorStruct errorStruct = RCUtils.validateDeviceParms(parameters);
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

   public interface RCInitListener {
      void onInitialized();
      void onError(Exception exception);
   }
   */
}
