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

package nl.privacybarometer.privacyvandaag.provider;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import nl.privacybarometer.privacyvandaag.BuildConfig;

import static nl.privacybarometer.privacyvandaag.provider.FeedData.SERVICE_CHANNEL_FEEDNAME;
import static nl.privacybarometer.privacyvandaag.provider.FeedDataContentProvider.addFeed;

/**
 * Upgrade functions to get existing database content in line with new app.
 */

public class UpgradeFeedDataContentProvider {
    private static final String TAG = UpgradeFeedDataContentProvider.class.getSimpleName() + " ~> ";


    /**
     * Resource identifiers change if images are added to or delete from the resources of the app.
     * Update the resource identifiers to get to the right logo's in the toolbar and left drawer navigation menu.
     *
     * @param context   necessary for getting access to database and resources.
     */
    public static void updateIconResourceIds(Context context) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = null;
        // perform query to lookup icon filename in the database.
        try {
            cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, // table to query
                    new String[]{FeedData.FeedColumns._ID, FeedData.FeedColumns.ICON, FeedData.FeedColumns.NAME}, // return columns
                    null, // selection
                    null, // selection args
                    null // order by
            );
            //  Log.e(TAG,DatabaseUtils.dumpCursorToString(cursor)); // Debugging: See what's in the cursor
            int posFeedId = 0;  // position of feedId in the returned results in the cursor
            int posIconName = 1; // position of name of the logo in the returned results in the cursor
            int posFeedName = 2; // position of name of the feed in the returned results in the cursor
            ContentValues values = new ContentValues(); // values for the update of the database
            long feedId;
            String iconFileName;
            String feedName;
            int iconResourceId = 0;

