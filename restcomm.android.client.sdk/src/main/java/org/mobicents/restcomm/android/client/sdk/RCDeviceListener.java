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

import org.mobicents.restcomm.android.client.sdk.RCDevice;

import java.util.HashMap;

/**
 *  RCDevice notifies its listener for RCDevice related events defined in this interface
 */
public interface RCDeviceListener {
    /**
     *  RCDevice started listening for incoming connections (<b>Not Implemented yet</b>)
     *
     *  @param device Device of interest
     */
    public abstract void onStartListening(RCDevice device);

    /**
     *  RCDevice stopped listening for incoming connections (<b>Not Implemented yet</b>)
     *
     *  @param device Device of interest
     */
    public abstract void onStopListening(RCDevice device);

    /**
     *  RCDevice stopped listening for incoming connections due to error(<b>Not Implemented yet</b>)
     *
     *  @param device Device of interest
     *  @param errorCode  Error code
     *  @param errorText  Error text
     */
    public abstract void onStopListening(RCDevice device, int errorCode, String errorText);

    /**
     *  RCDevice received incoming connection
     *
     *  @param device     Device of interest
     *  @param connection Newly established connection
     */
    public abstract void onIncomingConnection(RCDevice device, RCConnection connection);

    /**
     * Text message received
     * @param device  Device of interest
     * @param message  Tex message
     * @param parameters  Parameters, such as 'username' designating the username who sent the message
     */
    public abstract void onIncomingMessage(RCDevice device, String message, HashMap<String, String> parameters);

    /**
     *  Called to query whether the application wants to retrieve presence events. Return false to indicate that the application isn't interested (<b>Not implemented yet</b>)
     *
     *  @param device  Device of interest
     */
    public abstract boolean receivePresenceEvents(RCDevice device);

    /**
     * Called when the presence status has changed (<b>Not implemented yet</b>)
     * @param device  Device of interest
     * @param presenceEvent  Presence Event
     */
    public abstract void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent);
}
