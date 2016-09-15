package org.restcomm.android.sdk;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.restcomm.android.sdk.MediaClient.AppRTCAudioManager;
import org.restcomm.android.sdk.SignalingClient.JainSipClient.JainSipConfiguration;
import org.restcomm.android.sdk.SignalingClient.SignalingClient;
import org.restcomm.android.sdk.util.ErrorStruct;
import org.restcomm.android.sdk.util.RCLogger;
import org.restcomm.android.sdk.util.RCUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>RCDevice represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 * in order to be notified of Restcomm Client events you need to set a listener to RCDevice and implement the applicable methods, and also 'register'
 * to the applicable intents by calling RCDevice.setPendingIntents() and provide one intent for whichever activity will be receiving calls and another
 * intent for the activity receiving messages.
 * If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 * the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 * If there is an incoming connection you will be receiving an intent with action 'RCDevice.INCOMING_CALL', and with it you will get:
 * <ol>
 *    <li>The calling party id via string extra named RCDevice.EXTRA_DID, like: <i>intent.getStringExtra(RCDevice.EXTRA_DID)</i></li>
 *    <li>Whether the incoming call has video enabled via boolean extra named RCDevice.EXTRA_VIDEO_ENABLED, like:
 *       <i>intent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false)</i></li>
 *    <li>Restcomm parameters via serializable extra named RCDevice.EXTRA_CUSTOM_HEADERS, like: <i>intent.getSerializableExtra(RCDevice.EXTRA_CUSTOM_HEADERS)</i>
 *    where you need to cast the result to HashMap<String, String> where each entry is a Restcomm parameter with key and value of type string
 *    (for example you will find the Restcomm Call-Sid under 'X-RestComm-CallSid' key).</li>
 * </ol>
 * At that point you can use RCConnection methods to accept or reject the connection.
 * </p>
 * <p>
 * As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 * through an intent with action 'RCDevice.INCOMING_MESSAGE'. For an incoming message you can retrieve:
 * <ol>
 *    <li>The sending party id via string extra named RCDevice.EXTRA_DID, like: <i>intent.getStringExtra(RCDevice.EXTRA_DID)</i></li>
 *    <li>The actual message text via string extra named RCDevice.INCOMING_MESSAGE_TEXT, like: <i>intent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT)</i></li>
 * </ol>
 * </p>
 * @see RCConnection
 */
public class RCDevice extends Service implements SignalingClient.SignalingClientListener {
   /**
    * Device state
    */
   static DeviceState state;
   /**
    * Device capabilities (<b>Not Implemented yet</b>)
    */
   HashMap<DeviceCapability, Object> capabilities;
   /**
    * Listener that will be receiving RCDevice events described at RCDeviceListener
    */
   RCDeviceListener listener;
   /**
    * Is sound for incoming connections enabled
    */
   boolean incomingSoundEnabled;
   /**
    * Is sound for outgoing connections enabled
    */
   boolean outgoingSoundEnabled;
   /**
    * Is sound for disconnect enabled
    */
   boolean disconnectSoundEnabled;

   /**
    * Device state
    */
   public enum DeviceState {
      OFFLINE, /**
       * Device is offline
       */
      READY, /**
       * Device is ready to make and receive connections
       */
      BUSY,  /** Device is busy */
   }

   /**
    * Device capability (<b>Not Implemented yet</b>)
    */
   public enum DeviceCapability {
      INCOMING,
      OUTGOING,
      EXPIRATION,
      ACCOUNT_SID,
      APPLICATION_SID,
      APPLICATION_PARAMETERS,
      CLIENT_NAME,
   }

   /**
    * Parameter keys for RCClient.createDevice() and RCDevice.updateParams()
    */
   public static class ParameterKeys {
      public static final String INTENT_INCOMING_CALL = "incoming-call-intent";
      public static final String INTENT_INCOMING_MESSAGE = "incoming-message-intent";
      public static final String SIGNALING_USERNAME = "pref_sip_user";
      public static final String SIGNALING_DOMAIN = "pref_proxy_domain";
      public static final String SIGNALING_PASSWORD = "pref_sip_password";
      public static final String SIGNALING_SECURE_ENABLED = "signaling-secure";
      public static final String SIGNALING_LOCAL_PORT = "signaling-local-port";
      public static final String SIGNALING_JAIN_SIP_LOGGING_ENABLED = "jain-sip-logging-enabled";
      public static final String MEDIA_TURN_ENABLED = "turn-enabled";
      public static final String MEDIA_ICE_URL = "turn-url";
      public static final String MEDIA_ICE_USERNAME = "turn-username";
      public static final String MEDIA_ICE_PASSWORD = "turn-password";
      public static final String RESOURCE_SOUND_CALLING = "sound-calling";
      public static final String RESOURCE_SOUND_RINGING = "sound-ringing";
      public static final String RESOURCE_SOUND_DECLINED = "sound-declined";
      public static final String RESOURCE_SOUND_MESSAGE = "sound-message";
   }

   private static final String TAG = "RCDevice";

   // Service Intent actions sent from RCDevice Service -> Call Activity
   public static String ACTION_OUTGOING_CALL = "org.restcomm.android.sdk.ACTION_OUTGOING_CALL";
   public static String ACTION_INCOMING_CALL = "org.restcomm.android.sdk.ACTION_INCOMING_CALL";
   public static String ACTION_INCOMING_CALL_ANSWER_AUDIO = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_ANSWER_AUDIO";
   public static String ACTION_INCOMING_CALL_ANSWER_VIDEO = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_ANSWER_VIDEO";
   public static String ACTION_INCOMING_CALL_DECLINE = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_DECLINE";
   public static String ACTION_OPEN_MESSAGE_SCREEN = "org.restcomm.android.sdk.ACTION_OPEN_MESSAGE_SCREEN";
   public static String ACTION_INCOMING_MESSAGE = "org.restcomm.android.sdk.ACTION_INCOMING_MESSAGE";
   public static String ACTION_CALL_DISCONNECT = "org.restcomm.android.sdk.ACTION_CALL_DISCONNECT";

