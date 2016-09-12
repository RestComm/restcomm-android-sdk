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

package org.restcomm.android.sdk;

import java.util.HashMap;

public interface RCConnectionListener {
   /**
    * New outgoing connection is trying to connect
    *
    * @param connection Connection
    */
   void onConnecting(RCConnection connection);

   /**
    * Connection just connected. This can be either an incoming or outgoing connection
    *
    * @param connection Connection
    * @param customHeaders Custom SIP headers from Restcomm. Applicable only in outbound calls -for incoming calls customHeaders are found at the incoming call Intent
    */
   void onConnected(RCConnection connection, HashMap<String, String> customHeaders);

   /**
    * Established connection just got disconnected. Can be either incoming or outgoing connection
    *
    * @param connection Connection
    */
   void onDisconnected(RCConnection connection);

   /**
    * DTMF digit successfully sent
    *
    * @param connection Connection
    * @param statusCode  Status Code
    * @param statusText  Status Text
    */
   void onDigitSent(RCConnection connection, int statusCode, String statusText);

   /**
    * Incoming connection was just cancelled by remote party
    *
    * @param connection Connection
    */
   void onCancelled(RCConnection connection);

   /**
    * Incoming connection was just declined by remote party
    *
    * @param connection Connection
    */
   void onDeclined(RCConnection connection);

   /**
    * Connection just disconnected with an error
    *
    * @param connection Connection
    * @param errorCode  Error Code
    * @param errorText  Error Text
    */
   void onDisconnected(RCConnection connection, int errorCode, String errorText);

   /**
    * Connection just encountered an error (non-disconnect)
    *
    * @param connection Connection
    * @param errorCode  Error Code
    * @param errorText  Error Text
    */
   void onError(RCConnection connection, int errorCode, String errorText);

   /**
    * Local webrtc video is received (i.e. video stream from the local camera). If user hasn't used video for the call then this event
    * doens't occur
    *
    * @param connection Connection
    */
   void onLocalVideo(RCConnection connection);

   /**
    * Remote webrtc video is received (i.e. video stream from the peer's camera). If peers hasn't used video for the call then this event
    * doens't occur    *
    * @param connection Connection
    */
   void onRemoteVideo(RCConnection connection);

}

