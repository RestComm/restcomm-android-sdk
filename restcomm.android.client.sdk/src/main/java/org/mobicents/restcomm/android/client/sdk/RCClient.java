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

package org.mobicents.restcomm.android.client.sdk;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;

import org.mobicents.restcomm.android.sipua.RCLogger;


/**
 * Top level singleton to initialize and shut down the Restcomm Client SDK. RCClient is also responsible
 * for creating the RCDevice object that represents a virtual device that can create connections and
 * send text messages.
 *
 * @see RCDevice
 * @see RCConnection
 */
public class RCClient {
    private static RCClient instance = null;
    private static boolean initialized = false;

    public enum ErrorCodes {
        GENERIC_ERROR,
        CONNECTION_DECLINED,
        CONNECTION_TIMEOUT,
        NO_CONNECTIVITY,
        WEBRTC_PEERCONNECTION_ERROR,
        SIGNALLING_SIPURI_PARSE_ERROR,
        SIGNALLING_DNS_ERROR,
        SIGNALLING_DESTINATION_NOT_FOUND,
        SIGNALLING_TIMEOUT,
        SIGNALLING_REGISTER_ERROR,
        SIGNALLING_REGISTER_AUTH_ERROR,
        SIGNALLING_CALL_ERROR,
        SIGNALLING_INSTANT_MESSAGE_ERROR,
        PERMISSION_NOT_GRANTED_ERROR
    }

    public static String errorText(ErrorCodes errorCode) {

        if (errorCode == ErrorCodes.CONNECTION_DECLINED) {
            return "Connection declined";
        }
        else if (errorCode == ErrorCodes.CONNECTION_TIMEOUT) {
            return "Connection timed out";
        }
        else if (errorCode == ErrorCodes.NO_CONNECTIVITY) {
            return "No Wifi connectivity";
        }
        else if (errorCode == ErrorCodes.WEBRTC_PEERCONNECTION_ERROR) {
            return "Webrtc Peer Connection error";
        }
        else if (errorCode == ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR) {
            return "Error parsing SIP URI";
        }
        else if (errorCode == ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR) {
            return "Error in DNS resolving";
        }
        return "Generic Restcomm Client error";
    }

    static ArrayList<RCDevice> list;
    static Context context;
    private static final String TAG = "RCClient";


    protected RCClient() {
        // Exists to defeat instantiation.
    }

    // SDK users need to use initialize()
    private static RCClient getInstance()
    {
        if (instance == null) {
            instance = new RCClient();
        }
        return instance;
    }

    public static Context getContext()
    {
        return context;
    }

    /**
     * Initialize the Restcomm Client SDK
     * @param context  The Android Activity context
     * @param listener  The listener for upcoming events from Restcomm Client
     */
    public static void initialize(Context context, final RCInitListener listener)
    {
        if (context == null) {
            throw new IllegalArgumentException("Error: Context cannot be null");
        } else if (listener == null) {
            throw new IllegalArgumentException("Error: Listener cannot be null");
        } else {
            RCClient.getInstance();
            RCClient.context = context;
            list = new ArrayList<RCDevice>();
            initialized = true;

            listener.onInitialized();

            /*
            TODO: this would probably make more sense at some point
            Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    // notify that we are initialized
                    listener.onInitialized();
                }
            };
            mainHandler.post(myRunnable);
            */
        }
    }

    /**
     * Shut down the Restcomm Client
     */
    public static void shutdown()
    {
        if (!initialized) {
            return;
        }

        if (list.size() > 0) {
            RCDevice device = list.get(0);
            // remove the reference so that RCDevice instance is removed
            list.clear();
            list = null;
            // Need to make sure that shutdown() will finish its job synchronously.
            // Keep in mind that once this block is left device can be claimed by GC
            device.release();
        }
        else {
            RCLogger.e(TAG, "shutdown(): Warning Restcomm Client already shut down, skipping");
        }
        // allow the singleton to be GC'd
        instance = null;
        initialized = false;
    }

    /**
     * Retrieve whether Restcomm Client is initialized
     * @return
     */
    public static boolean isInitialized()
    {
        return initialized;
    }

    /**
     * Create an initialize a new Device object
     * @param parameters  Restcomm Client parameters
     * @param deviceListener  The listener for upcoming RCDevice events
     * @return The newly created RCDevice
     * @see RCDevice
     */
    public static RCDevice createDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener)
    {
        if (!initialized) {
            RCLogger.i(TAG, "Attempting to create RCDevice without first initializing RCClient");
            return null;
        }

        if (list.size() == 0) {
            RCDevice device = new RCDevice(parameters, deviceListener);
            list.add(device);
        }
        else {
            RCLogger.e(TAG, "Device already exists, so we 're returning this one");
        }

        return list.get(0);
    }

    /**
     * Retrieve a list of active Devices
     * @return  List of Devices
     */
    public static ArrayList<RCDevice> listDevices()
    {
        if (!initialized) {
            RCLogger.w(TAG, "RCClient uninitialized");
            return null;
        }
        if (list.size() == 0) {
            RCLogger.e(TAG, "Warning: RCDevice list size is 0");
        }

        return list;
    }

    public static void setLogLevel(int level)
    {
        RCLogger.setLogLevel(level);
    }

    /*
    // TODO: implement
    public static String getVersion()
    {
        return "";
    }
    */

    /**
     * Interface defining callbacks for RCClient, such as when it is fully initialized and in case of error
     */
    public interface RCInitListener {
        /**
         * Callback that is called when RCClient is fully initialized
         */
        void onInitialized();
        /**
         * Callback that is called if there's an error
         */
        void onError(Exception exception);
    }
}
