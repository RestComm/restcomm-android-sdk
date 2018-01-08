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

package org.restcomm.android.sdk.util;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDeviceListener;

/**
 *  Class for external context provided by RegistrationFsm users (i.e. RCDevice)
 */
public class RegistrationFsmContext {
    RCDeviceListener.RCConnectivityStatus connectivityStatus;
    RCClient.ErrorCodes status;
    String text;

    public RegistrationFsmContext(RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
    {
        this.connectivityStatus = connectivityStatus;
        this.status = status;
        this.text = text;
    }

    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder();
        result.append(connectivityStatus).append(", ").append(status).append(", ").append(text);
        return result.toString();
    }
}
