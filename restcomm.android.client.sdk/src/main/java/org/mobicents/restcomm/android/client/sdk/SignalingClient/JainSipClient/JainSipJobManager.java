package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import android.javax.sip.Transaction;
import android.javax.sip.header.CallIdHeader;

import java.util.HashMap;
import java.util.Map;

// Handles live JAIN SIP transactions. Each transaction is stored in 'transactions' map identified by an 'id' provided by the caller (in our RCDevice or RCConnection)
// and keeps information such as the JAIN SIP Transaction (Client or Server) amongst other things
class JainSipJobManager {
   // TODO: consider using interface instead
   JainSipClient jainSipClient;
   HashMap<String, JainSipJob> jobs;


   JainSipJobManager(JainSipClient jainSipClient)
   {
      this.jainSipClient = jainSipClient;
      jobs = new HashMap<>();
   }

   JainSipJob add(String jobId, JainSipJob.Type type, Transaction transaction, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      JainSipJob jainSipJob = new JainSipJob(this, jainSipClient, jobId, type, transaction, parameters, jainSipCall);
      jobs.put(jobId, jainSipJob);

      if (jainSipJob.hasFsm()) {
         jainSipJob.startFsm();
      }

      return jainSipJob;
   }

   JainSipJob add(String jobId, JainSipJob.Type type, HashMap<String, Object> parameters)
   {
      return add(jobId, type, null, parameters, null);
   }

   JainSipJob add(String jobId, JainSipJob.Type type, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      return add(jobId, type, null, parameters, jainSipCall);
   }

   JainSipJob get(String jobId)
   {
      if (jobs.containsKey(jobId)) {
         return jobs.get(jobId);
      }
      else {
         return null;
      }
   }

   JainSipJob getByBranchId(String branchId)
   {
      for (Map.Entry<String, JainSipJob> entry : jobs.entrySet()) {
         JainSipJob job = entry.getValue();
         if (job.transaction.getBranchId().equals(branchId)) {
            return job;
         }
      }
      return null;
   }

   JainSipJob getByCallId(String callId)
   {
      for (Map.Entry<String, JainSipJob> entry : jobs.entrySet()) {
         JainSipJob job = entry.getValue();

         if (((CallIdHeader)job.transaction.getRequest().getHeader("Call-ID")).getCallId().equals(callId)) {
            return job;
         }
      }
      return null;
   }

   void remove(String jobId)
   {
      if (jobs.containsKey(jobId)) {
         jobs.remove(jobId);
      }
   }

   void removeAll()
   {
      jobs.clear();
   }

   String getPrintableJobs()
   {
      return "Job count: " + jobs.size() + ", details: " + jobs.toString();
   }
}
