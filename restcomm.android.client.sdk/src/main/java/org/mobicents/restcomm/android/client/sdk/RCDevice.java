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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Parcelable;

import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;
//import java.util.iterator;

public class RCDevice implements SipUADeviceListener, Serializable {
    /**
     *  @abstract Device state (**Not Implemented yet**)
     */
    DeviceState state;
    /**
     *  @abstract Device capabilities (**Not Implemented yet**)
     */
    HashMap<DeviceCapability, Object> capabilities;
    /**
     *  @abstract Listener that will be receiving RCDevice events described at RCDeviceListener
     */
    RCDeviceListener listener;
    /**
     *  @abstract Is sound for incoming connections enabled (**Not Implemented yet**)
     */
    boolean incomingSoundEnabled;
    /**
     *  @abstract Is sound for outgoing connections enabled (**Not Implemented yet**)
     */
    boolean outgoingSoundEnabled;
    /**
     *  @abstract Is sound for disconnect enabled (**Not Implemented yet**)
     */
    boolean disconnectSoundEnabled;

    private SipProfile sipProfile;
    //RCDeviceListener deviceListener;
    //Context androidContext;
    RCClient client;

    public enum DeviceState {
        OFFLINE,
        READY,
        BUSY,
    }

    public enum DeviceCapability {
        INCOMING,
        OUTGOING,
        EXPIRATION,
        ACCOUNT_SID,
        APPLICATION_SID,
        APPLICATION_PARAMETERS,
        CLIENT_NAME,
    }

    public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
    public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";

    /**
     *  Initialize a new RCDevice object
     *
     *  @param capabilityToken Capability Token
     *  @param deviceListener  Delegate of RCDevice
     *
     *  @return Newly initialized object
     */
    protected RCDevice(RCClient client, String capabilityToken, RCDeviceListener deviceListener)
    {
        this.client = client;
        this.updateCapabilityToken(capabilityToken);
        this.listener = deviceListener;

        sipProfile = new SipProfile();
        // TODO: check if those headers are needed
        HashMap<String, String> customHeaders = new HashMap<>();
        //customHeaders.put("customHeader1","customValue1");
        //customHeaders.put("customHeader2", "customValue2");

        DeviceImpl deviceImpl = DeviceImpl.GetInstance();
        deviceImpl.Initialize(client.context, sipProfile, customHeaders);

    }
    public void release()
    {

    }

    public void listen()
    {

    }

    public void unlisten()
    {

    }

    public String getCapabilityToken()
    {
        return "";
    }

    public void updateCapabilityToken(String token)
    {

    }

    public RCConnection connect(Map<String, String> parameters, RCConnectionListener listener)
    {
        if (haveConnectivity()) {
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            //DeviceImpl.GetInstance().listener = this;
            DeviceImpl.GetInstance().connectionListener = connection;

            DeviceImpl.GetInstance().Call(parameters.get("username"));

            return connection;
        }
        else {
            return null;
        }
    }

    public boolean sendMessage(String message, Map<String, String> parameters)
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().SendMessage(parameters.get("username"), message);
            return true;
        }
        else {
            return false;
        }
    }

    public void disconnectAll()
    {
        if (haveConnectivity()) {
            // TODO: disconnect open connections
        }
    }

    public Map<DeviceCapability, Object> getCapabilities()
    {
        HashMap<DeviceCapability, Object> map = new HashMap<DeviceCapability, Object>();
        return map;
    }

    public DeviceState getState()
    {
        DeviceState state = DeviceState.READY;
        return state;
    }

    public void setDeviceListener(RCDeviceListener listener)
    {

    }
    public void setIncomingIntent(Intent intent)
    {
        /*
        intent.putExtra(EXTRA_DEVICE, this);
        intent.putExtra(EXTRA_CONNECTION, this);
        PendingIntent pendingIntent = PendingIntent.getActivity(client.context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        */
    }

    public void setIncomingSoundEnabled(boolean incomingSound)
    {

    }

    public boolean isIncomingSoundEnabled()
    {
        return true;
    }
    public void setOutgoingSoundEnabled(boolean outgoingSound)
    {

    }
    public boolean isOutgoingSoundEnabled()
    {
        return true;
    }

    public void setDisconnectSoundEnabled(boolean disconnectSound)
    {

    }

    public boolean isDisconnectSoundEnabled()
    {
        return true;
    }

    public void updateParams(HashMap<String, String> params)
    {
        if (haveConnectivity()) {
            for (String key : params.keySet()) {
                if (key.equals("pref_proxy_ip")) {
                    sipProfile.setRemoteIp(params.get(key));
                } else if (key.equals("pref_proxy_port")) {
                    sipProfile.setRemotePort(Integer.parseInt(params.get(key)));
                } else if (key.equals("pref_sip_user")) {
                    sipProfile.setSipUserName(params.get(key));
                } else if (key.equals("pref_sip_password")) {
                    sipProfile.setSipPassword(params.get(key));
                }
            }
            DeviceImpl.GetInstance().Register();
        }
    }

    // SipUA listeners
    public void onSipUAConnectionArrived(SipEvent event)
    {
        RCConnectionListener connectionListener = (RCConnectionListener)this.listener;
        RCConnection connection = new RCConnection(connectionListener);
        connection.incoming = true;
        connection.state = RCConnection.ConnectionState.CONNECTING;
        DeviceImpl.GetInstance().connectionListener = connection;

        this.listener.onIncomingConnection(this,connection);
    }

    public void onSipUAMessageArrived(SipEvent event)
    {
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("username", event.from);

        this.listener.onIncomingMessage(this, event.content, parameters);
    }

    // Helpers
    private boolean haveConnectivity()
    {
        RCClient client = RCClient.getInstance();
        WifiManager wifi = (WifiManager)client.context.getSystemService(client.context.WIFI_SERVICE);
        if (wifi.isWifiEnabled()) {
            return true;
        }
        else {
            if (this.listener != null) {
                this.listener.onStopListening(this, RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            }
            return false;
        }
    }

}