   // Intents sent by Notification subsystem -> RCDevice Service when user acts on the Notifications
   // Used when user taps in a missed call, where we want it to trigger a new call towards the caller
   public static String ACTION_NOTIFICATION_CALL_DEFAULT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DEFAULT";
   // Used when there's an active call and a foreground notification, and hence the user wants to go back the existing call activity without doing anything else
   public static String ACTION_NOTIFICATION_CALL_OPEN = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_OPEN";
   // Used when there's an active call and a foreground notification, and the user wants to disconnect
   public static String ACTION_NOTIFICATION_CALL_DISCONNECT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DISCONNECT";
   // User deleted the notification (by swiping on the left/right, or deleting all notifications)
   public static String ACTION_NOTIFICATION_CALL_DELETE = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DELETE";
   public static String ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO";
   public static String ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO";
   public static String ACTION_NOTIFICATION_CALL_DECLINE = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DECLINE";
   public static String ACTION_NOTIFICATION_MESSAGE_DEFAULT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_MESSAGE_DEFAULT";
   public static String ACTION_NOTIFICATION_CALL_MUTE_AUDIO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_MUTE_AUDIO";

   // Intent EXTRAs keys
   public static String EXTRA_MESSAGE_TEXT = "org.restcomm.android.sdk.EXTRA_MESSAGE_TEXT";
   public static String EXTRA_DID = "org.restcomm.android.sdk.EXTRA_DID";
   public static String EXTRA_CUSTOM_HEADERS = "org.restcomm.android.sdk.CUSTOM_HEADERS";
   public static String EXTRA_VIDEO_ENABLED = "org.restcomm.android.sdk.VIDEO_ENABLED";
   //public static String EXTRA_ACCEPT_WITH_VIDEO = "org.restcomm.android.sdk.EXTRA_ACCEPT_WITH_VIDEO";

   // Notification ids for calls and messages. Key is the sender and value in the notification id. We mainly need those
   // to make sure that notifications from a specific user are shown in a single slot, to avoid confusing the user. This means
   // that after we add a user in one of the maps they are never removed. Reason is there's a lot of overhead needed of
   // passing all intents back to RCDevice service even for messages and missed calls which are auto-answered, with not
   // too much value, at least for now
   private HashMap<String, Integer> callNotifications;
   private HashMap<String, Integer> messageNotifications;

   private final int ONCALL_NOTIFICATION_ID = 1;
   // Unique notification id (incremented for each new notification). Notice that '1' is reserved for RCConnection.ONCALL_NOTIFICATION_ID
   private int notificationId = 2;
   //private final int NOTIFICATION_ID_CALL = 1;
   //private final int NOTIFICATION_ID_MESSAGE = 2;
   // is an incoming call ringing, triggered by the Notification subsystem?
   private boolean activeCallNotification = false;
   // Numbers here refer to: delay duration, on duration 1, off duration 1, on duration 2, off duration2, ...
   long[] notificationVibrationPattern = { 0, 300, 300, 300, 300 };
   int notificationColor =  Color.parseColor("#3c5866");
   int[] notificationColorPattern = { 2000, 2000 };
   private boolean foregroundNoticationActive = false;

   //public static String EXTRA_NOTIFICATION_ACTION_TYPE = "org.restcomm.android.sdk.NOTIFICATION_ACTION_TYPE";
   //public static String EXTRA_SDP = "com.telestax.restcomm_messenger.SDP";
   //public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
   //public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";

   // Parameters passed in the RCDevice constructor
   private HashMap<String, Object> parameters;
   private Intent callIntent;
   private Intent messageIntent;
   private HashMap<String, RCConnection> connections;
   //private RCConnection incomingConnection;
   private RCDeviceListener.RCConnectivityStatus cachedConnectivityStatus = RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
   private SignalingClient signalingClient;
   private AppRTCAudioManager audioManager = null;
   //private Context context = null;

   // Binder given to clients
   private final IBinder deviceBinder = new RCDeviceBinder();
   // Has RCDevice been initialized?
   boolean isServiceInitialized = false;
   // Is an activity currently attached to RCDevice service?
   boolean isServiceAttached = false;
   // how many Activities are attached to the Service
   private int serviceReferenceCount = 0;

   public enum NotificationType {
      ACCEPT_CALL_VIDEO,
      ACCEPT_CALL_AUDIO,
      REJECT_CALL,
      NAVIGATE_TO_CALL,
   }

   // Apps must not have access to the constructor, as it is created inside the service
   public RCDevice()
   {
   }

   /**
    * Class used for the client Binder.  Because we know this service always
    * runs in the same process as its clients, we don't need to deal with IPC.
    */
   public class RCDeviceBinder extends Binder {
      public RCDevice getService()
      {
         // Return this instance of LocalService so clients can call public methods
         return RCDevice.this;
      }
   }


