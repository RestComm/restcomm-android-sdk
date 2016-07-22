package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import android.content.Context;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.javax.sip.ClientTransaction;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.Transaction;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.Formatter;

import org.apache.http.conn.util.InetAddressUtils;
import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.cert.CertPathValidatorException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class JainSipClient implements SipListener, JainSipNotificationManager.NotificationManagerListener {

   // Interface the JainSipClient listener needs to implement, to get events from us
   public interface JainSipClientListener {
      void onClientOpenedEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // on successful/unsuccessful register, onPrivateClientConnectorOpenedEvent

      void onClientErrorEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // mostly on unsuccessful register, onPrivateClientConnectorOpenErrorEvent

      void onClientClosedEvent(String id, RCClient.ErrorCodes status, String text);  // on successful unregister, onPrivateClientConnectorClosedEvent

      void onClientReconfigureEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // on successful register, onPrivateClientConnectorOpenedEvent

      void onClientConnectivityEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus);

      void onClientMessageArrivedEvent(String id, String peer, String messageText);

      void onClientMessageSentEvent(String id, RCClient.ErrorCodes status, String text);

      // Event to convey trying to Register, so that UI can convey that to user
      void onClientRegisteringEvent(String id);
   }

   public JainSipClientListener listener;
   JainSipMessageBuilder jainSipMessageBuilder;
   JainSipJobManager jainSipJobManager;
   JainSipNotificationManager jainSipNotificationManager;
   Context androidContext;
   HashMap<String, Object> configuration;
   // any client context that is not configuration related, like the rport
   HashMap<String, Object> jainSipClientContext;
   //boolean clientConnected = false;
   static boolean clientOpened = false;
   static final String TAG = "JainSipClient";
   // android handler token to identify registration refresh posts
   final int REGISTER_REFRESH_HANDLER_TOKEN = 1;
   Handler signalingHandler;
   final int DEFAULT_REGISTER_EXPIRY_PERIOD = 60;
   final int DEFAULT_LOCAL_SIP_PORT = 5090;
   // the registration refresh needs to happen sooner than expiry to make sure that the client has a registration at all times. Let's
   // set it to EXPIRY - 10 seconds. TODO: in the future we could randomize this so that for example it is between half the expiry
   // and full expiry (in this example, a random between [30, 60] seconds) to avoid having all android clients refreshing all at
   // the same time and stressing Restcomm. Actually this is how Sofia SIP in restcomm-ios-sdk does it by default.
   final int REGISTER_REFRESH_MINUS_INTERVAL = 10;
   // how long after we force close the client if it takes too long to process JainSipClient.close()
   static final int FORCE_CLOSE_INTERVAL = 3000;

   // JAIN SIP entities
   public SipFactory jainSipFactory;
   public SipStack jainSipStack;
   public ListeningPoint jainSipListeningPoint;
   public SipProvider jainSipProvider;

   public JainSipClient(Handler signalingHandler)
   {
      this.signalingHandler = signalingHandler;
   }

   // -- Published API
   public void open(String id, Context androidContext, HashMap<String, Object> configuration, JainSipClientListener listener)
   {
      RCLogger.i(TAG, "open(): " + configuration.toString());

      if (JainSipClient.clientOpened) {
         listener.onClientOpenedEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
               RCClient.ErrorCodes.ERROR_DEVICE_ALREADY_OPEN,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_ALREADY_OPEN));
         return;
      }

      this.listener = listener;
      this.androidContext = androidContext;
      this.configuration = configuration;
      jainSipMessageBuilder = new JainSipMessageBuilder();
      jainSipJobManager = new JainSipJobManager(this);
      jainSipNotificationManager = new JainSipNotificationManager(androidContext, signalingHandler, this);
      jainSipClientContext = new HashMap<String, Object>();

      jainSipFactory = SipFactory.getInstance();
      jainSipFactory.resetFactory();
      jainSipFactory.setPathName("android.gov.nist");

      Properties properties = new Properties();
      properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");

      // Setup TLS even if currently we aren't using it, so that if user changes the setting later
      // the SIP stack is ready to support it
      String keystoreFilename = "restcomm-android.keystore";
      HashMap<String, String> securityParameters = JainSipSecurityHelper.generateKeystore(androidContext, keystoreFilename);
      JainSipSecurityHelper.setProperties(properties, securityParameters.get("keystore-path"), securityParameters.get("keystore-password"));

      if (configuration.containsKey(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED) &&
            configuration.get(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED) == true) {
         // You need 16 for logging traces. 32 for debug + traces.
         // Your code will limp at 32 but it is best for debugging.
         properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
         File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
         properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", downloadPath.getAbsolutePath() + "/debug-jain.log");
         properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", downloadPath.getAbsolutePath() + "/server-jain.log");
      }

      try {
         jainSipStack = jainSipFactory.createSipStack(properties);
         jainSipJobManager.add(id, JainSipJob.Type.TYPE_OPEN, configuration);
      }
      catch (SipException e) {
         throw new RuntimeException("Failed to bootstrap the signaling stack", e);
         /*
         listener.onClientOpenedEvent(id, jainSipNotificationManager.getConnectivityStatus(), RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
         */
      }
   }

   public void close(final String id)
   {
      RCLogger.v(TAG, "close(): " + id);

      if (JainSipClient.clientOpened) {
         // cancel any pending scheduled registrations
         //signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

         // TODO: close any active calls
         //

         jainSipNotificationManager.close();
         jainSipJobManager.removeAll();

         if (configuration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !configuration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
            // non registrar-less, we need to unregister and when done shutdown
            jainSipJobManager.add(id, JainSipJob.Type.TYPE_CLOSE, this.configuration);
         }
         else {
            // registrar-less, just shutdown and notify UI thread
            try {
               jainSipClientUnbind();
               jainSipClientStopStack();

               listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
            }
            catch (JainSipException e) {
               e.printStackTrace();
               listener.onClientClosedEvent(id, e.errorCode, e.errorText);
            }
         }
      }
      else {
         throw new RuntimeException("JainSipClient already closed, bailing");
         /*
         RCLogger.w(TAG, "close(): JAIN SIP client already closed, bailing");
         listener.onClientClosedEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
               */
      }
   }

   public void reconfigure(String id, HashMap<String, Object> parameters, JainSipClientListener listener)
   {
      RCLogger.i(TAG, "reconfigure(): " + parameters.toString());

      // check which parameters actually changed by comparing this.configuration with parameters
      HashMap<String, Object> modifiedParameters = JainSipConfiguration.modifiedParameters(this.configuration, parameters);

      if (modifiedParameters.size() == 0) {
         listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipNotificationManager.getNetworkStatus()),
               RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
         return;
      }

      HashMap<String, Object> oldParameters = new HashMap<String, Object>();
      oldParameters.putAll(configuration);

      // remember that the new parameters can be just a subset of the currently stored in configuration, so to update the current parameters we need
      // to merge them with the new (i.e. keep the old and replace any new keys with new values)
      configuration = JainSipConfiguration.mergeParameters(configuration, parameters);

      // Set the media parameters right away, since they are irrelevant to signaling
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED));
      }
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_URL)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_URL, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_URL));
      }
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME));
      }
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD));
      }

      HashMap<String, Object> multipleParameters = new HashMap<String, Object>();
      multipleParameters.put("old-parameters", oldParameters);
      multipleParameters.put("new-parameters", configuration);
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED)) {
         // if signaling has changed we need to a. unregister from old using old creds, b. unbind, c. bind, d. register with new using new creds
         // start FSM and pass it both previous and current parameters
         jainSipJobManager.add(id, JainSipJob.Type.TYPE_RECONFIGURE_RELOAD_NETWORKING, multipleParameters);
      }
      else if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_USERNAME) ||
            modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_PASSWORD) ||
            modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN)) {
         // if username, password or domain has changed we need to a. unregister from old using old creds and b. register with new using new creds
         // start FSM and pass it both previous and current parameters
         jainSipJobManager.add(id, JainSipJob.Type.TYPE_RECONFIGURE, multipleParameters);
      }
      else {
         listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipNotificationManager.getNetworkStatus()),
               RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
      }
   }

   // ------ Call-related calls
   public void call(String jobId, HashMap<String, Object> parameters, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "call(): id: " + jobId + ", parameters: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      JainSipCall jainSipCall = new JainSipCall(this, listener);
      jainSipCall.open(jobId, parameters);
   }

   public void accept(String jobId, HashMap<String, Object> parameters, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "accept(): id: " + jobId + ", parameters: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      if (jainSipJob == null) {
         throw new RuntimeException("Error accepting a call that doesn't exist in job manager");
      }
      jainSipJob.jainSipCall.accept(jainSipJob, parameters);
   }

   public void disconnect(String jobId, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "hangup(): id: " + jobId);

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      jainSipJob.jainSipCall.disconnect(jainSipJob);
   }

   public void sendDigits(String jobId, String digits)
   {
      RCLogger.i(TAG, "sendDigits(): id: " + jobId + ", digits: " + digits);

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      jainSipJob.jainSipCall.sendDigits(jainSipJob, digits);
   }

   public void sendMessage(String id, HashMap<String, Object> parameters)
   {
      RCLogger.i(TAG, "call(): id: " + id + ", parameters: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onClientMessageSentEvent(id, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      try {
         Transaction transaction = jainSipClientSendMessage(parameters);
         jainSipJobManager.add(id, JainSipJob.Type.TYPE_MESSAGE, transaction, parameters, null);
      }
      catch (JainSipException e) {
         listener.onClientMessageSentEvent(id, e.errorCode, e.errorText);
      }
   }

   // ------ Internal APIs
   // Setup JAIN networking facilities
   public void jainSipClientBind(HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.w(TAG, "bind()");
      if (jainSipListeningPoint == null) {
         if (!jainSipNotificationManager.haveConnectivity()) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         }

         // new network interface is up, let's retrieve its ip address
         String transport = "tcp";
         if (JainSipConfiguration.getBoolean(configuration, RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED)) {
            transport = "tls";
         }

         Integer port = DEFAULT_LOCAL_SIP_PORT;
         if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT)) {
            port = (Integer) parameters.get(RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT);
         }

         try {
            jainSipListeningPoint = jainSipStack.createListeningPoint(getIPAddress(true), port, transport);
            jainSipProvider = jainSipStack.createSipProvider(jainSipListeningPoint);
            jainSipProvider.addSipListener(this);
            jainSipMessageBuilder.initialize(jainSipFactory, jainSipProvider);
         }
         /*
         catch (SocketException e) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE), e);
         }
         */
         catch (Exception e) {
            // not much the user can do, probably programming error
            throw new RuntimeException("Failed to set up networking facilities", e);
            /*
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING), e);
                  */
         }
      }
      else {
         //RCLogger.e(TAG, "jainSipBind(): Error: Listening point already instantiated");
         throw new RuntimeException("Error: listening point already created");
      }
   }

   // Release JAIN networking facilities
   public void jainSipClientUnbind() throws JainSipException
   {
      RCLogger.w(TAG, "unbind()");
      if (jainSipListeningPoint != null) {
         try {
            jainSipProvider.removeSipListener(this);
            if (jainSipProvider.getListeningPoints().length > 1) {
               RCLogger.e(TAG, "unbind(): Listening Point count > 1: " + jainSipProvider.getListeningPoints().length);
            }
            jainSipStack.deleteSipProvider(jainSipProvider);
            jainSipStack.deleteListeningPoint(jainSipListeningPoint);

            jainSipListeningPoint = null;
         }
         catch (ObjectInUseException e) {
            throw new RuntimeException("Failed to tear down networking facilities", e);
         }
      }
   }

   void jainSipClientStartStack()
   {
      try {
         jainSipStack.start();
         JainSipClient.clientOpened = true;
      }
      catch (SipException e) {
         throw new RuntimeException("Failed to start the signaling stack", e);
      }
   }

   void jainSipClientStopStack()
   {
      jainSipStack.stop();
      jainSipMessageBuilder.shutdown();
      jainSipFactory.resetFactory();

        /*
        configuration = null;
        androidContext = null;
        listener = null;
        jainSipFsm = null;
        */

      JainSipClient.clientOpened = false;
   }

   public ClientTransaction jainSipClientRegister(JainSipJob jainSipJob, final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipRegister()");
      // Debug purposes to track the JainSipJob objects
      RCLogger.v(TAG, "jainSipRegister(), job count: " + jainSipJobManager.jobs.size());

      if (!jainSipNotificationManager.haveConnectivity()) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
      }

      int expiry = DEFAULT_REGISTER_EXPIRY_PERIOD;
      if (parameters.containsKey("signaling-register-expiry") && !parameters.get("signaling-register-expiry").equals("")) {
         expiry = (Integer) parameters.get("signaling-register-expiry");
         if (expiry <= REGISTER_REFRESH_MINUS_INTERVAL) {
            RCLogger.w(TAG, "jainSipRegister(): Register expiry period too small, using default: " + DEFAULT_REGISTER_EXPIRY_PERIOD);
            expiry = DEFAULT_REGISTER_EXPIRY_PERIOD;
         }
      }

      ClientTransaction transaction;
      try {
         Request registerRequest = jainSipMessageBuilder.buildRegisterRequest(jainSipListeningPoint, expiry, parameters);
         RCLogger.v(TAG, "jainSipRegister(): Sending SIP request: \n" + registerRequest.toString());

         // only notify on registering on specific types of jobs, otherwise we would swamp the App with notifications
         if (jainSipJob.type == JainSipJob.Type.TYPE_RECONFIGURE || jainSipJob.type == JainSipJob.Type.TYPE_RECONFIGURE_RELOAD_NETWORKING ||
               jainSipJob.type == JainSipJob.Type.TYPE_START_NETWORKING || jainSipJob.type == JainSipJob.Type.TYPE_RELOAD_NETWORKING) {
            listener.onClientRegisteringEvent(jainSipJob.id);
         }

         // Remember that this might block waiting for DNS server
         transaction = this.jainSipProvider.getNewClientTransaction(registerRequest);
         transaction.sendRequest();
      }
      catch (SipException e) {
         if (e.getMessage().contains("Trust anchor for certification path not found")) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_UNTRUSTED_SERVER), e);
         }
         else {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT), e);
         }
      }

      // cancel any pending scheduled registrations (in case this is an on-demand registration and we end up posting to handler on top of the old)
      signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);
      // schedule a registration update after 'registrationRefresh' seconds
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            jainSipJobManager.add(Long.toString(System.currentTimeMillis()), JainSipJob.Type.TYPE_REGISTER_REFRESH, parameters);
         }
      };
      signalingHandler.postAtTime(runnable, REGISTER_REFRESH_HANDLER_TOKEN, SystemClock.uptimeMillis() + (expiry - REGISTER_REFRESH_MINUS_INTERVAL) * 1000);

      return transaction;
   }

   public ClientTransaction jainSipClientUnregister(final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipUnregister()");

      if (!jainSipNotificationManager.haveConnectivity()) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
      }

      ClientTransaction transaction = null;
      try {
         Request registerRequest = jainSipMessageBuilder.buildRegisterRequest(jainSipListeningPoint, 0, parameters);
         RCLogger.v(TAG, "jainSipUnregister(): Sending SIP request: \n" + registerRequest.toString());

         // Remember that this might block waiting for DNS server
         transaction = this.jainSipProvider.getNewClientTransaction(registerRequest);
         transaction.sendRequest();
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_COULD_NOT_CONNECT), e);
      }

      // cancel any pending scheduled registrations
      signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

      return transaction;
   }

   public Transaction jainSipClientSendMessage(final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipClientSendMessage()");

      try {
         Request request = jainSipMessageBuilder.buildMessageRequest((String) parameters.get("username"),
               (String) parameters.get("text-message"), jainSipListeningPoint, configuration);
         RCLogger.v(TAG, "jainSipClientSendMessage(): Sending SIP request: \n" + request.toString());

         ClientTransaction transaction = this.jainSipProvider.getNewClientTransaction(request);
         transaction.sendRequest();
         return transaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_MESSAGE_SEND_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_SEND_FAILED), e);
      }
   }

   // Notice that this is used both for registrations and calls
   public void jainSipAuthenticate(JainSipJob jainSipJob, HashMap<String, Object> parameters, ResponseEventExt responseEventExt) throws JainSipException
   {
      try {
         AuthenticationHelper authenticationHelper = ((SipStackExt) jainSipStack).getAuthenticationHelper(
               new JainSipAccountManagerImpl((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
                     responseEventExt.getRemoteIpAddress(), (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_PASSWORD)), jainSipMessageBuilder.getHeaderFactory());

         // we 're subtracting one since the first attempt has already taken place
         // (that way we are enforcing MAX_AUTH_ATTEMPTS at most)
         if (jainSipJob.shouldRetry()) {
            ClientTransaction authenticationTransaction = authenticationHelper.handleChallenge(responseEventExt.getResponse(),
                  (ClientTransaction) jainSipJob.transaction, jainSipProvider, 5, true);

            // update previous transaction with authenticationTransaction (remember that previous ended with 407 final response)
            //String authCallId = ((CallIdHeader) authenticationTransaction.getRequest().getHeader("Call-ID")).getCallId();
            jainSipJob.updateTransaction(authenticationTransaction);
            // TODO: not sure if this is needed. Auth doesn't change Call-Id, right?
            //jainSipJob.updateCallId(authCallId);
            RCLogger.v(TAG, "Sending SIP request: \n" + authenticationTransaction.getRequest().toString());
            authenticationTransaction.sendRequest();
            jainSipJob.increaseAuthAttempts();
         }
         else {
            // actually this should not happen. Restcomm should return forbidden if the credentials are wrong and not challenge again
            /*
            throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED));
                  */
            throw new RuntimeException("Failed to authenticate after max attempts");
         }
      }
      catch (Exception e) {
         // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
         // we can adjust and only do a e.printStackTrace()
         throw new RuntimeException("Failed to authenticate", e);
      }
   }

   // ------ SipListener events
   // Remember that SipListener events run in a separate thread created by JAIN SIP, which makes sharing of resources between our signaling thread and this
   // JAIN SIP thread a bit difficult. To avoid that let's do the actual handling of these events in the signaling thread.
   public void processRequest(final RequestEvent requestEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            Request request = requestEvent.getRequest();
            RCLogger.v(TAG, "Received SIP request: \n" + request.toString());
            CallIdHeader callIdHeader = (CallIdHeader)request.getHeader("Call-ID");
            String callId = callIdHeader.getCallId();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            String method = request.getMethod();

            if (method.equals(Request.INVITE)) {
               // New INVITE, need to create new job
               JainSipCall jainSipCall = new JainSipCall(JainSipClient.this, (JainSipCall.JainSipCallListener)listener);
               // Remember, this is new dialog and hence serverTransaction is null
               JainSipJob jainSipJob = jainSipJobManager.add(callId, JainSipJob.Type.TYPE_CALL, null, null, jainSipCall);

               jainSipCall.processRequest(jainSipJob, requestEvent);
            }
            else if (method.equals(Request.MESSAGE)) {
               try {
                  if (serverTransaction == null) {
                     // no server transaction yet
                     serverTransaction = jainSipProvider.getNewServerTransaction(request);
                  }

                  Response response = jainSipMessageBuilder.buildResponse(Response.OK, request);
                  RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
                  serverTransaction.sendResponse(response);
                  String messageText = ((SIPMessage)request).getMessageContent();
                  listener.onClientMessageArrivedEvent(callId, ((SIPMessage)request).getFrom().getAddress().toString(), messageText);
               }
               catch (Exception e) {
                  e.printStackTrace();
               }
            }
            else if (method.equals(Request.BYE) || method.equals(Request.CANCEL) || method.equals(Request.ACK)) {
               JainSipJob jainSipJob = jainSipJobManager.getByCallId(callId);
               if (jainSipJob == null) {
                  // no need to notify UI thread
                  RCLogger.e(TAG, "processRequest(): error, got request for unknown transaction job. Method: " + method);
                  return;
               }

               // forward to JainSipCall for processing
               jainSipJob.jainSipCall.processRequest(jainSipJob, requestEvent);
            }
         }
      };
      signalingHandler.post(runnable);
   }

   public void processResponse(final ResponseEvent responseEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
            Response response = responseEvent.getResponse();
            RCLogger.v(TAG, "Received SIP response: \n" + response.toString());

            //JainSipJob jainSipJob = jainSipJobManager.getByBranchId(responseEvent.getClientTransaction().getBranchId());
            JainSipJob jainSipJob = jainSipJobManager.getByCallId(((CallIdHeader)response.getHeader("Call-ID")).getCallId());
            if (jainSipJob == null) {
               RCLogger.e(TAG, "processResponse(): error, got response for unknown job");
               return;
            }

            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            String method = cseq.getMethod();
            if (method.equals(Request.REGISTER)) {
               if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
                  jainSipJob.processFsm(jainSipJob.id, "auth-required", responseEventExt, null, null);
               }
               else if (response.getStatusCode() == Response.FORBIDDEN) {
                  jainSipJob.processFsm(jainSipJob.id, "register-failure", null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN));
               }
               else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
                  jainSipJob.processFsm(jainSipJob.id, "register-failure", null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE));
               }
               else if (response.getStatusCode() == Response.OK) {
                  // register succeeded
                  ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
                  // keep around the Via received and rport parms so that we can populate the contact properly
                  if (viaHeader.getReceived() != null) {
                     jainSipClientContext.put("via-received", viaHeader.getReceived());
                  }
                  else {
                     jainSipClientContext.remove("via-received");
                  }

                  if (viaHeader.getRPort() != -1) {
                     jainSipClientContext.put("via-rport", viaHeader.getRPort());
                  }
                  else {
                     jainSipClientContext.remove("via-rport");
                  }

                  jainSipJob.processFsm(jainSipJob.id, "register-success", null, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
               }
            }
            else if (method.equals(Request.INVITE) || method.equals(Request.BYE) || method.equals(Request.CANCEL) ||
                  method.equals(Request.INFO)) {
               // forward to JainSipCall for processing
               jainSipJob.jainSipCall.processResponse(jainSipJob, responseEvent);
            }
            else if (method.equals(Request.MESSAGE)) {
               if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
                  try {
                     jainSipAuthenticate(jainSipJob, configuration, responseEventExt);
                  }
                  catch (JainSipException e) {
                     listener.onClientMessageSentEvent(jainSipJob.id, e.errorCode, e.errorText);
                  }
               }
               else if (response.getStatusCode() == Response.OK) {
                  listener.onClientMessageSentEvent(jainSipJob.id, RCClient.ErrorCodes.SUCCESS,
                        RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
               }
               else if (response.getStatusCode() == Response.FORBIDDEN) {
                  listener.onClientMessageSentEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN));
               }
               else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
                  listener.onClientMessageSentEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE));
               }
            }
         }
      };
      signalingHandler.post(runnable);
   }

   public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            // TODO:
            /*
                RCLogger.i(TAG, "SipManager.processDialogTerminated: " + dialogTerminatedEvent.toString() + "\n" +
                        "\tdialog: " + dialogTerminatedEvent.getDialog().toString());
            */
         }
      };
      signalingHandler.post(runnable);
   }

   public void processIOException(final IOExceptionEvent exceptionEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            // TODO:
            RCLogger.e(TAG, "SipManager.processIOException: " + exceptionEvent.toString() + "\n" +
                  "\thost: " + exceptionEvent.getHost() + "\n" +
                  "\tport: " + exceptionEvent.getPort());
         }
      };
      signalingHandler.post(runnable);
   }

   public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            // TODO:
            RCLogger.i(TAG, "processTransactionTerminated()");
            /*
                RCLogger.i(TAG, "SipManager.processTransactionTerminated: " + transactionTerminatedEvent.toString() + "\n" +
                        "\tclient transaction: " + transactionTerminatedEvent.getClientTransaction() + "\n" +
                        "\tserver transaction: " + transactionTerminatedEvent.getServerTransaction() + "\n" +
                        "\tisServerTransaction: " + transactionTerminatedEvent.isServerTransaction());
             */
         }
      };
      signalingHandler.post(runnable);
   }

   public void processTimeout(final TimeoutEvent timeoutEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            Request request;
            Transaction transaction;
            if (timeoutEvent.isServerTransaction()) {
               request = timeoutEvent.getServerTransaction().getRequest();
               //transaction = timeoutEvent.getServerTransaction();
            }
            else {
               request = timeoutEvent.getClientTransaction().getRequest();
               //transaction = timeoutEvent.getClientTransaction();
            }

            RCLogger.i(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
            JainSipJob jainSipJob = jainSipJobManager.getByCallId(((CallIdHeader) request.getHeader("Call-ID")).getCallId());
            if (jainSipJob == null) {
               // transaction is not identified, just emit a log error; don't notify UI thread
               RCLogger.i(TAG, "processTimeout(): transaction not identified");
               return;
            }

            if (jainSipJob.type == JainSipJob.Type.TYPE_CALL) {
               jainSipJob.jainSipCall.processTimeout(jainSipJob, timeoutEvent);
            }
            else if (jainSipJob.type == JainSipJob.Type.TYPE_MESSAGE) {
               // TODO: call JainSipMessage.processTimeout()
            }
            else {
               // register, register refresh, reconfigure, etc
               jainSipJob.processFsm(jainSipJob.id, "timeout", null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
            }
         }
      };
      signalingHandler.post(runnable);
   }

   // ------ NotificationManagerListener events
   public void onConnectivityChange(JainSipNotificationManager.ConnectivityChange connectivityChange)
   {
      // No matter the connectivity change, cancel any pending scheduled registrations
      signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

      if (connectivityChange == JainSipNotificationManager.ConnectivityChange.OFFLINE) {
         try {
            jainSipClientUnbind();
            listener.onClientConnectivityEvent(Long.toString(System.currentTimeMillis()), RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone);
         }
         catch (JainSipException e) {
            // let's notify the App regardless of the exception since we no longer have connectivity
            e.printStackTrace();
            listener.onClientConnectivityEvent(Long.toString(System.currentTimeMillis()), RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone);
         }
      }
      else if (connectivityChange == JainSipNotificationManager.ConnectivityChange.OFFLINE_TO_WIFI ||
            connectivityChange == JainSipNotificationManager.ConnectivityChange.OFFLINE_TO_CELLULAR_DATA) {
         HashMap<String, Object> parameters = new HashMap<>(this.configuration);
         if (connectivityChange == JainSipNotificationManager.ConnectivityChange.OFFLINE_TO_WIFI) {
            parameters.put("connectivity-status", RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi);
         }
         else {
            parameters.put("connectivity-status", RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi);
         }
         jainSipJobManager.add(Long.toString(System.currentTimeMillis()), JainSipJob.Type.TYPE_START_NETWORKING, parameters);
      }
      else if (connectivityChange == JainSipNotificationManager.ConnectivityChange.CELLULAR_DATA_TO_WIFI ||
            connectivityChange == JainSipNotificationManager.ConnectivityChange.WIFI_TO_CELLULAR_DATA) {
         HashMap<String, Object> parameters = new HashMap<>(this.configuration);
         if (connectivityChange == JainSipNotificationManager.ConnectivityChange.CELLULAR_DATA_TO_WIFI) {
            parameters.put("connectivity-status", RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi);
         }
         else {
            parameters.put("connectivity-status", RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular);
         }
         jainSipJobManager.add(Long.toString(System.currentTimeMillis()), JainSipJob.Type.TYPE_RELOAD_NETWORKING, parameters);
      }
   }

   // -- Helpers
   // TODO: Improve this, try to not depend on such low level facilities
   public String getIPAddress(boolean useIPv4) throws SocketException
   {
      RCLogger.i(TAG, "getIPAddress()");
      if (jainSipNotificationManager.getNetworkStatus() == JainSipNotificationManager.NetworkStatus.NetworkStatusWiFi) {
         WifiManager wifiMgr = (WifiManager) androidContext.getSystemService(Context.WIFI_SERVICE);
         WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
         int ip = wifiInfo.getIpAddress();
         return Formatter.formatIpAddress(ip);
      }

      if (jainSipNotificationManager.getNetworkStatus() == JainSipNotificationManager.NetworkStatus.NetworkStatusCellular) {
         List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
         for (NetworkInterface intf : interfaces) {
            if (!intf.getName().matches("wlan.*")) {
               List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
               for (InetAddress addr : addrs) {
                  if (!addr.isLoopbackAddress()) {
                     String sAddr = addr.getHostAddress().toUpperCase();
                     boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                     if (useIPv4) {
                        if (isIPv4)
                           return sAddr;
                     }
                     else {
                        if (!isIPv4) {
                           int delim = sAddr.indexOf('%'); // drop ip6 port
                           // suffix
                           return delim < 0 ? sAddr : sAddr.substring(0,
                                 delim);
                        }
                     }
                  }
               }
            }
         }
      }
      return "";
   }

}
