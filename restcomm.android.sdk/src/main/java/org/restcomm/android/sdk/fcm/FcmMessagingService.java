/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.restcomm.android.sdk.fcm;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;

import android.util.Log;

public class FcmMessagingService extends FirebaseMessagingService {

   private static final String TAG = "FcmMessagingService";

   /**
    * Called when message is received.
    *
    * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
    */
   // [START receive_message]
   @Override
   public void onMessageReceived(RemoteMessage remoteMessage) {
      // [START_EXCLUDE]
      // There are two types of messages data messages and notification messages. Data messages are handled
      // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
      // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
      // is in the foreground. When the app is in the background an automatically generated notification is displayed.
      // When the user taps on the notification they are returned to the app. Messages containing both notification
      // and data payloads are treated as notification messages. The Firebase console always sends notification
      // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
      // [END_EXCLUDE]

      // TODO(developer): Handle FCM messages here.
      // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
      Log.d(TAG, "From: " + remoteMessage.getFrom());

      // Check if message contains a data payload.
      if (remoteMessage.getData().size() > 0) {
         Log.d(TAG, "Message data payload: " + remoteMessage.getData());

         if (/* Check if data needs to be processed by long running job */ true) {
            // For long-running tasks (10 seconds or more) use Firebase Job Dispatcher.
            scheduleJob();
         } else {
            // Handle message within 10 seconds
            handleNow();
         }
      }

      // Check if message contains a notification payload.
      if (remoteMessage.getNotification() != null) {
         Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
      }

      // Also if you intend on generating your own notifications as a result of a received FCM
      // message, here is where that should be initiated. See sendNotification method below.

      sendNotification(remoteMessage.getFrom(), "Push Notification Call");
   }
   // [END receive_message]

   /**
    * Schedule a job using FirebaseJobDispatcher.
    */
   private void scheduleJob() {
      // [START dispatch_job]
      FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
      Job myJob = dispatcher.newJobBuilder()
                  .setService(FcmJobService.class)
                  .setTag("fcm-job-tag")
                  .build();
      dispatcher.schedule(myJob);
      // [END dispatch_job]
   }

   /**
    * Handle time allotted to BroadcastReceivers.
    */
   private void handleNow() {
        Log.d(TAG, "Short lived task is done.");
    }

   /**
    * Create and show a simple notification containing the received FCM message.
    *
    * @param messageBody FCM message body received.
    */
   private void sendNotification(String from, String messageBody) {
      FcmMessages.getInstance().sendNotification(from, messageBody);
   }
}
