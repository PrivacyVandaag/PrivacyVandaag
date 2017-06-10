/**
 * Privacy Vandaag
 * <p/>
 * Copyright (c) 2015 Privacy Barometer
 * Copyright (c) 2015 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 * Hulpmiddelen om datumtijd conversies te doen voor weergave in de app.
 * Wordt aangeroepen vanuit
 *  > adapter > EntriesCursorAdapter.java en
 *  > adapter > DrawerAdapter.java
 *
 * Daarnaast nog MD5 berekening voor netwerk-operaties.
 *
 */


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class StringUtils {

    private static final DateFormat TIME_FORMAT = android.text.format.DateFormat.getTimeFormat(MainApplication.getContext());
    private static final int SIX_HOURS = 21600000; // six hours in milliseconds
    private static DateFormat DATE_SHORT_FORMAT = null;

    private static final SimpleDateFormat DAY_OF_THE_WEEK_FORMAT = new SimpleDateFormat("E", Locale.ROOT);
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("M", Locale.ROOT);
//    private static final SimpleDateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("d", Locale.ROOT);
    private static final SimpleDateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("d", MainApplication.getContext().getResources().getConfiguration().locale);
    //private static final String[] DAG = { "Zaterdag", "Zondag", "Maandag", "Dinsdag", "Woensdag", "Donderdag", "Vrijdag"} ;
    private static final String[] DAG_KORT = { "", "Zo", "Ma", "Di", "Wo", "Do", "Vr", "Za" };
    private static final String[] MAAND_KORT = { "" , "jan", "feb", "mrt", "apr", "mei", "jun", "jul", "aug", "sep", "okt", "nov", "dec"} ;

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("CET");


    static {
        // getBestTimePattern() is only available in API 18 and up (Android 4.3 and better)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            DATE_SHORT_FORMAT = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(MainApplication.getContext().getResources().getConfiguration().locale, "EE d MMMM"));
        }
        /*
        else {  // niet gebruikt
            DATE_SHORT_FORMAT = android.text.format.DateFormat.getDateFormat(MainApplication.getContext());
        }
        */
    }

    /**
     * Simpele mooie weergave van datum tijd voor gebruik in de leftdrawer
     *
     * @param timestamp long met de timestamp van het item in UNIX milliseconden
     * @return String: simpel maar mooi vormgegeven tijdsaanduiding
     */
    static public String getDateTimeStringSimple(long timestamp) {
        String outString;

        Date date = new Date(timestamp);
        Calendar calTimestamp = Calendar.getInstance();
        calTimestamp.setTimeInMillis(timestamp);
        Calendar calCurrent = Calendar.getInstance();

        // Give full date for timedates more than six hours in past or future
        if ((calCurrent.getTimeInMillis() - timestamp < SIX_HOURS) && (calCurrent.getTimeInMillis() - timestamp > 0)) {
            outString = TIME_FORMAT.format(date);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            outString = DATE_SHORT_FORMAT.format(date) + Constants.SPACE + TIME_FORMAT.format(date);
        } else {
            String maand = "";
            int maandVanHetJaar =Integer.valueOf (MONTH_FORMAT.format(date));
            if (maandVanHetJaar <13) maand = MAAND_KORT[maandVanHetJaar];
            outString = DAY_OF_THE_MONTH_FORMAT.format(date) + Constants.SPACE + maand + Constants.COMMA_SPACE + TIME_FORMAT.format(date);
        }

        return outString;
    }

    /**
     * Uitgebreidere functie om een mooie datum tijd weer te geven in de EntryList.
     * Er wordt ook gekeken of het gisteren, vandaag of morgen is.
     *
     * Hier wordt ook rekening gehouden met het tijdsverschil met GMT en zomertijd (daylight saving time) etc.
     *
     * @param timestamp
     * @param yesterdayMidnight
     * @param lastMidnight
     * @param comingMidnight
     * @param tomorrowMidnight
     * @return String: Uitgebreide mooi vormgegeven tijdsaanduiding
     */
    static public String getDateTimeString(long timestamp, long yesterdayMidnight, long lastMidnight, long comingMidnight, long tomorrowMidnight) {
        String datumString;
        int offSet = TIME_ZONE.getOffset(timestamp);
        Date date = new Date(timestamp + offSet);

        // Is het gisteren, vandaag of morgen?
        if (timestamp > yesterdayMidnight && timestamp < tomorrowMidnight) {// Het is gisteren, vandaag of morgen
            if (timestamp < lastMidnight) {
                datumString = MainApplication.getContext().getString(R.string.gisteren);
            } else if (timestamp < comingMidnight) {
                datumString = MainApplication.getContext().getString(R.string.vandaag);
            } else datumString = MainApplication.getContext().getString(R.string.morgen);
        }
        // Geef de gewone dag aanduiding.
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {  // Vanaf API 18 ben je dan in een keer klaar
            return (DATE_SHORT_FORMAT.format(date) + Constants.SPACE + TIME_FORMAT.format(date));
        }
        // Geef de gewone dag aanduiding voor oudere API's
        else {
            String dag = "";
            String maand = "";
            int dagVanDeWeek =Integer.valueOf (DAY_OF_THE_WEEK_FORMAT.format(date));
            int maandVanHetJaar =Integer.valueOf (MONTH_FORMAT.format(date));

            if (dagVanDeWeek<8) dag = DAG_KORT[dagVanDeWeek];
            if (maandVanHetJaar <13) maand = MAAND_KORT[maandVanHetJaar];
            datumString = dag + Constants.SPACE + DAY_OF_THE_MONTH_FORMAT.format(date) + Constants.SPACE + maand;
        }
        // Voeg de tijd toe
        return (datumString + Constants.COMMA_SPACE + TIME_FORMAT.format(date));
    }


    /**
     * Bepaal aan de hand van de huidige tijd wanneer het de laatste keer middernacht was.
     * Handig om te bepalen of een artikel / evenement gisteren, vandaag of morgen is.
     *
     * LET OP! Dit is al lokale tijd, dus hoeft niet te worden aangepast!
     *
     * @return long: timestamp van de laatste middernacht (00.00 uur)
     */
    public static long getLastMidnight() {
        Calendar mMidnight = Calendar.getInstance();
        mMidnight.set(Calendar.HOUR_OF_DAY, 0);
        mMidnight.set(Calendar.MINUTE, 0);
        mMidnight.set(Calendar.SECOND, 0);
        return mMidnight.getTimeInMillis();
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
