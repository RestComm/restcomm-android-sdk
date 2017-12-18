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

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class RCLogger {
    private static int ALL = 8;
    private static int globalLevel = Log.ERROR;

    public static void setLogLevel(int level)
    {
        globalLevel = level;
    }

    public static int getLogLevel()
    {
        return globalLevel;
    }

    public static boolean isVerboseEnabled()
    {
        return globalLevel <= Log.VERBOSE || globalLevel == 8;
    }

    public static boolean isDebugEnabled()
    {
        return globalLevel <= Log.DEBUG || globalLevel == 8;
    }

    public static boolean isInfoEnabled()
    {
        return globalLevel <= Log.INFO || globalLevel == 8;
    }

    public static boolean isWarnEnabled()
    {
        return globalLevel <= Log.WARN || globalLevel == 8;
    }

    public static boolean isErrorEnabled()
    {
        return globalLevel <= Log.ERROR || globalLevel == 8;
    }

    public static boolean isAssertEnabled()
    {
        return globalLevel <= Log.ASSERT || globalLevel == 8;
    }

    public static void v(String tag, String msg, Throwable t)
    {
        if (RCLogger.isVerboseEnabled()) {
            Log.v(tag, filter(msg), t);
        }
    }

    public static void v(String tag, String msg)
    {
        if (RCLogger.isVerboseEnabled()) {
            /*
            if (!Log.isLoggable(tag, Log.VERBOSE)) {
                RCLogger.i(tag, msg);
            }
            */
            Log.v(tag, RCLogger.filter(msg));
        }
    }

    public static void d(String tag, String msg, Throwable t)
    {
        if (RCLogger.isDebugEnabled()) {
            /*
            if (!Log.isLoggable(tag, Log.DEBUG)) {
                Log.i(tag, msg);
            }
            */

            Log.d(tag, filter(msg), t);
        }
    }

    public static void d(String tag, String msg)
    {
        if (RCLogger.isDebugEnabled()) {
            Log.d(tag, filter(msg));
        }
    }

    public static void i(String tag, String msg, Throwable t)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, filter(msg), t);
        }
    }

    public static void i(String tag, String msg)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, filter(msg));
        }
    }

    public static void w(String tag, String msg, Throwable t)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, filter(msg), t);
        }
    }

    public static void w(String tag, String msg)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, filter(msg));
        }
    }

    public static void e(String tag, String msg, Throwable t)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, filter(msg), t);
        }
    }

    public static void e(String tag, String msg)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, filter(msg));
        }
    }

    public static void wtf(String tag, String msg, Throwable t)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, filter(msg), t);
        }
    }

    public static void wtf(String tag, String msg)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, filter(msg));
        }
    }

   /**
    * Filter logs for sensitive information and also some other needed stuff
    * @param msg Input string
    * @return Filtered string
    */
    private static String filter(String msg)
    {
        // Remove sensitive information. If we want to add more sensitive data, here's where we need to filter it
        msg = msg.replaceAll("turn-password=.*?, ", "turn-password=, ")  // turn password filtering
              .replaceAll("pref_sip_password=.*?, ", "pref_sip_password=, ")  // SIP password
              .replaceAll("secret=.*?&", "secret=&")  // for ICE/TURN url password
              .replaceAll("push-fcm-key=.*?, ", "push-fcm-key==, ") //fcm server key
              .replaceAll("push-account-email=.*?, ", "push-account-email==, ") // for push
              .replaceAll("push-account-password=.*?, ", "push-account-password==, "); // for push
        //return msg.replaceAll("\"", "").replaceAll("\\r", "").replaceAll("", "");
        // Remove special carriage return characters that seem to not be allowed to be written in logcat.
        // WARNING: Also another VERY weird issue is that if we are logging full INVITE requests (together with SDP)
        // there is no SDP shown in logcat. And turns out there's an logcat issue when dealing with two consecutive new line characters
        // So to work around that we replace two new lines with one
        return msg.replaceAll("\\r", "").replaceAll("\\n\\n", "\n");
    }

    // This isn't going to be used as we it forces the user to use it in order to obscure anything. So a new developer that might forget will still
    // put existing password keys in the open. Instead we 're using a filtering logic (check .filter() above)
   /**
    * Returns a string with all parameters logged, but where sensitive values will be obscured. The keys whose values we want to obscure are hard coded for now
    * @param parameters parameters we want logged
    * @return String with all sensitive fields obscured
    */
   /*
    public static String obscureHashMap2String(HashMap<String, Object> parameters)
    {
        String output = parameters.toString();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().equals("turn-password") || entry.getKey().equals("pref_sip_password")) {
                output = output.replace((String)entry.getValue(), "");
            }
        }
        return output;
    }
    */

}