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

/**
 * RCDevice notifies its listener for RCDevice related events defined in this interface
 */
public interface RCDeviceListener {
   enum RCConnectivityStatus {
      RCConnectivityStatusNone,  // no restcomm connectivity either we have no internet connectivity or couldn't register to restcomm (or both)
      RCConnectivityStatusWiFi,  // restcomm reachable and online via Wifi (or if in registrarless mode we don't register with restcomm; we just know that we have internet connectivity)
      RCConnectivityStatusCellular,  // restcomm reachable and online via cellular (same as above for registraless)
   }

   /**
    * RCDevice initialized successfully
    *
    * @param device Device of interest
    * @param connectivityStatus Connectivity status
    * @param statusCode Status code
    * @param statusText Status text
    */
   void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText);

   /**
    *  RCDevice failed to initialize
    *
    *  @param errorCode Error code for the error
    *  @param errorText Error text for the error
    */
   void onInitializationError(int errorCode, String errorText);

   /**
    * RCDevice started listening for incoming connections
    *
    * @param device Device of interest
    * @param connectivityStatus Connectivity status when started listening
    */
   void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus);

   /**
    * RCDevice stopped listening for incoming connections
    *
    * @param device Device of interest
    */
   void onStopListening(RCDevice device);

   /**
    * RCDevice was released
    *
    * @param device Device of interest
    * @param statusCode Error code for the error
    * @param statusText Error text for the error
    */
   void onReleased(RCDevice device, int statusCode, String statusText);

   /**
    * RCDevice text message has been sent
    *
    * @param device Device of interest
    * @param statusCode Status code
    * @param statusText Status text
    */
   void onMessageSent(RCDevice device, int statusCode, String statusText);

   /**
    * RCDevice stopped listening for incoming connections due to error
    *
    * @param device    Device of interest
    * @param errorCode Error code
    * @param errorText Error text
    */
   void onStopListening(RCDevice device, int errorCode, String errorText);

   /**
    * RCDevice connectivity status has been updated
    *
    * @param device             Device of interest
    * @param connectivityStatus Connectivity status of Device
    */
   void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus);

   /**
    *  RCDevice received incoming connection
    *
    *  @param device     Device of interest
    *  @param connection Newly established connection
    */
   //void onIncomingConnection(RCDevice device, RCConnection connection);

   /**
    * Text message received
    * @param device  Device of interest
    * @param message  Tex message
    * @param parameters  Parameters, such as 'username' designating the username who sent the message
    */
   //void onIncomingMessage(RCDevice device, String message, HashMap<String, String> parameters);

   /**
    * Called to query whether the application wants to retrieve presence events. Return false to indicate that the application isn't interested (<b>Not implemented yet</b>)
    *
    * @param device Device of interest
    */
   boolean receivePresenceEvents(RCDevice device);

   /**
    * Called when the presence status has changed (<b>Not implemented yet</b>)
    *
    * @param device        Device of interest
    * @param presenceEvent Presence Event
    */
   void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent);
}
