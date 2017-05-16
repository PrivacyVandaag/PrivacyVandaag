package nl.privacybarometer.privacyvandaag.utils;

import android.content.Context;

import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedDataContentProvider;

import static nl.privacybarometer.privacyvandaag.provider.FeedDataContentProvider.URI_FILTERS;

/**
 * Created by wizzweb on 28-10-2015.
 */
public class UpgradeActions {

    /**
     * If some tasks need to be performed on upgrade of the app, they should be put here.
     *
     * @return
     */
    public static boolean startUpgradeActions(Context context, int oldVersionCode) {

        // Empty all tables in database and reset the feeds ID. This creates a fresh start!
        // Deleting the feeds will also delete all entries and tasks.
        // This is only necessary if upgrade for database is not possible, like for update from versionCode < 100
        if (oldVersionCode < 100) {
            MainApplication.getContext().getContentResolver().delete(FeedData.FeedColumns.CONTENT_URI, null, null);
            // Since this is a major update, reset all settings and act like it is first open.
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, true);
        } else {
            // Resource identifiers change if images are added to or delete from the resources of the app.
            // Get the right resource identifiers for the logo's in case they changed
            // This should always be done to make sure the right resource identifiers are used!!!
            FeedDataContentProvider.updateIconResourceIds(context);
        }
        return true;
    }




}
