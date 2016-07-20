package org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;


class JainSipNotificationManager extends BroadcastReceiver {

   // Define JainSipNotificationManager events
   public interface NotificationManagerListener {
      void onConnectivityChange(ConnectivityChange connectivityChange);
   }

   enum NetworkStatus {
      NetworkStatusNone,
      NetworkStatusWiFi,
      NetworkStatusCellular,
   }

   public enum ConnectivityChange {
      OFFLINE,
      OFFLINE_TO_WIFI,
      OFFLINE_TO_CELLULAR_DATA,
      WIFI_TO_CELLULAR_DATA,
      CELLULAR_DATA_TO_WIFI,
   }

   Context androidContext;
   NotificationManagerListener listener;
   static final String TAG = "JainSipNotificationManager";
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

      if (newConnectivityStatus == networksStatus) {
         RCLogger.w(TAG, "Connectivity event, but remained the same: " + newConnectivityStatus);
         return;
      }

      if (newConnectivityStatus == NetworkStatus.NetworkStatusNone) {
         RCLogger.w(TAG, "Connectivity event; lost connectivity from: " + networksStatus);
         connectivityChange = ConnectivityChange.OFFLINE;
      }

      // old state wifi and new state mobile or the reverse; need to shutdown and restart network facilities
      if (networksStatus == NetworkStatus.NetworkStatusWiFi &&
            newConnectivityStatus == NetworkStatus.NetworkStatusCellular) {
         RCLogger.w(TAG, "Connectivity event: switch from wifi to cellular data");
         connectivityChange = ConnectivityChange.WIFI_TO_CELLULAR_DATA;
      }


      if (networksStatus == NetworkStatus.NetworkStatusCellular &&
            newConnectivityStatus == NetworkStatus.NetworkStatusWiFi) {
         RCLogger.w(TAG, "Connectivity event: switch from cellular data to wifi");
         connectivityChange = ConnectivityChange.CELLULAR_DATA_TO_WIFI;
      }

      if (networksStatus == NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusWiFi) {
         RCLogger.w(TAG, "Connectivity event: wifi available");
         connectivityChange = ConnectivityChange.OFFLINE_TO_WIFI;
      }

      if (networksStatus == NetworkStatus.NetworkStatusNone &&
            newConnectivityStatus == NetworkStatus.NetworkStatusCellular) {
         RCLogger.w(TAG, "Connectivity event: cellular data available");
         connectivityChange = ConnectivityChange.OFFLINE_TO_CELLULAR_DATA;
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
      else {
         return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular;
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
