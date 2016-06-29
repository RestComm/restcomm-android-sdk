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

package org.mobicents.restcomm.android.sipua;

	public enum SipManagerState {
        /**
         * This state is used while the SipManager tries to register the SIP account with the provider.
         */
        REGISTERING,
        /**
         * This state is used while the SipManager tries to unregister the SIP account from the provider.
         */
        UNREGISTERING, 
        /**
         * This state is used after the SipManager has successfully registered the SIP account with the provider.
         */
        READY,
        /**
         * This state is used when the SipManager the called contact's UA is signalling an incoming call to the user.
         */
        RINGING,
        /**
         * This state is used while the call is being established.
         */
        CALLING,
        /**
         * This state is used when the call is established.
         */
        ESTABLISHED,

        /**
         * This state is used when the SipManager is initialized.
         */
        IDLE,
        /**
         * This state is used when a call is incoming.
         */
        INCOMING,
        /**
         * This state is used when a signalling timeout occurred.
         */
        TIMEOUT,
        /**
         * This state is used when an error occurred.
         */
        ERROR,
        /**
         * This state is used when a contact is busy.
         */
        BUSY,
        /**
         * This state is used when the remote contact declined the call.
         */
        DECLINED,
        /**
         * This state is used when a remote contact is invalid.
         */
        INVALID,
        /**
         * This state is used when a remote contact ended the call.
         */
        BYE,
        /**
          * This state is used when a remote contact ended the call.
          */
        STACK_STOPPED,
}

