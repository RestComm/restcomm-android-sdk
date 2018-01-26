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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.restcomm.android.sdk.MediaClient.AppRTCAudioManager;
import org.restcomm.android.sdk.SignalingClient.JainSipClient.JainSipConfiguration;
import org.restcomm.android.sdk.SignalingClient.SignalingClient;
import org.restcomm.android.sdk.fcm.FcmConfigurationHandler;
import org.restcomm.android.sdk.fcm.FcmPushRegistrationListener;
import org.restcomm.android.sdk.fcm.model.FcmBinding;
import org.restcomm.android.sdk.storage.StorageManagerPreferences;
import org.restcomm.android.sdk.storage.StorageUtils;
import org.restcomm.android.sdk.util.RegistrationFsm;
import org.restcomm.android.sdk.util.RegistrationFsmContext;
import org.restcomm.android.sdk.util.RCException;
import org.restcomm.android.sdk.util.RCLogger;
import org.restcomm.android.sdk.util.RCUtils;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.UntypedStateMachine;
import org.squirrelframework.foundation.fsm.UntypedStateMachineBuilder;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * RCDevice represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
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
 *    where you need to cast the result to HashMap&lt;String, String&gt; where each entry is a Restcomm parameter with key and value of type string
 *    (for example you will find the Restcomm Call-Sid under 'X-RestComm-CallSid' key).</li>
 * </ol>
 * At that point you can use RCConnection methods to accept or reject the connection.
 * <br>
 * As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 * through an intent with action 'RCDevice.INCOMING_MESSAGE'. For an incoming message you can retrieve:
 * <ol>
 *    <li>The sending party id via string extra named RCDevice.EXTRA_DID, like: <i>intent.getStringExtra(RCDevice.EXTRA_DID)</i></li>
 *    <li>The actual message text via string extra named RCDevice.INCOMING_MESSAGE_TEXT, like: <i>intent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT)</i></li>
 * </ol>
 * <h3>Taking advantage of Android Notifications</h3>
 * The Restcomm  SDK comes integrated with Android Notifications. This means that while your App is in the
 * background all events from incoming calls/messages are conveyed via Notifications. Depending on the type of event the designated Intent is used
 * to deliver the event. For example if an incoming text message arrives and your App is in the background, then the message will be shown as a notification and if
 * the user taps on it, then whichever intent you passed as RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE in RCDevice.initialize() will be sent to your App with
 * the extras already discussed previously in normal (i.e. foreground) operation. Likewise, if an incoming call arrives and your App is in the background, then
 * the call will be shown as a notification and the user will be able to:
 * <ol>
 *    <li>Decline it by swiping the notification left, right, or tapping on the hang up Action Button. Then, no intent is sent to the App, as the user most likely doesn't want it opened at this point.</li>
 *    <li>Accept it as audio and video call by tapping on the video Action Button. In this case, whichever intent you passed as RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE in RCDevice.initialize() will be sent to your App with
 * the extras already discussed previously in normal (i.e. foreground) operation. The only difference is the action, which will be ACTION_INCOMING_CALL_ANSWER_VIDEO. So from App perspective when you get this action
 * you should answer the call as audio and video</li>
 *    <li>Accept it as audio-only call by tapping on the audio Action Button. Same as previously with the only difference that the action will be ACTION_INCOMING_CALL_ANSWER_AUDIO</li>
 * </ol>
 * Keep in mind that once the call is answered then you get a foreground (i.e. sticky) notification in the Notification Drawer for the duration of the call via which you can either mute audio or hang up the call
 * while working on other Android Apps without interruption.
 * <br>
 * <h3>Android Service</h3>
 * You need to keep in mind that RCDevice is an Android Service, to facilitiate proper backgrounding functionality. Also this service is both 'bound' and 'started'. Bound
 * to be able to access it via easy-to-use API and started to make sure that it doesn't shut down when all Activities have been unbounded. Also, notice that although it
 * is a 'started' service you don't need to call startService(), because that happens internally the first time the service is bound.
 *
 * <h2>How to use RCDevice Service</h2>
 * You typically bind to the service with bindService() at your Activity's onStart() method and unbind using unbindService() at your Activity's onStop() method. Your
 * Activity needs to extend ServiceConnection to be able to receive binding events and then once you receive onServiceConnected() which means that your Activity is successfully
 * bound to the RCDevice service, you need to initialize RCDevice with your parameters.
 * You can also check the Sample Applications on how to properly use that at the Examples directory in the GitHub repository
 * @see RCConnection
 */

public class RCDevice extends Service implements SignalingClient.SignalingClientListener, FcmPushRegistrationListener, RegistrationFsm.RCDeviceFSMListener {
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
    * Media ICE server type, or how should SDK figure out the ICE servers
    */
   public enum MediaIceServersDiscoveryType {
      ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V2,  /** Use Xirsys V2 configuration URL to retrieve the ICE servers */
      ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3,  /** Use Xirsys V3 configuration URL to retrieve the ICE servers */
      ICE_SERVERS_CUSTOM,  /** Don't use a configuration URL, but directly provide the set of ICE servers (i.e. the App needs to have logic to retrieve them  and provide them) */
   }


   /**
    * Parameter keys for RCClient.createDevice() and RCDevice.reconfigure()
    */
   public static class ParameterKeys {
      public static final String INTENT_INCOMING_CALL = "incoming-call-intent";
      public static final String INTENT_INCOMING_MESSAGE = "incoming-message-intent";
      public static final String SIGNALING_USERNAME = "pref_sip_user";
      public static final String SIGNALING_DOMAIN = "pref_proxy_domain";
      public static final String SIGNALING_PASSWORD = "pref_sip_password";
      public static final String SIGNALING_SECURE_ENABLED = "signaling-secure";
      public static final String SIGNALING_LOCAL_PORT = "signaling-local-port";
      public static final String DEBUG_JAIN_SIP_LOGGING_ENABLED = "jain-sip-logging-enabled";
      public static final String DEBUG_DISABLE_CERTIFICATE_VERIFICATION = "disable-certificate-verification";
      // WARNING This is NOT for production. It's for Integration Tests, where there is no activity to receive call/message events
      public static final String DEBUG_USE_BROADCASTS_FOR_EVENTS = "debug-use-broadcast-for-events";
      public static final String MEDIA_TURN_ENABLED = "turn-enabled";
      public static final String MEDIA_ICE_SERVERS_DISCOVERY_TYPE = "media-ice-servers-discovery-type";
      public static final String MEDIA_ICE_SERVERS = "media-ice-servers";
      //public static final String MEDIA_ICE_ENDPOINT = "media-ice-endpoint";
      public static final String MEDIA_ICE_URL = "turn-url";
      public static final String MEDIA_ICE_USERNAME = "turn-username";
      public static final String MEDIA_ICE_PASSWORD = "turn-password";
      public static final String MEDIA_ICE_DOMAIN = "ice-domain";
      public static final String RESOURCE_SOUND_CALLING = "sound-calling";
      public static final String RESOURCE_SOUND_RINGING = "sound-ringing";
      public static final String RESOURCE_SOUND_DECLINED = "sound-declined";
      public static final String RESOURCE_SOUND_MESSAGE = "sound-message";
      //push notifications
      public static final String PUSH_NOTIFICATIONS_APPLICATION_NAME = "push-application-name";
      public static final String PUSH_NOTIFICATIONS_ACCOUNT_EMAIL = "push-account-email";
      public static final String PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD = "push-account-password";
      public static final String PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT = "push-enable-push-for-account";
      public static final String PUSH_NOTIFICATIONS_PUSH_DOMAIN = "push-domain";
      public static final String PUSH_NOTIFICATIONS_HTTP_DOMAIN  = "push-http-domain";
      public static final String PUSH_NOTIFICATIONS_FCM_SERVER_KEY = "push-fcm-key";
      public static final String PUSH_NOTIFICATION_TIMEOUT_MESSAGING_SERVICE = "push-timeout-message-service";

   }

   private static final String TAG = "RCDevice";

   // Service Intent actions sent from RCDevice Service -> Call Activity

   /**
    * Call Activity Intent action sent when a new call is requested by tapping on a missed call on the Notification Drawer
    */
   public static String ACTION_OUTGOING_CALL = "org.restcomm.android.sdk.ACTION_OUTGOING_CALL";

