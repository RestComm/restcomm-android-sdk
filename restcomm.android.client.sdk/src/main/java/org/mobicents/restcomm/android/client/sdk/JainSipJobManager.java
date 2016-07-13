package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.Transaction;

import java.util.HashMap;
import java.util.Map;

// Handles live JAIN SIP transactions. Each transaction is stored in 'transactions' map identified by an 'id' provided by the caller (in our RCDevice or RCConnection)
// and keeps information such as the JAIN SIP Transaction (Client or Server) amongst other things
public class JainSipJobManager {
   // TODO: consider using interface instead
   JainSipClient jainSipClient;
   HashMap<String, JainSipJob> transactions;


   JainSipJobManager(JainSipClient jainSipClient)
   {
      this.jainSipClient = jainSipClient;
      transactions = new HashMap<>();
   }

   JainSipJob add(String id, JainSipJob.Type type, Transaction transaction, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      //JainSipJob jainSipJob = new JainSipJob(this, jainSipClient, id, type, registrationType, transaction, parameters);
      JainSipJob jainSipJob = new JainSipJob(this, jainSipClient, id, type, transaction, parameters, jainSipCall);
      transactions.put(id, jainSipJob);
      jainSipJob.processFsm(id, "", null, null, null);
      return jainSipJob;
   }

    /*
    void add(String id, JainSipJob.Type type, Transaction transaction, HashMap<String, Object> parameters) {
        add(id, type, transaction, parameters);
    }
    */

   JainSipJob add(String id, JainSipJob.Type type, HashMap<String, Object> parameters)
   {
      return add(id, type, null, parameters, null);
   }

   JainSipJob add(String id, JainSipJob.Type type, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      return add(id, type, null, parameters, jainSipCall);
   }
    /*
    void add(String id, JainSipJob.Type type, JainSipJob.RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters, Runnable onCompletion) {
        //synchronized (this) {
        JainSipJob t = new JainSipJob(jainSipClient, id, type, registrationType, transaction, parameters, onCompletion);
        //HashMap<String, Object> data = new HashMap<>();
        //data.put("transaction", transaction);
        //data.put("parameters", parameters);
        //data.put("type",);

        transactions.put(id, t);
        //}
    }
    */

   JainSipJob get(String id)
   {
      //synchronized (this) {
      if (transactions.containsKey(id)) {
         // this doesn't seem harmful let's suppress it for now
         //@SuppressWarnings("unchecked")
         return transactions.get(id);
      }
      else {
         return null;
      }
      //}
   }

   JainSipJob getUsingCallId(String callId)
   {
      for (Map.Entry<String, JainSipJob> entry : transactions.entrySet()) {
         JainSipJob transaction = entry.getValue();
         if (transaction.callId.equals(callId)) {
            return transaction;
         }
      }
      return null;
   }

   void remove(String id)
   {
      //synchronized (this) {
      if (transactions.containsKey(id)) {
         // this doesn't seem harmful let's suppress it for now
         //@SuppressWarnings("unchecked")
         transactions.remove(id);
      }
      //}
   }

   void removeAll()
   {
      //synchronized (this) {
      transactions.clear();
      //}
   }

}
