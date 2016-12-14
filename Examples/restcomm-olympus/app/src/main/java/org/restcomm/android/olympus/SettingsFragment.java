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

package org.restcomm.android.olympus;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;

import org.restcomm.android.sdk.RCDevice;

public class SettingsFragment extends PreferenceFragment {
   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);

      // Load the preferences from an XML resource
      addPreferencesFromResource(R.xml.preferences);

      // setup listener to be used by all EditTextPreferences
      Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            Boolean rtnval = true;

            // don't touch non string preferences and just return true
            if (!(newValue instanceof String)) {
               return true;
            }

            String value = (String)newValue;
            if (value.contains(" ")) {
               final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
               builder.setTitle(preference.getTitle() + " validation error");
               builder.setMessage("Should not contain space(s); edit was canceled");
               builder.setPositiveButton(android.R.string.ok, null);
               builder.show();
               rtnval = false;
            }
            return rtnval;
         }
      };

      getPreferenceScreen().findPreference(RCDevice.ParameterKeys.SIGNALING_USERNAME).setOnPreferenceChangeListener(listener);
      getPreferenceScreen().findPreference(RCDevice.ParameterKeys.SIGNALING_DOMAIN).setOnPreferenceChangeListener(listener);

      /*
      pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            Boolean rtnval = true;

            // don't touch non string preferences and just return true
            if (!(newValue instanceof String)) {
               return true;
            }

            String value = (String)newValue;
            if (value.startsWith(" ") || value.endsWith(" ")) {
               //value = value.trim();
               final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
               builder.setTitle("Validation Error");
               builder.setMessage("String preference should not start or end with space. Please edit it again");
               builder.setPositiveButton(android.R.string.ok, null);
               builder.show();
               rtnval = false;
            }
            return rtnval;
         }
      });
      */
   }

}