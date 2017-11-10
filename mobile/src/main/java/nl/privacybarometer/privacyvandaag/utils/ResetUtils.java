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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.io.File;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.provider.FeedData;

/**
 * Clear the feed of all entries
 */
public class ResetUtils {
    private static final String TAG = ResetUtils.class.getSimpleName() + " ~> ";


    public void resetLastUpdateTime(String feedId) {
        // First delete existing tasks for this feed.
        deleteExistingTasksFromFeed(feedId);

        // Second, delete the images because their filenames are in the database
        deleteExistingImagesFromFeed(feedId);
        // Now we can safely delete the entries from the database.
        deleteExistingEntriesFromFeed(feedId);

        // Set the last refresh time to zero, so the FetcherService will reread entire RSS feed.
        long resetTime = 0;
        ContentValues values = new ContentValues();
        values.put(FeedData.FeedColumns.REAL_LAST_UPDATE, resetTime);
        values.put(FeedData.FeedColumns.LAST_UPDATE, resetTime);
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null);
    }

    // TODO: Put this in the FeedDataContentProvider.java where it is supposed to be.

    private void deleteExistingEntriesFromFeed(String feedId) {
        long keepDateBorderTimeLocal = System.currentTimeMillis();
        String where = FeedData.EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + FeedData.EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + FeedData.EntryColumns.FEED_ID + "=" + feedId;
        MainApplication.getContext().getContentResolver().delete(FeedData.EntryColumns.CONTENT_URI, where, null);

    }

    private void deleteExistingTasksFromFeed(String feedId) {
        // get all entries from database
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Cursor cursor = null;
        // perform query to lookup image filenames that are part of the entries to be deleted.
        try {
            cursor = cr.query(FeedData.EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedId), // table to query
                    new String[]{  // return columns
                            FeedData.EntryColumns._ID
                    },
                    FeedData.EntryColumns.WHERE_NOT_FAVORITE, // selection
                    null, // selection args
                    null // order by
            );
            String where;
            // iterate through all the entries and delete the possible tasks.
            while (cursor != null && cursor.moveToNext()) {
               // Log.e(TAG,"Removing task for entry " +  cursor.getLong(0));
                where = FeedData.TaskColumns.ENTRY_ID + "=" + cursor.getLong(0);
                MainApplication.getContext().getContentResolver().delete(
                        FeedData.TaskColumns.CONTENT_URI, where, null);
         }
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    private void deleteExistingImagesFromFeed(String feedId) {
        // get all images from database
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Cursor cursor = null;
        // perform query to lookup image filenames that are part of the entries to be deleted.
        try {
            cursor = cr.query(FeedData.EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedId), // table to query
                    new String[]{  // return columns
                            FeedData.EntryColumns._ID, FeedData.EntryColumns.IMAGE_URL
                    },
                    FeedData.EntryColumns.WHERE_NOT_FAVORITE, // selection
                    null, // selection args
                    null // order by
            );
            //Log.e(TAG,DatabaseUtils.dumpCursorToString(cursor)); // Debugging: See what's in the cursor
            int posEntryId = 0;  // position of feedId in the returned results in the cursor
            int posImgUrlName = 1; // position of name of the logo in the returned results in the cursor

            String finalImgPath;
            Long entryId;
            String imgUrlName;
            // iterate trhough all the filenames and delete the images.
            while (cursor != null && cursor.moveToNext()) {
                entryId = cursor.getLong(posEntryId);
                imgUrlName = cursor.getString(posImgUrlName);
                if (imgUrlName != null) {
                    finalImgPath = NetworkUtils.getDownloadedImagePath(cursor.getLong(posEntryId),
                            cursor.getString(posImgUrlName));
                    // Log.e(TAG,">>>>>> Deleting " + finalImgPath);
                    new File(finalImgPath).delete();
                }
            }


        } finally {
                if (cursor != null)
                    cursor.close();
        }
    }
}
