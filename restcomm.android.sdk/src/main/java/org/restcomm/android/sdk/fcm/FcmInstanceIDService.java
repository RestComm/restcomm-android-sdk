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

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import android.util.Log;

import org.restcomm.android.sdk.storage.StorageManagerPreferences;

public class FcmInstanceIDService extends FirebaseInstanceIdService {

   private static final String TAG = "FcmInstanceIDService";

   /**
   * Called if InstanceID token is updated. This may occur if the security of
   * the previous token had been compromised. Note that this is called when the InstanceID token
   * is initially generated so this is where you would retrieve the token.
   */
   @Override
   public void onTokenRefresh() {

      String refreshedToken = FirebaseInstanceId.getInstance().getToken();
      Log.d(TAG, "Refreshed token: " + refreshedToken);

      sendRegistrationToServer();
   }

   /**
   * Persist token to third-party servers.
   *
   * Modify this method to associate the user's FCM InstanceID token with any server-side account
   * maintained by your application.
   *
   */
   private void sendRegistrationToServer() {
      Log.d(TAG, "Updating server");
      StorageManagerPreferences  storageManagerPreferences = new StorageManagerPreferences(this);
      new FcmConfigurationHandler(storageManagerPreferences, null).updateBinding();
   }
}
