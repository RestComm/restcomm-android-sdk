/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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

package org.restcomm.android.sdk.storage;

import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

/**
 *  Manage common Storage actions like save, get params
 */
public class StorageUtils {

    /**
     * Saves hash map key value pairs into Store Manager
     * @param storageManagerInterface StorageManagerInterface instance
     * @param params parameters
     */
    public static void saveParams(StorageManagerInterface storageManagerInterface, HashMap<String, Object> params){
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String){
                storageManagerInterface.saveString(key, (String) value);
            } else if (value instanceof Integer) {
                storageManagerInterface.saveInt(key, (int) value);
            } else if (value instanceof Enum){
                storageManagerInterface.saveInt(key, ((Enum) value).ordinal());
            } else if (value instanceof Boolean){
                storageManagerInterface.saveBoolean(key, (boolean)value);
            } else if (value instanceof Intent){
                storageManagerInterface.saveString(key,((Intent) value).toUri(Intent.URI_INTENT_SCHEME));
            }
        }
    }

    /**
     * Returns the parameters from the Storage Manager as hash map
     * @param storageManagerInterface StorageManagerInterface instance
     * @return HashMap
     */
    public static HashMap<String, Object> getParams(StorageManagerInterface storageManagerInterface){
        return new HashMap<>(storageManagerInterface.getAllEntries());
    }
}
