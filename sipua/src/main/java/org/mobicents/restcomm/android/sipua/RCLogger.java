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

package org.mobicents.restcomm.android.sipua;

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
            Log.v(tag, msg, t);
        }
    }

    public static void v(String tag, String msg)
    {
        if (RCLogger.isVerboseEnabled()) {
            Log.v(tag, msg);
        }
    }

    public static void d(String tag, String msg, Throwable t)
    {
        if (RCLogger.isDebugEnabled()) {
            Log.d(tag, msg, t);
        }
    }

    public static void d(String tag, String msg)
    {
        if (RCLogger.isDebugEnabled()) {
            Log.d(tag, msg);
        }
    }

    public static void i(String tag, String msg, Throwable t)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, msg, t);
        }
    }

    public static void i(String tag, String msg)
    {
        if (RCLogger.isInfoEnabled()) {
            Log.i(tag, msg);
        }
    }

    public static void w(String tag, String msg, Throwable t)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, msg, t);
        }
    }

    public static void w(String tag, String msg)
    {
        if (RCLogger.isWarnEnabled()) {
            Log.w(tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable t)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, msg, t);
        }
    }

    public static void e(String tag, String msg)
    {
        if (RCLogger.isErrorEnabled()) {
            Log.e(tag, msg);
        }
    }

    public static void wtf(String tag, String msg, Throwable t)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, msg, t);
        }
    }

    public static void wtf(String tag, String msg)
    {
        if (RCLogger.isAssertEnabled()) {
            Log.wtf(tag, msg);
        }
    }

}