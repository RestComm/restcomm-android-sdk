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
