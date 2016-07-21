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

/*
import org.mobicents.restcomm.android.client.sdk.SignalingClient.UIClient;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

import java.util.HashMap;
import java.util.Map;

// ------ RCMessage is not used yet, we 'll use it when we introduce the new messaging API


// RCMessage represents a text message.
public class RCMessage implements UIClient.UIMessageListener {

   // Message State
   public enum MessageState {
      PENDING, // Text message is in state pending
      SENT, // Text message is in state sent
   }

   // State of the text message
   MessageState state;

   // Direction of the text message. True if text message is incoming; false otherwise
   boolean incoming;

   // Message parameters
   public HashMap<String, Object> parameters; // TODO: remove public

   // Listener that will be called on RCMessage events described at RCMessageListener
   RCMessageListener listener;

   //public String id;
   //private UIClient uiClient;
   private static final String TAG = "RCMessage";

   public RCMessage(HashMap<String, Object> parameters, boolean incoming, MessageState state, RCMessageListener listener)
   {
      this.parameters = parameters;
      this.incoming = incoming;
      this.state = state;
      //this.uiClient = uiClient;
      this.listener = listener;
   }

   // provide static factory method
   public static RCMessage newInstanceOutgoing(HashMap<String, Object> parameters, RCMessageListener listener)
   {
      return new RCMessage(parameters, false, MessageState.PENDING, listener);
   }

   public void onMessageSentEvent(String id)
   {
      RCLogger.i(TAG, "onMessageSentEvent(): id: " + id);

      listener.onSent(this);
   }
}
*/
