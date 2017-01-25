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

package org.restcomm.android.sdk.SignalingClient.JainSipClient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCLogger;

/**
 * JainSipNotificationManager listens for Android connectivity changes and notifies NotificationManagerListener (typically JainSipClient)
 */
class JainSipNotificationManager extends BroadcastReceiver {

   // Define JainSipNotificationManager events
   public interface NotificationManagerListener {
      void onConnectivityChange(ConnectivityChange connectivityChange);
   }

   enum NetworkStatus {
      NetworkStatusNone,
      NetworkStatusWiFi,
      NetworkStatusCellular,
      NetworkStatusEthernet,
   }

   public enum ConnectivityChange {
      OFFLINE,
      OFFLINE_TO_WIFI,
      OFFLINE_TO_CELLULAR_DATA,
      OFFLINE_TO_ETHERNET,
      HANDOVER_TO_CELLULAR_DATA,
      HANDOVER_TO_WIFI,
      HANDOVER_TO_ETHERNET,
   }

   Context androidContext;
   NotificationManagerListener listener;
   static final String TAG = "NotificationManager";
   NetworkStatus networksStatus;


   JainSipNotificationManager(Context androidContext, Handler handler, NotificationManagerListener listener)
   {
      this.androidContext = androidContext;
      this.listener = listener;
      IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      // initialize current connectivity status
      networksStatus = checkConnectivity(androidContext);
      // register for connectivity related events
      androidContext.registerReceiver(this, filter, null, handler);
   }

   public void close()
   {
      // unregister from events
      androidContext.unregisterReceiver(this);
   }

   @Override
   public void onReceive(Context context, Intent intent)
   {
      ConnectivityChange connectivityChange = ConnectivityChange.OFFLINE;

      // retrieve new connectivity status
      NetworkStatus newConnectivityStatus = checkConnectivity(context);
      /*
      try {
         Thread.sleep(4000);
      }
      catch (InterruptedException e) {

      }
      */

      if (newConnectivityStatus == networksStatus) {
         RCLogger.w(TAG, "Connectivity change, but remained the same: " + newConnectivityStatus);
         return;
      }

      if (newConnectivityStatus == NetworkStatus.NetworkStatusNone) {
         //RCLogger.w(TAG, "Connectivity event; lost connectivity from: " + networksStatus);
         connectivityChange = ConnectivityChange.OFFLINE;
      }

      RCLogger.w(TAG, "Connectivity change from: " + networksStatus + " to: " + newConnectivityStatus);

      // old state wifi and new state cellular or the reverse; need to shutdown and restart network facilities
      if (networksStatus != NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusCellular) {
         connectivityChange = ConnectivityChange.HANDOVER_TO_CELLULAR_DATA;
      }

      if (networksStatus != NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusWiFi) {
         connectivityChange = ConnectivityChange.HANDOVER_TO_WIFI;
      }

      if (networksStatus != NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusEthernet) {
         connectivityChange = ConnectivityChange.HANDOVER_TO_ETHERNET;
      }

      if (networksStatus == NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusWiFi) {
         connectivityChange = ConnectivityChange.OFFLINE_TO_WIFI;
      }

      if (networksStatus == NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusCellular) {
         connectivityChange = ConnectivityChange.OFFLINE_TO_CELLULAR_DATA;
      }

      if (networksStatus == NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusEthernet) {
         connectivityChange = ConnectivityChange.OFFLINE_TO_ETHERNET;
      }

      // update current connectivity status
      networksStatus = newConnectivityStatus;

      // notify listener of the change
      listener.onConnectivityChange(connectivityChange);
   }

   static public NetworkStatus checkConnectivity(Context context)
   {
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
      if (activeNetwork != null) {
         if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected()) {
            RCLogger.w(TAG, "Connectivity event: WIFI");
            return NetworkStatus.NetworkStatusWiFi;
         }

         if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE && activeNetwork.isConnected()) {
            Log.w(TAG, "Connectivity event: CELLULAR DATA");
            return NetworkStatus.NetworkStatusCellular;
         }

         if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET && activeNetwork.isConnected()) {
            Log.w(TAG, "Connectivity event: ETHERNET");
            return NetworkStatus.NetworkStatusCellular;
         }
      }
      RCLogger.w(TAG, "Connectivity event: NONE");
      return NetworkStatus.NetworkStatusNone;
   }

   // convert from NetworkStatus -> ConnectivityStatus
   static public RCDeviceListener.RCConnectivityStatus networkStatus2ConnectivityStatus(NetworkStatus networkStatus)
   {
      if (networkStatus == NetworkStatus.NetworkStatusNone) {
         return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
      }
      else if (networkStatus == NetworkStatus.NetworkStatusWiFi) {
         return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi;
      }
      else if (networkStatus == NetworkStatus.NetworkStatusCellular) {
         return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular;
      }
      else {
         return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusEthernet;
      }
   }

   boolean haveConnectivity()
   {
      if (networksStatus != NetworkStatus.NetworkStatusNone) {
         return true;
      }
      else {
         return false;
      }
   }

   NetworkStatus getNetworkStatus()
   {
      return networksStatus;
   }
}
