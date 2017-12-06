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

public class FcmBinding implements FcmModelInterface {

    private static final String TAG = FcmBinding.class.getCanonicalName();

    private String sid;
    private String identity;
    private String applicationSid;
    private String bindingType;
    private String address;

    public FcmBinding(){}

    public FcmBinding(String sid, String identity, String applicationSid, String bindingType, String address){
        this.sid = sid;
        this.identity = identity;
        this.applicationSid = applicationSid;
        this.bindingType = bindingType;
        this.address = address;
    }

    public String getSid() {
        return sid;
    }

    public String getIdentity() {
        return identity;
    }

    public String getApplicationSid() {
        return applicationSid;
    }

    public String getBindingType() {
        return bindingType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sid", this.sid);
            jsonObject.put("identity", this.identity);
            jsonObject.put("applicationSid", this.applicationSid);
            jsonObject.put("bindingType", this.bindingType);
            jsonObject.put("address", this.address);
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
            String identity = jsonObject.getString("identity");
            String applicationSid = jsonObject.getString("applicationSid");
            String bindingType = jsonObject.getString("bindingType");
            String address = jsonObject.getString("address");
            this.sid = sid;
            this.identity = identity;
            this.applicationSid = applicationSid;
            this.bindingType = bindingType;
            this.address = address;
        } catch (JSONException e) {
            RCLogger.e(TAG, e.toString());
        }
    }
}
