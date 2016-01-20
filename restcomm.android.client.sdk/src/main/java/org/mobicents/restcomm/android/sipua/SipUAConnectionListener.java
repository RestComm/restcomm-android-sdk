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

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

public interface SipUAConnectionListener {
    // fires when outgoing call starts ringing
    public abstract void onSipUAConnecting(SipEvent event);
    // fires either when incoming or outgoing call is established
    public abstract void onSipUAConnected(SipEvent event);
    // fires either when we get an incoming BYE request or when we get a response to our outgoing BYE request
    public abstract void onSipUADisconnected(SipEvent event);
    // fires when we get an incoming CANCEL request
    public abstract void onSipUACancelled(SipEvent event);
    // fires when we get an incoming 'declined' response to our INVITE
    public abstract void onSipUADeclined(SipEvent event);
    // fires when we get a signalling error
    public abstract void onSipUAError(final RCClient.ErrorCodes errorCode, final String errorText);
}
