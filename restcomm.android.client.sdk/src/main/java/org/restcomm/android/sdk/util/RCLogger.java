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
            Log.v(tag, escape(msg), t);
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
            Log.v(tag, RCLogger.escape(msg));
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

            Log.d(tag, escape(msg), t);
        }
    }

    public static void d(String tag, String msg)
    {
        if (RCLogger.isDebugEnabled()) {
            Log.d(tag, escape(msg));
        }
    }

    public static void i(String tag, String msg, Throwable t)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, escape(msg), t);
        }
    }

    public static void i(String tag, String msg)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, escape(msg));
        }
    }

    public static void w(String tag, String msg, Throwable t)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, escape(msg), t);
        }
    }

    public static void w(String tag, String msg)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, escape(msg));
        }
    }

    public static void e(String tag, String msg, Throwable t)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, escape(msg), t);
        }
    }

    public static void e(String tag, String msg)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, escape(msg));
        }
    }

    public static void wtf(String tag, String msg, Throwable t)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, escape(msg), t);
        }
    }

    public static void wtf(String tag, String msg)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, escape(msg));
        }
    }

    private static String escape(String msg)
    {
        //return msg.replaceAll("\"", "").replaceAll("\\r", "").replaceAll("", "");
        // Remove special carriage return characters that seem to not be allowed to be written in logcat.
        // WARNING: Also another VERY weird issue is that if we are logging full INVITE requests (together with SDP)
        // there is no SDP shown in logcat. And turns out there's an logcat issue when dealing with two consecutive new line characters
        // So to work around that we replace two new lines with one
        return msg.replaceAll("\\r", "").replaceAll("\\n\\n", "\n");
    }

}