            while (cursor != null && cursor.moveToNext()) {
                values.clear(); // reset values to be used again. This is more efficient than declaring each time a new ContentValues object.
                feedId = Long.parseLong(cursor.getString(posFeedId));   // cursor results are always returned as String
                iconFileName = cursor.getString(posIconName);   // read the filename of the icon
                if (iconFileName == null) {  // No filename of icon found. Let's put it in.
                    feedName = cursor.getString(posFeedName);   // read the feed name to determine the belonging icon.
                    if (feedName != null) {
                        if (feedName.toLowerCase().contains("barometer"))
                            iconFileName = "logo_icon_pb";
                        else if (feedName.toLowerCase().contains("freedom"))
                            iconFileName = "logo_icon_bof";
                        else if (feedName.toLowerCase().contains("first"))
                            iconFileName = "logo_icon_pf";
                        else if (feedName.toLowerCase().contains("vrijbit"))
                            iconFileName = "logo_icon_vrijbit";
                        else if (feedName.toLowerCase().contains("kdvp"))
                            iconFileName = "logo_icon_kdvp";
                        else if (feedName.toLowerCase().contains("persoonsgegevens"))
                            iconFileName = "logo_icon_ap";
                        else if (feedName.toLowerCase().contains("nieuws"))
                            iconFileName = "logo_icon_privacy_in_het_nieuws";
                        else if (feedName.toLowerCase().contains("service"))
                            iconFileName = "logo_icon_serviceberichten";

                        // We have an icon file, so let's store it in the database.
                        if (iconFileName != null) values.put(FeedData.FeedColumns.ICON, iconFileName);
                    }
                }
                // At this point the iconFileName can still be null. So check it.
                if ((iconFileName != null) && (iconFileName.length() > 0)) {   // We have an icon file, so let's find the resource ID to it.
                    iconFileName = iconFileName.trim(); // Maybe it was previously not stored correctly?
                    // lookup the icon by its name get the resource identifier
                    iconResourceId = context.getResources().getIdentifier(iconFileName, "drawable", context.getPackageName());
                    // put the resource Id in the database to work with during normal operation of the app.
                    values.put(FeedData.FeedColumns.ICON_DRAWABLE, iconResourceId);
                    cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null); // update the feed with the new resource identifier.
                }
            }
        } catch (Exception e) {
            // exception handling
            Log.e (TAG,"error upgrade resource Id's. " + e);
        } finally {
            if(cursor != null){
                cursor.close();
            }
        }
    }

    /**
     * Update feed information in database from app build version < 100 to build version 137.
     *
     * @param context   necessary for getting access to database and resources.
     */
    public static void updateExistingFeedsFromVersion100To137(Context context) {

        // First add the new feeds to the database
        addFeed(context, "https://www.privacybarometer.nl/app/privacy_vandaag_tweetselectie_to_rss.php", "Privacy in het nieuws", false, "logo_icon_privacy_in_het_nieuws");
        addFeed(context, "https://www.vrijbit.nl/index.php?option=com_k2&view=itemlist&format=feed", "Vrijbit", true, "logo_icon_vrijbit");
        addFeed(context, "https://www.kdvp.nl/?format=feed&type=rss", "KDVP", true, "logo_icon_kdvp");


        // Next, update the feeds with new information and put them in right order.
       /*
            from old database situation:
                0: "https://www.privacybarometer.nl/feed/", "Privacy Barometer", true, "", "", 61, "logo_icon_pb"); // 61 is het aantal dagen dat items bewaard moeten worden.
                1:  "https://www.bof.nl/feed/", "Bits of Freedom",  true, "", "", 61, "logo_icon_bof");
                2: "https://www.privacyfirst.nl/index.php?option=com_k2&view=itemlist&format=feed", "Privacy First",  true, "", "", 61, "logo_icon_pf");
                3: "https://cbpweb.nl/nl/rss", "CBP",  false, "", "", 61, "logo_icon_cbp");
                4: "https://autoriteitpersoonsgegevens.nl/nl/rss", "Autoriteit Persoonsgegevens",  false, "", "", 61, "logo_icon_ap");
                5: "https://www.privacybarometer.nl/app/feed/", "Privacy Vandaag",  false, "", "", 61, "logo_icon_pv"); // 61 is het aantal dagen dat items bewaard moeten worden.

            to new database situation. The index is not the ID, but the desired order of the feeds:
                0: "https://www.privacybarometer.nl/app/privacy_vandaag_tweetselectie_to_rss.php", "Privacy in het nieuws", false, "logo_icon_privacy_in_het_nieuws");
                1:  "https://www.privacybarometer.nl/feed/", "Privacy Barometer", true, "logo_icon_pb");
                2:  "https://www.bof.nl/feed/", "Bits of Freedom", true, "logo_icon_bof");
                3:  "https://www.privacyfirst.nl/index.php?option=com_k2&view=itemlist&format=feed", "Privacy First", true, "logo_icon_pf");
                4:  "https://www.vrijbit.nl/index.php?option=com_k2&view=itemlist&format=feed", "Vrijbit",  true, "logo_icon_vrijbit");
                5:  "https://www.kdvp.nl/?format=feed&type=rss", "KDVP",  true, "logo_icon_kdvp");
                6:  "https://autoriteitpersoonsgegevens.nl/nl/rss", "Autoriteit Persoonsgegevens",  false, "logo_icon_ap");
                7: "https://www.privacybarometer.nl/app/feed/" + BuildConfig.PRODUCT_FLAVOR, SERVICE_CHANNEL_FEEDNAME,  false, "logo_icon_serviceberichten"); // 61 is het aantal dagen dat items bewaard moeten worden.
        */

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = null;
        // perform query to lookup feed Id's and feed names in the database.
        try {
            cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, // table to query
                    new String[]{FeedData.FeedColumns._ID, FeedData.FeedColumns.NAME}, // return columns
                    null, // selection
                    null, // selection args
                    null // order by
            );
            //  Log.e(TAG,DatabaseUtils.dumpCursorToString(cursor)); // Debugging: See what's in the cursor
            int posFeedId = 0;  // position of feedId in the returned results in the cursor
            int posFeedName = 1; // position of name of the feed in the returned results in the cursor
            long feedId;
            String feedName;
            while (cursor != null && cursor.moveToNext()) {
                feedId = Long.parseLong(cursor.getString(posFeedId));   // cursor results are always returned as String
                feedName = cursor.getString(posFeedName);   // read the feed name to determine the belonging icon.
                if (feedName != null) {
                    if (feedName.toLowerCase().contains("nieuws"))
                        updateFeed(context, feedId, "https://www.privacybarometer.nl/app/privacy_vandaag_tweetselectie_to_rss.php",
                                "Privacy in het nieuws", false, "logo_icon_privacy_in_het_nieuws",0);
                    else if (feedName.toLowerCase().contains("barometer"))
                        updateFeed(context, feedId,
                                "https://www.privacybarometer.nl/feed/", "Privacy Barometer", true, "logo_icon_pb",1);
                    else if (feedName.toLowerCase().contains("freedom"))
                        updateFeed(context, feedId, "https://www.bitsoffreedom.nl/feed/",
                                "Bits of Freedom", true, "logo_icon_bof",2);
                    else if (feedName.toLowerCase().contains("first"))
                        updateFeed(context, feedId, "https://www.privacyfirst.nl/index.php?option=com_k2&view=itemlist&format=feed",
                                "Privacy First", true, "logo_icon_pf",3);
                    else if (feedName.toLowerCase().contains("vrijbit"))
                        updateFeed(context, feedId, "https://www.vrijbit.nl/index.php?option=com_k2&view=itemlist&format=feed",
                                "Vrijbit", true, "logo_icon_vrijbit",4);
                    else if (feedName.toLowerCase().contains("kdvp"))
                        updateFeed(context, feedId, "https://www.kdvp.nl/?format=feed&type=rss",
                                "KDVP", true, "logo_icon_kdvp", 5);
                    else if (feedName.toLowerCase().contains("persoonsgegevens"))
                        updateFeed(context, feedId, "https://autoriteitpersoonsgegevens.nl/nl/rss",
                                "Autoriteit Persoonsgegevens", false, "logo_icon_ap",6);
                    else if (feedName.toLowerCase().contains("vandaag"))
                        updateFeed(context, feedId, "https://www.privacybarometer.nl/app/feed/" + BuildConfig.PRODUCT_FLAVOR,
                                SERVICE_CHANNEL_FEEDNAME, false, "logo_icon_serviceberichten",7);
                }

            }

        } catch (Exception e) {
            Log.e(TAG, "Update of existing feeds not succeeded." + e);
            // TODO: display message update not succeeded. Delete all data en start app again.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }





    /**
     * Update feed information in database from app build version < 149 to build version 151.
     *
     * @param context   necessary for getting access to database and resources.
     */
    public static void updateExistingFeedsFromVersion149To152(Context context) {

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = null;
        // perform query to lookup feed Id's and feed names in the database.
        try {
            cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, // table to query
                    new String[]{FeedData.FeedColumns._ID, FeedData.FeedColumns.NAME}, // return columns
                    null, // selection
                    null, // selection args
                    null // order by
            );
            //  Log.e(TAG,DatabaseUtils.dumpCursorToString(cursor)); // Debugging: See what's in the cursor
            int posFeedId = 0;  // position of feedId in the returned results in the cursor
            int posFeedName = 1; // position of name of the feed in the returned results in the cursor
            long feedId;
            String feedName;
            while (cursor != null && cursor.moveToNext()) {
                feedId = Long.parseLong(cursor.getString(posFeedId));   // cursor results are always returned as String
                feedName = cursor.getString(posFeedName);   // read the feed name to determine the belonging icon.
                if (feedName != null) {
                    if (feedName.toLowerCase().contains("freedom"))
                        updateFeed(context, feedId, "https://www.bitsoffreedom.nl/feed/",
                                "Bits of Freedom", true, "logo_icon_bof",2);
                    else if (feedName.toLowerCase().contains("kdvp"))
                        updateFeed(context, feedId, "https://www.kdvp.nl/?format=feed&type=rss",
                                "KDVP", true, "logo_icon_kdvp", 5);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Update of existing feeds not succeeded." + e);
            // TODO: display message update not succeeded. Delete all data and start app again.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }






    /**
     *
     * updateFeed(
     *      > context,
     *      > feed Id
     *      > feed URL,
     *      > feed title,
     *      > retrieve full article or stick to RSS excerpt (true/false)
     *      > feed icon file name (without the .png extension)
     *      > order of the feed in the menu (starting from 0)
     *  );
     */


    private static void updateFeed(Context context,long feedId, String mUrl, String mTitle,
                                   boolean retrieveFullText, String mIconDrawable, int mPriority) {
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues(); // values for the update of the database
        int iconResourceId;

        if (feedId > -1) {
            values.put(FeedData.FeedColumns.URL, mUrl );
            values.put(FeedData.FeedColumns.NAME, mTitle);
            values.put(FeedData.FeedColumns.NOTIFY, true);
            values.put(FeedData.FeedColumns.RETRIEVE_FULLTEXT, retrieveFullText);
            values.put(FeedData.FeedColumns.PRIORITY, mPriority);
            values.put(FeedData.FeedColumns.ICON_DRAWABLE, mIconDrawable);

            if (mIconDrawable.length() > 0) {   // We have an icon file, so let's find the resource ID to it.
                mIconDrawable = mIconDrawable.trim(); // Maybe it was previously not stored correctly?
                // lookup the icon by its name get the resource identifier
                iconResourceId = context.getResources().getIdentifier(mIconDrawable, "drawable", context.getPackageName());
                // put the resource Id in the database to work with during normal operation of the app.
                values.put(FeedData.FeedColumns.ICON_DRAWABLE, iconResourceId);
            }

            cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null); // update the feed with the new resource identifier.
        }
    }




}