   /**
    * Call Activity Intent action sent when a incoming call arrives
    */
   public static String ACTION_INCOMING_CALL = "org.restcomm.android.sdk.ACTION_INCOMING_CALL";
   /**
    * Call Activity Intent action sent when an incoming call is requested to be answered with audio only via Notification Drawer. Notice that
    * the application still has control and needs to answer the call depending on its preference with RCConnection.accept(). Also, extras are
    * exactly the same as for ACTION_OUTGOING_CALL, for example the peer DID will be at 'EXTRA_DID'
    */
   public static String ACTION_INCOMING_CALL_ANSWER_AUDIO = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_ANSWER_AUDIO";
   /**
    * Call Activity Intent action sent when an incoming call is requested to be answered with audio and video via Notification Drawer. Notice that
    * the application still has control and needs to answer the call depending on its preference with RCConnection.accept(). Also, extras are
    * exactly the same as for ACTION_OUTGOING_CALL, for example the peer DID will be at 'EXTRA_DID'
    */
   public static String ACTION_INCOMING_CALL_ANSWER_VIDEO = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_ANSWER_VIDEO";

   /**
    * Call Activity Intent action sent when a live background call is resumed (either via Notification Drawer or via App opening). The Application
    * should just allow the existing Call Activity to open.
    */
   public static String ACTION_RESUME_CALL = "org.restcomm.android.sdk.ACTION_RESUME_CALL";

   /**
    * Call Activity Intent action sent when the call activity has been destroyed previously and we want to re-created it. One such case is when the user is in the Call Activity and presses back
    * to navigate to Messages screen in order for example to send a text message while talking. When back is pressed the  Call Activity is destroyed and so are the webrtc video views. This intent
    * ensures that any media resources like local and remote video are bound to relevant Activity resources, like views, etc in a seamless manner
    */
   //public static String ACTION_RESUME_CALL_DESTROYED_ACTIVITY = "org.restcomm.android.sdk.ACTION_RESUME_CALL_DESTROYED_ACTIVITY";

   /**
    * Call Activity Intent action sent when a ringing call was declined via Notification Drawer. You don't have to act on that,
    * but it usually provides a better user experience if you do. If you don't act on that then the Call Activity
    * will be opened, right after the call is disconnected from the SDK. This is usually poor experience as it takes you back
    * to the App for a call you just disconnected, and hence of little interest. Instead, a better approach close the
    * Call Activity behind the scenes and remain in you current workflow.
    */
   //public static String ACTION_INCOMING_CALL_DECLINE = "org.restcomm.android.sdk.ACTION_INCOMING_CALL_DECLINE";
   /**
    * Call Activity Intent action sent when a live call was disconnected via Notification Drawer. You don't have to act on that,
    * but it usually provides a better user experience if you do. If you don't act on that then the Call Activity
    * will be opened, right after the call is disconnected from the SDK. This is usually poor experience as it takes you back
    * to the App for a call you just disconnected, and hence of little interest. Instead, a better approach close the
    * Call Activity behind the scenes and remain in you current workflow.
    */
   public static String ACTION_CALL_DISCONNECT = "org.restcomm.android.sdk.ACTION_CALL_DISCONNECT";
   /**
    * Message Activity Intent action sent by the SDK when an incoming text message arrived
    */
   public static String ACTION_INCOMING_MESSAGE = "org.restcomm.android.sdk.ACTION_INCOMING_MESSAGE";

   public static final String ACTION_FCM = "org.restcomm.android.sdk.ACTION_FCM";


   // Internal intents sent by Notification subsystem -> RCDevice Service when user acts on the Notifications
   // Used when user taps in a missed call, where we want it to trigger a new call towards the caller
   private static String ACTION_NOTIFICATION_CALL_DEFAULT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DEFAULT";
   // Used when there's an active call and a foreground notification, and hence the user wants to go back the existing call activity without doing anything else
   //private static String ACTION_NOTIFICATION_CALL_OPEN = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_OPEN";
   // Used when there's an active call and a foreground notification, and the user wants to disconnect
   private static String ACTION_NOTIFICATION_CALL_DISCONNECT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DISCONNECT";
   // User deleted the notification (by swiping on the left/right, or deleting all notifications)
   private static String ACTION_NOTIFICATION_CALL_DELETE = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DELETE";
   private static String ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO";
   private static String ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO";
   private static String ACTION_NOTIFICATION_CALL_DECLINE = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_DECLINE";
   private static String ACTION_NOTIFICATION_MESSAGE_DEFAULT = "org.restcomm.android.sdk.ACTION_NOTIFICATION_MESSAGE_DEFAULT";
   private static String ACTION_NOTIFICATION_CALL_MUTE_AUDIO = "org.restcomm.android.sdk.ACTION_NOTIFICATION_CALL_MUTE_AUDIO";

   private static final String NOTIFICATION_CHANNEL_ID = "1010";

   private static final String DEFAULT_FOREGROUND_CHANNEL_ID = "Default Foreground Channel ID";
   private static final String DEFAULT_FOREGROUND_CHANNEL = "Default Foreground Channel";
   private static final String PRIMARY_CHANNEL_ID = "Primary Channel ID";
   private static final String PRIMARY_CHANNEL = "Primary Channel";

   // Intent EXTRAs keys

   /**
    * The actual message text
    */
   public static String EXTRA_MESSAGE_TEXT = "org.restcomm.android.sdk.EXTRA_MESSAGE_TEXT";
   /**
    * The caller-id for the incoming call or message
    */
   public static String EXTRA_DID = "org.restcomm.android.sdk.EXTRA_DID";
   /**
    * Potential custom SIP headers sent by Restcomm-Connect
    */
   public static String EXTRA_CUSTOM_HEADERS = "org.restcomm.android.sdk.CUSTOM_HEADERS";
   /**
    * Whether the peer started the incoming call with video enabled or not
    */
   public static String EXTRA_VIDEO_ENABLED = "org.restcomm.android.sdk.VIDEO_ENABLED";

   // Notification ids for calls and messages. Key is the sender and value in the notification id. We mainly need those
   // to make sure that notifications from a specific user are shown in a single slot, to avoid confusing the user. This means
   // that after we add a user in one of the maps they are never removed. Reason is there's a lot of overhead needed of
   // passing all intents back to RCDevice service even for messages and missed calls which are auto-answered, with not
   // too much value, at least for now
   private HashMap<String, Integer> callNotifications = new HashMap<>();
   private HashMap<String, Integer> messageNotifications = new HashMap<>();


   private final int ONCALL_NOTIFICATION_ID = 1;
   // Unique notification id (incremented for each new notification). Notice that '1' is reserved for RCConnection.ONCALL_NOTIFICATION_ID
   private int notificationId = 2;
   //private final int NOTIFICATION_ID_CALL = 1;
   //private final int NOTIFICATION_ID_MESSAGE = 2;
   // is an incoming call ringing, triggered by the Notification subsystem?
   private boolean activeCallNotification = false;
   // Numbers here refer to: delay duration, on duration 1, off duration 1, on duration 2, off duration2, ...
   long[] notificationVibrationPattern = { 0, 100, 100, 100, 1000 };
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
   public static boolean isServiceInitialized = false;
   // Is an activity currently attached to RCDevice service?
   public static boolean isServiceAttached = false;

   private StorageManagerPreferences storageManagerPreferences;

   //message counter for backgrounding
   private long messageTimeOutInterval;
   private long messageTimeOutIntervalLimit = 10000; //10 seconds
   private static final long TIMEOUT_INTERVAL_TICK = 1000; //1 second
   //handler for message timeout count
   private Handler messageTimeoutHandler;
   // FSM to synchonize between signaling and push registration
   //AbstractStateMachine<RegistrationFsm, RegistrationFsm.FSMState, RegistrationFsm.FSMEvent, RegistrationFsmContext> registrationFsm;
   UntypedStateMachine registrationFsm;

