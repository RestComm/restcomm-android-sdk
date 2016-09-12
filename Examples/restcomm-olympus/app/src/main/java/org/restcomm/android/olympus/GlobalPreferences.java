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

import android.content.Context;
import android.content.SharedPreferences;

/*
 * Here we store global preferences, that don't reside in the SettingsFragment
 */
public class GlobalPreferences {
   private static final String PREFS_NAME = "general-prefs.xml";
   private static final String PREFS_SIGNED_UP_KEY = "user-signed-up";
   private static final String PREFS_EXTERNAL_CALL_URI = "external-call-uri";

   SharedPreferences prefsGeneral = null;
   private Context context;

   GlobalPreferences(Context context)
   {
      this.context = context;
      prefsGeneral = context.getSharedPreferences(PREFS_NAME, 0);
   }

   boolean haveSignedUp()
   {
      return prefsGeneral.getBoolean(PREFS_SIGNED_UP_KEY, false);
   }

   void setSignedUp(boolean signedUp)
   {
      SharedPreferences.Editor prefEdit = prefsGeneral.edit();
      prefEdit.putBoolean(PREFS_SIGNED_UP_KEY, signedUp);
      prefEdit.apply();
   }

   String getExternalCallUri()
   {
      return prefsGeneral.getString(PREFS_EXTERNAL_CALL_URI, "");
   }

   void setExternalCallUri(String callUri)
   {
      SharedPreferences.Editor prefEdit = prefsGeneral.edit();
      prefEdit.putString(PREFS_EXTERNAL_CALL_URI, callUri);
      prefEdit.apply();
   }
}
