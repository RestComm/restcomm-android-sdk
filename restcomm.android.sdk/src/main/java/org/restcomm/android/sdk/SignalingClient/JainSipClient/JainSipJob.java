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

import android.gov.nist.javax.sip.ResponseEventExt;
import android.javax.sip.Transaction;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCLogger;

import java.util.Arrays;
import java.util.HashMap;

/**
 * JainSipJob represents the context of a signaling action until it is either finished or an error occurs. All signaling actions MUST be started as jobs, because
 * jobs keep the context that binds requests & responses together. An important note is that not all jobs need an FSM, but whether an FSM is started or not
 * for a job depends on JainSipJob.hasFsm()
 *
 * A JainSipJob mainly holds the following information:
 * - jobId: that uniquely identifies a job within the signaling facilities. This typically is a unix timestamp (including milis)
 *   for outgoing requests provided in the request by the App, and the SIP Call-Id for incoming requests
 * - transaction: the SIP Transaction object associated with the job at this point in time. Remember that a job might consist of multiple transactions, hence
 *   this field might be updated during the job's lifetime
 * - parameters: a HashMap holding an arbitrary number of parameters applicable to the job. These can originate at the App, or they can be updated during the course
 *   of job execution
 */
class JainSipJob {
   public enum FsmStates {
      START_BIND_REGISTER,
      AUTH,
      NOTIFY,
      REGISTER,
      UNREGISTER,
      SHUTDOWN,
      AUTH_1,
      AUTH_2,
      UNBIND_BIND_REGISTER,
      BIND_REGISTER,
   }

   public enum FsmEvents {
      NONE,
      TIMEOUT,
      AUTH_REQUIRED,
      REGISTER_FAILURE,
      REGISTER_SUCCESS,
   }

   /**
    * Some signaling jobs (important: not all jobs have an FSM) are also associated with a state machine to be able to properly address invoking
    * the same functionalities in different job contexts without losing track and at the same time notifying the correct UI entities of job status.
    *
    * The idea here is that you initialize a job and based on its type (i.e. TYPE_OPEN, etc) we have a set of states (i.e. FsmStates) that this jobs typically needs
    * to go through. For example job of type TYPE_OPEN needs to start the signaling stack, bind to networking facilities, register, authorize and finally
    * notify the App. These steps are reflected in the states member variable and initialized when the job is constructed.
    *
    * Once the FSM is initialized we can then call JainSipFsm.process() that will do the actual FSM processing based on current state (i.e. states variable) and the event (i.e. FrmEvents)
    * passed by the caller (check JainSipFsm.process())
    *
    * It's important to avoid calling any methods within JainSipFsm.process() that inside them call JainSipFsm.process(), cause that would probably cause corruption
    * of the FSM state
    *
    * Some areas that need further improvement at some point:
    * - JainSipFsm.process() code has become spaghetti so we need to consider avoiding code duplication
    * - Right now we use JainSipJob.parameters to access and store context information, which can sometimes proves error prone (especially for reconfigure jobs where we
    *   stuff in it two separate sub parameters
    * - when some state needs to be skipped the logic is lousy
    */
   class JainSipFsm {
      FsmStates[] states;
      JainSipJob.Type type;
      JainSipClient jainSipClient;
      static final String TAG = "JainSipFsm";
      int index;

      JainSipFsm(JainSipJob.Type type, JainSipClient jainSipClient)
      {
         init(type, jainSipClient);
      }

