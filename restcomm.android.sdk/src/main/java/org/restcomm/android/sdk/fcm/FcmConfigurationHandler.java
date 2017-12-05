/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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

package org.restcomm.android.sdk.fcm;

import com.google.firebase.iid.FirebaseInstanceId;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.fcm.model.FcmApplication;
import org.restcomm.android.sdk.fcm.model.FcmBinding;
import org.restcomm.android.sdk.fcm.model.FcmCredentials;
import org.restcomm.android.sdk.storage.StorageManagerInterface;
import org.restcomm.android.sdk.util.RCLogger;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Pair;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Manage logic for registering device and user for push notifications
 */

public class FcmConfigurationHandler {

    private static final String TAG = FcmConfigurationHandler.class.getCanonicalName();

    private static final String FCM_ACCOUNT_SID = "fcm-account-sid";
    private static final String FCM_CLIENT_SID = "fcm-client-sid";
    private static final String FCM_APPLICATION = "fcm-application";
    private static final String FCM_CREDENTIALS = "fcm-credentials";
    private static final String FCM_BINDING = "fcm-binding";

    private static final String TYPE = "fcm";

    private WeakReference<StorageManagerInterface> mStorageManagerWeak;
    private FcmConfigurationClient mFcmConfigurationClient;
    private String mEmail;
    private String mUsername;
    private String mApplicationName;
    private String mFcmSecretKey;
    private boolean mEnablePush;

    private WeakReference<FcmPushRegistrationListener> mListenerWeak;


