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

package org.restcomm.android.sdk.SignalingClient;

import android.os.Handler;
import android.os.HandlerThread;

// SignalingHandlerThread encapsulates the signaling thread (separate from UI thread) and facilitates asynchronous communication between he two.
// It installs an Android Handler and takes care of all signaling actions from UI thread -> JainSipClient and all responses/events from JainSipClient -> UI thread
class SignalingHandlerThread extends HandlerThread {
   Handler signalingHandler;
   private static final String TAG = "SignalingHandlerThread";
   //Handler uiHandler;

   SignalingHandlerThread(SignalingClient uiHandler)
   {
      super("signaling-handler-thread");
      //this.uiHandler = uiHandler;

      start();
      signalingHandler = new SignalingHandler(this.getLooper(), uiHandler);
   }

   Handler getHandler()
   {
      return signalingHandler;
   }

    /*
    public void start()
    {
        super.start();
    }
    */
}
