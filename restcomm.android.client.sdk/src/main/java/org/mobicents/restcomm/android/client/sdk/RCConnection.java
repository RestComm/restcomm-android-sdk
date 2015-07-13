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

import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.mobicents.restcomm.android.sipua.SipUAConnectionListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

/**
 *  RCConnection represents a call. An RCConnection can be either incoming or outgoing. RCConnections are not created by themselves but
 *  as a result on an action on RCDevice. For example to initiate an outgoing connection you call RCDevice.connect() which instantiates
 *  and returns a new RCConnection. On the other hand when an incoming connection arrives the RCDevice delegate is notified with
 *  RCDeviceListener.onIncomingConnection() and passes the new RCConnection object that is used by the delegate to
 *  control the connection.
 *
 *  When an incoming connection arrives through RCDeviceListener.onIncomingConnection() it is considered RCConnectionStateConnecting until it is either
 *  accepted with RCConnection.accept() or rejected with RCConnection.reject(). Once the connection is accepted the RCConnection transitions to RCConnectionStateConnected
 *  state.
 *
 *  When an outgoing connection is created with RCDevice.connect() it starts with state RCConnectionStatePending. Once it starts ringing on the remote party it
 *  transitions to RCConnectionStateConnecting. When the remote party answers it, the RCConnection state transitions to RCConnectionStateConnected.
 *
 *  Once an RCConnection (either incoming or outgoing) is established (i.e. RCConnectionStateConnected) media can start flowing over it. DTMF digits can be sent over to
 *  the remote party using RCConnection.sendDigits() (<b>Not implemented yet</b>). When done with the RCConnection you can disconnect it with RCConnection.disconnect().
 */
public class RCConnection implements SipUAConnectionListener {
    /**
     * Connection State
     */
    public enum ConnectionState {
        PENDING,  /** Connection is in state pending */
        CONNECTING,  /** Connection is in state connecting */
        CONNECTED,  /** Connection is in state connected */
        DISCONNECTED,  /** Connection is in state disconnected */
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

    /**
     *  Initialize a new RCConnection object. <b>Important</b>: this is used internally by RCDevice and is not meant for application use
     *
     *  @param connectionListener RCConnection listener that will be receiving RCConnection events (@see RCConnectionListener)
     *
     *  @return Newly initialized object
     */
    public RCConnection(RCConnectionListener connectionListener)
    {
        this.listener = connectionListener;
    }

    // 'Copy' constructor
    public RCConnection(RCConnection connection)
    {
        this.incoming = connection.incoming;
        this.muted = connection.muted;

        this.state = connection.state;
        // not used yet
        this.parameters = null;  //new HashMap<String, String>(connection.parameters);
        this.listener = connection.listener;
    }

    /**
     * Retrieves the current state of the connection
     */
    public ConnectionState getState()
    {
        return this.state;
    }

    /**
     * Retrieves the set of application parameters associated with this connection <b>Not Implemented yet</b>
     * @return Connection parameters
     */
    public Map<String, String> getParameters()
    {
        return parameters;
    }

    /**
     * Returns whether the connection is incoming or outgoing
     * @return True if incoming, false otherwise
     */
    public boolean isIncoming()
    {
        return this.incoming;
    }

    /**
     * Accept the incoming connection
     */
    public void accept()
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().Accept();
            this.state = state.CONNECTED;
        }
    }

    /**
     * Ignore incoming connection
     */
    public void ignore()
    {

    }

    /**
     * Reject incoming connection
     */
    public void reject()
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().Reject();
            this.state = state.DISCONNECTED;
        }
    }

    /**
     * Disconnect the established connection
     */
    public void disconnect()
    {
        if (haveConnectivity()) {
            if (state == ConnectionState.CONNECTING) {
                DeviceImpl.GetInstance().Cancel();
            } else if (state == ConnectionState.CONNECTED) {
                DeviceImpl.GetInstance().Hangup();
            }
            //this.state = state.DISCONNECTED;
        }

        // need to free webrtc resources as well; notify RCDevice that hosts webrtc facilities
        ArrayList<RCDevice> deviceList = RCClient.getInstance().listDevices();
        if (deviceList.size() > 0) {
            RCDevice device = deviceList.get(0);
            device.disconnect();
        }
    }

    /**
     * Mute connection so that the other party cannot local audio
     * @param muted True to mute and false in order to unmute
     */
    public void setMuted(boolean muted)
    {
        DeviceImpl.GetInstance().Mute(muted);
    }

    /**
     * Retrieve whether connection is muted or not
     * @return True connection is muted and false otherwise
     */
    public boolean isMuted()
    {
        return false;
    }

    /**
     * Send DTMF digits over the connection (<b>Not Implemented yet</b>)
     * @param digits A string of DTMF digits to be sent
     */
    public void sendDigits(String digits)
    {

    }

    /**
     * Update connection listener to be receiving Connection related events
     * @param listener  New connection listener
     */
    public void setConnectionListener(RCConnectionListener listener)
    {
        this.listener = listener;
        DeviceImpl.GetInstance().sipuaConnectionListener = this;
    }

    // SipUA Connection Listeners
    public void onSipUAConnecting(SipEvent event)
    {
        this.state = ConnectionState.CONNECTING;
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onConnecting(finalConnection);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUAConnected(SipEvent event)
    {
        this.state = ConnectionState.CONNECTED;
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onConnected(finalConnection);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUADisconnected(SipEvent event)
    {
        // we 're first notifying listener and then setting new state because we want the listener to be able to
        // differentiate between disconnect and remote cancel events with the same listener method: onDisconnected.
        // In the first case listener will see stat CONNECTED and in the second CONNECTING
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onDisconnected(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
    }

    public void onSipUACancelled(SipEvent event)
    {
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onCancelled(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
    }

    public void onSipUADeclined(SipEvent event)
    {
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onDeclined(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
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
                this.listener.onDisconnected(this, RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            }
            return false;
        }
    }

    // Parcelable stuff (not needed for now -let's keep around in case we use it at some point):
    /*
    @Override
    public int describeContents() {
        return 0;
    }

    // Parceable stuff
    public void writeToParcel(Parcel out, int flags) {
        //out.writeInt(state.ordinal());
        boolean one[] = new boolean[1];
        one[0] = incoming;
        out.writeBooleanArray(one);
    }

    public static final Parcelable.Creator<RCConnection> CREATOR = new Parcelable.Creator<RCConnection>() {
        public RCConnection createFromParcel(Parcel in) {
            return new RCConnection(in);
        }

        public RCConnection[] newArray(int size) {
            return new RCConnection[size];
        }
    };

    private RCConnection(Parcel in) {
        //state = in.readInt();
        boolean one[] = new boolean[1];
        in.readBooleanArray(one);
        incoming = one[0];
    }
    */
}
