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

//import org.apache.http.conn.util.InetAddressUtils;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCLogger;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * JainSipClient hides the JAIN SIP complexity and offers an easy to use API to implement SIP signaling. JainSipClient is typically used by Signaling Handler
 * that encapsulates the separate, signaling thread. JainSipClient creates SIP requests via methods like open(), close(), call(), etc and replies or events
 * are sent back to the 'user' via listener callbacks.
 *
 * Although all signaling functionality is accessed via the JainSipClient entity, internally we have introduced JainSipCall that handles call related functionalities
 * to better organize things. This means that call related APIs like call(), accept(), disconnect(), etc are forwarded to JainSipCall. In the reverse direction when
 * a response/event comes in from JAIN SIP it is firstly received by JainSipClient (since its the SipListener for JAIN SIP), but then if it identifies that it is call
 * related, it is once again forwarded to JainSipCall.
 *
 * With each of the following signaling actions: open(), reconfigure(), close(), sendMessage() and call() a JainSipJob is created that carries around its context until
 * it is either finished or an error occurs. The rest of the signaling actions like accept(), sendDtmf(), don't create new jobs. Instead they act on existing ones.
 * Main pieces of the job are the current transaction. Remember that a single job can consist of more than one SIP transaction. For example the TYPE_RECONFIGURE job consists
 * of an unregister transaction, followed by an authentication transaction, followed by register transaction, followed by an authentication transaction. Some jobs are also
 * associated with a JainSipFsm object that implements a simple state machine to be able to properly address invoking the same functionalities in different job contexts
 * without losing track and at the same time notifying the correct UI entities of job status. Right now JainSipFsm is only used in complex jobs that need to batch multiple
 * transactions together, like: TYPE_OPEN, TYPE_REGISTER_REFRESH, TYPE_CLOSE, TYPE_RECONFIGURE, TYPE_RECONFIGURE_RELOAD_NETWORKING, TYPE_RELOAD_NETWORKING, TYPE_START_NETWORKING,
 * TYPE_CALL. Jobs are managed by JainSipJobManager
 *
 * JAIN SIP requests and responses are typically built by JainSipMessageBuilder
 */
public class JainSipClient implements SipListener, JainSipNotificationManager.NotificationManagerListener {

   // Interface the JainSipClient listener needs to implement, to get events from us
   public interface JainSipClientListener {
      void onClientOpenedReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // on successful/unsuccessful register, onPrivateClientConnectorOpenedEvent

      void onClientErrorReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // mostly on unsuccessful register, onPrivateClientConnectorOpenErrorEvent

      void onClientClosedEvent(String jobId, RCClient.ErrorCodes status, String text);  // on successful unregister, onPrivateClientConnectorClosedEvent

      void onClientReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);  // on successful register, onPrivateClientConnectorOpenedEvent

