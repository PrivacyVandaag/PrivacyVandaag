/*
 * Copyright (c) 2015-2017 Privacy Vandaag / Privacy Barometer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package nl.privacybarometer.privacyvandaag.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;

/**
 * Utility to write events to a log file that can be read or analyzed later.
 * Particularly useful to check certain operations over a period of time.
 *
 * The log file is saved to the user's internal SD card and can be read with any text reader
 * Use appendLog(TAG,"status info"); to append your status info to the log file.
 * The status info is also written to Android's Log method (Log.e(TAG,"Status");
 *
 * To get access to the SDcard of the device, declare the relevant permission in AndroidManifest:
 *	    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
 *      <uses-permission android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS" />
 *
 * This utility should never be used in production releases!!
 *
 */
public class LogFile {
    private static final String TAG = LogFile.class.getSimpleName() + " ~> ";

    private static final DateFormat TIME_FORMAT = android.text.format.DateFormat.getTimeFormat(MainApplication.getContext());
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EE d MMMM", DeprecateUtils.locale(MainApplication.getContext()));
    private static final String TABS = "\t\t";
    private static final String NEWLINE = "\n";
    private static final String COLON_SPACE = ": ";
    private static final String LOG_FILE = "myLogFile.txt";


    /**
     * Write and append to log file. Useful to monitor app status over longer period of time.
     * @param text
     */
    public static void appendLog(String tag, String text) {
        String dateString;

        String rootDir = Environment.getExternalStorageDirectory().getPath();
        // Log.e(TAG,"External log file:  " + rootDir);

        File logFile = new File(rootDir + "/" + LOG_FILE);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            }
            catch (IOException e) {
                Log.e(TAG,"Error creating logfile: " + logFile.getPath());
            }
        }
        try {
            // add date time to log text
            Date datetime = new Date(System.currentTimeMillis());   // Current time in UTC
            dateString=DATE_FORMAT.format(datetime) + Constants.COMMA_SPACE + TIME_FORMAT.format(datetime);

            Log.e(TAG,tag + COLON_SPACE +text);
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(dateString + TABS + tag + COLON_SPACE + text + NEWLINE );
            buf.newLine();
            buf.close();
        }
        catch (IOException e) {
        }
    }



}