    /**
     *  @param listener
     *  @param storageManagerInterface, it will be used for getting/saving
     *  Parameters data. Parameters used:
     *  RCDevice.ParameterKeys.SIGNALING_USERNAME - Identity for the client, like bob
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME - (name of the client application)
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL - (account's email)
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD - (password for an account)
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT - (true if we want to enable push on server for the account, otherwise false)
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN - (domain for the push notifications; for example: push.restcomm.com)
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN - (Restcomm HTTP domain, like 'cloud.restcomm.com')
     *  RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY - (server hash key for created application in firebase cloud messaging)
     **/
    public FcmConfigurationHandler(StorageManagerInterface storageManagerInterface, FcmPushRegistrationListener listener){
        this.mStorageManagerWeak = new WeakReference<StorageManagerInterface>(storageManagerInterface);
        this.mListenerWeak = new WeakReference<FcmPushRegistrationListener>(listener);
        mEmail = storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "");
        String password = storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "");
        String pushDomain = storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "");
        String httpDomain = storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "");
        mEnablePush = storageManagerInterface.getBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
        mUsername = storageManagerInterface.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "");
        mApplicationName =  storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "");
        mFcmSecretKey = storageManagerInterface.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "");
        this.mFcmConfigurationClient = new FcmConfigurationClient(mEmail, password, pushDomain, httpDomain);
    }

    /**
        Method will register/update the account’s data for push messaging on restcomm
        server
    **/
    @SuppressWarnings("unchecked")
    public void registerForPush(){
        //get all data before running in background (we dont want context from storage manager to be inside)
        String accountSid = mStorageManagerWeak.get().getString(FCM_ACCOUNT_SID, null);
        String clientSid = mStorageManagerWeak.get().getString(FCM_CLIENT_SID, null);
        String applicationString = mStorageManagerWeak.get().getString(FCM_APPLICATION, null);
        String credentialsString = mStorageManagerWeak.get().getString(FCM_CREDENTIALS, null);
        String bindingString = mStorageManagerWeak.get().getString(FCM_BINDING, null);

        HashMap map = new HashMap<String, String>();
        map.put(FCM_ACCOUNT_SID, accountSid);
        map.put(FCM_CLIENT_SID, clientSid);
        map.put(FCM_APPLICATION, applicationString);
        map.put(FCM_CREDENTIALS, credentialsString);
        map.put(FCM_BINDING, bindingString);

        new AsyncTaskRegisterForPush(mEmail, mFcmConfigurationClient, mUsername, mApplicationName, mFcmSecretKey).execute(map);
    }

    private class AsyncTaskRegisterForPush extends AsyncTask<HashMap<String, String>, Void, Pair<HashMap<String, String>, RCClient.ErrorCodes>> {
        String email;
        FcmConfigurationClient fcmConfigurationClient;
        String username;
        String applicationName;
        String fcmSecretKey;

        public AsyncTaskRegisterForPush(String email, FcmConfigurationClient fcmConfigurationClient,
                                        String username, String applicationName, String fcmSecretKey){
            this.email = email;
            this.username = username;
            this.fcmConfigurationClient = fcmConfigurationClient;
            this.applicationName = applicationName;
            this.fcmSecretKey = fcmSecretKey;
        }

        @Override
        protected Pair<HashMap<String, String>, RCClient.ErrorCodes> doInBackground(HashMap<String, String>... hashMap) {
            HashMap<String, String> resultHashMap = new HashMap<>();
            HashMap<String, String> inputHashMap = hashMap[0];

            //check the accountSid, if account sid is null we nee to get the new one from the server
            RCLogger.v(TAG, "Getting an account sid");
            String accountSid = inputHashMap.get(FCM_ACCOUNT_SID);

            if (accountSid == null){
                RCLogger.v(TAG, "Account sid not found, getting it from server.");
                accountSid = fcmConfigurationClient.getAcccountSid(email);
            }

            if (accountSid !=null){
                RCLogger.v(TAG, "Account sid found; Storing account sid;");
                resultHashMap.put(FCM_ACCOUNT_SID, accountSid);

                RCLogger.v(TAG, "Getting a client sid");
                String clientSid = inputHashMap.get(FCM_CLIENT_SID);
                if (clientSid == null){
                    RCLogger.v(TAG, "Client sid not found, getting it from server.");
                    clientSid = fcmConfigurationClient.getClientSid(accountSid, username);
                }
                if (clientSid != null){
                    RCLogger.v(TAG, "Client sid found; Storing client sid;");
                    resultHashMap.put(FCM_CLIENT_SID, clientSid);

                    //we need to check should we enable/disable push notifications on server
                    //for now we will leave it always enabled
                    //fcmConfigurationClient.enableClientPushSettings(mEnablePush, accountSid, clientSid);
                    mEnablePush = true;

                    if (mEnablePush) {
                        //APPLICATION
                        RCLogger.v(TAG, "Getting an application;");
                        String fcmApplicationString = inputHashMap.get(FCM_APPLICATION);
                        FcmApplication application = getApplication(fcmApplicationString);
                        if (application == null) {
                            RCLogger.v(TAG, "Application not found, raising error;");
                            return new Pair<>(null, RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_MISSING);
                        }

                        RCLogger.v(TAG, "Application found, Storing it.");
                        resultHashMap.put(FCM_APPLICATION, application.getJSONObject().toString());

                        //CREDENTIALS
                        RCLogger.v(TAG, "Getting the credentials;");
                        String fcmCredentialsString = inputHashMap.get(FCM_CREDENTIALS);
                        FcmCredentials credentials = getCredentials(fcmCredentialsString, application);
                        if (credentials == null) {
                            RCLogger.v(TAG, "Credentials not found, raising error;");
                            return new Pair<>(null, RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_CREDENTIALS_MISSING);
                        }

                        RCLogger.v(TAG, "Credentials found, Storing it.");
                        resultHashMap.put(FCM_CREDENTIALS, credentials.getJSONObject().toString());

                        //BINDING
                        RCLogger.v(TAG, "Getting binding");
                        FcmBinding binding = fcmConfigurationClient.getBinding(application);
                        String token = FirebaseInstanceId.getInstance().getToken();
                        if (binding != null && !binding.getAddress().equals(token)) {
                            RCLogger.v(TAG, "Updating binding");
                            binding.setAddress(token);
                            binding = fcmConfigurationClient.updateBinding(binding);
                        } else if (binding == null) {
                            RCLogger.v(TAG, "Creating binding");
                            binding = new FcmBinding("", clientSid, application.getSid(), TYPE, token);
                            binding = fcmConfigurationClient.createBinding(binding);
                        }

                        if (binding == null) {
                            return new Pair<>(null, RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_BINDING_MISSING);
                        }

                        resultHashMap.put(FCM_BINDING, binding.getJSONObject().toString());
                    }
                    return new Pair<>(resultHashMap, RCClient.ErrorCodes.SUCCESS);
                } else {
                    return new Pair<>(null, RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_CLIENT_SID_MISSING);
                }
            } else {
                return new Pair<>(null, RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_SID_MISSING);
            }
        }


        private FcmApplication getApplication(String applicationStorageString) {
            FcmApplication application;

            if (!TextUtils.isEmpty(applicationStorageString)) {
                //get fcm application object
                application = new FcmApplication();
                application.fillFromJson(applicationStorageString);

                return application;
            }

            application = fcmConfigurationClient.getApplication(this.applicationName);
            if (application == null) {
                //create application
                application = new FcmApplication("", this.applicationName);
                application = fcmConfigurationClient.createApplication(application);
            }

            return  application;
        }


        private FcmCredentials getCredentials(String credentialsStorageString, FcmApplication application){
            FcmCredentials credentials;

            if(!TextUtils.isEmpty(credentialsStorageString)){
                //get fcm credentials object
                credentials = new FcmCredentials();
                credentials.fillFromJson(credentialsStorageString);

                if (credentials.getApplicationSid().equals(application.getSid())){
                    return credentials;
                }
            }

            credentials = fcmConfigurationClient.getCredentials(application);
            //create new credentials
            if (credentials == null){
                credentials = new FcmCredentials("", application.getSid(), TYPE);
                credentials = fcmConfigurationClient.createCredentials(credentials, fcmSecretKey);
            }
            return credentials;
        }


        @Override
        protected void onPostExecute(Pair<HashMap<String, String>, RCClient.ErrorCodes> result) {
            if(result.second == RCClient.ErrorCodes.SUCCESS){
                HashMap<String, String> resultHash = result.first;
                //save data to storage
                String accountSid = resultHash.get(FCM_ACCOUNT_SID);
                String clientSid = resultHash.get(FCM_CLIENT_SID);
                String applicationString = resultHash.get(FCM_APPLICATION);
                String credentialsString = resultHash.get(FCM_CREDENTIALS);
                String bindingString = resultHash.get(FCM_BINDING);

                RCLogger.v(TAG, "Storing RESULTS");
                if (mStorageManagerWeak.get() != null) {
                    mStorageManagerWeak.get().saveString(FCM_ACCOUNT_SID, accountSid);
                    mStorageManagerWeak.get().saveString(FCM_CLIENT_SID, clientSid);
                    mStorageManagerWeak.get().saveString(FCM_APPLICATION, applicationString);
                    mStorageManagerWeak.get().saveString(FCM_CREDENTIALS, credentialsString);
                    mStorageManagerWeak.get().saveString(FCM_BINDING, bindingString);
                }


                //if listener exists
                if (mListenerWeak.get() != null) {
                    mListenerWeak.get().onRegisteredForPush(RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                }

            } else {
                RCClient.ErrorCodes errorCode = result.second;
                if (mListenerWeak.get() != null) {
                    mListenerWeak.get().onRegisteredForPush(errorCode, RCClient.errorText(errorCode));
                }
            }

        }

    }
}
