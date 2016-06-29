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

import android.javax.sip.TransactionUnavailableException;

import java.text.ParseException;
import java.util.HashMap;

public interface ISipManager {

	public void SendMessage(String to, String message) throws NotInitializedException;
	public void SendDTMF(String digit) throws NotInitializedException;
	public void Register(int expiry) throws ParseException, TransactionUnavailableException;
	public void Call(String to, int localRtpPort, HashMap<String, String> sipHeaders) throws NotInitializedException, ParseException;
	public void CallWebrtc(String to, String sdp, HashMap<String, String> sipHeaders) throws NotInitializedException, ParseException;
	public void Hangup() throws NotInitializedException;
}