      void init(JainSipJob.Type type, JainSipClient jainSipClient)
      {
         this.type = type;
         this.jainSipClient = jainSipClient;
         index = 0;

         if (type == JainSipJob.Type.TYPE_OPEN) {
            states = new FsmStates[] { FsmStates.START_BIND_REGISTER, FsmStates.AUTH, FsmStates.NOTIFY};
         }
         else if (type == Type.TYPE_REGISTER_REFRESH) {
            states = new FsmStates[] { FsmStates.REGISTER, FsmStates.AUTH, FsmStates.NOTIFY};
         }
         else if (type == Type.TYPE_CLOSE) {
            states = new FsmStates[] { FsmStates.UNREGISTER, FsmStates.AUTH, FsmStates.SHUTDOWN};
         }
         else if (type == Type.TYPE_RECONFIGURE) {
            states = new FsmStates[] { FsmStates.UNREGISTER, FsmStates.AUTH_1, FsmStates.REGISTER, FsmStates.AUTH_2, FsmStates.NOTIFY};
         }
         else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
            states = new FsmStates[] { FsmStates.UNREGISTER, FsmStates.AUTH_1, FsmStates.UNBIND_BIND_REGISTER, FsmStates.AUTH_2, FsmStates.NOTIFY};
         }
         else if (type == Type.TYPE_RELOAD_NETWORKING) {
            states = new FsmStates[] { FsmStates.UNBIND_BIND_REGISTER, FsmStates.AUTH, FsmStates.NOTIFY};
         }
         else if (type == Type.TYPE_START_NETWORKING) {
            states = new FsmStates[] { FsmStates.BIND_REGISTER, FsmStates.AUTH, FsmStates.NOTIFY};
         }
      }

