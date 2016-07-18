package org.mobicents.restcomm.android.client.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

class NotificationManager extends BroadcastReceiver {

   // Define NotificationManager events
   public interface NotificationManagerListener {
      void onConnectivityChange(ConnectivityChange connectivityChange);
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
   static final String TAG = "NotificationManager";
   RCDeviceListener.RCConnectivityStatus connectivityStatus;


   NotificationManager(Context androidContext, Handler handler, NotificationManagerListener listener)
   {
      this.androidContext = androidContext;
      this.listener = listener;
      IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      // initialize current connectivity status
      connectivityStatus = checkConnectivity(androidContext);
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
      RCDeviceListener.RCConnectivityStatus newConnectivityStatus = checkConnectivity(context);

      if (newConnectivityStatus == connectivityStatus) {
         RCLogger.w(TAG, "Connectivity event, but remained the same: " + newConnectivityStatus);
         return;
      }

      if (newConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         RCLogger.w(TAG, "Connectivity event; lost connectivity from: " + connectivityStatus);
         connectivityChange = ConnectivityChange.OFFLINE;
      }

      // old state wifi and new state mobile or the reverse; need to shutdown and restart network facilities
      if (connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi &&
            newConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular) {
         RCLogger.w(TAG, "Connectivity event: switch from wifi to cellular data");
         connectivityChange = ConnectivityChange.WIFI_TO_CELLULAR_DATA;
      }


      if (connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular &&
            newConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi) {
         RCLogger.w(TAG, "Connectivity event: switch from cellular data to wifi");
         connectivityChange = ConnectivityChange.CELLULAR_DATA_TO_WIFI;
      }

      if (connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone &&
            newConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi) {
         RCLogger.w(TAG, "Connectivity event: wifi available");
         connectivityChange = ConnectivityChange.OFFLINE_TO_WIFI;
      }

      if (connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone &&
            newConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular) {
         RCLogger.w(TAG, "Connectivity event: cellular data available");
         connectivityChange = ConnectivityChange.OFFLINE_TO_CELLULAR_DATA;
      }

      // update current connectivity status
      connectivityStatus = newConnectivityStatus;

      // notify listener of the change
      listener.onConnectivityChange(connectivityChange);
   }

   static public RCDeviceListener.RCConnectivityStatus checkConnectivity(Context context)
   {
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
      if (activeNetwork != null) {
         if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected()) {
            RCLogger.w(TAG, "Connectivity event: WIFI");
            return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi;
         }

         if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE && activeNetwork.isConnected()) {
            Log.w(TAG, "Connectivity event: CELLULAR DATA");
            return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular;
         }
      }
      RCLogger.w(TAG, "Connectivity event: NONE");
      return RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
   }

   boolean haveConnectivity()
   {
      if (connectivityStatus != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         return true;
      }
      else {
         return false;
      }
   }

   RCDeviceListener.RCConnectivityStatus getConnectivityStatus()
   {
      return connectivityStatus;
   }
}
