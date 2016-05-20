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

import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.webrtc.VideoTrack;

public interface RCConnectionListener {
    /**
     * New outgoing connection is trying to connect
     * @param connection Connection
     */
    public abstract void onConnecting(RCConnection connection);

    /**
     * Connection just connected. This can be either an incoming or outgoing connection
     * @param connection Connection
     */
    public abstract void onConnected(RCConnection connection);

    /**
     * Established connection just got disconnected. Can be either incoming or outgoing connection
     * @param connection Connection
     */
    public abstract void onDisconnected(RCConnection connection);

    /**
     * Incoming connection was just cancelled by remote party
     * @param connection Connection
     */
    public abstract void onCancelled(RCConnection connection);

    /**
     * Incoming connection was just declined by remote party
     * @param connection Connection
     */
    public abstract void onDeclined(RCConnection connection);

    /**
     * Connection just disconnected with an error
     * @param connection Connection
     * @param errorCode Error Code
     * @param errorText Error Text
     */
    public abstract void onDisconnected(RCConnection connection, int errorCode, String errorText);

    /**
     * Connection just disconnected with an error
     * @param connection Connection
     * @param videoTrack View hosting the local video
     */
    public void onReceiveLocalVideo(RCConnection connection, VideoTrack videoTrack);

    /**
     * Connection just disconnected with an error
     * @param connection Connection
     * @param videoTrack Video track hosting the remote video
     */
    public void onReceiveRemoteVideo(RCConnection connection, VideoTrack videoTrack);
}

