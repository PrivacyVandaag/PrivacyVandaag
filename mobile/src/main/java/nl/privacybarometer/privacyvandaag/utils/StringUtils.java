/*
 * Copyright (c) 2015-2017 Privacy Vandaag / Privacy Barometer
 *
 * Copyright (c) 2015 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
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

import android.annotation.TargetApi;
import android.os.Build;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.R;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Util for date time conversions and formatting.
 * Is used in
 *  > adapter > EntriesCursorAdapter.java and
 *  > adapter > DrawerAdapter.java
 *
 * At the end, a MD5 calculating method for network operations (filenames).
 *
 */

public class StringUtils {
    private static final DateFormat TIME_FORMAT = android.text.format.DateFormat.getTimeFormat(MainApplication.getContext());
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EE d MMMM", DeprecateUtils.locale(MainApplication.getContext()));
    private static final int SIX_HOURS = 21600000; // six hours in milliseconds

    /**
     * Date and time formatting for left drawer
     * @param timestamp of the article in UTC in UNIX milliseconds
     */
    static public String getDateTimeStringSimple(long timestamp) {
        Date date = new Date(timestamp);
        long now = System.currentTimeMillis();  // Current time in UTC
        // Give full date for timedates more than six hours in past or future
        if ((timestamp > now - SIX_HOURS) && (timestamp < now + SIX_HOURS)) {
            return TIME_FORMAT.format(date);
        } else {
            return (DATE_FORMAT.format(date) + Constants.SPACE + TIME_FORMAT.format(date));
        }
    }

    /**
     * Date and time formatting for EntryList.
     */
    static public String getDateTimeString(long timestamp, long yesterdayMidnight, long lastMidnight, long comingMidnight, long tomorrowMidnight) {
        String dateString;
        Date date = new Date(timestamp);

        // Is it yesterday, today or tomorrow?
        if (timestamp > yesterdayMidnight && timestamp < tomorrowMidnight) {
            if (timestamp < lastMidnight) { // It is yesterday
                dateString = MainApplication.getContext().getString(R.string.gisteren);
            } else if (timestamp < comingMidnight) {    // It is today
                dateString = MainApplication.getContext().getString(R.string.vandaag);
            } else {    // It is tomorrow
                dateString = MainApplication.getContext().getString(R.string.morgen);
            }
            return (dateString + Constants.COMMA_SPACE + TIME_FORMAT.format(date));
        } else {    // Use normal date time format.
            if (timestamp > lastMidnight - (5 * Constants.DURATION_OF_ONE_DAY)) {   // It is past couple of days, include time.
                return DATE_FORMAT.format(date) + Constants.COMMA_SPACE + TIME_FORMAT.format(date);
            } else {    // It is too long ago. Exact time is not really relevant here.
                return DATE_FORMAT.format(date);
            }
        }
    }


    /**
     * Get last midnight. This is useful to determine what is today or what was yesterday.
     * @return long: timestamp van de laatste middernacht (00.00 uur)
     */
    public static long getLastMidnight() {
        Calendar mMidnight = Calendar.getInstance();    // Get 'now'
        mMidnight.set(Calendar.HOUR_OF_DAY, 0); // Set hours, minutes and seconds to 0.
        mMidnight.set(Calendar.MINUTE, 0);
        mMidnight.set(Calendar.SECOND, 0);
        return mMidnight.getTimeInMillis(); // and we have the timestamp of last midnight.
    }


    /**
     * Get MD5 hash of a string. Used to make filenames for images.
     * @param input
     * @return
     */
    static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            if (md != null) {
                byte[] messageDigest = md.digest(input.getBytes());
                BigInteger number = new BigInteger(1, messageDigest);
                return number.toString(16);
            } else return null;
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
