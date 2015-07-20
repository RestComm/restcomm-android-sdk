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
import java.util.List;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.util.Log;


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
    public enum ErrorCodes {
        GENERIC_ERROR,
        CONNECTION_DECLINED,
        CONNECTION_TIMEOUT,
        NO_CONNECTIVITY,
    }

    public static String errorText(ErrorCodes errorCode) {

        if (errorCode == ErrorCodes.CONNECTION_DECLINED) {
            return "Connection declined";
        }
        else if (errorCode == ErrorCodes.CONNECTION_TIMEOUT) {
            return "Connection timed out";
        }
        else if (errorCode == ErrorCodes.NO_CONNECTIVITY) {
            return "No network connectivity";
        }
        return "Generic Restcomm Client error";
    }

    static ArrayList<RCDevice> list = new ArrayList<RCDevice>();
    Context context;
    private static final String TAG = "RCClient";


    protected RCClient() {
        // Exists only to defeat instantiation.
    }

    public static RCClient getInstance()
    {
        if (instance == null) {
            instance = new RCClient();
        }
        return instance;
    }

    /**
     * Initialize the Restcomm Client SDK
     * @param context  The Android Activity context
     * @param listener  The listener for upcoming events from Restcomm Client
     */
    public static void initialize(Context context, RCInitListener listener)
    {
        if (context == null) {
            throw new IllegalArgumentException("Error: Context cannot be null");
        } else if (listener == null) {
            throw new IllegalArgumentException("Error: Listener cannot be null");
        } else {
            RCClient client = RCClient.getInstance();
            client.context = context;
            // notify that we are initialized
            listener.onInitialized();
        }
    }

    /**
     * Shut down the Restcomm Client (<b>Not implemented yet</b>
     */
    public static void shutdown()
    {

    }

    /**
     * Retrieve whether Restcomm Client is initialized (<b>Not implemented yet</b>)
     * @return
     */
    public static boolean isInitialized()
    {
        return true;
    }

    /**
     * Create an initialize a new Device object
     * @param capabilityToken  The capability token to use
     * @param deviceListener  The listener for upcoming RCDevice events
     * @return The newly created RCDevice
     * @see RCDevice
     */
    public static RCDevice createDevice(String capabilityToken, RCDeviceListener deviceListener,
                                        GLSurfaceView videoView, SharedPreferences prefs, int viewId)
    {
        if (list.size() == 0) {
            RCDevice device = new RCDevice(capabilityToken, deviceListener, videoView, prefs, viewId);
            list.add(device);
        }
        else {
            Log.i(TAG, "Device already exists, so we 're returning this one -multiple devices not implemented");
        }

        return list.get(0);
    }

    /**
     * Retrieve a list of active Devices
     * @return  List of Devices
     */
    public ArrayList<RCDevice> listDevices()
    {
        //ArrayList<RCDevice> list = new ArrayList<RCDevice>();
        return list;
    }

    /*
    public static void setLogLevel(int level)
    {

    }

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