   @Override
   public void onCreate()
   {
      // Only runs once, when service is created
      Log.i(TAG, "%% onCreate");

   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId)
   {
      // Runs whenever the user calls startService()
      Log.i(TAG, "%% onStartCommand");

      if (intent == null) {
         // TODO: this might be an issue, if it happens often. If the service is killed all context will be lost, so it won't
         // be able to automatically re-initialize. The only possible way to avoid this would be to return START_REDELIVER_INTENT
         // but then we would need to retrieve the parameters the Service was started with, and for that we 'd need to pack
         // all parameters inside the original intent. something which I tried but failed back when I implemented backgrounding,
         // and main reason was that I wasn't able to pack other intents in that Intent
         Log.e(TAG, "%% onStartCommand after having been killed");
      }

      if (intent != null && intent.getAction() != null) {
         String intentAction = intent.getAction();
         // if action originates at Notification subsystem, need to handle it
         if (intentAction.equals(ACTION_NOTIFICATION_CALL_DEFAULT) || intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO) ||
               intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO) || intentAction.equals(ACTION_NOTIFICATION_CALL_DECLINE) ||
               intentAction.equals(ACTION_NOTIFICATION_CALL_DELETE) || intentAction.equals(ACTION_NOTIFICATION_MESSAGE_DEFAULT) ||
               intentAction.equals(ACTION_NOTIFICATION_CALL_OPEN) || intentAction.equals(ACTION_NOTIFICATION_CALL_DISCONNECT) ||
               intentAction.equals(ACTION_NOTIFICATION_CALL_MUTE_AUDIO)) {
            handleNotification(intent);
         }
      }

      // If we get killed (usually due to memory pressure), after returning from here, restart
      return START_STICKY;
   }

   @Override
   public IBinder onBind(Intent intent)
   {
      Log.i(TAG, "%%  onBind");

      // We want the service to be both 'bound' and 'started' so that it lingers on after all clients have been unbound (I know the application is supposed
      // to call startService(), but let's make an exception in order to keep the API simple and easy to use
      startService(intent);

      isServiceAttached = true;

      // provide the binder
      return deviceBinder;
   }

   @Override
   public void onRebind(Intent intent)
   {
      Log.i(TAG, "%%  onRebind");

      isServiceAttached = true;
   }

   @Override
   public void onDestroy()
   {
      Log.i(TAG, "%% onDestroy");
   }

   @Override
   public boolean onUnbind(Intent intent)
   {
      Log.i(TAG, "%%  onUnbind");

      isServiceAttached = false;

      return true;
   }

   /*
    * Methods for clients to use
    */
   public boolean isInitialized()
   {
      return isServiceInitialized;
   }


   public boolean isAttached()
   {
      return isServiceAttached;
   }

   /**
    * Attach and initialize (if not already initialized) the RCDevice Service with parameters
    *
    * @param activityContext Activity context
    * @param parameters      Parameters for the Device entity (prefer using the string constants shown below, i.e. RCDevice.ParameterKeys.*, instead of
    *                        using strings like 'signaling-secure', etc. Possible keys: <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_USERNAME</b>: Identity for the client, like <i>'bob'</i> (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm instance to use, like <i>'cloud.restcomm.com'</i>. Leave empty for registrar-less mode<br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use, like <i>'https://turn.provider.com/turn'</i> (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? If this is the case, then a key pair is generated when
    *                        signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *                        the System Wide Android CA Store, so that we properly accept legit server certificates (optional) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING</b>: The SDK provides the user with default sounds for calling, ringing, busy (declined) and message events, but the user can override them
    *                        by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them here with Resource IDs like R.raw.user_provided_calling_sound. This parameter
    *                        configures the sound you will hear when you make a call and until the call is either replied or you hang up<br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING</b>: The sound you will hear when you receive a call <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED</b>: The sound you will hear when your call is declined <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE</b>: The sound you will hear when you receive a message <br>
    * @param deviceListener  The listener for upcoming RCDevice events
    * @return True if this is the first time RCDevice Service is attached to and hence initialization took place. False, if the service has already been initialized
    * Remember that once the Service starts in continues to run in the background even if the App doesn't have any activity running
    * @see RCDevice
    */
   public boolean initialize(Context activityContext, HashMap<String, Object> parameters, RCDeviceListener deviceListener)
   {
      if (!isServiceInitialized) {
         isServiceInitialized = true;
         //context = activityContext;

         RCLogger.i(TAG, "RCDevice(): " + parameters.toString());

         ErrorStruct errorStruct = RCUtils.validateParms(parameters);
         if (errorStruct.statusCode != RCClient.ErrorCodes.SUCCESS) {
            throw new RuntimeException(errorStruct.statusText);
         }

         //this.updateCapabilityToken(capabilityToken);
         this.listener = deviceListener;

         if (!parameters.containsKey(RCDevice.ParameterKeys.INTENT_INCOMING_CALL) ||
               !parameters.containsKey(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE)) {
            throw new RuntimeException(RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_INTENTS));
         }

         setPendingIntents((Intent) parameters.get(RCDevice.ParameterKeys.INTENT_INCOMING_CALL),
               (Intent) parameters.get(ParameterKeys.INTENT_INCOMING_MESSAGE));

         // TODO: check if those headers are needed
         HashMap<String, String> customHeaders = new HashMap<>();
         state = DeviceState.OFFLINE;

         connections = new HashMap<String, RCConnection>();
         // initialize JAIN SIP if we have connectivity
         this.parameters = parameters;

         // check if TURN keys are there
         //params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));

         signalingClient = SignalingClient.getInstance();
         signalingClient.open(this, getApplicationContext(), parameters);

         // Create and audio manager that will take care of audio routing,
         // audio modes, audio device enumeration etc.
         audioManager = AppRTCAudioManager.create(getApplicationContext(), new Runnable() {
                  // This method will be called each time the audio state (number and
                  // type of devices) has been changed.
                  @Override
                  public void run()
                  {
                     onAudioManagerChangedState();
                  }
               }
         );

         // Store existing audio settings and change audio mode to
         // MODE_IN_COMMUNICATION for best possible VoIP performance.
         RCLogger.d(TAG, "Initializing the audio manager...");
         audioManager.init(parameters);

         callNotifications = new HashMap<>();
         messageNotifications = new HashMap<>();

         /* Used for debugging purposes
         getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityCreated()");
            }

            @Override
            public void onActivityStarted(Activity activity)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityStarted()");
            }

            @Override
            public void onActivityResumed(Activity activity)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityResumed()");
            }

            @Override
            public void onActivityPaused(Activity activity)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityPaused()");
            }

            @Override
            public void onActivityStopped(Activity activity)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityStopped()");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivitySaveInstanceState()");
            }

            @Override
            public void onActivityDestroyed(Activity activity)
            {
               RCLogger.e(TAG, activity.getClass().getName() + ".onActivityDestroyed()");
            }
         });
         */

         return true;
      }

      //serviceReferenceCount++;

      // already initialized
      return false;
   }

   /**
    * Call when App is inactive (i.e. in the background) and hence notifications should be used instead of firing intents right away.
    * This typically needs to be called at onStop() of your Activity
    */
   /*
   public void detach()
   {
      //isServiceAttached = false;
      //serviceReferenceCount--;
   }
   */
   public void setLogLevel(int level)
   {
      RCLogger.setLogLevel(level);
   }

   // TODO: this is for RCConnection, but see if they can figure out the connectivity in a different way, like asking the signaling thread directly?
   public RCDeviceListener.RCConnectivityStatus getConnectivityStatus()
   {
      return cachedConnectivityStatus;
   }

   // 'Copy' constructor
   public RCDevice(RCDevice device)
   {
      this.incomingSoundEnabled = device.incomingSoundEnabled;
      this.outgoingSoundEnabled = device.outgoingSoundEnabled;
      this.disconnectSoundEnabled = device.disconnectSoundEnabled;
      this.listener = device.listener;

      // Not used yet
      this.capabilities = null;
   }

   /**
    * Shut down and release the RCDevice Service
    */
   public void release()
   {
      RCLogger.i(TAG, "release()");
      if (audioManager != null) {
         audioManager.close();
         audioManager = null;
      }

      this.listener = null;

      signalingClient.close();
      state = DeviceState.OFFLINE;

      isServiceAttached = false;
      //detach();
      isServiceInitialized = false;
   }

   /**
    * Start listening for incoming connections
    */
   public void listen()
   {
      RCLogger.i(TAG, "listen()");

      if (state == DeviceState.READY) {
         // TODO: implement with new signaling

      }
   }

   /**
    * Stop listening for incoming connections
    */
   public void unlisten()
   {
      RCLogger.i(TAG, "unlisten()");

      if (state == DeviceState.READY) {
         // TODO: implement with new signaling
      }
   }

   /**
    * Update Device listener to be receiving Device related events. This is
    * usually needed when we switch activities and want the new activity to receive
    * events
    *
    * @param listener New device listener
    */
   public void setDeviceListener(RCDeviceListener listener)
   {
      RCLogger.i(TAG, "setDeviceListener()");

      this.listener = listener;
   }

   /**
    * Retrieves the capability token passed to RCClient.createDevice
    *
    * @return Capability token
    */
   public String getCapabilityToken()
   {
      return "";
   }

   /**
    * Updates the capability token (<b>Not implemented yet</b>)
    */
   public void updateCapabilityToken(String token)
   {

   }

   /**
    * Create an outgoing connection to an endpoint. Important: if you work with Android API 23 or above you will need to handle dynamic Android permissions in your Activity
    * as described at https://developer.android.com/training/permissions/requesting.html. More specifically the Restcomm Client SDK needs RECORD_AUDIO, CAMERA (only if the local user
    * has enabled local video via RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED; if not then this permission isn't needed), and USE_SIP permission
    * to be able to connect(). For an example of such permission handling you can check MainActivity of restcomm-hello world sample App. Notice that if any of these permissions
    * are missing, the call will fail with a ERROR_CONNECTION_PERMISSION_DENIED error.
    *
    * @param parameters Parameters such as the endpoint we want to connect to, etc. Possible keys: <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PEER</b>: Who is the called number, like <i>'+1235'</i> or <i>'sip:+1235@cloud.restcomm.com'</i> <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED</b>: Whether we want WebRTC video enabled or not <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO</b>: PercentFrameLayout containing the view where we want the local video to be rendered in. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO</b>: PercentFrameLayout containing the view where we want the remote video to be rendered. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required  <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC</b>: Preferred video codec to use. Default is VP8. Possible values: <i>'VP8', 'VP9'</i> <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS</b>: An optional HashMap<String,String> of custom SIP headers we want to add. For an example
    *                   please check HelloWorld sample or Olympus App. <br>
    * @param listener   The listener object that will receive events when the connection state changes
    * @return An RCConnection object representing the new connection or null in case of error. Error
    * means that RCDevice.state not ready to make a call (this usually means no WiFi available)
    */
   public RCConnection connect(Map<String, Object> parameters, RCConnectionListener listener)
   {
      RCLogger.i(TAG, "connect(): " + parameters.toString());

      if (cachedConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         // Phone state Intents to capture connection failed event
         String username = "";
         if (parameters != null && parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER) != null)
            username = parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER).toString();
         sendQoSNoConnectionIntent(username, this.getConnectivityStatus().toString());
      }

      if (state == DeviceState.READY) {
         RCLogger.i(TAG, "RCDevice.connect(), with connectivity");

         state = DeviceState.BUSY;

         RCConnection connection = new RCConnection.Builder(false, RCConnection.ConnectionState.PENDING, this, signalingClient, audioManager)
               .listener(listener)
               .peer((String) parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER))
               .build();
         connection.open(parameters);

         // keep connection in the connections hashmap
         connections.put(connection.getId(), connection);

         return connection;
      }
      else {
         return null;
      }
   }

   /**
    * Send an instant message to an endpoint
    *
    * @param message    Message text
    * @param parameters Parameters used for the message, such as 'username' that holds the recepient for the message
    */
   public boolean sendMessage(String message, Map<String, String> parameters)
   {
      RCLogger.i(TAG, "sendMessage(): message:" + message + "\nparameters: " + parameters.toString());

      if (state == DeviceState.READY) {
         HashMap<String, Object> messageParameters = new HashMap<>();
         messageParameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER));
         messageParameters.put("text-message", message);
         //RCMessage message = RCMessage.newInstanceOutgoing(messageParameters, listener);
         signalingClient.sendMessage(messageParameters);
         return true;
      }
      else {
         return false;
      }
   }

   /**
    * Disconnect all connections
    */
   public void disconnectAll()
   {
      RCLogger.i(TAG, "disconnectAll()");

      if (state == DeviceState.BUSY) {
         for (Map.Entry<String, RCConnection> entry : connections.entrySet()) {
            RCConnection connection = entry.getValue();
            connection.disconnect();
         }
         connections.clear();
         state = DeviceState.READY;
      }
   }

   /**
    * Retrieve the capabilities
    *
    * @return Capabilities
    */
   public Map<DeviceCapability, Object> getCapabilities()
   {
      HashMap<DeviceCapability, Object> map = new HashMap<DeviceCapability, Object>();
      return map;
   }

   /**
    * Retrieve the Device state
    *
    * @return State
    */
   public DeviceState getState()
   {
      return state;
   }

   /**
    * Set Intents for incoming calls and messages that will later be wrapped in PendingIntents. In order to be notified of RestComm Client
    * events you need to associate your Activities with intents and provide one intent for whichever activity
    * will be receiving calls and another intent for the activity receiving messages. If you use a single Activity
    * for both then you can pass the same intent both as a callIntent as well as a messageIntent
    *
    * @param callIntent    an intent that will be sent on an incoming call
    * @param messageIntent an intent that will be sent on an incoming text message
    */
   public void setPendingIntents(Intent callIntent, Intent messageIntent)
   {
      RCLogger.i(TAG, "setPendingIntents()");

      this.callIntent = callIntent;
      this.messageIntent = messageIntent;
      //pendingCallIntent = PendingIntent.getActivity(context, 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      //pendingMessageIntent = PendingIntent.getActivity(context, 0, messageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
   }

   // Get incoming ringing connection
   public RCConnection getPendingConnection()
   {
      Iterator it = connections.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry pair = (Map.Entry) it.next();
         RCConnection connection = (RCConnection) pair.getValue();
         if (connection.incoming && connection.state == RCConnection.ConnectionState.CONNECTING) {
            return connection;
         }
      }

      return null;
   }

   // Get live connection, to reference live calls after we have left the call window
   public RCConnection getLiveConnection()
   {
      Iterator it = connections.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry pair = (Map.Entry) it.next();
         RCConnection connection = (RCConnection) pair.getValue();
         if (connection.state == RCConnection.ConnectionState.CONNECTED) {
            return connection;
         }
      }

      return null;
   }

   /**
    * Should a ringing sound be played in a incoming connection or message
    *
    * @param incomingSound Whether or not the sound should be played
    */
   public void setIncomingSoundEnabled(boolean incomingSound)
   {
      RCLogger.i(TAG, "setIncomingSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setIncoming(incomingSound);
   }

   /**
    * Retrieve the incoming sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isIncomingSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getIncoming();
      return true;
   }

   /**
    * Should a ringing sound be played in an outgoing connection or message
    *
    * @param outgoingSound Whether or not the sound should be played
    */
   public void setOutgoingSoundEnabled(boolean outgoingSound)
   {
      RCLogger.i(TAG, "setOutgoingSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setOutgoing(outgoingSound);
   }

   /**
    * Retrieve the outgoint sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isOutgoingSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getOutgoing();
      return true;
   }

   /**
    * Should a disconnect sound be played when disconnecting a connection
    *
    * @param disconnectSound Whether or not the sound should be played
    */
   public void setDisconnectSoundEnabled(boolean disconnectSound)
   {
      RCLogger.i(TAG, "setDisconnectSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setDisconnect(disconnectSound);
   }

   /**
    * Retrieve the disconnect sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isDisconnectSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getDisconnect();
      return true;
   }

   /**
    * Update Device parameters such as username, password, domain, etc
    *
    * @param params Parameters for the Device entity (prefer using the string constants shown below, i.e. RCDevice.ParameterKeys.*, instead of using strings
    *               like 'signaling-secure', etc. Possible keys: <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_USERNAME</b>: Identity for the client, like <i>'bob'</i> (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm instance to use, like <i>'cloud.restcomm.com'</i>. Leave empty for registrar-less mode<br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use, like <i>'https://turn.provider.com/turn'</i> (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? If this is the case, then a key pair is generated when
    *               signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *               the System Wide Android CA Store, so that we properly accept legit server certificates (optional) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    * @see RCDevice
    */
   public boolean updateParams(HashMap<String, Object> params)
   {
      signalingClient.reconfigure(params);

      // remember that the new parameters can be just a subset of the currently stored in this.parameters, so to update the current parameters we need
      // to merge them with the new (i.e. keep the old and replace any new keys with new values)
      this.parameters = JainSipConfiguration.mergeParameters(this.parameters, params);

      // TODO: need to provide asynchronous status for this
      return true;
   }

   public HashMap<String, Object> getParameters()
   {
      return parameters;
   }

   public SignalingClient.SignalingClientCallListener getConnectionByJobId(String jobId)
   {
      if (connections.containsKey(jobId)) {
         return connections.get(jobId);
      }
      else {
         throw new RuntimeException("No RCConnection exists to handle message with jobid: " + jobId);
      }
   }

   // -- SignalingClientListener events for incoming messages from signaling thread
   // Replies

   public void onOpenReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onOpenReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status != RCClient.ErrorCodes.SUCCESS) {
         if (isServiceAttached) {
            listener.onInitialized(this, connectivityStatus, status.ordinal(), text);
         }
         else {
            RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onInitialized(): " +
                  RCClient.errorText(status));
         }
         return;
      }

      state = DeviceState.READY;
      if (isServiceAttached) {
         listener.onInitialized(this, connectivityStatus, RCClient.ErrorCodes.SUCCESS.ordinal(), RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onInitialized(): " +
               RCClient.errorText(status));
      }

   }

   public void onCloseReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onCloseReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      if (status == RCClient.ErrorCodes.SUCCESS) {
         // TODO: notify App that device is closed
      }
      else {
      }

      // Shut down the service
      stopSelf();
   }

   public void onReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onReconfigureReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
         state = DeviceState.READY;
         if (isServiceAttached) {
            listener.onStartListening(this, connectivityStatus);
         }
         else {
            RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onStartListening(): " +
                  RCClient.errorText(status));
         }

      }
      else {
         state = DeviceState.OFFLINE;
         if (isServiceAttached) {
            listener.onStopListening(this, status.ordinal(), text);
         }
         else {
            RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: isServiceAttached(): " +
                  RCClient.errorText(status));
         }
      }
   }

   public void onMessageReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onMessageReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      if (isServiceAttached) {
         listener.onMessageSent(this, status.ordinal(), text);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onMessageSent(): " +
               RCClient.errorText(status));
      }

   }

   // Unsolicited Events
   public void onCallArrivedEvent(String jobId, String peer, String sdpOffer, HashMap<String, String> customHeaders)
   {
      RCLogger.i(TAG, "onCallArrivedEvent(): id: " + jobId + ", peer: " + peer);

      // filter out potential '<' and '>' and leave just the SIP URI
      String peerSipUri = peer.replaceAll("^<", "").replaceAll(">$", "");
      String peerUsername = peerSipUri.replaceAll(".*?sip:", "").replaceAll("@.*$", "");

      RCConnection connection = new RCConnection.Builder(true, RCConnection.ConnectionState.CONNECTING, this, signalingClient, audioManager)
            .jobId(jobId)
            .incomingCallSdp(sdpOffer)
            .peer(peerSipUri)
            .build();

      // keep connection in the connections hashmap
      connections.put(jobId, connection);

      state = DeviceState.BUSY;

      if (isServiceAttached) {
         // Service is attached to an activity, let's send the intent normally that will open the call activity
         audioManager.playRingingSound();

         callIntent.setAction(ACTION_INCOMING_CALL);
         callIntent.putExtra(RCDevice.EXTRA_DID, peerSipUri);
         callIntent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO));
         if (customHeaders != null) {
            callIntent.putExtra(RCDevice.EXTRA_CUSTOM_HEADERS, customHeaders);
         }
         //startActivity(callIntent);
         PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
         try {
            pendingIntent.send();
         }
         catch (PendingIntent.CanceledException e) {
            throw new RuntimeException("Pending Intent cancelled", e);
         }

         // Phone state Intents to capture incoming phone call event
         sendQoSIncomingConnectionIntent(peerSipUri, connection);
      }
      else {
         String text;
         if (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO) {
            text = "Incoming video call";
         }
         else {
            text = "Incoming audio call";
         }

         // Intent to open the call activity (for when tapping on the general notification area)
         Intent serviceIntentDefault = new Intent(ACTION_NOTIFICATION_CALL_DEFAULT, null, getApplicationContext(), RCDevice.class);
         serviceIntentDefault.putExtra(RCDevice.EXTRA_DID, peerSipUri);
         serviceIntentDefault.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO));
         if (customHeaders != null) {
            serviceIntentDefault.putExtra(RCDevice.EXTRA_CUSTOM_HEADERS, customHeaders);
         }

         // Intent to directly answer the call as video (using separate actions instead of EXTRAs, cause with EXTRAs the intents aren't actually differentiated: see PendingIntent reference documentation)
         Intent serviceIntentVideo = new Intent(ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO, null, getApplicationContext(), RCDevice.class);
         serviceIntentVideo.putExtras(serviceIntentDefault);

         // Intent to directly answer the call as audio
         Intent serviceIntentAudio = new Intent(ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO, null, getApplicationContext(), RCDevice.class);
         serviceIntentAudio.putExtras(serviceIntentDefault);

         // Intent to decline the call without opening the App Activity
         Intent serviceIntentDecline = new Intent(ACTION_NOTIFICATION_CALL_DECLINE, null, getApplicationContext(), RCDevice.class);
         serviceIntentDecline.putExtras(serviceIntentDefault);

         // Intent for when the user deletes notification
         Intent serviceIntentDelete = new Intent(ACTION_NOTIFICATION_CALL_DELETE, null, getApplicationContext(), RCDevice.class);
         serviceIntentDelete.putExtras(serviceIntentDefault);

         // Service is not attached to an activity, let's use a notification instead
         NotificationCompat.Builder builder =
               new NotificationCompat.Builder(RCDevice.this)
                     .setSmallIcon(R.drawable.ic_call_24dp)
                     .setContentTitle(peerUsername)
                     .setContentText(text)
                     .setSound(Uri.parse("android.resource://" + getPackageName() + "/" + audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_RINGING)))
                     // Need this to show up as Heads-up Notification
                     .setPriority(NotificationCompat.PRIORITY_HIGH)
                     .setAutoCancel(true)  // cancel notification when user acts on it (Important: only applies to default notification area, not additional actions)
                     .addAction(R.drawable.ic_videocam_24dp, "", PendingIntent.getService(getApplicationContext(), 0, serviceIntentVideo, PendingIntent.FLAG_ONE_SHOT))
                     .addAction(R.drawable.ic_call_24dp, "", PendingIntent.getService(getApplicationContext(), 0, serviceIntentAudio, PendingIntent.FLAG_ONE_SHOT))
                     .addAction(R.drawable.ic_call_end_24dp, "", PendingIntent.getService(getApplicationContext(), 0, serviceIntentDecline, PendingIntent.FLAG_ONE_SHOT))
                     .setVibrate(notificationVibrationPattern)
                     .setLights(notificationColor, notificationColorPattern[0], notificationColorPattern[1])
                     .setContentIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDefault, PendingIntent.FLAG_ONE_SHOT))
                     .setDeleteIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDelete, PendingIntent.FLAG_ONE_SHOT));

         Notification notification = builder.build();
         // Add FLAG_INSISTENT so that the notification rings repeatedly (FLAG_INSISTENT is not exposed via builder, let's add manually)
         //notification.flags = notification.flags | Notification.FLAG_INSISTENT;

         boolean notificationIdExists = true;
         Integer activeNotificationId = callNotifications.get(peerUsername);
         if (activeNotificationId == null) {
            // get new notification id
            activeNotificationId = notificationId;
            notificationIdExists = false;
         }

         NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
         // mId allows you to update the notification later on.
         notificationManager.notify(activeNotificationId, notification);

         if (!notificationIdExists) {
            // We used a new notification id, so we need to update call notifications
            callNotifications.put(peerUsername, notificationId);
            notificationId++;
         }

         activeCallNotification = true;
      }
   }

   // Handles intents from the Notifications subsystem and sends them to UI entities for processing if applicable
   void handleNotification(Intent intent)
   {
      String intentAction = intent.getAction();

      //if (!intentAction.equals(ACTION_NOTIFICATION_MESSAGE_DEFAULT)) {
      if (intentAction.equals(ACTION_NOTIFICATION_CALL_DEFAULT) || intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO) ||
            intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO) || intentAction.equals(ACTION_NOTIFICATION_CALL_DECLINE) ||
            intentAction.equals(ACTION_NOTIFICATION_CALL_DELETE)) {
         // The user has acted on a call notification, let's cancel it
         String username = intent.getStringExtra(EXTRA_DID).replaceAll(".*?sip:", "").replaceAll("@.*$", "");

         NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
         notificationManager.cancel(callNotifications.get(username));

         //callNotifications.remove(username);

         activeCallNotification = false;
      }

      Intent actionIntent = null;
      if (intentAction.equals(ACTION_NOTIFICATION_CALL_OPEN)) {
         RCConnection connection = getLiveConnection();
         if (connection != null) {
            if (connection.isIncoming()) {
               callIntent.setAction(ACTION_INCOMING_CALL);
            }
            else {
               callIntent.setAction(ACTION_OUTGOING_CALL);
            }
            callIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // don't forget to copy the extras to callIntent
            callIntent.putExtras(intent);
            actionIntent = callIntent;
         }
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_DEFAULT)) {
         callIntent.setAction(ACTION_INCOMING_CALL);
         // don't forget to copy the extras to callIntent
         callIntent.putExtras(intent);
         actionIntent = callIntent;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO)) {
         callIntent.setAction(ACTION_INCOMING_CALL_ANSWER_VIDEO);
         // don't forget to copy the extras
         callIntent.putExtras(intent);
         actionIntent = callIntent;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO)) {
         callIntent.setAction(ACTION_INCOMING_CALL_ANSWER_AUDIO);
         // don't forget to copy the extras
         callIntent.putExtras(intent);
         actionIntent = callIntent;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_DECLINE) || intentAction.equals(ACTION_NOTIFICATION_CALL_DELETE)) {
         RCConnection pendingConnection = getPendingConnection();
         if (pendingConnection != null) {
            pendingConnection.reject();
         }
         // if the call has been requested to be declined, we shouldn't do any UI handling
         return;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_MUTE_AUDIO)) {
         RCConnection liveConnection = getLiveConnection();
         if (liveConnection != null) {
            if (liveConnection.isAudioMuted()) {
               liveConnection.setAudioMuted(false);
            }
            else {
               liveConnection.setAudioMuted(true);
            }
         }

         // if the call has been requested to be muted, we shouldn't do any UI handling
         return;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_CALL_DISCONNECT)) {
         RCConnection liveConnection = getLiveConnection();
         if (liveConnection != null) {
            liveConnection.disconnect();

            // if the call has been requested to be disconnected, we shouldn't do any UI handling
            callIntent.setAction(ACTION_CALL_DISCONNECT);
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  //FLAG_ACTIVITY_SINGLE_TOP
            actionIntent = callIntent;

            // Important if we just trigger the call intent, then after the call is disconnected we will land to the previous screen in
            // that Task Stack, which is not what we want. Instead, we want the call activity to be finished and to just remain where
            // we were. To do that we need to create a new Task Stack were we only place the call activity
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the target Intent to the top of the stack
            stackBuilder.addNextIntent(actionIntent);
            // Gets a PendingIntent containing the entire back stack, but with Component as the active Activity
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
               resultPendingIntent.send();
            }
            catch (PendingIntent.CanceledException e) {
               throw new RuntimeException("Pending Intent cancelled", e);
            }
         }

         return;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_MESSAGE_DEFAULT)) {
         messageIntent.setAction(ACTION_INCOMING_MESSAGE);
         // don't forget to copy the extras
         messageIntent.putExtras(intent);
         actionIntent = messageIntent;
      }
      else {
         throw new RuntimeException("Failed to handle Notification");
      }

      // We need to create a Task Stack to make sure we maintain proper flow at all times
      TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
      // Adds either call or message intent's Component in the stack together with all its parent activities (remember that for this to work the App Manifest needs to describe their relationship)
      stackBuilder.addParentStack(actionIntent.getComponent());
      // Adds the target Intent to the top of the stack
      stackBuilder.addNextIntent(actionIntent);
      // Gets a PendingIntent containing the entire back stack, but with Component as the active Activity
      PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
      try {
         resultPendingIntent.send();
      }
      catch (PendingIntent.CanceledException e) {
         throw new RuntimeException("Pending Intent cancelled", e);
      }
   }

   public void onRegisteringEvent(String jobId)
   {
      RCLogger.i(TAG, "onRegisteringEvent(): id: " + jobId);
      state = DeviceState.OFFLINE;
      if (isServiceAttached) {
         listener.onStopListening(this, RCClient.ErrorCodes.SUCCESS.ordinal(), "Trying to register with Service");
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onStopListening()");
      }

   }



   void onMuteChanged(RCConnection connection)
   {
      updateForegroundNotification(connection);
   }

   private void updateForegroundNotification(RCConnection connection)
   {
      String peerUsername = connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", "");

      callIntent.setAction(ACTION_OUTGOING_CALL);
      callIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

      // Intent to open the call activity (for when tapping on the general notification area)
      Intent serviceIntentMute = new Intent(ACTION_NOTIFICATION_CALL_MUTE_AUDIO, null, getApplicationContext(), RCDevice.class);
      // Intent to decline the call without opening the App Activity
      Intent serviceIntentDisconnect = new Intent(ACTION_NOTIFICATION_CALL_DISCONNECT, null, getApplicationContext(), RCDevice.class);

      int resId = R.drawable.ic_mic_24dp;
      if (connection.isAudioMuted()) {
         resId = R.drawable.ic_mic_off_24dp;
      }

      // Service is not attached to an activity, let's use a notification instead
      NotificationCompat.Builder builder =
            new NotificationCompat.Builder(RCDevice.this)
                  .setSmallIcon(R.drawable.ic_phone_in_talk_24dp)
                  .setContentTitle(peerUsername)
                  .setContentText("Tap to return to call")
                  .addAction(R.drawable.ic_call_end_24dp, "Hang up", PendingIntent.getService(getApplicationContext(), 0, serviceIntentDisconnect, PendingIntent.FLAG_ONE_SHOT))
                  .addAction(resId, "Mute", PendingIntent.getService(getApplicationContext(), 0, serviceIntentMute, PendingIntent.FLAG_ONE_SHOT))
                  .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, callIntent, PendingIntent.FLAG_ONE_SHOT));

      startForeground(ONCALL_NOTIFICATION_ID, builder.build());
   }

   public void startForegroundNotification(RCConnection connection)
   {
      if (!foregroundNoticationActive) {
         foregroundNoticationActive = true;

         updateForegroundNotification(connection);
      }
   }

   public void stopForegroundNotification(RCConnection connection)
   {
      if (foregroundNoticationActive) {
         stopForeground(true);
         foregroundNoticationActive = false;
      }
   }

   public void cancelNotificationSoundIfNeeded(RCConnection connection)
   {
      if (activeCallNotification) {
         // Peer has canceled the call and there's an active call ringing, we need to cancel the notification
         String peerUsername = connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", "");
         NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
         notificationManager.cancel(callNotifications.get(peerUsername));

         // And then create a new notification to show that the call is missed, together with a means to call the peer. Notice
         // that if this notification is tapped, the peer will be called using the video preference of
         callIntent.setAction(ACTION_OUTGOING_CALL);
         callIntent.putExtra(RCDevice.EXTRA_DID, connection.getPeer());
         callIntent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO));

         // We need to create a Task Stack to make sure we maintain proper flow at all times
         TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
         // Adds either call or message intent's Component in the stack together with all its parent activities (remember that for this to work the App Manifest needs to describe their relationship)
         stackBuilder.addParentStack(callIntent.getComponent());
         // Adds the target Intent to the top of the stack
         stackBuilder.addNextIntent(callIntent);
         // Gets a PendingIntent containing the entire back stack, but with Component as the active Activity
         PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

         // Service is not attached to an activity, let's use a notification instead
         NotificationCompat.Builder builder =
               new NotificationCompat.Builder(RCDevice.this)
                     .setSmallIcon(R.drawable.ic_phone_missed_24dp)
                     .setContentTitle(connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", ""))
                     .setContentText("Missed call")
                     //.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ringing_sample)) // audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_RINGING)))
                     // Need this to show up as Heads-up Notification
                     .setPriority(NotificationCompat.PRIORITY_HIGH)
                     .setAutoCancel(true)  // cancel notification when user acts on it (Important: only applies to default notification area, not additional actions)
                     .setContentIntent(resultPendingIntent);

         Notification notification = builder.build();
         notificationManager.notify(callNotifications.get(peerUsername), notification);
         // Remove the call notification, as it will be removed automatically
         //callNotifications.remove(peerUsername);

         activeCallNotification = false;
      }
   }

   public void onMessageArrivedEvent(String jobId, String peer, String messageText)
   {
      RCLogger.i(TAG, "onMessageArrivedEvent(): id: " + jobId + ", peer: " + peer + ", text: " + messageText);

      audioManager.playMessageSound();
      HashMap<String, String> parameters = new HashMap<String, String>();
      // filter out potential '<' and '>' and leave just the SIP URI
      String peerSipUri = peer.replaceAll("^<", "").replaceAll(">$", "");
      String peerUsername = peerSipUri.replaceAll(".*?sip:", "").replaceAll("@.*$", "");

      //parameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, from);

      if (isServiceAttached) {
         messageIntent.setAction(ACTION_INCOMING_MESSAGE);
         messageIntent.putExtra(EXTRA_DID, peerSipUri);
         messageIntent.putExtra(EXTRA_MESSAGE_TEXT, messageText);
         //startActivity(messageIntent);

         PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, messageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
         try {
            pendingIntent.send();
         }
         catch (PendingIntent.CanceledException e) {
            throw new RuntimeException("Pending Intent cancelled", e);
         }
      }
      else {
         // Intent to open the call activity (for when tapping on the general notification area)
         Intent serviceIntentDefault = new Intent(ACTION_NOTIFICATION_MESSAGE_DEFAULT, null, getApplicationContext(), RCDevice.class);
         serviceIntentDefault.putExtra(RCDevice.EXTRA_DID, peerSipUri);
         serviceIntentDefault.putExtra(EXTRA_MESSAGE_TEXT, messageText);

         // Service is not attached to an activity, let's use a notification instead
         NotificationCompat.Builder builder =
               new NotificationCompat.Builder(RCDevice.this)
                     .setSmallIcon(R.drawable.ic_chat_24dp)
                     .setContentTitle(peerUsername)
                     .setContentText(messageText)
                     .setSound(Uri.parse("android.resource://" + getPackageName() + "/" + audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_MESSAGE)))  // R.raw.message_sample)) //
                     // Need this to show up as Heads-up Notification
                     .setPriority(NotificationCompat.PRIORITY_HIGH)
                     .setAutoCancel(true)  // cancel notification when user acts on it
                     .setVibrate(notificationVibrationPattern)
                     .setLights(notificationColor, notificationColorPattern[0], notificationColorPattern[1])
                     .setContentIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDefault, PendingIntent.FLAG_ONE_SHOT));

         boolean notificationIdExists = true;
         Integer activeNotificationId = messageNotifications.get(peerUsername);
         if (activeNotificationId == null) {
            // get new notification id
            activeNotificationId = notificationId;
            notificationIdExists = false;
         }

         Notification notification = builder.build();
         NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
         // mId allows you to update the notification later on.
         notificationManager.notify(activeNotificationId, notification);
         if (!notificationIdExists) {
            messageNotifications.put(peerUsername, notificationId);
            notificationId++;
         }
      }
   }

   public void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.e(TAG, "onErrorEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
      }
      else {
         if (isServiceAttached) {
            listener.onStopListening(this, status.ordinal(), text);
         }
         else {
            RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onStopListening(): " +
                  RCClient.errorText(status));
         }

      }
   }

   public void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      RCLogger.i(TAG, "onConnectivityEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus);
      cachedConnectivityStatus = connectivityStatus;
      if (state == DeviceState.OFFLINE && connectivityStatus != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.READY;
      }
      if (state != DeviceState.OFFLINE && connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.OFFLINE;
      }
      if (isServiceAttached) {
         listener.onConnectivityUpdate(this, connectivityStatus);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onConnectivityUpdate(): " +
               connectivityStatus);
      }

   }

   // ------ Helpers

   // -- Notify QoS module of Device related event through intents, if the module is available
   // Phone state Intents to capture incoming call event
   private void sendQoSIncomingConnectionIntent(String user, RCConnection connection)
   {
      Intent intent = new Intent("org.restcomm.android.CALL_STATE");
      intent.putExtra("STATE", "ringing");
      intent.putExtra("INCOMING", true);
      intent.putExtra("FROM", user);
      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
         intent.setClass(getApplicationContext(), aclass);
         getApplicationContext().sendBroadcast(intent);
      }
      catch (ClassNotFoundException e) {
         // If there is no MMC class isn't here, no intent
      }
   }

   private void sendQoSNoConnectionIntent(String user, String message)
   {
      Intent intent = new Intent("org.restcomm.android.CONNECT_FAILED");
      intent.putExtra("STATE", "connect failed");
      intent.putExtra("ERRORTEXT", message);
      intent.putExtra("ERROR", RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY);
      intent.putExtra("INCOMING", false);
      intent.putExtra("USER", user);
      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
         intent.setClass(getApplicationContext(), aclass);
         getApplicationContext().sendBroadcast(intent);
      }
      catch (ClassNotFoundException e) {
         // If there is no MMC class isn't here, no intent
      }
   }

   void removeConnection(String jobId)
   {
      RCLogger.i(TAG, "removeConnection(): id: " + jobId + ", total connections before removal: " + connections.size());
      connections.remove(jobId);
   }

   private void onAudioManagerChangedState()
   {
      // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
      // is active.
   }
}
