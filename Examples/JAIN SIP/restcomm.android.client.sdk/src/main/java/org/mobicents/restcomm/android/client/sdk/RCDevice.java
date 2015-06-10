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
import android.app.PendingIntent;
import org.mobicents.restcomm.android.client.sdk.RCConnection;

public class RCDevice {
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

    /**
     *  Initialize a new RCDevice object
     *
     *  @param capabilityToken Capability Token
     *  @param delegate        Delegate of RCDevice
     *
     *  @return Newly initialized object
     */
    protected RCDevice()
    {

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
        RCConnection connection = new RCConnection();
        return connection;
    }

    public void disconnectAll()
    {

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
    public void setIncomingIntent(PendingIntent intent)
    {

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
}




