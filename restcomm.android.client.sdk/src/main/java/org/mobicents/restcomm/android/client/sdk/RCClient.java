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
import android.util.Log;
//import org.mobicents.restcomm.android.client.sdk.RCClient;

public class RCClient {
    private static RCClient instance = null;
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

    public static void shutdown()
    {

    }

    public static boolean isInitialized()
    {
        return true;
    }

    public static RCDevice createDevice(String capabilityToken, RCDeviceListener deviceListener)
    {
        RCDevice device = new RCDevice(RCClient.getInstance(), capabilityToken, deviceListener);
        if (list.size() == 0) {
            list.add(device);
        }
        else {
            Log.e(TAG, "Error: device already exists -multiple devices not implemented");
            return null;
        }

        return device;
    }

    public List<RCDevice> listDevices()
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

    public interface RCInitListener {
        void onInitialized();
        void onError(Exception exception);
    }
}
