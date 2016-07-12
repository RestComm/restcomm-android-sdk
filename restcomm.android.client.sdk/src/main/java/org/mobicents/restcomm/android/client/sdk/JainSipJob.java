package org.mobicents.restcomm.android.client.sdk;

import android.gov.nist.javax.sip.ResponseEventExt;
import android.javax.sip.Transaction;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;

public class JainSipJob {
   // Each transaction is associated with an FSM
   public class JainSipFsm {
      String[] states;
      //String id;
      JainSipJob.Type type;
      //HashMap<String, Object> parameters;
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
            states = new String[]{"unregister", "auth", "register", "auth", "notify"};
         }
         else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
            states = new String[]{"unregister", "auth", "unbind-bind-register", "auth", "notify"};
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
         if (JainSipJob.this.id.equals(id)) {
            boolean loop;
            do {
               loop = false;
               if (index >= states.length) {
                  RCLogger.e(TAG, "process(): no more states to process");
               }
               if (type == Type.TYPE_OPEN) {
                  if (states[index].equals("start-bind-register")) {
                     try {
                        jainSipClient.jainSipStartStack(JainSipJob.this.id);

                        if (!jainSipClient.notificationManager.haveConnectivity()) {
                           jainSipClient.listener.onClientOpenedEvent(id, jainSipClient.notificationManager.getConnectivityStatus(),
                                 RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY,
                                 RCClient.errorText(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY));
                           jainSipJobManager.remove(id);
                           return;
                        }
                        jainSipClient.jainSipBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipRegister(id, parameters);
                        }
                        else {
                           // No Domain there we are done here
                           jainSipClient.listener.onClientOpenedEvent(id, jainSipClient.notificationManager.getConnectivityStatus(),
                                 RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        jainSipClient.listener.onClientOpenedEvent(id, jainSipClient.notificationManager.getConnectivityStatus(), e.errorCode, e.errorText);
                        jainSipJobManager.remove(id);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientOpenedEvent(id, jainSipClient.notificationManager.getConnectivityStatus(), e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipClient.listener.onClientOpenedEvent(id, jainSipClient.notificationManager.getConnectivityStatus(), statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_REGISTER_REFRESH) {
                  if (states[index].equals("register")) {
                     try {
                        transaction = jainSipClient.jainSipRegister(id, parameters);
                     }
                     catch (JainSipException e) {
                        jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                        jainSipJobManager.remove(id);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-failure")) {
                        jainSipClient.listener.onClientErrorEvent(id, statusCode, statusText);
                     }
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_CLOSE) {
                  if (states[index].equals("unregister")) {
                     try {
                        transaction = jainSipClient.jainSipUnregister(transactionId, parameters);

                     }
                     catch (JainSipException e) {
                        jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("shutdown")) {
                     if (event.equals("register-failure")) {
                        RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                     else if (event.equals("register-success") || event.equals("register-failure")) {
                        try {
                           jainSipClient.jainSipUnbind();
                           jainSipClient.jainSipStopStack();
                           jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                        }
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE) {
                  if (states[index].equals("unregister")) {
                     if (!jainSipClient.notificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY));
                        jainSipJobManager.remove(id);
                        return;
                     }
                     try {
                        if (((HashMap<String, Object>) parameters.get("old-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                              !((HashMap<String, Object>) parameters.get("old-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipUnregister(id, (HashMap<String, Object>) parameters.get("old-parameters"));
                        }
                        else {
                           // No Domain, need to loop through to next step
                           loop = true;
                        }
                     }
                     catch (JainSipException e) {
                        RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
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
                              transactionId = Long.toString(System.currentTimeMillis());
                              transaction = jainSipClient.jainSipRegister(transactionId, (HashMap<String, Object>) parameters.get("new-parameters"));
                           }
                           else {
                              // No domain, need to loop through to next step
                              loop = true;
                           }
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                        }
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipClient.listener.onClientReconfigureEvent(id, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
                  if (states[index].equals("unregister")) {
                     if (!jainSipClient.notificationManager.haveConnectivity()) {
                        jainSipClient.listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY,
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY));
                        jainSipJobManager.remove(id);
                        return;
                     }

                     try {
                        if (((HashMap<String, Object>) parameters.get("old-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                              !((HashMap<String, Object>) parameters.get("old-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipUnregister(id, (HashMap<String, Object>) parameters.get("old-parameters"));
                        }
                        else {
                           // No domain, need to loop through to next step
                           loop = true;
                        }
                     }
                     catch (JainSipException e) {
                        RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
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
                           jainSipClient.jainSipUnbind();

                           //HashMap<String, Object> newParameters = (HashMap<String, Object>) parameters.get("new-parameters");
                           jainSipClient.jainSipBind((HashMap<String, Object>) parameters.get("new-parameters"));

                           if (((HashMap<String, Object>) parameters.get("new-parameters")).containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                                 !((HashMap<String, Object>) parameters.get("new-parameters")).get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                              // Domain has been provided do the registration
                              transactionId = Long.toString(System.currentTimeMillis());
                              transaction = jainSipClient.jainSipRegister(transactionId, (HashMap<String, Object>) parameters.get("new-parameters"));
                           }
                           else {
                              // No domain, need to loop through to next step
                              loop = true;
                           }
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                           jainSipJobManager.remove(id);
                        }
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                        }
                        catch (JainSipException e) {
                           jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                        }
                     }
                     else {
                        loop = true;
                     }
                  }
                  else if (states[index].equals("notify")) {
                     if (event.equals("register-success") || event.equals("register-failure")) {
                        jainSipClient.listener.onClientReconfigureEvent(id, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_RELOAD_NETWORKING) {
                  if (states[index].equals("unbind-bind-register")) {
                     // no need for connectivity check here, we know there is connectivity
                     try {
                        jainSipClient.jainSipUnbind();

                        jainSipClient.jainSipBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipRegister(id, parameters);
                        }
                        else {
                           // No domain, need to loop through to next step
                           RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                           jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        RCLogger.e(TAG, "process(): register failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                        jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           RCLogger.e(TAG, "process(): auth failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                           jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
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
                        jainSipClient.listener.onClientErrorEvent(id, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
               else if (type == Type.TYPE_START_NETWORKING) {
                  if (states[index].equals("bind-register")) {
                     // no need for connectivity check here, we know there is connectivity
                     try {
                        jainSipClient.jainSipBind(parameters);

                        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                           // Domain has been provided do the registration
                           transaction = jainSipClient.jainSipRegister(id, parameters);
                        }
                        else {
                           // No Domain there we are done here
                           RCDeviceListener.RCConnectivityStatus connectivityStatus = (RCDeviceListener.RCConnectivityStatus) parameters.get("connectivity-status");
                           jainSipClient.listener.onClientConnectivityEvent(id, connectivityStatus);
                           jainSipJobManager.remove(id);
                        }
                     }
                     catch (JainSipException e) {
                        jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                     }
                  }
                  else if (states[index].equals("auth")) {
                     // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                     if (event.equals("auth-required")) {
                        ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                        try {
                           jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                        }
                        catch (JainSipException e) {
                           RCLogger.e(TAG, "process(): auth failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                           jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
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
                        jainSipClient.listener.onClientErrorEvent(id, statusCode, statusText);
                        jainSipJobManager.remove(id);
                     }
                  }
               }
                    /*
                    else if (type == Type.TYPE_CALL) {
                        if (states[index].equals("invite")) {
                            try {
                                transaction = jainSipCall.jainSipCallInvite(id, parameters);
                            } catch (JainSipException e) {
                                jainSipCall.listener.onCallErrorEvent(id, e.errorCode, e.errorText);
                                jainSipJobManager.remove(id);
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt) arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipCall.listener.onCallErrorEvent(id, e.errorCode, e.errorText);
                                    jainSipJobManager.remove(id);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("notify")) {
                            if (event.equals("invite-failure")) {
                                jainSipCall.listener.onCallErrorEvent(id, statusCode, statusText);
                            }
                            if (event.equals("invite-success") || event.equals("invite-failure")) {
                                jainSipCall.listener.onCallErrorEvent(id, statusCode, statusText);
                                jainSipJobManager.remove(id);
                            }
                        }
                    }
                    */
               index++;
            } while (loop);
         }
      }
   }

   public enum Type {
      // TODO: remove those when we are done with new logic
      TYPE_REGISTRATION,
      //TYPE_CALL,
      TYPE_MESSAGE,

      //
      TYPE_OPEN,
      TYPE_REGISTER_REFRESH,
      TYPE_CLOSE,
      TYPE_RECONFIGURE,
      TYPE_RECONFIGURE_RELOAD_NETWORKING,
      TYPE_RELOAD_NETWORKING,
      TYPE_START_NETWORKING,

      TYPE_CALL,
   }

    /*
    public enum RegistrationType {
        REGISTRATION_INITIAL,
        REGISTRATION_REFRESH,
        REGISTRATION_UNREGISTER,
    }
    */

   // id is the App provided identification for this job and doesn't change until the job is finished
   public String id;
   // transactionId is the id for the current sip transaction. transactionId for the first transaction for this job is the same as the job id,
   // but as more sip transactions are created for the job, it is updated to reflect the latest sip transaction
   public String transactionId;
   public Type type;
   //public RegistrationType registrationType;
   public Transaction transaction;
   public HashMap<String, Object> parameters;
   public JainSipCall jainSipCall;
   //public Runnable onCompletion;
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
      this.transactionId = id;
      this.type = type;
      //this.registrationType = registrationType;
      this.transaction = transaction;
      this.parameters = parameters;
      this.authenticationAttempts = 0;
      this.jainSipClient = jainSipClient;
      //this.onCompletion = onCompletion;
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

   void updateTransactionId(String transactionId)
   {
      this.transactionId = transactionId;
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