      /**
       * This is either called to start the FSM or resume it
       * @param jobId Id for the current job
       * @param event The input event provided by the caller, to help FSM understand what kind of transition to do
       * @param arg Optional argument for the job
       * @param statusCode Optional status code we want to convey (to the UI thread typically)
       * @param statusText Optional the status text we want to convey (to the UI thread typically)
       */
      void process(String jobId, FsmEvents event, Object arg, RCClient.ErrorCodes statusCode, String statusText)
      {
         if (statusCode == null) {
            statusCode = RCClient.ErrorCodes.SUCCESS;
         }

         if (statusText == null) {
            statusText = RCClient.errorText(RCClient.ErrorCodes.SUCCESS);
         }

         if (states == null) {
            // if states is null, then it means that no FSM should be used at all
            return;
         }

         if (JainSipJob.this.jobId.equals(jobId)) {
            boolean loop;
            do {
               loop = false;
               if (index >= states.length) {
                  RCLogger.e(TAG, "process(): no more states to process");
               }
               if (type == Type.TYPE_OPEN) {
                  // no matter what state we are in if we get a timeout we need to just bail
                  if (event.equals(FsmEvents.TIMEOUT)) {
                     jainSipClient.listener.onClientOpenedReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                           RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                           RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                     jainSipJobManager.remove(jobId);
                     return;
                  }
                  if (states[index].equals(FsmStates.START_BIND_REGISTER)) {
                     try {
                        jainSipClient.jainSipClientStartStack();

                        if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                           jainSipClient.listener.onClientOpenedReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                                 RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                           jainSipJobManager.remove(jobId);
                           return;
                        }
                        jainSipClient.jainSipClientBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                        }
                        else {
                           // No Domain there we are done here
                           jainSipClient.listener.onClientOpenedReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientOpenedReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        jainSipJobManager.remove(jobId);
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientOpenedReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientOpenedReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                     }
                     if (event.equals(FsmEvents.REGISTER_SUCCESS)) {
                        jainSipClient.listener.onClientOpenedReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                     }

                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }
               else if (type == Type.TYPE_REGISTER_REFRESH) {
                  // no matter what state we are in if we get a timeout we need to just bail
                  if (event.equals(FsmEvents.TIMEOUT)) {
                     jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                           RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                           RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                     jainSipJobManager.remove(jobId);
                     return;
                  }

                  if (states[index].equals(FsmStates.REGISTER)) {
                     try {
                        transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        jainSipJobManager.remove(jobId);
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                     }
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }
               else if (type == Type.TYPE_CLOSE) {
                  if (states[index].equals(FsmStates.UNREGISTER)) {
                     try {
                        transaction = jainSipClient.jainSipClientUnregister(parameters);
                        final String finalId = jobId;

                        // Schedule a check to see if we managed to close the signaling facilities. If not then we need to force closing.
                        // The reason we need that is for example if the unregister times out, which in SIP takes 32 seconds. This means
                        // that after the App is left the SIP stack will remain alive for 32 secs, which means that if user tries to re-open
                        // the stack it will fail.
                        Runnable runnable = new Runnable() {
                           @Override
                           public void run()
                           {
                              if (jainSipClient.jainSipJobManager.get(finalId) != null) {
                                 RCLogger.e(TAG, "process(): Unregister is taking too long. Forcing signaling facilities to stop");
                                 // failed to unregister; we need to unbind & stop stack or at next initialization we will fail
                                 try {
                                    // don't forget to terminate the transaction, or else the timeout will fire and will be useless
                                    transaction.terminate();
                                    jainSipClient.jainSipClientUnbind();
                                    jainSipClient.jainSipClientStopStack();
                                 }
                                 catch (Exception e) {
                                    // at this point we can't recover
                                    throw new RuntimeException("Failed to release signaling facilities", e);
                                 }
                              }
                           }
                        };
                        jainSipClient.signalingHandler.postDelayed(runnable, JainSipClient.FORCE_CLOSE_INTERVAL);

                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientClosedEvent(jobId, e.errorCode, e.errorText);

                        // failed to unregister; we need to unbind & stop stack or at next initialization we will fail
                        try {
                           jainSipClient.jainSipClientUnbind();
                           jainSipClient.jainSipClientStopStack();
                        }
                        catch (JainSipException inner) {
                           // at this point we can't recover
                           throw new RuntimeException("Failed to unbind signaling facilities", e);
                        }
                        jainSipJobManager.remove(jobId);
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           // failed to unregister; we need to unbind & stop stack or at next initialization we will fail
                           try {
                              jainSipClient.jainSipClientUnbind();
                              jainSipClient.jainSipClientStopStack();
                           }
                           catch (JainSipException inner) {
                              // at this point we can't recover
                              throw new RuntimeException("Failed to unbind signaling facilities", e);
                           }
                           jainSipClient.listener.onClientClosedEvent(jobId, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.SHUTDOWN)) {
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        try {
                           jainSipClient.jainSipClientUnbind();
                           jainSipClient.jainSipClientStopStack();
                           jainSipClient.listener.onClientClosedEvent(jobId, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                        }
                        catch (JainSipException e) {
                           // at this point we can't recover
                           throw new RuntimeException("Failed to unbind signaling facilities", e);
                           //jainSipClient.listener.onClientClosedEvent(jobId, e.errorCode, e.errorText);
                        }
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE) {
                  if (event.equals(FsmEvents.TIMEOUT)) {
                     // Important: if time out occurred on unregister we need to ignore and jump to register step. Take for example a case
                     // where the user does such a setup that registration fails and then they change again to a valid settings. In this case
                     // the first registration will timeout, but we don't care, we still need to continue with the register step
                     if (states[index].equals(FsmStates.AUTH_1)) {
                        // timeout occured in unregister
                        RCLogger.w(TAG, "process(): unregister timed out in reconfigure, ignoring unregister step");
                        index += 1;
                        event = FsmEvents.REGISTER_FAILURE;
                     }
                     else {
                        jainSipClient.listener.onClientReconfigureReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                              RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                        jainSipJobManager.remove(jobId);
                        return;
                     }
                  }

                  if (states[index].equals(FsmStates.UNREGISTER)) {
                     if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                        jainSipJobManager.remove(jobId);
                        return;
                     }
                     try {
                        if (((HashMap<String, Object>) parameters.get("old-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                              !((HashMap<String, Object>) parameters.get("old-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientUnregister((HashMap<String, Object>) parameters.get("old-parameters"));
                        }
                        else {
                           // No Domain, need to loop through to next step
                           loop = true;
                           // TODO: need to improve
                           // we need this to properly handle the 'register' step below
                           event = FsmEvents.REGISTER_SUCCESS;
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();

                        // we failed to unregister, but this is a valid use case (like when user provides wrong domain). We need
                        // to avoid authentication and jump to registration with new settings
                        // TODO: this is a pretty messy way to convey that we want to jump 1 step
                        index += 1;
                        loop = true;
                        event = FsmEvents.REGISTER_FAILURE;
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH_1)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.REGISTER)) {
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                        RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        try {
                           if (((HashMap<String, Object>) parameters.get("new-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                                 !((HashMap<String, Object>) parameters.get("new-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                              // Domain has been provided do the registration
                              transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"));
                           }
                           else {
                              // No domain, need to loop through to next step
                              loop = true;
                           }
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH_2)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
                  if (states[index].equals(FsmStates.UNREGISTER)) {
                     if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                        jainSipJobManager.remove(jobId);
                        return;
                     }
                     try {
                        if (((HashMap<String, Object>) parameters.get("old-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                              !((HashMap<String, Object>) parameters.get("old-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientUnregister((HashMap<String, Object>) parameters.get("old-parameters"));
                        }
                        else {
                           // No domain, need to loop through to next step
                           // TODO: this is a pretty messy way to convey that we want to jump 1 step
                           index += 1;
                           loop = true;
                           event = FsmEvents.REGISTER_SUCCESS;
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();

                        // we failed to unregister, but this is a valid use case (like when user provides wrong domain). We need
                        // to avoid authentication and jump to registration with new settings
                        // TODO: this is a pretty messy way to convey that we want to jump 1 step
                        index += 1;
                        loop = true;
                        event = FsmEvents.REGISTER_FAILURE;
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH_1)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.UNBIND_BIND_REGISTER)) {
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                        RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        try {
                           jainSipClient.jainSipClientUnbind();

                           jainSipClient.jainSipClientBind((HashMap<String, Object>) parameters.get("new-parameters"));

                           if (((HashMap<String, Object>) parameters.get("new-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                                 !((HashMap<String, Object>) parameters.get("new-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                              // Domain has been provided do the registration
                              transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"));
                           }
                           else {
                              // No domain, need to loop through to next step
                              // TODO: this is a pretty messy way to convey that we want to jump 1 step
                              index += 1;
                              loop = true;
                              event = FsmEvents.REGISTER_FAILURE;
                           }
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH_2)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_SUCCESS) || event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientReconfigureReply(jobId, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                        jainSipJobManager.remove(jobId);
                     }

                  }
               }
               else if (type == Type.TYPE_RELOAD_NETWORKING) {
                  if (states[index].equals(FsmStates.UNBIND_BIND_REGISTER)) {
                     // no need for connectivity check here, we know there is connectivity
                     try {
                        jainSipClient.jainSipClientUnbind();

                        jainSipClient.jainSipClientBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                        }
                        else {
                           // No domain, need to loop through to next step
                           RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                           jainSipClient.listener.onClientConnectivityEvent(jobId, connectivityStatus);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_SUCCESS)) {
                        RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                        jainSipClient.listener.onClientConnectivityEvent(jobId, connectivityStatus);
                        jainSipJobManager.remove(jobId);
                     }
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }
               else if (type == Type.TYPE_START_NETWORKING) {
                  if (states[index].equals(FsmStates.BIND_REGISTER)) {
                     // no need for connectivity check here, we know there is connectivity
                     try {
                        jainSipClient.jainSipClientBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                        }
                        else {
                           // No Domain there we are done here
                           RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                           jainSipClient.listener.onClientConnectivityEvent(jobId, connectivityStatus);
                           jainSipJobManager.remove(jobId);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals(FsmStates.AUTH)) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals(FsmEvents.AUTH_REQUIRED)) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals(FsmStates.NOTIFY)) {
                     if (event.equals(FsmEvents.REGISTER_SUCCESS)) {
                        RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                        jainSipClient.listener.onClientConnectivityEvent(jobId, connectivityStatus);
                        jainSipJobManager.remove(jobId);
                     }
                     if (event.equals(FsmEvents.REGISTER_FAILURE)) {
                        jainSipClient.listener.onClientErrorReply(jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                        jainSipJobManager.remove(jobId);
                     }
                  }
               }

               index++;
            } while (loop);
         }
      }

      @Override
      public String toString()
      {
         StringBuilder result = new StringBuilder();
         //String NEW_LINE = System.getProperty("line.separator");

         result.append(this.getClass().getName() + " Object { ");
         result.append("Type: " + type + ", ");
         result.append("Index: " + index + " ");
         result.append("}");

         return result.toString();
      }
   }

   public enum Type {
      TYPE_OPEN,
      TYPE_REGISTER_REFRESH,
      TYPE_CLOSE,
      TYPE_RECONFIGURE,
      TYPE_RECONFIGURE_RELOAD_NETWORKING,
      TYPE_RELOAD_NETWORKING,
      TYPE_START_NETWORKING,
      TYPE_CALL,
      TYPE_MESSAGE,
   }

   // jobId is a unique identifier for a Job. It is App provided for outgoing requests (typically Unix time with miliseconds, as a string)
   // and SIP Call-Id for incoming requests. Notice that for outgoing requests the App provided jobId is also used as SIP Call-ID, to make
   // troubleshooting easier
   public String jobId;
   public Type type;
   // current JAIN sip transaction this job is currently executing. Remember that usually one Job is made up of multiple transactions occuring one after another
   public Transaction transaction;
   public HashMap<String, Object> parameters;
   public JainSipCall jainSipCall;
   public int authenticationAttempts;
   public static int MAX_AUTH_ATTEMPTS = 3;
   JainSipClient jainSipClient;
   JainSipJobManager jainSipJobManager;
   JainSipFsm jainSipFsm;
   static final String TAG = "JainSipJob";

   JainSipJob(JainSipJobManager jainSipJobManager, JainSipClient jainSipClient, String jobId, Type type, Transaction transaction, HashMap<String, Object> parameters,
              JainSipCall jainSipCall)
   {
      this.jobId = jobId;
      this.type = type;
      this.transaction = transaction;
      this.parameters = parameters;
      this.authenticationAttempts = 0;
      this.jainSipClient = jainSipClient;
      this.jainSipJobManager = jainSipJobManager;
      this.jainSipFsm = new JainSipFsm(type, jainSipClient);
      this.jainSipCall = jainSipCall;
   }

   void startFsm()
   {
      jainSipFsm.process(jobId, FsmEvents.NONE, null, null, null);
   }

   // Not all jobs have FSM. Simple jobs don't need one. Check if current job has an FSM
   boolean hasFsm()
   {
      if (type == JainSipJob.Type.TYPE_OPEN ||
            type == Type.TYPE_REGISTER_REFRESH ||
            type == Type.TYPE_CLOSE ||
            type == Type.TYPE_RECONFIGURE ||
            type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING ||
            type == Type.TYPE_RELOAD_NETWORKING ||
            type == Type.TYPE_START_NETWORKING) {
         return true;
      }
      return false;
   }

   void processFsm(String jobId, FsmEvents event, Object arg, RCClient.ErrorCodes statusCode, String statusText)
   {
      if (hasFsm()) {
         jainSipFsm.process(jobId, event, arg, statusCode, statusText);
      }
   }

   void updateTransaction(Transaction transaction)
   {
      this.transaction = transaction;
   }

   // Should we retry authentication if previous failed? We retry a max of MAX_AUTH_ATTEMPTS
   boolean shouldRetry()
   {
      if (authenticationAttempts < JainSipJob.MAX_AUTH_ATTEMPTS - 1) {
         return true;
      }
      else {
         return false;
      }
   }

   void increaseAuthAttempts()
   {
      authenticationAttempts += 1;
   }

   @Override
   public String toString()
   {
      StringBuilder result = new StringBuilder();
      String NEW_LINE = System.getProperty("line.separator");

      result.append(this.getClass().getName() + " Object {" + NEW_LINE);
      result.append(" Job Id: " + jobId + NEW_LINE);
      result.append(" Type: " + type + NEW_LINE);
      result.append(" Transaction: " + transaction + NEW_LINE);
      result.append(" Parameters: " + parameters + NEW_LINE);
      result.append(" Fsm: " + jainSipFsm + NEW_LINE);
      result.append("}");

      return result.toString();
   }

}
