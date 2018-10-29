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

import android.content.Context;
import android.util.Log;

import java.io.File;

import nl.privacybarometer.privacyvandaag.provider.UpgradeFeedDataContentProvider;


/**
 * Perform some upgrade actions when a new app is installed.
 * This is mainly to get existing database and resources in line with new app.
 */

public class UpgradeActions {
    private static final String TAG = UpgradeActions.class.getSimpleName() + " ~> ";

    /**
     * If some tasks need to be performed on upgrade of the app, they should be put here.
     */
    public static boolean startUpgradeActions(Context context, int oldVersionCode) {

        wipePicassoCache(context);


        if (oldVersionCode < 100) {
            // Delete all data from database and reset auto increment value.
            //MainApplication.getContext().getContentResolver().delete(FeedData.FeedColumns.CONTENT_URI, null, null);

            // Add new feeds and update feed information of existing feeds in database
            UpgradeFeedDataContentProvider.updateExistingFeedsFromVersion100To137(context);
            // Since this is a major update, reset all settings and act like it is first open.
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, true);
        } else if (oldVersionCode < 151) {  // KDVP feed changed to https

            UpgradeFeedDataContentProvider.updateExistingFeedsFromVersion149To152(context);

        }

        // Resource identifiers change if images are added to or delete from the resources of the app.
        // Get the right resource identifiers for the logo's in case they changed
        // This should always be done to make sure the right resource identifiers are used!!!
        UpgradeFeedDataContentProvider.updateIconResourceIds(context);



        return true;
    }

    /**
     * In older versions, picasso did not scale down images. Cache can be several megabytes.
     * To free up storage space, we make sure old picasso files are removed on upgrade.
     */
    private static void wipePicassoCache(Context context) {
        File cacheDirectory = context.getCacheDir();
        File[] cacheFiles = cacheDirectory.listFiles(); // get all items in cache directory
        if (cacheFiles != null) {
            for (File f : cacheFiles) { // loop through all the items
                if (f.isDirectory()) {  // If the item is a (sub)directory
                    // Log.e(TAG, "Directory found: " + f.getName() );
                    cacheDirectory = new File(context.getCacheDir(), f.getName() + "/");
                    File[] subfiles = cacheDirectory.listFiles(); // get all items in subdirectory
                    if (subfiles != null) {
                        for (File subf : subfiles) {    // loop through all files in subdirectory
                            // fileSize = subf.length();    // in Bytes
                            // Delete the files in the picasso cache
                            if (subf.isFile() && cacheDirectory.getName().contains("picasso")) {
                                // Log.e(TAG, "deleting " + subf.getName());
                                subf.delete();
                            }
                        }
                    } // else Log.e(TAG, "No files found.");
                }
            }
        }
    }

}