      void onClientConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus);

      void onClientMessageArrivedEvent(String jobId, String peer, String messageText);

      void onClientMessageReply(String jobId, RCClient.ErrorCodes status, String text);

      // Event to convey trying to Register, so that UI can convey that to user
      void onClientRegisteringEvent(String jobId);
   }

   public JainSipClientListener listener;
   JainSipMessageBuilder jainSipMessageBuilder;
   JainSipJobManager jainSipJobManager;
   JainSipNotificationManager jainSipNotificationManager;
   private Context androidContext;
   HashMap<String, Object> configuration;
   // any client context that is not configuration related, like the rport
   HashMap<String, Object> jainSipClientContext;
   //boolean clientConnected = false;
   private static boolean clientOpened = false;
   private static final String TAG = "JainSipClient";
   // android handler token to identify registration refresh posts
   private final int REGISTER_REFRESH_HANDLER_TOKEN = 1;
   Handler signalingHandler;
   private final int DEFAULT_REGISTER_EXPIRY_PERIOD = 60;
   private final int DEFAULT_LOCAL_SIP_PORT = 5090;
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
   public void open(String jobId, Context androidContext, HashMap<String, Object> configuration, JainSipClientListener listener)
   {
      RCLogger.i(TAG, "open(): " + configuration.toString());

      if (JainSipClient.clientOpened) {
         listener.onClientOpenedReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
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
      JainSipSecurityHelper.setProperties(properties, securityParameters.get("keystore-path"), securityParameters.get("keystore-password"),
            (Boolean)configuration.get(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION));

      if (configuration.containsKey(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED) &&
            (Boolean)configuration.get(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED)) {
         // You need 16 for logging traces. 32 for debug + traces.
         // Your code will limp at 32 but it is best for debugging.
         properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
         File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
         properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", downloadPath.getAbsolutePath() + "/debug-jain.log");
         properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", downloadPath.getAbsolutePath() + "/server-jain.log");
      }

      try {
         jainSipStack = jainSipFactory.createSipStack(properties);
         jainSipMessageBuilder.normalizeDomain(configuration);

         jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_OPEN, configuration);
      }
      catch (SipException e) {
         throw new RuntimeException("Failed to bootstrap the signaling stack", e);
         /*
         listener.onClientOpenedReply(jobId, jainSipNotificationManager.getConnectivityStatus(), RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
         */
      }
   }

   public void close(final String jobId)
   {
      RCLogger.i(TAG, "close(): " + jobId);

      if (JainSipClient.clientOpened) {
         // cancel any pending scheduled registrations
         //signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

         // TODO: close any active calls
         //

         jainSipNotificationManager.close();
         jainSipJobManager.removeAll();

         if (configuration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !configuration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
            // non registrar-less, we need to unregister and when done shutdown
            jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_CLOSE, this.configuration);
         }
         else {
            // registrar-less, just shutdown and notify UI thread
            try {
               jainSipClientUnbind();
               jainSipClientStopStack();

               listener.onClientClosedEvent(jobId, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
            }
            catch (JainSipException e) {
               e.printStackTrace();
               listener.onClientClosedEvent(jobId, e.errorCode, e.errorText);
            }
         }
      }
      else {
         throw new RuntimeException("JainSipClient already closed, bailing");
         /*
         RCLogger.w(TAG, "close(): JAIN SIP client already closed, bailing");
         listener.onClientClosedEvent(jobId, RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
               */
      }
   }

   public void reconfigure(String jobId, HashMap<String, Object> parameters, JainSipClientListener listener)
   {
      RCLogger.i(TAG, "reconfigure(): " + parameters.toString());

      // normalize before checking which parameters changed
      jainSipMessageBuilder.normalizeDomain(parameters);

      // check which parameters actually changed by comparing this.configuration with parameters
      HashMap<String, Object> modifiedParameters = JainSipConfiguration.modifiedParameters(this.configuration, parameters);

      if (modifiedParameters.size() == 0) {
         listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipNotificationManager.getNetworkStatus()),
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
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_URL)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL));
      }
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME));
      }
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD)) {
         this.configuration.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD));
      }

      HashMap<String, Object> multipleParameters = new HashMap<String, Object>();
      multipleParameters.put("old-parameters", oldParameters);
      multipleParameters.put("new-parameters", configuration);
      if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED)) {
         // if signaling has changed we need to a. unregister from old using old creds, b. unbind, c. bind, d. register with new using new creds
         // start FSM and pass it both previous and current parameters
         jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_RECONFIGURE_RELOAD_NETWORKING, multipleParameters);
      }
      else if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_USERNAME) ||
            modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_PASSWORD) ||
            modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN)) {
         // if username, password or domain has changed we need to a. unregister from old using old creds and b. register with new using new creds
         // start FSM and pass it both previous and current parameters
         jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_RECONFIGURE, multipleParameters);
      }
      else {
         listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipNotificationManager.getNetworkStatus()),
               RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
      }
   }

   // ------ Call-related methods
   public void call(String jobId, HashMap<String, Object> parameters, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "call(): jobId: " + jobId + ", username: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      try {
         jainSipMessageBuilder.normalizePeer(parameters, configuration);

         JainSipCall jainSipCall = new JainSipCall(this, listener);
         jainSipCall.open(jobId, parameters);
      }
      catch (JainSipException e) {
         listener.onCallErrorEvent(jobId, e.errorCode, e.errorText);
      }
   }

   public void accept(String jobId, HashMap<String, Object> parameters, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "accept(): jobId: " + jobId + ", parameters: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      if (jainSipJob == null) {
         throw new RuntimeException("Error accepting a call that doesn't exist in job manager, jobId: " + jobId);
      }
      jainSipJob.jainSipCall.accept(jainSipJob, parameters);
   }

   public void disconnect(String jobId, String reason, JainSipCall.JainSipCallListener listener)
   {
      RCLogger.i(TAG, "disconnect(): jobId: " + jobId + ", reason: " + reason);

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      // There are cases where a call legitimately fails before a job is created (an example is when invalid SIP URI is used, where RCConnection will return error
      // to the User but the user still needs to disconnect() manually), hence this will return null and
      // in those cases we don't need to do anything
      if (jainSipJob != null) {
         jainSipJob.jainSipCall.disconnect(jainSipJob, reason);
      }
      else {
         // let's emit a warning just in case we hit an actual error case with this
         RCLogger.w(TAG, "disconnect(): job doesn't exist for the call; this can be a valid scenario");
      }
   }

   public void sendDigits(String jobId, String digits)
   {
      RCLogger.i(TAG, "sendDigits(): jobId: " + jobId + ", digits: " + digits);

      JainSipJob jainSipJob = jainSipJobManager.get(jobId);
      jainSipJob.jainSipCall.sendDigits(jainSipJob, digits);
   }

   // ------ Message-related methods
   public void sendMessage(String jobId, HashMap<String, Object> parameters)
   {
      RCLogger.i(TAG, "sendMessage(): jobId: " + jobId + ", parameters: " + parameters.toString());

      if (!jainSipNotificationManager.haveConnectivity()) {
         listener.onClientMessageReply(jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }

      try {
         jainSipMessageBuilder.normalizePeer(parameters, configuration);

         Transaction transaction = jainSipClientSendMessage(parameters);
         jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_MESSAGE, transaction, parameters, null);
      }
      catch (JainSipException e) {
         listener.onClientMessageReply(jobId, e.errorCode, e.errorText);
      }
   }

   // ------ Internal APIs
   // Setup JAIN networking facilities
   public void jainSipClientBind(HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "bind()");
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
      RCLogger.v(TAG, "unbind()");
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
      RCLogger.v(TAG, "jainSipRegister(), jobs status: " + jainSipJobManager.getPrintableJobs());

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
         RCLogger.i(TAG, "Sending SIP request: \n" + registerRequest.toString());

         // only notify on registering on specific types of jobs, otherwise we would swamp the App with notifications
         if (jainSipJob.type == JainSipJob.Type.TYPE_RECONFIGURE || jainSipJob.type == JainSipJob.Type.TYPE_RECONFIGURE_RELOAD_NETWORKING ||
               jainSipJob.type == JainSipJob.Type.TYPE_START_NETWORKING || jainSipJob.type == JainSipJob.Type.TYPE_RELOAD_NETWORKING) {
            listener.onClientRegisteringEvent(jainSipJob.jobId);
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
         RCLogger.i(TAG, "Sending SIP request: \n" + registerRequest.toString());

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
         Request request = jainSipMessageBuilder.buildMessageRequest((String) parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER),
               (String) parameters.get("text-message"), jainSipListeningPoint, configuration);
         RCLogger.i(TAG, "Sending SIP request: \n" + request.toString());

         ClientTransaction transaction = this.jainSipProvider.getNewClientTransaction(request);
         transaction.sendRequest();
         return transaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_MESSAGE_COULD_NOT_CONNECT,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_COULD_NOT_CONNECT), e);
      }
   }

   // Notice that this is used both for registrations and calls
   public void jainSipAuthenticate(JainSipJob jainSipJob, HashMap<String, Object> parameters, ResponseEventExt responseEventExt) throws JainSipException
   {
      try {
         String password = (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_PASSWORD);
         if (password == null) {
            password = "";
         }

         AuthenticationHelper authenticationHelper = ((SipStackExt) jainSipStack).getAuthenticationHelper(
               new JainSipAccountManagerImpl((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
                     responseEventExt.getRemoteIpAddress(), password), jainSipMessageBuilder.getHeaderFactory());

         // we 're subtracting one since the first attempt has already taken place
         // (that way we are enforcing MAX_AUTH_ATTEMPTS at most)
         if (jainSipJob.shouldRetry()) {
            ClientTransaction authenticationTransaction = authenticationHelper.handleChallenge(responseEventExt.getResponse(),
                  (ClientTransaction) jainSipJob.transaction, jainSipProvider, 5, true);

            // update previous transaction with authenticationTransaction (remember that previous ended with 407 final response)
            jainSipJob.updateTransaction(authenticationTransaction);
            RCLogger.i(TAG, "Sending SIP request: \n" + authenticationTransaction.getRequest().toString());
            authenticationTransaction.sendRequest();
            jainSipJob.increaseAuthAttempts();
         }
         else {
            // actually this should not happen. Restcomm should return forbidden if the credentials are wrong and not challenge again
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
            RCLogger.i(TAG, "Received SIP request: \n" + request.toString());
            String callId = ((CallIdHeader)request.getHeader("Call-ID")).getCallId();

            // create a new jobId for the new job
            String jobId = Long.toString(System.currentTimeMillis());
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            String method = request.getMethod();

            if (method.equals(Request.INVITE)) {
               // New INVITE, need to create new job
               JainSipCall jainSipCall = new JainSipCall(JainSipClient.this, (JainSipCall.JainSipCallListener)listener);
               // Remember, this is new dialog and hence serverTransaction is null
               JainSipJob jainSipJob = jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_CALL, null, null, jainSipCall);

               jainSipCall.processRequest(jainSipJob, requestEvent);
            }
            else if (method.equals(Request.MESSAGE)) {
               try {
                  if (serverTransaction == null) {
                     // no server transaction yet
                     serverTransaction = jainSipProvider.getNewServerTransaction(request);
                  }

                  Response response = jainSipMessageBuilder.buildResponse(Response.OK, request);
                  RCLogger.i(TAG, "Sending SIP response: \n" + response.toString());
                  serverTransaction.sendResponse(response);
                  String messageText = ((SIPMessage)request).getMessageContent();
                  listener.onClientMessageArrivedEvent(jobId, ((SIPMessage)request).getFrom().getAddress().toString(), messageText);
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
            RCLogger.i(TAG, "Received SIP response: \n" + response.toString());

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
                  jainSipJob.processFsm(jainSipJob.jobId, JainSipJob.FsmEvents.AUTH_REQUIRED, responseEventExt, null, null);
               }
               else if (response.getStatusCode() == Response.FORBIDDEN) {
                  jainSipJob.processFsm(jainSipJob.jobId, JainSipJob.FsmEvents.REGISTER_FAILURE, null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_AUTHENTICATION_FORBIDDEN));
               }
               else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
                  jainSipJob.processFsm(jainSipJob.jobId, JainSipJob.FsmEvents.REGISTER_FAILURE, null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_SERVICE_UNAVAILABLE));
               }
               else if (response.getStatusCode() == Response.OK) {
                  // register succeeded
                  //ViaHeader viaHeader = (ViaHeader) response.getHeader(ViaHeader.NAME);
                  updateViaReceivedAndRport((ViaHeader)response.getHeader(ViaHeader.NAME));

                  jainSipJob.processFsm(jainSipJob.jobId, JainSipJob.FsmEvents.REGISTER_SUCCESS, null, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
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
                     listener.onClientMessageReply(jainSipJob.jobId, e.errorCode, e.errorText);
                  }
               }
               else if (response.getStatusCode() == Response.OK) {
                  listener.onClientMessageReply(jainSipJob.jobId, RCClient.ErrorCodes.SUCCESS,
                        RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
               }
               else if (response.getStatusCode() == Response.FORBIDDEN) {
                  listener.onClientMessageReply(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_MESSAGE_AUTHENTICATION_FORBIDDEN,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_AUTHENTICATION_FORBIDDEN));
               }
               else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
                  listener.onClientMessageReply(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_MESSAGE_SERVICE_UNAVAILABLE,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_SERVICE_UNAVAILABLE));
               }
            }
         }
      };
      signalingHandler.post(runnable);
   }

   public void processDialogTerminated(final DialogTerminatedEvent dialogTerminatedEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
                RCLogger.v(TAG, "SipManager.processDialogTerminated: " + dialogTerminatedEvent.toString() + "\n" +
                        "\tdialog: " + dialogTerminatedEvent.getDialog().toString());
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
            RCLogger.e(TAG, "SipManager.processIOException: " + exceptionEvent.toString() + "\n" +
                  "\thost: " + exceptionEvent.getHost() + "\n" +
                  "\tport: " + exceptionEvent.getPort());
         }
      };
      signalingHandler.post(runnable);
   }

   public void processTransactionTerminated(final TransactionTerminatedEvent transactionTerminatedEvent)
   {
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
                RCLogger.v(TAG, "processTransactionTerminated: " + transactionTerminatedEvent.toString() + "\n" +
                        "\tclient transaction: " + transactionTerminatedEvent.getClientTransaction() + "\n" +
                        "\tserver transaction: " + transactionTerminatedEvent.getServerTransaction() + "\n" +
                        "\tisServerTransaction: " + transactionTerminatedEvent.isServerTransaction());
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

            RCLogger.w(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
            JainSipJob jainSipJob = jainSipJobManager.getByCallId(((CallIdHeader) request.getHeader("Call-ID")).getCallId());
            if (jainSipJob == null) {
               // transaction is not identified, just emit a log error; don't notify UI thread
               RCLogger.e(TAG, "processTimeout(): transaction not identified");
               return;
            }

            if (jainSipJob.type == JainSipJob.Type.TYPE_CALL) {
               jainSipJob.jainSipCall.processTimeout(jainSipJob, timeoutEvent);
            }
            else if (jainSipJob.type == JainSipJob.Type.TYPE_MESSAGE) {
               listener.onClientMessageReply(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_MESSAGE_TIMEOUT,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_TIMEOUT));
               jainSipJobManager.remove(jainSipJob.jobId);
            }
            else {
               // register, register refresh, reconfigure, etc
               jainSipJob.processFsm(jainSipJob.jobId, JainSipJob.FsmEvents.TIMEOUT, null, RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
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
      RCLogger.v(TAG, "getIPAddress()");
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
                     boolean isIPv4 = addr instanceof Inet4Address;  //InetAddressUtils.isIPv4Address(sAddr);
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

   private void updateViaReceivedAndRport(ViaHeader viaHeader)
   {
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
   }


}
