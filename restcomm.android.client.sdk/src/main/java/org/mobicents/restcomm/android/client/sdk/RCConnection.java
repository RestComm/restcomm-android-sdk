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

import java.util.HashMap;
import java.util.Map;
import org.mobicents.restcomm.android.sipua.SipUAConnectionListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

public class RCConnection implements SipUAConnectionListener {
    public enum ConnectionState {
        PENDING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
    };

    String IncomingParameterFromKey = "RCConnectionIncomingParameterFromKey";
    String IncomingParameterToKey = "RCConnectionIncomingParameterToKey";
    String IncomingParameterAccountSIDKey ="RCConnectionIncomingParameterAccountSIDKey";
    String IncomingParameterAPIVersionKey = "RCConnectionIncomingParameterAPIVersionKey";
    String IncomingParameterCallSIDKey = "RCConnectionIncomingParameterCallSIDKey";

    /**
     *  @abstract State of the connection.
     *
     *  @discussion A new connection created by RCDevice starts off RCConnectionStatePending. It transitions to RCConnectionStateConnecting when it starts ringing. Once the remote party answers it it transitions to RCConnectionStateConnected. Finally, when disconnected it resets to RCConnectionStateDisconnected.
     */
    ConnectionState state;

    /**
     *  @abstract Direction of the connection. True if connection is incoming; false otherwise
     */
    boolean incoming;

   /**
     *  @abstract Connection parameters (**Not Implemented yet**)
     */
    HashMap<String, String> parameters;

    /**
     *  @abstract Listener that will be called on RCConnection events described at RCConnectionListener
     */
    RCConnectionListener listener;

    /**
     *  @abstract Is connection currently muted? If a connection is muted the remote party cannot hear the local party
     */
    boolean muted;

    public RCConnection(RCConnectionListener connectionListener)
    {
        this.listener = connectionListener;
    }

    public ConnectionState getState()
    {
        return ConnectionState.DISCONNECTED;
    }

    public Map<String, String> getParameters()
    {
        HashMap<String, String> map = new HashMap<String, String>();

        return map;
    }

    public boolean isIncoming()
    {
        return true;
    }

    public void accept()
    {
        DeviceImpl.GetInstance().Accept();
        this.state = state.CONNECTED;
    }

    public void ignore()
    {

    }

    public void reject()
    {
        DeviceImpl.GetInstance().Reject();
        this.state = state.DISCONNECTED;
    }

    public void disconnect()
    {
        if (state == ConnectionState.CONNECTING) {
            DeviceImpl.GetInstance().Cancel();
        }
        else if (state == ConnectionState.CONNECTED) {
            DeviceImpl.GetInstance().Hangup();
        }
        this.state = state.DISCONNECTED;
    }

    public void setMuted(boolean muted)
    {

    }

    public boolean isMuted()
    {
        return false;
    }

    public void sendDigits(String digits)
    {

    }

    public void setConnectionListener(RCConnectionListener listener)
    {

    }

    // SipUA Connection Listeners
    public void onSipUAConnecting(SipEvent event)
    {
        this.state = ConnectionState.CONNECTING;
        this.listener.onConnecting(this);
    }

    public void onSipUAConnected(SipEvent event)
    {
        this.state = ConnectionState.CONNECTED;
        this.listener.onConnected(this);
    }

    public void onSipUADisconnected(SipEvent event)
    {
        this.state = ConnectionState.DISCONNECTED;
        this.listener.onDisconnected(this);
    }

}
