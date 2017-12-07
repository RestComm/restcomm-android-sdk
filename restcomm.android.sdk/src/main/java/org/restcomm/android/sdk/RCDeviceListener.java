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
      RCConnectivityStatusNone,  /** no restcomm connectivity either we have no internet connectivity or couldn't register to restcomm (or both) */
      RCConnectivityStatusWiFi,  /** restcomm reachable and online via Wifi (or if in registrarless mode we don't register with restcomm; we just know that we have internet connectivity) */
      RCConnectivityStatusCellular,  /** restcomm reachable and online via cellular (same as above for registraless) */
      RCConnectivityStatusEthernet,  /** restcomm reachable and online via ethernet (same as above for registraless) */
   }

   /**
    * RCDevice initialized either successfully or with error (check statusCode and statusText). For regular scenarios (i.e. non-registrarless) success
    * means that this means that registration is successful. For registrar-less scenarios success means that RCDevice is initialized properly (with no registration)
    *
    * @param device Device of interest
    * @param connectivityStatus Connectivity status
    * @param statusCode Status code
    * @param statusText Status text
    */
   void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText);

   /**
    * RCDevice started listening for incoming connections. This occurs when we asynchronously receive the result from RCDevice.updateParams() and it was successful, which means that we registered successfully
    *
    * @param device Device of interest
    * @param connectivityStatus Connectivity status when started listening
    */
   void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus);

   /**
    * RCDevice was released as a result of calling RCDevice.release()
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
    * @param jobId In order to correlate this event to the RCDevice.sendMessage() it corresponds to we use the jobId
    */
   void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId);

   /**
    * RCDevice stopped listening for incoming connections (either as a result of correct processing, or error). This occurs when:
    * a. RCDevice.updateParams() just sent out the registration request (we need that to convey to the UI that we are offline for a bit, until we get a response to the registrations),
    * b. RCDevice.updateParams() registration failed or
    * c. A periodic registration refresh failed in which case we need to notify the UI that we are offline
    *
    * @param device    Device of interest
    * @param statusCode Status code
    * @param statusText Status text
    */
   void onStopListening(RCDevice device, int statusCode, String statusText);

   /**
    * RCDevice connectivity status has been updated, like losing internet connectivity, or regaining internet connectivity
    *
    * @param device             Device of interest
    * @param connectivityStatus Connectivity status of Device
    */
   void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus);


   /**
    * RCDevice warning has been raised, check statusCode and statusText
    * @param statusCode Status code
    * @param statusText Status text
    */
   void onWarning(int statusCode,  String statusText);

   /**
    *  RCDevice failed to initialize
    *
    *  @param errorCode Error code for the error
    *  @param errorText Error text for the error
    */
   //void onInitializationError(int errorCode, String errorText);

   /**
    * RCDevice stopped listening for incoming connections.
    *
    * @param device Device of interest
    */
   //void onStopListening(RCDevice device);

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
    * Commenting these out to avoid uneeded boilerplate code for developers, until we implement this that is
    * @param device Device of interest
    * @return placeholder for now
    */
   //boolean receivePresenceEvents(RCDevice device);

   /**
    * Called when the presence status has changed (<b>Not implemented yet</b>)
    * Commenting these out to avoid uneeded boilerplate code for developers, until we implement this that is
    * @param device        Device of interest
    * @param presenceEvent Presence Event
    */
   //void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent);
}
