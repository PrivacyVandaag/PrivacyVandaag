package nl.privacybarometer.privacyvandaag.receiver;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.provider.FeedData;

/**
 * Created by PrivacyVandaag on 29-7-2015.
 * If updates are made to the feeds in the database, they should also be made here, so on upgrade of the app,
 * the modifications are also made to existing databases of previous versions of the app.
 */
public class OnUpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED) || intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {

                /* TESTING
                    if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)) {
                        Bundle extras = intent.getExtras();
                        if (extras != null) {
                            Uri uri = intent.getData();
                            String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
                            String[] components = intent.getStringArrayExtra(
                                    Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST);
                            Log.e("PrivVandaag", "Changed Met Extras");
                            Log.e("PrivVandaag", "getPackageName: " + pkg);
                            Toast.makeText(context, "getPackageName: " + pkg, Toast.LENGTH_LONG).show();
                            Log.e("PrivVandaag", "UID" + uid);
                            final int size = components.length;
                            String compIter;
                            for (int i = 0; i < size; i++)
                            {
                                compIter = components[i];
                                Log.e("PrivVandaag", "component " + i + ": " + compIter );
                            }
                            Log.e("PrivVandaag", "getPackageName" + components[0]);
                        }
                    } else if (intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
                            Log.e("PrivVandaag", "Action Package Replaced");
                            Bundle extras = intent.getExtras();
                            if (extras != null) {
                                Log.e("PrivVandaag", "Replaced Met Extras");
                                Toast.makeText(context, "app heet ER ZIJN EXTRAA s:" + msg6, Toast.LENGTH_LONG).show();
                            }
                      Einde TESTING  */

        /**
         * Start Actions On Upgrade Of Package (on the feeds in the database)
         *
         */

          /*
            // Set the CBP feed not to retrieve full text
            String url = "https://cbpweb.nl/nl/rss";
            ContentResolver cr = context.getContentResolver();
            // Lookup feed in database: args: name table (feeds), values to return, where-selection, where-selection-arguments (replaces ? in where-selection),groupBy, Having, OrderBy,Limit);
            Cursor cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, new String[]{"name"}, FeedData.FeedColumns.URL + Constants.DB_ARG, new String[]{url}, null);
            if (cursor.moveToFirst()) { // If URL exists, update the parameters.
                ContentValues values = new ContentValues();
                values.put(FeedData.FeedColumns.RETRIEVE_FULLTEXT, false);
                cr.update(FeedData.FeedColumns.CONTENT_URI, values, FeedData.FeedColumns.URL + Constants.DB_ARG, new String[]{url});
            }
            cursor.close();

            // Set the service channel not to retrieve full text
            url = "https://www.privacybarometer.nl/app/feed/";
            cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, new String[]{"name"}, FeedData.FeedColumns.URL + Constants.DB_ARG, new String[]{url}, null);
            if (cursor.moveToFirst()) { // If URL exists, update the parameters.
                ContentValues values = new ContentValues();
                values.put(FeedData.FeedColumns.RETRIEVE_FULLTEXT, false);
                cr.update(FeedData.FeedColumns.CONTENT_URI, values, FeedData.FeedColumns.URL + Constants.DB_ARG, new String[]{url});
            }
            cursor.close();
            Toast.makeText(context, "Database bijgewerkt.", Toast.LENGTH_LONG).show();

        // End Action On Upgrade Of Package */

            //final String msg = "intent:" + intent + " - action:" + intent.getAction();
        }
    }
}
