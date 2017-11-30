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

import org.restcomm.android.sdk.storage.StorageManager;

/**
 * Manage logic for registering device and user for push notifications
 *
 */

public class FcmConfigurationHandler {

    private StorageManager mStorageManager;

    private FcmOnPushRegistrationListener mListener;

    /**
     *    @param FcmOnPushRegistrationListener listener
     *    @param StorageManager storageManager, it will be used for getting/saving
     *   Parameters data. Parameters used:
     *   RCDevice.ParameterKeys.SIGNALING_USERNAME - Identity for the client, like bob
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME - (name of the client application)
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL - (account's email)
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD - (password for an account)
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT - (true if we want to enable push on server for the account, otherwise false)
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN - (domain for the push notifications; for example: push.restcomm.com)
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN - (Restcomm HTTP domain, like 'cloud.restcomm.com')
     *   RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY - (server hash key for created application in firebase cloud messaging)
     **/
    public FcmConfigurationHandler(StorageManager storageManager, FcmOnPushRegistrationListener listener){
        this.mStorageManager = storageManager;
        this.mListener = listener;
    }

    /**
        Method will register/update the account’s data for push messaging on restcomm
        server
    **/
    public void registerForPush(){
        
    }


}
