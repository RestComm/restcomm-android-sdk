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

import android.content.Intent;
import android.util.Log;

import org.restcomm.android.sdk.RCDevice;

public class FcmMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FcmMessagingService";

    /**
     * Called when message is received.
     *
     * @param remoteMessage
     *         Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Push Notification from: " + remoteMessage.getFrom());
        sendNotification();
    }

    private void sendNotification() {
        //start service
        //we dont need to start a service if its already running.
        //The restcomm server will always send the push...
        if (!RCDevice.isServiceAttached && !RCDevice.isServiceInitialized){
            Log.d(TAG, "service is not attached");
            Intent intent = new Intent(this, RCDevice.class);
            intent.setAction(RCDevice.ACTION_FCM);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
                startForegroundService(intent);
            } else{
                startService(intent);
            }
        }

        Log.d(TAG, "sendNotification is called");
    }
}
