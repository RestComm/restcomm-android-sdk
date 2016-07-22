package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import android.gov.nist.javax.sip.ResponseEventExt;
import android.javax.sip.Transaction;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

import java.util.Arrays;
import java.util.HashMap;

class JainSipJob {
   // Each transaction is associated with an FSM
   class JainSipFsm {
      String[] states;
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
         //this.id = id;
         this.type = type;
         this.jainSipClient = jainSipClient;
         //this.parameters = parameters;
         index = 0;

         if (type == JainSipJob.Type.TYPE_OPEN) {
            states = new String[]{"start-bind-register", "auth", "notify"};
         }
         else if (type == Type.TYPE_REGISTER_REFRESH) {
            states = new String[]{"register", "auth", "notify"};
         }
         else if (type == Type.TYPE_CLOSE) {
            states = new String[]{"unregister", "auth", "shutdown"};
         }
         else if (type == Type.TYPE_RECONFIGURE) {
            states = new String[]{"unregister", "auth-1", "register", "auth-2", "notify"};
         }
         else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
            states = new String[]{"unregister", "auth-1", "unbind-bind-register", "auth-2", "notify"};
         }
         else if (type == Type.TYPE_RELOAD_NETWORKING) {
            states = new String[]{"unbind-bind-register", "auth", "notify"};
         }
         else if (type == Type.TYPE_START_NETWORKING) {
            states = new String[]{"bind-register", "auth", "notify"};
         }
         else if (type == Type.TYPE_CALL) {
            states = new String[]{"invite", "auth", "notify"};
         }
      }

        /*
        void init(String id, JainSipJob.Type type, JainSipClient jainSipClient) {
            this.init(id, type, jainSipClient);
        }
        */

      /* process FSM
       * id: the job id for the current job
       * event: the input event provided by the caller, to help FSM understand what kind of transition to do
       * arg: (optional) argument for the job
       * statusCode: (optional) the status code we want to convey to the UI thread typically
       * statusText: (optional) the status text we want to convey to the UI thread typically
       */
      void process(String id, String event, Object arg, RCClient.ErrorCodes statusCode, String statusText)
      {
         if (states == null) {
            // if states is null, then it means that no FSM should be used at all
            return;
         }

         if (JainSipJob.this.id.equals(id)) {
            boolean loop;
            do {
               loop = false;
               if (index >= states.length) {
                  RCLogger.e(TAG, "process(): no more states to process");
               }
               if (type == Type.TYPE_OPEN) {
                  // no matter what state we are in if we get a timeout we need to just bail
                  if (event.equals("timeout")) {
                     jainSipClient.listener.onClientOpenedEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                           RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                           RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                     jainSipJobManager.remove(id);
                     return;
                  }
                  if (states[index].equals("start-bind-register")) {
                     try {
                        jainSipClient.jainSipClientStartStack();

                        if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                           jainSipClient.listener.onClientOpenedEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                                 RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                           jainSipJobManager.remove(id);
                           return;
                        }
                        jainSipClient.jainSipClientBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                        }
                        else {
                           // No Domain there we are done here
                           jainSipClient.listener.onClientOpenedEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientOpenedEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        jainSipJobManager.remove(id);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientOpenedEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-failure")) {
                        jainSipClient.listener.onClientOpenedEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                     }
                     if (event.equals("register-success")) {
                        jainSipClient.listener.onClientOpenedEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                     }

                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_REGISTER_REFRESH) {
                  // no matter what state we are in if we get a timeout we need to just bail
                  if (event.equals("timeout")) {
                     jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                           RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                           RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                     jainSipJobManager.remove(id);
                     return;
                  }

                  if (states[index].equals("register")) {
                     try {
                        transaction = jainSipClient.jainSipClientRegister(JainSipJob.this, parameters);
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        jainSipJobManager.remove(id);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-failure")) {
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                     }
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_CLOSE) {
                  if (states[index].equals("unregister")) {
                     try {
                        transaction = jainSipClient.jainSipClientUnregister(parameters);
                        final String finalId = id;

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
                        jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);

                        // failed to unregister; we need to unbind & stop stack or at next initialization we will fail
                        try {
                           jainSipClient.jainSipClientUnbind();
                           jainSipClient.jainSipClientStopStack();
                        }
                        catch (JainSipException inner) {
                           // at this point we can't recover
                           throw new RuntimeException("Failed to unbind signaling facilities", e);
                        }
                        jainSipJobManager.remove(id);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
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
                           jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("shutdown")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        try {
                           jainSipClient.jainSipClientUnbind();
                           jainSipClient.jainSipClientStopStack();
                           jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                        }
                        catch (JainSipException e) {
                           // at this point we can't recover
                           throw new RuntimeException("Failed to unbind signaling facilities", e);
                           //jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                        }
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE) {
                  if (event.equals("timeout")) {
                     // Important: if time out occurred on unregister we need to ignore and jump to register step. Take for example a case
                     // where the user does such a setup that registration fails and then they change again to a valid settings. In this case
                     // the first registration will timeout, but we don't care, we still need to continue with the register step
                     if (states[index].equals("auth-1")) {
                        // timeout occured in unregister
                        RCLogger.w(TAG, "process(): unregister timed out in reconfigure, ignoring unregister step");
                        index += 1;
                        event = "register-failure";
                     }
                     else {
                        jainSipClient.listener.onClientReconfigureEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                              RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_TIMEOUT));
                        jainSipJobManager.remove(id);
                        return;
                     }
                  }

                  if (states[index].equals("unregister")) {
                     if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                        jainSipJobManager.remove(id);
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
                           event = "register-success";
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();

                        // we failed to unregister, but this is a valid use case (like when user provides wrong domain). We need
                        // to avoid authentication and jump to registration with new settings
                        // TODO: this is a pretty messy way to convey that we want to jump 1 step
                        index += 1;
                        loop = true;
                        event = "register-failure";
                     }
                  }
                  else if (states[index].equals("auth-1")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("register")) {
                     if (event.equals("register-failure")) {
                        // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                        RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                     if (event.equals("register-success") || event.equals("register-failure")) {
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
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                  }
                  else if (states[index].equals("auth-2")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
                  if (states[index].equals("unregister")) {
                     if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
                        jainSipJobManager.remove(id);
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
                           loop = true;
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();

                        // we failed to unregister, but this is a valid use case (like when user provides wrong domain). We need
                        // to avoid authentication and jump to registration with new settings
                        // TODO: this is a pretty messy way to convey that we want to jump 1 step
                        index += 1;
                        loop = true;
                        event = "register-failure";
                     }
                  }
                  else if (states[index].equals("auth-1")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("unbind-bind-register")) {
                     if (event.equals("register-failure")) {
                        // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                        RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                     if (event.equals("register-success") || event.equals("register-failure")) {
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
                              loop = true;
                           }
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                  }
                  else if (states[index].equals("auth-2")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                                 e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipClient.listener.onClientReconfigureEvent(id, JainSipNotificationManager.networkStatus2ConnectivityStatus(jainSipClient.jainSipNotificationManager.getNetworkStatus()),
                              statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RELOAD_NETWORKING) {
                  if (states[index].equals("unbind-bind-register")) {
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
                           jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success")) {
                        RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                        jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                        jainSipJobManager.remove(id);
                     }
                     if (event.equals("register-failure")) {
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_START_NETWORKING) {
                  if (states[index].equals("bind-register")) {
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
                           jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        e.printStackTrace();
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           e.printStackTrace();
                           jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success")) {
                        RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                        jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                        jainSipJobManager.remove(id);
                     }
                     if (event.equals("register-failure")) {
                        jainSipClient.listener.onClientErrorEvent(id, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }

               index++;
            } while (loop);
         }
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

   // id is a unique identifier for a Job. It is App provided for outgoing requests (typically Unix time with miliseconds, as a string)
   // and SIP Call-Id for incoming requests. Notice that for outgoing requests the App provided id is also used as SIP Call-ID, to make
   // troubleshooting easier
   public String id;
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

   JainSipJob(JainSipJobManager jainSipJobManager, JainSipClient jainSipClient, String id, Type type, Transaction transaction, HashMap<String, Object> parameters,
              JainSipCall jainSipCall)
   {
      this.id = id;
      this.type = type;
      this.transaction = transaction;
      this.parameters = parameters;
      this.authenticationAttempts = 0;
      this.jainSipClient = jainSipClient;
      this.jainSipJobManager = jainSipJobManager;
      this.jainSipFsm = new JainSipFsm(type, jainSipClient);
      this.jainSipCall = jainSipCall;
   }

    /*
    JainSipJob(JainSipClient jainSipClient, String id, Type type, Transaction transaction, HashMap<String, Object> parameters) {
        this(jainSipClient, id, type, RegistrationType.REGISTRATION_INITIAL, transaction, parameters, null);
    }

    JainSipJob(JainSipClient jainSipClient, String id, Type type, RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters) {
        this(jainSipClient, id, type, registrationType, transaction, parameters, null);
    }
    */

   void start()
   {

   }

   void processFsm(String id, String event, Object arg, RCClient.ErrorCodes statusCode, String statusText)
   {
      jainSipFsm.process(id, event, arg, statusCode, statusText);
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
}