   // Apps must not use the constructor, as it is created inside the service, but making it (package) private seems to cause crashes in some devices
   public RCDevice()
   {
      super();
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


   /**
    * Internal service callback; not meant for application use
    */
   @Override
   public void onCreate()
   {
      // Only runs once, when service is created
      Log.i(TAG, "%% onCreate");

   }

   /**
    * Internal service callback; not meant for application use
    */
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

         //check is intent is from push notifications,
         //if it is, check is service initialized,
         //if its not initialize it
         if (intentAction.equals(ACTION_FCM)){
            setLogLevel(Log.VERBOSE);

            //if service is attached we dont need to run foreground
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
               NotificationCompat.Builder builder = getNotificationBuilder(false);
               builder.setSmallIcon(R.drawable.ic_chat_24dp);
               builder.setAutoCancel(true);
               builder.setContentTitle("Restcomm Connect");
               builder.setContentText("Background initialization...");
               startForeground(notificationId, builder.build());
            }

            //initialize
            if (!isServiceInitialized) {
               //get values
               storageManagerPreferences = new StorageManagerPreferences(this);
               HashMap<String, Object> parameters = StorageUtils.getParams(storageManagerPreferences);
               try {
                  initialize(null, parameters, null);
               } catch (RCException e) {
                  RCLogger.e(TAG, e.toString());
               }
            }
         } else {
            // if action originates at Notification subsystem, need to handle it
            if (intentAction.equals(ACTION_NOTIFICATION_CALL_DEFAULT) || intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_VIDEO) ||
                    intentAction.equals(ACTION_NOTIFICATION_CALL_ACCEPT_AUDIO) || intentAction.equals(ACTION_NOTIFICATION_CALL_DECLINE) ||
                    intentAction.equals(ACTION_NOTIFICATION_CALL_DELETE) || intentAction.equals(ACTION_NOTIFICATION_MESSAGE_DEFAULT) ||
                    intentAction.equals(ACTION_NOTIFICATION_CALL_DISCONNECT) || intentAction.equals(ACTION_NOTIFICATION_CALL_MUTE_AUDIO)) {
               onNotificationIntent(intent);
            }
         }
      }

      return START_NOT_STICKY;
   }

   /**
    * Internal service callback; not meant for application use
    */
   @Override
   public IBinder onBind(Intent intent)
   {
      Log.i(TAG, "%% onBind");

      // We want the service to be both 'bound' and 'started' so that it lingers on after all clients have been unbound (I know the application is supposed
      // to call startService(), but let's make an exception in order to keep the API simple and easy to use
      startService(intent);

      isServiceAttached = true;

      // provide the binder
      return deviceBinder;
   }

   /**
    * Internal service callback; not meant for application use
    */
   @Override
   public void onRebind(Intent intent)
   {
      Log.i(TAG, "%% onRebind");

      isServiceAttached = true;
   }

   /**
    * Internal service callback; not meant for application use
    */
   @Override
   public void onDestroy()
   {

      Log.i(TAG, "%% onDestroy");
      //maybe user killed service
      if (isInitialized()) {
         release();
      }

   }

   /**
    * Internal service callback; not meant for application use
    */
   @Override
   public boolean onUnbind(Intent intent)
   {
      Log.i(TAG, "%%  onUnbind");

      isServiceAttached = false;

      if (RCDevice.state != DeviceState.BUSY) {
         Log.i(TAG, "%%  DeviceState state is not BUSY, we are releasing!");
         release();
      }

      // We need to return true so that the service's onRebind(Intent) method is called later when new clients bind to it
      // Reason this is important is to make sure isServiceAttached is always consistent and up to date. Consider this case:
      // 1. User starts call and hits Home button while call is ongoing. At that point call activity is stopped and service
      //   is unbound (and hence isServiceAttached is set to false). Notice though that it's still running as a foreground service,
      //   since the call is still live)
      // 2. User taps on the App launcher and resumes the call by being navigated to the call screen
      // 3. In the call activity code we are binding to the service, but because onUnbind() returned false previously, no onBind() or onRebind()
      //   is called and hence isServiceAttached remains false, even though we are attached to it and this messes up the service state.
      return true;
   }

   @Override
   public void onTaskRemoved(Intent rootIntent) {
      super.onTaskRemoved(rootIntent);
      release();
   }

   /*
    * Check if RCDevice is already initialized. Since RCDevice is an Android Service that is supposed to run in the background,
    * this is needed to make sure that RCDevice.initialize() is only invoked once, the first time an Activity binds to the service
    */
   public boolean isInitialized()
   {
      return isServiceInitialized;
   }

   /**
    * Is service attached to an Activity
    * @return true if yes, no if not
    */
   public boolean isAttached()
   {
      return isServiceAttached;
   }

   /**
    * Initialize RCDevice (if not already initialized) the RCDevice Service with parameters. Notice that this needs to happen after the service has been bound. Remember that
    * once the Service starts in continues to run in the background even if the App doesn't have any activity running
    *
    * @param activityContext Activity context
    * @param parameters      Parameters for the Device entity (prefer using the string constants shown below, i.e. RCDevice.ParameterKeys.*, instead of
    *                        using strings like 'signaling-secure', etc. Possible keys: <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_USERNAME</b>: Identity for the client, like <i>'bob'</i> (mandatory) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client (optional for registrar-less scenarios). Be VERY careful to securely handle this inside your App. Never store it statically and in cleartext form in your App before submitting to Google Play Store as you run the risk of any of the folks downloading it figuring it out your credentials <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm endpoint to use, like <i>'cloud.restcomm.com'</i>. By default port 5060 will be used for cleartext signaling and 5061 for encrypted signaling. You can override the port by suffixing the domain; for example to use port 5080 instead, use the following: <i>'cloud.restcomm.com:5080'</i>. Don't pass this parameter (or leave empty) for registrar-less mode (optional) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE</b>: Media ICE server discovery type, or how should SDK figure out which the actual set ICE servers to use internally. Use ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V2 to utilize a V2 Xirsys configuration URL to retrieve the ICE servers. Use ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3 to utilize a V3 Xirsys configuration URL to retrieve the ICE servers. Use ICE_SERVERS_CUSTOM if you don't want to use a configuration URL, but instead provide the set of ICE servers youself to the SDK (i.e. the App needs to have logic to retrieve them and provide them) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use when using a Xirsys configuration URL, like <i>'https://service.xirsys.com/ice'</i> for Xirsys V2 (i.e. ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V2), and <i>https://es.xirsys.com/_turn/</i> for Xirsys V3 (i.e. ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3). If no Xirsys configuration URL is used (i.e. ICE_SERVERS_CUSTOM) then this key is not applicable shouldn't be passed (optional) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication when using a Xirsys configuration URL (optional)  <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication when using a Xirsys configuration URL (optional). Be VERY careful to securely handle this inside your App. Never store it statically and in cleartext form in your App before submitting to Google Play Store as you run the risk of any of the folks downloading it figuring it out your credentials  <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN</b>: ICE Domain to be used in the ICE configuration URL when using a Xirsys configuration URL. Notice that V2 Domains are called Channels in V3 organization, but we use this same key in both cases (optional) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? Always set this to true, unless you have a very good reason to use cleartext signaling (like debugging). If this is the case, then a key pair is generated when
    *                        signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *                        the System Wide Android CA Store, so that we properly accept only legit server certificates. If not passed (or false) signaling is cleartext (optional) <br>
    *                        <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *                        <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING</b>: The SDK provides the user with default sounds for calling, ringing, busy (declined) and message events, but the user can override them
    *                        by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them here with Resource IDs like R.raw.user_provided_calling_sound. This parameter
    *                        configures the sound you will hear when you make a call and until the call is either replied or you hang up<br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING</b>: The sound you will hear when you receive a call <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED</b>: The sound you will hear when your call is declined <br>
    *                        <b>RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE</b>: The sound you will hear when you receive a message <br>
    *
    *                        //push notification keys
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME</b>: name of the client application
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL</b>: account's email
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD </b>: password for an account
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT</b>: true if we want to enable push on server for the account, otherwise false
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN</b>: domain for the push notifications; for example: push.restcomm.com
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN</b>: Restcomm HTTP domain, like 'cloud.restcomm.com'
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY</b>: server hash key for created application in firebase cloud messaging
    *                        <b>RCDevice.ParameterKeys.PUSH_NOTIFICATION_TIMEOUT_MESSAGING_SERVICE</b>: RCDevice will have timer introduced for closing because of the message background logic this is introduced in the design. The timer by default will be 5 seconds; It can be changed by sending parameter with value (in milliseconds)

    * @param deviceListener  The listener for upcoming RCDevice events
    * @return True always for now
    * @see RCDevice
    */
   public boolean initialize(Context activityContext, HashMap<String, Object> parameters, RCDeviceListener deviceListener) throws RCException
   {
      try {
         if (!isServiceInitialized) {

            isServiceInitialized = true;
            //context = activityContext;
            state = DeviceState.OFFLINE;

            RCLogger.i(TAG, "RCDevice(): " + parameters.toString());


            //this.updateCapabilityToken(capabilityToken);
            this.listener = deviceListener;


            RCUtils.validateDeviceParms(parameters);

            storageManagerPreferences = new StorageManagerPreferences(this);
            StorageUtils.saveParams(storageManagerPreferences, parameters);

            //because intents are saved as uri strings we need to check; do we have an
            //actual intent. If not, we must check is it a string and return an intent
            Object callObj = parameters.get(RCDevice.ParameterKeys.INTENT_INCOMING_CALL);
            Object messageObj = parameters.get(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE);

            if (callObj instanceof String && messageObj instanceof String) {
               Intent intentCall;
               Intent intentMessage;

               try {
                  intentCall = Intent.parseUri((String) callObj, Intent.URI_INTENT_SCHEME);
               } catch (URISyntaxException e) {
                  throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_INTENT_CALL_MISSING);
               }

               try {
                  intentMessage = Intent.parseUri((String) messageObj, Intent.URI_INTENT_SCHEME);
               } catch (URISyntaxException e) {
                  throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_INTENT_MESSAGE_MISSING);
               }

               setIntents(intentCall, intentMessage);
            } else if (callObj instanceof Intent && messageObj instanceof Intent){
               setIntents((Intent) callObj, (Intent) messageObj);
            }

            //set messages timer
            if (parameters.get(ParameterKeys.PUSH_NOTIFICATION_TIMEOUT_MESSAGING_SERVICE) != null){
               messageTimeOutIntervalLimit = (long) parameters.get(ParameterKeys.PUSH_NOTIFICATION_TIMEOUT_MESSAGING_SERVICE);
            }

            connections = new HashMap<String, RCConnection>();

            //if there is already data for registering to push, dont clear it (onOpenReply is using this parameter)
            // initialize JAIN SIP if we have connectivity
            this.parameters = parameters;

            // initialize registration FSM before we start signaling and push notification registrations
            // important: needs to happen *before* signaling and push registration
            startRegistrationFsm();

            // check if TURN keys are there
            //params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));
            if (signalingClient == null) {
               signalingClient = new SignalingClient();
               signalingClient.open(this, getApplicationContext(), parameters);
            }

            registerForPushNotifications(false);

            if (audioManager == null) {
               // Create and audio manager that will take care of audio routing,
               // audio modes, audio device enumeration etc.
               audioManager = AppRTCAudioManager.create(getApplicationContext(), new Runnable() {
                          // This method will be called each time the audio state (number and
                          // type of devices) has been changed.
                          @Override
                          public void run() {
                             onAudioManagerChangedState();
                          }
                       }
               );

               // Store existing audio settings and change audio mode to
               // MODE_IN_COMMUNICATION for best possible VoIP performance.
               RCLogger.d(TAG, "Initializing the audio manager...");
               audioManager.init(parameters);
            }
         }
         else {
            throw new RCException(RCClient.ErrorCodes.ERROR_DEVICE_ALREADY_INITIALIZED);
         }
         return false;
      }catch (RCException e){
         isServiceInitialized = false;
         throw e;
      }
   }

   /**
    * Set level for SDK logging
    * @param level  Level to set, following the Android logging lever defined at Log.*
    */
   public void setLogLevel(int level)
   {
      RCLogger.setLogLevel(level);
   }

   /**
    * Used internally in the library to get the current connectivity status
    * @return The connectivity status
    */
   RCDeviceListener.RCConnectivityStatus getConnectivityStatus()
   {
      return cachedConnectivityStatus;
   }

   // 'Copy' constructor
   RCDevice(RCDevice device)
   {
      this.incomingSoundEnabled = device.incomingSoundEnabled;
      this.outgoingSoundEnabled = device.outgoingSoundEnabled;
      this.disconnectSoundEnabled = device.disconnectSoundEnabled;
      this.listener = device.listener;

      // Not used yet
      this.capabilities = null;
   }

   /**
    * Shut down and release the RCDevice Service. Notice that the actual release of the Android Service happens when we get a reply from Signaling
    */
   public void release()
   {
      RCLogger.i(TAG, "release()");
      if (audioManager != null) {
         audioManager.close();
         audioManager = null;
      }

      if (signalingClient != null) {
         signalingClient.close();
         signalingClient = null;
      }
      state = DeviceState.OFFLINE;

      isServiceAttached = false;
      isServiceInitialized = false;

      stopRegistrationFsm();

      stopForeground(true);
   }

   /**
    * Update Device listener to be receiving Device related events. This is usually needed when we switch activities and want the new activity
    * to continue receiving RCDevice events
    *
    * @param listener New device listener
    */
   public void setDeviceListener(RCDeviceListener listener)
   {
      RCLogger.d(TAG, "setDeviceListener()");

      this.listener = listener;
   }

   /**
    * Retrieves the capability token passed to RCClient.createDevice (<b>Not implemented yet</b>)
    *
    * @return Capability token
    */
   public String getCapabilityToken()
   {
      return "";
   }

   /**
    * Updates the capability token (<b>Not implemented yet</b>)
    * @param token the token to use
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
    *                   <b>RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED</b>: Whether we want WebRTC video enabled or not. If this is true, then we must provide proper views for CONNECTION_LOCAL_VIDEO and CONNECTION_REMOTE_VIDEO respectively (please check below). If this is false, then CONNECTION_LOCAL_VIDEO and CONNECTION_REMOTE_VIDEO must either not be provided or be null (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO</b>: PercentFrameLayout containing the view where we want the local video to be rendered in. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO</b>: PercentFrameLayout containing the view where we want the remote video to be rendered. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required (optional)  <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC</b>: Preferred audio codec to use. Default is OPUS. Possible values are enumerated at <i>RCConnection.AudioCodec</i> (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC</b>: Preferred video codec to use. Default is VP8. Possible values are enumerated at <i>RCConnection.VideoCodec</i> (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION</b>: Preferred video resolution to use. Default is HD (1280x720). Possible values are enumerated at <i>RCConnection.VideoResolution</i>  (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE</b>: Preferred frame rate to use. Default is 30fps. Possible values are enumerated at <i>RCConnection.VideoFrameRate</i> (optional) <br>
    *                   <b>RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS</b>: An optional HashMap&lt;String,String&gt; of custom SIP headers we want to add. For an example
    *                   please check restcomm-helloworld or restcomm-olympus sample Apps (optional) <br>
    *                   <b>RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT</b>: An optional Integer denoting how long to wait for ICE candidates. Zero means default behaviour which is
    *                   to depend on onIceGatheringComplete from Peer Connection facilities. Any other integer value means to wait at most that amount of time no matter if onIceGatheringComplete has fired.
    *                   The problem we are addressing here is the new Peer Connection ICE gathering timeout which is 40 seconds which is way too long. Notice that the root cause here is in reality
    *                   lack of support for Trickle ICE, so once it is supported we won't be needing such workarounds.
    *                   please check restcomm-helloworld or restcomm-olympus sample Apps (optional) <br>
    * @param listener   The listener object that will receive events when the connection state changes
    * @return An RCConnection object representing the new connection or null in case of error. Error
    * means that RCDevice.state not ready to make a call (this usually means no WiFi available)
    */
   public RCConnection connect(HashMap<String, Object> parameters, RCConnectionListener listener) throws RCException
   {
      RCLogger.i(TAG, "connect(): " + parameters.toString());

      RCUtils.validateConnectionParms(parameters);

      if (cachedConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         // Phone state Intents to capture connection failed event
         String username = "";
         if (parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER) != null)
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
         throw new RCException(RCClient.ErrorCodes.ERROR_CONNECTION_DEVICE_NOT_READY);
         //return null;
      }
   }


   /**
    * Send an instant message to an endpoint
    *
    * @param message    Message text
    * @param parameters Parameters used for the message, such as 'username' that holds the recepient for the message
    * @return  Job Id (string) of the the job created to asynchronously handle the transmission of the message, so that we can correlate the status when it arrives later
    */
   public String sendMessage(String message, Map<String, String> parameters) throws RCException
   {
      RCLogger.i(TAG, "sendMessage(): message:" + message + "\nparameters: " + parameters.toString());


      if (state != DeviceState.OFFLINE) {
         HashMap<String, Object> messageParameters = new HashMap<>();
         messageParameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER));
         messageParameters.put("text-message", message);
         return signalingClient.sendMessage(messageParameters);
      }
      else {
         //return new MessageStatus(null, false);
         throw new RCException(RCClient.ErrorCodes.ERROR_MESSAGE_SEND_FAILED_DEVICE_OFFLINE,
                 RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_SEND_FAILED_DEVICE_OFFLINE));
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
    * Retrieve the capabilities (<b>Not Implemented yet</b>)
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
   public void setIntents(Intent callIntent, Intent messageIntent)
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
   // Internal method; not meant for application use
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
    *               <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client. Be VERY careful to securely handle this inside your App. Never store it statically and in cleartext form in your App before submitting to Google Play Store as you run the risk of any of the folks downloading it figuring it out your credentials <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm instance to use, like <i>'cloud.restcomm.com'</i>. Don't pass this parameters (or leave empty) for registrar-less mode<br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use, like <i>'https://turn.provider.com/turn'</i> (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication (mandatory). Be VERY careful to securely handle this inside your App. Never store it statically and in cleartext form in your App before submitting to Google Play Store as you run the risk of any of the folks downloading it figuring it out your credentials <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN</b>: ICE Domain to be used in the ICE configuration URL (mandatory) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? If this is the case, then a key pair is generated when
    *               signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *               the System Wide Android CA Store, so that we properly accept only legit server certificates. If not passed (or false) signaling is cleartext (optional) <br>
    *               <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *               <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    *
    *                //push notification keys
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME</b>: name of the client application
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL</b>: account's email
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD </b>: password for an account
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT</b>: true if we want to enable push on server for the account, otherwise false
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN</b>: domain for the push notifications; for example: push.restcomm.com
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN</b>: Restcomm HTTP domain, like 'cloud.restcomm.com'
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY</b>: server hash key for created application in firebase cloud messaging
    *                <b>RCDevice.ParameterKeys.PUSH_NOTIFICATION_TIMEOUT_MESSAGING_SERVICE</b>: RCDevice will have timer introduced for closing because of the message background logic this is introduced in the design. The timer by default will be 5 seconds; It can be changed by sending parameter with value (in milliseconds)
    * @see RCDevice
    */
   @SuppressWarnings("unchecked")
   public void reconfigure(HashMap<String, Object> params) throws RCException
   {
      // Let's try to fail early & synchronously on validation issues to make them easy to spot,
      // and if something comes up asynchronously either in signaling or push configuration, we notify via callback


      if (storageManagerPreferences == null) {
         storageManagerPreferences = new StorageManagerPreferences(this);
      }

      HashMap<String, Object>  cachedParams = (HashMap<String, Object>)storageManagerPreferences.getAllEntries();
      cachedParams.putAll(params);

      RCUtils.validateSettingsParms(cachedParams);

      // remember that the new parameters can be just a subset of the currently stored in this.parameters, so to update the current parameters we need
      // to merge them with the new (i.e. keep the old and replace any new keys with new values)
      this.parameters = JainSipConfiguration.mergeParameters(params, cachedParams);
      //save params for background
      StorageUtils.saveParams(storageManagerPreferences, parameters);

      signalingClient.reconfigure(params);
      registerForPushNotifications(true);
   }

   HashMap<String, Object> getParameters()
   {
      return parameters;
   }

   /**
    * Internal method; not meant for application use.
    * @param jobId the jobId to use for the filtering of connections
    * @return the connection that has the given job id
    */
   public SignalingClient.SignalingClientCallListener getConnectionByJobId(String jobId)
   {
      // TODO: we can't make it package-local as signaling facilities reside at a separate package,but still this needs to be hidden from App use
      if (connections.containsKey(jobId)) {
         return connections.get(jobId);
      }
      else {
         throw new RuntimeException("No RCConnection exists to handle message with jobid: " + jobId);
      }
   }

   // -- SignalingClientListener events for incoming messages from signaling thread
   // Replies
    /**
     * Internal service callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param connectivityStatus connectivity status at the point of the reply
     * @param status status code for this action
     * @param text status text for this action
     */
    public void onOpenReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
    {
       RCLogger.i(TAG, "onOpenReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);

       cachedConnectivityStatus = connectivityStatus;

       registrationFsm.fire(RegistrationFsm.FSMEvent.signalingInitializationRegistrationEvent, new RegistrationFsmContext(connectivityStatus, status, text));
    }


    /**
     * Internal service callback for when we get a reply from release(); not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param status status code for this action
     * @param text status text for this action
     */
   public void onCloseReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onCloseReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      if (this.listener != null) {
         this.listener.onReleased(this, status.ordinal(), text);
      }
      this.listener = null;
      // Shut down the service
      release();
      stopSelf();

   }

    /**
     * Internal service callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param connectivityStatus connectivity status at the point of the reply
     * @param status status code for this action
     * @param text status text for this action
     */
   public void onReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onReconfigureReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;

      registrationFsm.fire(RegistrationFsm.FSMEvent.signalingReconfigureRegistrationEvent, new RegistrationFsmContext(connectivityStatus, status, text));
   }

    /**
     * Internal service callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param status status code for this action
     * @param text status text for this action
     */
   public void onMessageReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onMessageReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      if (isAttached()) {
         this.listener.onMessageSent(this, status.ordinal(), text, jobId);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onMessageSent(): " +
               RCClient.errorText(status));
      }

   }

   // Unsolicited Events
    /**
     * Internal service callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param peer the peer from which the call arrived
     * @param sdpOffer sdp offer sent by the peer
     * @param customHeaders any custom SIP headers sent by the peer
     */
   public void onCallArrivedEvent(String jobId, String peer, String sdpOffer, HashMap<String, String> customHeaders)
   {
      RCLogger.i(TAG, "onCallArrivedEvent(): id: " + jobId + ", peer: " + peer);

      // filter out potential '<' and '>' and leave just the SIP URI
      String peerSipUri = peer.replaceAll("^<", "").replaceAll(">$", "");

      RCConnection connection = new RCConnection.Builder(true, RCConnection.ConnectionState.CONNECTING, this, signalingClient, audioManager)
            .jobId(jobId)
            .incomingCallSdp(sdpOffer)
            .peer(peerSipUri)
            .deviceAlreadyBusy(state == DeviceState.BUSY)
            .customHeaders(customHeaders)
            .build();

      // keep connection in the connections hashmap
      connections.put(jobId, connection);

      if (state == DeviceState.BUSY) {
         // If we are already talking disconnect the new call
         connection.reject();
         return;
      }

      state = DeviceState.BUSY;

      if (isAttached()) {
         audioManager.playRingingSound();
         // Service is attached to an activity, let's send the intent normally that will open the call activity
         callIntent.setAction(ACTION_INCOMING_CALL);
         callIntent.putExtra(RCDevice.EXTRA_DID, peerSipUri);
         callIntent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO));
         if (customHeaders != null) {
            callIntent.putExtra(RCDevice.EXTRA_CUSTOM_HEADERS, customHeaders);
         }
         //startActivity(callIntent);

         if (!this.parameters.containsKey(ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS) || !(Boolean) this.parameters.get(ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS)) {
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            try {
               pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
               throw new RuntimeException("Pending Intent cancelled", e);
            }
         }
         else {
            // ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS == true, we need to broadcast a separate intent, so that the Test Case is able to receive it.
            // For some reason PendingIntent is not received properly even when we construct a broadcast for it using getBroadcast()
            Intent testIntent = new Intent(RCDevice.ACTION_INCOMING_CALL);  //, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
            testIntent.putExtra(RCDevice.EXTRA_DID, peerSipUri);
            sendBroadcast(testIntent);
         }
         // Phone state Intents to capture incoming phone call event
         sendQoSIncomingConnectionIntent(peerSipUri, connection);
      } else {
         onNotificationCall(connection, customHeaders);
      }
   }

    /**
     * Internal service callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     */
   public void onRegisteringEvent(String jobId)
   {
      RCLogger.i(TAG, "onRegisteringEvent(): id: " + jobId);
      state = DeviceState.OFFLINE;
      if (isAttached()) {
         this.listener.onConnectivityUpdate(this, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onConnectivityUpdate() due to new registering");
      }
   }

    /**
     * Internal signaling callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param peer the peer from which the text message arrived
     * @param messageText actual text of the incoming text message
     */
   public void onMessageArrivedEvent(String jobId, String peer, String messageText)
   {
      RCLogger.i(TAG, "onMessageArrivedEvent(): id: " + jobId + ", peer: " + peer + ", text: " + messageText);
      // filter out potential '<' and '>' and leave just the SIP URI
      String peerSipUri = peer.replaceAll("^<", "").replaceAll(">$", "");

      //parameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, from);

      if (messageIntent != null) {
         if (isAttached()) {
            audioManager.playMessageSound();

            messageIntent.setAction(ACTION_INCOMING_MESSAGE);
            messageIntent.putExtra(EXTRA_DID, peerSipUri);
            messageIntent.putExtra(EXTRA_MESSAGE_TEXT, messageText);
            //startActivity(messageIntent);

            if (!this.parameters.containsKey(ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS) || !(Boolean) this.parameters.get(ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS)) {
               PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, messageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
               try {
                  pendingIntent.send();
               } catch (PendingIntent.CanceledException e) {
                  throw new RuntimeException("Pending Intent cancelled", e);
               }
            }
            else {
               // ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS == true, we need to broadcast a separate intent, so that the Test Case is able to receive it.
               // For some reason PendingIntent is not received properly even when we construct a broadcast for it using getBroadcast()
               Intent testIntent = new Intent(RCDevice.ACTION_INCOMING_MESSAGE);  //, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
               testIntent.putExtra(EXTRA_DID, peerSipUri);
               testIntent.putExtra(EXTRA_MESSAGE_TEXT, messageText);
               sendBroadcast(testIntent);
            }
         } else {
            if (messageTimeoutHandler == null) {
               messageTimeoutHandler = new Handler();
            }
            startRepeatingTask();

            //set timer again
            messageTimeOutInterval = messageTimeOutIntervalLimit;
            onNotificationMessage(peerSipUri, messageText);
         }
      }
      else {
         // messageIntent is null cannot really forward event to App, lets ignore with a warning
         RCLogger.w(TAG, "onMessageArrivedEvent(): Incoming text message event is discarded because Intent is missing for incoming text messages. " +
                 "To receive such event please initialize RCDevice with a RCDevice.ACTION_INCOMING_MESSAGE intent");
      }
   }

    /**
     * Internal signaling callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param connectivityStatus connectivity status at the point of the reply
     * @param status status code for this action
     * @param text status text for this action
     */
   public void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.e(TAG, "onErrorEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      stopForeground(true);
      cachedConnectivityStatus = connectivityStatus;
      if (status != RCClient.ErrorCodes.SUCCESS) {
         if (isAttached()) {
            this.listener.onError(this, status.ordinal(), text);
         } else {
            RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onError(): " +
                    RCClient.errorText(status));
         }
      }

   }

    /**
     * Internal signaling callback; not meant for application use
     * @param jobId the job Id that was used in the original request, so that we can correlate the two
     * @param connectivityStatus connectivity status at the point of the reply
     */
   public void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      RCLogger.i(TAG, "onConnectivityEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus);
      cachedConnectivityStatus = connectivityStatus;

      storageManagerPreferences = new StorageManagerPreferences(this);
      boolean pushEnabled = storageManagerPreferences.getBoolean(ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
      String binding = storageManagerPreferences.getString(FcmConfigurationHandler.FCM_BINDING, null);
      boolean registeredForPush = binding!=null;

      if (state == DeviceState.OFFLINE && connectivityStatus != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone){
         if (pushEnabled && registeredForPush) {
            state = DeviceState.READY;
         } else if (!pushEnabled) {
            state = DeviceState.READY;
         }
      }
      if (state != DeviceState.OFFLINE && connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.OFFLINE;
      }
      if (isAttached()) {
         this.listener.onConnectivityUpdate(this, connectivityStatus);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onConnectivityUpdate(): " +
               connectivityStatus);
      }

   }

   // ---- Android Notifications Handling
   // Handle notification for incoming call
   void onNotificationCall(RCConnection connection, HashMap<String, String> customHeaders)
   {
      String peerSipUri = connection.getPeer().replaceAll("^<", "").replaceAll(">$", "");
      String peerUsername = peerSipUri.replaceAll(".*?sip:", "").replaceAll("@.*$", "");

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


      NotificationCompat.Builder builder = getNotificationBuilder(true);

      builder.setSmallIcon(R.drawable.ic_call_24dp)
              .setContentTitle(peerUsername)
              .setContentText(text)
              // Need this to show up as Heads-up Notification
              .setPriority(NotificationCompat.PRIORITY_HIGH)
              .setAutoCancel(true)  // cancel notification when user acts on it (Important: only applies to default notification area, not additional actions)
              .setVibrate(notificationVibrationPattern)
              .setVisibility(Notification.VISIBILITY_PUBLIC)
              .setLights(notificationColor, notificationColorPattern[0], notificationColorPattern[1]);

      if (audioManager != null)
         builder = builder.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_RINGING)));
      if (callIntent != null) {
         builder = builder
                 .addAction(R.drawable.ic_videocam_24dp, "Video", PendingIntent.getService(getApplicationContext(), 0, serviceIntentVideo, PendingIntent.FLAG_UPDATE_CURRENT))
                 .addAction(R.drawable.ic_call_24dp, "Audio", PendingIntent.getService(getApplicationContext(), 0, serviceIntentAudio, PendingIntent.FLAG_UPDATE_CURRENT))
                 .addAction(R.drawable.ic_call_end_24dp, "Hang Up", PendingIntent.getService(getApplicationContext(), 0, serviceIntentDecline, PendingIntent.FLAG_UPDATE_CURRENT))
                 .setContentIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDefault, PendingIntent.FLAG_UPDATE_CURRENT))
                 .setDeleteIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDelete, PendingIntent.FLAG_UPDATE_CURRENT));

      } else {
         //we dont want to show the notification to primary channel
         builder = builder.setContentIntent(PendingIntent.getService(getApplicationContext(), 0, serviceIntentDefault, PendingIntent.FLAG_UPDATE_CURRENT));
      }


      Notification notification = builder.build();
      // Add FLAG_INSISTENT so that the notification rings repeatedly (FLAG_INSISTENT is not exposed via builder, let's add manually)
      notification.flags = notification.flags | Notification.FLAG_INSISTENT;

      boolean notificationIdExists = true;
      Integer activeNotificationId = callNotifications.get(peerUsername);

      if (activeNotificationId == null) {
         activeNotificationId = notificationId;
         notificationIdExists = false;
      }

      //show to the user notification and start foreground
      startForeground(notificationId, notification);


      if (!notificationIdExists) {
         // We used a new notification id, so we need to update call notifications
         callNotifications.put(peerUsername, notificationId);
         notificationId++;
      }

      activeCallNotification = true;
   }

   // Handle notification for incoming message
   void onNotificationMessage(String peerSipUri, String messageText)
   {
      String peerUsername = peerSipUri.replaceAll(".*?sip:", "").replaceAll("@.*$", "");

      // Intent to open the call activity (for when tapping on the general notification area)
      Intent serviceIntentDefault = new Intent(ACTION_NOTIFICATION_MESSAGE_DEFAULT, null, getApplicationContext(), RCDevice.class);
      serviceIntentDefault.putExtra(RCDevice.EXTRA_DID, peerSipUri);
      serviceIntentDefault.putExtra(EXTRA_MESSAGE_TEXT, messageText);

      NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

      NotificationCompat.Builder builder = getNotificationBuilder(true);
      builder.setSmallIcon(R.drawable.ic_chat_24dp)
              .setContentTitle(peerUsername)
              .setContentText(messageText);
      if (audioManager != null)
         builder.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_MESSAGE)))  // R.raw.message_sample)) //
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

      // mId allows you to update the notification later on.
      notificationManager.notify(activeNotificationId, notification);
      if (!notificationIdExists) {
         messageNotifications.put(peerUsername, notificationId);
         notificationId++;
      }
   }

   // Handles intents from the Notifications subsystem (i.e. Notification -> RCDevice Service) and sends them to UI entities for processing if applicable
   void onNotificationIntent(Intent intent)
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
      /*
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
      */
      if (intentAction.equals(ACTION_NOTIFICATION_CALL_DEFAULT)) {
         if (callIntent != null) {
            callIntent.setAction(ACTION_INCOMING_CALL);
            // don't forget to copy the extras to callIntent
            callIntent.putExtras(intent);
            actionIntent = callIntent;
         } else {
            Context context = getApplicationContext();
            PackageManager packageManager = context.getPackageManager();
            actionIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());
         }

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

         release();

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

            if (!isAttached()) {
               // if the call has been requested to be disconnected, we shouldn't do any UI handling
               callIntent.setAction(ACTION_CALL_DISCONNECT);
               //callIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
               callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
               actionIntent = callIntent;
               startActivity(actionIntent);
            }

            /*
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
            */
         }

         return;
      }
      else if (intentAction.equals(ACTION_NOTIFICATION_MESSAGE_DEFAULT)) {
         if (messageIntent == null){
            storageManagerPreferences = new StorageManagerPreferences(this);
            String messageIntentString = storageManagerPreferences.getString(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, null);
            if (messageIntentString !=null){
               try {
                  messageIntent = Intent.parseUri(messageIntentString, Intent.URI_INTENT_SCHEME);

                  //service was stopped and user taps on Notification case
                  if (!isInitialized()){
                     HashMap<String, Object> parameters = StorageUtils.getParams(storageManagerPreferences);
                     try {
                        initialize(null, parameters, null);
                     } catch (RCException e) {
                        RCLogger.e(TAG, e.toString());
                     }
                  }
               } catch (URISyntaxException e) {
                  throw new RuntimeException("Failed to handle Notification");
               }
            }
         }
         messageIntent.setAction(ACTION_INCOMING_MESSAGE);

         // don't forget to copy the extras
         messageIntent.putExtras(intent);
         actionIntent = messageIntent;
         //we want to stop foreground (notification is tapped)
         stopForeground(true);
         stopRepeatingTask();
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

   // RCConnection mute has changed, need to update foreground notification
   void onNotificationMuteChanged(RCConnection connection)
   {
      notificationHandleForegroundUpdate(connection);
   }

   // RCConnection is established, need to add foreground notification
   void onNotificationCallConnected(RCConnection connection)
   {
      if (!foregroundNoticationActive) {
         foregroundNoticationActive = true;

         notificationHandleForegroundUpdate(connection);
      }
   }

   // RCConnection is disconnected, need to remove foreground notification
   void onNotificationCallDisconnected(RCConnection connection)
   {
      if (foregroundNoticationActive) {
         stopForeground(true);
         foregroundNoticationActive = false;
      }
   }

   // RCConnection has cancelled, need to transition incoming call notification to missed
   void onNotificationCallCanceled(RCConnection connection)
   {
      if (activeCallNotification) {
         // Peer has canceled the call and there's an active call ringing, we need to cancel the notification
         String peerUsername = connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", "");
         NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          //There's no peer username provided in Push Notification Message, so
          //the callNotifications map cannot be populated with the right key
          notificationManager.cancel(callNotifications.get(peerUsername));

         // And then create a new notification to show that the call is missed, together with a means to call the peer. Notice
         // that if this notification is tapped, the peer will be called using the video preference of
         callIntent.setAction(ACTION_OUTGOING_CALL);
         callIntent.putExtra(RCDevice.EXTRA_DID, peerUsername);
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
         NotificationCompat.Builder builder = getNotificationBuilder(true);
         builder.setSmallIcon(R.drawable.ic_phone_missed_24dp)
         .setContentTitle(connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", ""))
         .setContentText("Missed call")
         //.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ringing_sample)) // audioManager.getResourceIdForKey(ParameterKeys.RESOURCE_SOUND_RINGING)))
         // Need this to show up as Heads-up Notification
         .setPriority(NotificationCompat.PRIORITY_HIGH)
         .setAutoCancel(true)  // cancel notification when user acts on it (Important: only applies to default notification area, not additional actions)
         .setContentIntent(resultPendingIntent);

         Notification notification = builder.build();


         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
            //we need to stop foreground notification
            //if we leave it, the notification 'Missed call' will not be dismissible
            //because foreground notificaiton is shown for id: callNotifications.get(peerUsername)
            stopForeground(true);
         }

         notificationManager.notify(callNotifications.get(peerUsername), notification);
         // Remove the call notification, as it will be removed automatically
         //callNotifications.remove(peerUsername);

         activeCallNotification = false;
      }
   }

   public void onDeviceFSMInitializeDone(RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      if (isAttached()) {
         this.listener.onInitialized(this, connectivityStatus, status.ordinal(), text);
      } else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onInitialized(): " +
                 RCClient.errorText(status));
      }

      if (status == RCClient.ErrorCodes.SUCCESS) {
         state = DeviceState.READY;
      }

      // Right now I don't think we can support this. Even though it's the right thing to do from API perspective
      // we have the issue that Olympus is built in a way that if something goes wrong in RCDevice.initialize()
      // we don't initialize() again, instead we fix it with reconfigure() through SettingsActivity. Let's leave it
      // at that for now and we can reconsider once push & backgrounding is stable.
      /*
      else if (status == RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY) {
         state = DeviceState.OFFLINE;
      }
      else {
         release();
      }
      */
   }

   public void onDeviceFSMReconfigureDone(RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      if (status == RCClient.ErrorCodes.SUCCESS) {
         state = DeviceState.READY;
      }
      else {
         state = DeviceState.OFFLINE;
      }

      if (isAttached()) {
         this.listener.onReconfigured(this, connectivityStatus, status.ordinal(), text);
      }
      else {
         RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onReconfigured(): " +
                 RCClient.errorText(status));
      }
   }

   private void notificationHandleForegroundUpdate(RCConnection connection)
   {
      String peerUsername = connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", "");

      callIntent.setAction(ACTION_RESUME_CALL);
      callIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

      // Intent to open the call activity (for when tapping on the general notification area)
      Intent serviceIntentMute = new Intent(ACTION_NOTIFICATION_CALL_MUTE_AUDIO, null, getApplicationContext(), RCDevice.class);
      // Intent to decline the call without opening the App Activity
      Intent serviceIntentDisconnect = new Intent(ACTION_NOTIFICATION_CALL_DISCONNECT, null, getApplicationContext(), RCDevice.class);

      int resId = R.drawable.ic_mic_24dp;
      String muteString = "Unmuted";
      if (connection.isAudioMuted()) {
         resId = R.drawable.ic_mic_off_24dp;
         muteString = "Muted";
      }


      // we dont need high importance, user knows he is on the call
      NotificationCompat.Builder builder = getNotificationBuilder(false);

      builder.setSmallIcon(R.drawable.ic_phone_in_talk_24dp)
              .setContentTitle(peerUsername)
              .setContentText("Tap to return to call")
              // Notice that for some reason using FLAG_UPDATE_CURRENT doesn't work. The problem is that the intent creates a new Call Activity instead of
              // taking us to the existing.
              .addAction(resId, muteString, PendingIntent.getService(getApplicationContext(), 0, serviceIntentMute, PendingIntent.FLAG_CANCEL_CURRENT))
              .addAction(R.drawable.ic_call_end_24dp, "Hang up", PendingIntent.getService(getApplicationContext(), 0, serviceIntentDisconnect, PendingIntent.FLAG_CANCEL_CURRENT))
              .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, callIntent, PendingIntent.FLAG_CANCEL_CURRENT));

      startForeground(ONCALL_NOTIFICATION_ID, builder.build());
   }

   public void registerForPushNotifications(boolean isUpdate)
   {
      if (storageManagerPreferences != null) {
         boolean neededUpdate = new FcmConfigurationHandler(storageManagerPreferences, this).registerForPush(parameters, isUpdate);
         if (!neededUpdate) {
            // if no update is needed, we need to notify FSM right away that push registration is not needed, so that we don't get stuck here
            if (!isUpdate) {
               registrationFsm.fire(RegistrationFsm.FSMEvent.pushInitializationRegistrationNotNeededEvent, new RegistrationFsmContext(cachedConnectivityStatus,
                       RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS)));
            } else {
               registrationFsm.fire(RegistrationFsm.FSMEvent.pushReconfigureRegistrationNotNeededEvent, new RegistrationFsmContext(cachedConnectivityStatus,
                       RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS)));
            }
         }
         else {
            // update is needed. We are now asynchronously handling push registration updates;
            // we need that to convey to the UI that we are offline for a bit, until we get a response to the registrations
            state = DeviceState.OFFLINE;
            if (isAttached()) {
               this.listener.onConnectivityUpdate(this, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone);
            }
            else {
               RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onConnectivityUpdate() due to new push registering");
            }
         }
      }
   }

   /**
    * We need to have push notification parameters saved /cashed
    * if we need the SDK to work in the background.
    * This method is used to clear the cache!
    * <p>
    * IMPORTANT!!!
    * Use this method wisely and only after RCDevice() is released.
    */
   public void clearCache()
   {
      //clear push settings
      final HashMap<String, Object> paramsStorage = new HashMap<String, Object>();

      paramsStorage.put(FcmConfigurationHandler.FCM_ACCOUNT_SID, "");
      paramsStorage.put(FcmConfigurationHandler.FCM_CLIENT_SID, "");
      paramsStorage.put(FcmConfigurationHandler.FCM_APPLICATION, "");
      paramsStorage.put(FcmConfigurationHandler.FCM_BINDING, "");

      StorageManagerPreferences storageManagerPreferences = new StorageManagerPreferences(this);
      StorageUtils.saveParams(storageManagerPreferences, paramsStorage);
   }

   // -- FcmMessageListener
    @Override
    public void onRegisteredForPush(RCClient.ErrorCodes status, String text, boolean isUpdate) {
       RCLogger.i(TAG, "onRegisteredForPush(): status: " + status + ", text: " + text + ", update: " + isUpdate);

       if (!isUpdate) {
          registrationFsm.fire(RegistrationFsm.FSMEvent.pushInitializationRegistrationEvent, new RegistrationFsmContext(cachedConnectivityStatus, status, text));
       }
       else {
          registrationFsm.fire(RegistrationFsm.FSMEvent.pushReconfigureRegistrationEvent, new RegistrationFsmContext(cachedConnectivityStatus, status, text));
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
         Class aclass = Class.forName("org.restcomm.app.qoslib.Services.Intents.IntentHandler");
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
         Class aclass = Class.forName("org.restcomm.app.qoslib.Services.Intents.IntentHandler");
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
      if (connections.containsKey(jobId)) {
         connections.remove(jobId);
      }
   }

   private void onAudioManagerChangedState()
   {
      // TODO: disable video if AppRTCAudioManager.AudioDevice.EARPIECE is active
   }

    //FCM message time logic
    Runnable mStatusChecker = new Runnable() {
       @Override
       public void run() {
          if (messageTimeOutInterval >= 0){
             messageTimeOutInterval -= TIMEOUT_INTERVAL_TICK;
             messageTimeoutHandler.postDelayed(mStatusChecker, TIMEOUT_INTERVAL_TICK);
          } else {
             stopRepeatingTask();
             release();
          }
       }
    };

   void startRepeatingTask() {
      stopRepeatingTask();
      mStatusChecker.run();
   }

   void stopRepeatingTask() {
      if (messageTimeoutHandler != null) {
         messageTimeoutHandler.removeCallbacks(mStatusChecker);
      }
   }

   /**
    * Method returns the Notification builder
    * For Oreo devices we can have channels with HIGH and LOW importance.
    * If highImportance is true builder will be created with HIGH priority
    * For pre Oreo devices builder without channel will be returned
    * @param highImportance true if we need HIGH channel, false if we need LOW
    * @return
    */
   private NotificationCompat.Builder getNotificationBuilder(boolean highImportance){
      NotificationCompat.Builder builder;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
         if (highImportance){
            NotificationChannel channel = new NotificationChannel(PRIMARY_CHANNEL_ID, PRIMARY_CHANNEL, NotificationManager.IMPORTANCE_HIGH);
            channel.setLightColor(Color.GREEN);
            channel.enableLights(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.setVibrationPattern(notificationVibrationPattern);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(RCDevice.this, PRIMARY_CHANNEL_ID);
         } else {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = new NotificationChannel(DEFAULT_FOREGROUND_CHANNEL_ID, DEFAULT_FOREGROUND_CHANNEL, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(notificationChannel);

           builder = new NotificationCompat.Builder(RCDevice.this, DEFAULT_FOREGROUND_CHANNEL_ID);
         }

      } else {
         builder = new NotificationCompat.Builder(RCDevice.this);
      }

      return builder;
   }

   private void startRegistrationFsm()
   {
      RCLogger.i(TAG, "startRegistrationFsm()");

      // Build state transitions
      UntypedStateMachineBuilder fsmBuilder = StateMachineBuilderFactory.create(RegistrationFsm.class, RegistrationFsm.RCDeviceFSMListener.class);

      // Seems 'fromAny' doesn't work as expected, so sadly we need to add more states. Let's leave this around though in case we can improve in the future
      //fsmBuilder.transit().fromAny().to(RegistrationFsm.FSMState.signalingReadyState).on(RegistrationFsm.FSMEvent.signalingInitializationRegistrationEvent).callMethod("toSignalingInitializationReady");
      //fsmBuilder.transit().fromAny().to(RegistrationFsm.FSMState.pushReadyState).on(RegistrationFsm.FSMEvent.pushInitializationRegistrationEvent).callMethod("toPushInitializationReady");

      // Set up the state transitions of the FSM and associate methods to handle them
      // transitions to signaling ready, either during initialization or reconfiguration
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.signalingReadyState)
              .on(RegistrationFsm.FSMEvent.signalingInitializationRegistrationEvent).callMethod("toSignalingInitializationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.pushReadyState).to(RegistrationFsm.FSMState.signalingReadyState)
              .on(RegistrationFsm.FSMEvent.signalingInitializationRegistrationEvent).callMethod("toSignalingInitializationReady");

      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.signalingReadyState)
              .on(RegistrationFsm.FSMEvent.signalingReconfigureRegistrationEvent).callMethod("toSignalingReconfigurationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.pushReadyState).to(RegistrationFsm.FSMState.signalingReadyState)
              .on(RegistrationFsm.FSMEvent.signalingReconfigureRegistrationEvent).callMethod("toSignalingReconfigurationReady");


      // transitions to push ready when push configuration really happens asynchronously,
      // either during initialization or reconfiguration
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushInitializationRegistrationEvent).callMethod("toPushInitializationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.signalingReadyState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushInitializationRegistrationEvent).callMethod("toPushInitializationReady");

      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushReconfigureRegistrationEvent).callMethod("toPushReconfigurationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.signalingReadyState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushReconfigureRegistrationEvent).callMethod("toPushReconfigurationReady");



      // transitions to push ready when push configuration is not necessary because server already up to date,
      // either during initialization or reconfiguration
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushInitializationRegistrationNotNeededEvent).callMethod("toPushInitializationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.signalingReadyState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushInitializationRegistrationNotNeededEvent).callMethod("toPushInitializationReady");

      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.initialState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushReconfigureRegistrationNotNeededEvent).callMethod("toPushReconfigurationReady");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.signalingReadyState).to(RegistrationFsm.FSMState.pushReadyState)
              .on(RegistrationFsm.FSMEvent.pushReconfigureRegistrationNotNeededEvent).callMethod("toPushReconfigurationReady");


      // transitions back to initial state
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.signalingReadyState).to(RegistrationFsm.FSMState.initialState)
              .on(RegistrationFsm.FSMEvent.resetStateMachine).callMethod("toInitialState");
      fsmBuilder.externalTransition().from(RegistrationFsm.FSMState.pushReadyState).to(RegistrationFsm.FSMState.initialState)
              .on(RegistrationFsm.FSMEvent.resetStateMachine).callMethod("toInitialState");

      HashMap<String, String> map = new HashMap<>();
      // notice the extraParams argument is passed to the RegistrationFsm constructor
      registrationFsm = fsmBuilder.newStateMachine(RegistrationFsm.FSMState.initialState, this);
/*
      registrationFsm.addStateMachineListener(new StateMachine.StateMachineListener<UntypedStateMachine, Object, Object, Object>() {
         @Override
         public void stateMachineEvent(StateMachine.StateMachineEvent<UntypedStateMachine, Object, Object, Object> event)
         {
            RCLogger.i(TAG, "stateMachineEvent():" + event);
         }
      });
*/
   }

   private void stopRegistrationFsm()
   {
      RCLogger.i(TAG, "stopRegistrationFsm()");
      if (registrationFsm != null) {
         if (registrationFsm.isStarted() && !registrationFsm.isTerminated()) {
            RCLogger.i(TAG, "startRegistrationFsm(): terminate() actually ran");
            registrationFsm.terminate();
         }
         registrationFsm = null;
      }
   }

}
