package nl.privacybarometer.privacyvandaag.utils;

import android.content.ContentResolver;
import android.content.ContentValues;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.provider.FeedData;

/**
 * Created by wizzweb on 16-1-2016.
 */
public class ResetUtils {
    private static final String TAG = ResetUtils.class.getSimpleName() + " ~> ";


    public void resetLastUpdateTime(String feedId) {
        deleteOldEntriesFromFeed(feedId);

        long resetTime = 0;
        ContentValues values = new ContentValues();
        values.put(FeedData.FeedColumns.REAL_LAST_UPDATE, resetTime);

        ContentResolver cr = MainApplication.getContext().getContentResolver();
        cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null);

        // Log.e(TAG, "feed is " + feedId);
        /** Onderstaande alleen voor controle-doeleinden **/
        /*
        Cursor cursor = cr.query(FeedData.FeedColumns.CONTENT_URI(feedId), null, null, null, null);
        if (cursor.moveToFirst()) {

            int urlPosition = cursor.getColumnIndex(FeedData.FeedColumns.URL);
            int idPosition = cursor.getColumnIndex(FeedData.FeedColumns._ID);
            int titlePosition = cursor.getColumnIndex(FeedData.FeedColumns.NAME);
            int fetchModePosition = cursor.getColumnIndex(FeedData.FeedColumns.FETCH_MODE);
            int realLastUpdatePosition = cursor.getColumnIndex(FeedData.FeedColumns.REAL_LAST_UPDATE);

            String feedTitle = cursor.getString(titlePosition);    // used for notifications
            String id = cursor.getString(idPosition);
            long lastRetrieveTime = cursor.getLong(realLastUpdatePosition);

            Log.e(TAG, "feedTitle = " + feedTitle);
            Log.e(TAG, "id = " + id);
            Log.e(TAG, "lastRetrieveTime = " + lastRetrieveTime);
            cursor.close();
        }   // Einde controle wat er in de database gebeurt
        //*/
    }

    private void deleteOldEntriesFromFeed(String feedId) {
        long keepDateBorderTimeLocal = System.currentTimeMillis();

        /*
        if (keepDateBorderTime > 0) {
            String where = FeedData.EntryColumns.DATE + '<' + keepDateBorderTime + Constants.DB_AND + FeedData.EntryColumns.WHERE_NOT_FAVORITE;
            // Delete the entries, the cache files will be deleted by the content provider
            MainApplication.getContext().getContentResolver().delete(FeedData.EntryColumns.CONTENT_URI, where, null);
        }
        Cursor cursor = MainApplication.getContext().getContentResolver().query(FeedData.FeedColumns.CONTENT_URI, new String[]{FeedData.FeedColumns._ID, FeedData.FeedColumns.KEEP_TIME}, null, null, null);
        while (cursor.moveToNext()) {
            long feedid = cursor.getLong(0);
            long keepTimeLocal = cursor.getLong(1) == 1 ? (long) (Constants.DURATION_OF_ONE_DAY / 4) : cursor.getLong(1) * Constants.DURATION_OF_ONE_DAY; // Als het op 1 dag staat, doen we een halve dag.
            long keepDateBorderTimeLocal = keepTimeLocal > 0 ? System.currentTimeMillis() - keepTimeLocal : 0;
            if (keepDateBorderTimeLocal > 0) {
                String where = FeedData.EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + FeedData.EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + FeedData.EntryColumns.FEED_ID + "=" + String.valueOf(feedid);
                MainApplication.getContext().getContentResolver().delete(FeedData.EntryColumns.CONTENT_URI, where, null);
            }
        }
        cursor.close();
        */

        String where = FeedData.EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + FeedData.EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + FeedData.EntryColumns.FEED_ID + "=" + feedId;
        MainApplication.getContext().getContentResolver().delete(FeedData.EntryColumns.CONTENT_URI, where, null);

    }

}
