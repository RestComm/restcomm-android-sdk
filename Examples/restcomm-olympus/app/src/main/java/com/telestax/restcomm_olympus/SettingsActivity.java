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

package com.telestax.restcomm_olympus;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;

import java.util.HashMap;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    SharedPreferences prefs;
    HashMap<String, Object> params;
    RCDevice device;
    boolean updated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar) findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(R.id.content_frame, new SettingsFragment()).commit();

        device = RCClient.listDevices().get(0);
        params = new HashMap<String, Object>();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    protected void onResume() {
        super.onResume();

        updated = false;
        // retrieve the device
        //RCDevice device = RCClient.listDevices().get(0);

        if (device.getState() == RCDevice.DeviceState.OFFLINE) {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        } else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (updated) {
                if (!device.updateParams(params)) {
                    // TODO:
                    //showOkAlert("RCDevice Error", "No Wifi connectivity");
                }
            }

            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals("pref_proxy_domain")) {
            params.put("pref_proxy_domain", prefs.getString("pref_proxy_domain", "sip:cloud.restcomm.com:5060"));
            updated = true;
        }
        else if (key.equals("pref_sip_user")) {
            params.put("pref_sip_user", prefs.getString("pref_sip_user", "android-sdk"));
            updated = true;
        }
        else if (key.equals("pref_sip_password")) {
            params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
            updated = true;
        }
        else if (key.equals("turn-enabled")) {
            params.put("turn-enabled", prefs.getBoolean("turn-enabled", true));
            updated = true;
        }
        else if (key.equals("turn-url")) {
            params.put("turn-url", prefs.getString("turn-url", ""));
            updated = true;
        }
        else if (key.equals("turn-username")) {
            params.put("turn-username", prefs.getString("turn-username", ""));
            updated = true;
        }
        else if (key.equals("turn-password")) {
            params.put("turn-password", prefs.getString("turn-password", ""));
            updated = true;
        }
        else if (key.equals("signaling-secure")) {
            params.put("signaling-secure", prefs.getBoolean("signaling-secure", false));
            updated = true;
        }
    }
}