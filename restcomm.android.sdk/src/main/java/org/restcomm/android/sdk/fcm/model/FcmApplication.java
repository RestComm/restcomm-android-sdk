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

package org.restcomm.android.sdk.fcm.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.restcomm.android.sdk.util.RCLogger;

public class FcmApplication implements FcmModelInterface {

    private static final String TAG = FcmApplication.class.getCanonicalName();

    private String sid;
    private String friendlyName;

    public FcmApplication(){}

    public FcmApplication(String sid, String friendlyName){
        this.sid = sid;
        this.friendlyName = friendlyName;
    }

    public String getSid() {
        return sid;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sid", this.sid);
            jsonObject.put("friendlyName", this.friendlyName);
        } catch (JSONException e) {
            RCLogger.e(TAG, e.toString());
        }
        return jsonObject;
    }

    @Override
    public void fillFromJson(String jsonString) {
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonString);
            String sid = jsonObject.getString("sid");
            String name = jsonObject.getString("friendlyName");
            this.sid = sid;
            this.friendlyName = name;
        } catch (JSONException e) {
            RCLogger.e(TAG, e.toString());
        }
    }
}
