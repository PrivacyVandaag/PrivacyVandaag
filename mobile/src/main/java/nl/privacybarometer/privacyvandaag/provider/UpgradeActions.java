package nl.privacybarometer.privacyvandaag.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Als er acties nodig zijn om de app en de database bij te werken naar de nieuwe upgrade van de app
 * dan gebeurt dat hier. Aanpassingen aan de structuur van de database horen in de DatabaseHelper > onUpgrade()
 *
 * Created by privacyvandaag on 28-10-2015.
 */
public class UpgradeActions {
    private static final String TAG = UpgradeActions.class.getSimpleName() + " ~> ";

    /**
     * De upgrade method.
     * @param mContext nodig om toegang tot de database en de resources te krijgen
     * @return Voorlopig altijd true, maar dit kan afhangen van de upgrade acties.
     */
    public static boolean startUpgradeActions(Context mContext) {
        // Er zijn afbeeldingen toegevoegd, dus de numerieke verwijzingen naar die plaatjes kloppen niet meer.
        // We lopen in de database alle feeds af om de verwijzing naar de logo's weer goed te zetten
        String mImageNew = "";
        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = new ContentValues();

        Cursor cursor = cr.query(FeedData.FeedColumns.CONTENT_URI, new String[]{FeedData.FeedColumns._ID}, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    int feedid = (int) cursor.getLong(0);
                    switch (feedid) {
                        case 1: mImageNew = "logo_icon_pb"; break;
                        case 2: mImageNew = "logo_icon_bof"; break;
                        case 3: mImageNew = "logo_icon_pf"; break;
                        case 4: mImageNew = "logo_icon_ap"; break;
                        case 5: mImageNew = "logo_icon_pv"; break;
                    }
                    int mDrawableResourceId = mContext.getResources().getIdentifier(mImageNew, "drawable", mContext.getPackageName());
                    values.put(FeedData.FeedColumns.ICON_DRAWABLE, mDrawableResourceId); // plaats nieuwe referentie naar icon  in database
                    cr.update(FeedData.FeedColumns.CONTENT_URI(feedid), values, null, null);
                    values.clear();
                }
            } finally {
                cursor.close();
            }
        }

        // Wijziging van de naam CBP in Autoriteit Persoonsgegevens
        String feedId="4";  // Dit is de feed ID van de Autoriteit Persoonsgegevens
        values.put(FeedData.FeedColumns.URL, "https://autoriteitpersoonsgegevens.nl/nl/rss"); // plaats nieuwe url in database
        values.put(FeedData.FeedColumns.NAME, "Autoriteit Persoonsgegevens"); // plaats nieuwe url in database
        cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null);
        values.clear();

        Log.e(TAG, "upgrade acties van database uitgevoerd.");
        return true;
    }
}
