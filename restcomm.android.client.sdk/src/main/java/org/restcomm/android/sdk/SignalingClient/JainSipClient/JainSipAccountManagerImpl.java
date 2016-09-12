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

import android.gov.nist.javax.sip.clientauthutils.AccountManager;
import android.gov.nist.javax.sip.clientauthutils.UserCredentials;

import android.javax.sip.ClientTransaction;


public class JainSipAccountManagerImpl implements AccountManager {

   String Username;
   String Password;
   String RemoteIp;

   public JainSipAccountManagerImpl(String username, String RemoteIp, String password)
   {
      this.Username = username;
      this.Password = password;
      this.RemoteIp = RemoteIp;

   }

   public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm)
   {
      return new JainSipUserCredentialsImpl(Username, RemoteIp, Password);
   }
}