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
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nl.privacybarometer.privacyvandaag.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import nl.privacybarometer.privacyvandaag.BuildConfig;
import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import static nl.privacybarometer.privacyvandaag.Constants.FETCHMODE_DO_NOT_FETCH;

/**
 * Main data for communicating with and using the database
 */

public class FeedData {
    public static final String CONTENT = "content://";
    public static final String AUTHORITY = "nl.privacybarometer.privacyvandaag.provider.FeedData";
    public static final String CONTENT_AUTHORITY = CONTENT + AUTHORITY;
    public static final String FEEDS_TABLE_WITH_GROUP_PRIORITY = FeedColumns.TABLE_NAME + " LEFT JOIN (SELECT " + FeedColumns._ID + " AS joined_feed_id, " + FeedColumns.PRIORITY +
            " AS group_priority FROM " + FeedColumns.TABLE_NAME + ") AS f ON (" + FeedColumns.TABLE_NAME + '.' + FeedColumns.GROUP_ID + " = f.joined_feed_id)";
    public static final String ENTRIES_TABLE_WITH_FEED_INFO = EntryColumns.TABLE_NAME + " JOIN (SELECT " + FeedColumns._ID + " AS joined_feed_id, " + FeedColumns.NAME + ", " + FeedColumns.URL + ", " +
            FeedColumns.ICON + ", " + FeedColumns.ICON_DRAWABLE + ", " + FeedColumns.GROUP_ID + " FROM " + FeedColumns.TABLE_NAME + ") AS f ON (" + EntryColumns.TABLE_NAME + '.' + EntryColumns.FEED_ID + " = f.joined_feed_id)";
    public static final String ALL_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " + EntryColumns.IS_READ + " IS NULL)";

    // ModPrivacyVandaag: New query for counting all unread entries but ONLY from active feed channels.
    public static final String UNREAD_ALLE_ENTRIES_FROM_ACTIVE_FEEDS = "(SELECT COUNT(*) FROM " + EntryColumns.TABLE_NAME + " AS a INNER JOIN " + FeedColumns.TABLE_NAME + " AS b ON a." + EntryColumns.FEED_ID + " = b." + FeedColumns._ID + " WHERE a." + EntryColumns.IS_READ + " IS NULL " + Constants.DB_AND + " b." + FeedColumns.FETCH_MODE + " != " + FETCHMODE_DO_NOT_FETCH + " )";
    public static final String NUMBER_OF_ACTIVE_FEEDS = "(SELECT COUNT(*) FROM " + FeedColumns.TABLE_NAME + " WHERE " + FeedColumns.FETCH_MODE + " != " + FETCHMODE_DO_NOT_FETCH + " )";
    public static final String FAVORITES_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " + EntryColumns.IS_FAVORITE + Constants.DB_IS_TRUE + ')';
    static final String TYPE_PRIMARY_KEY = "INTEGER PRIMARY KEY AUTOINCREMENT";
    static final String TYPE_EXTERNAL_ID = "INTEGER(7)";
    static final String TYPE_TEXT = "TEXT";
    static final String TYPE_TEXT_UNIQUE = "TEXT UNIQUE";
    static final String TYPE_DATE_TIME = "DATETIME";
    static final String TYPE_INT = "INT";
    static final String TYPE_BOOLEAN = "INTEGER(1)";

        // Zet de bewaartermijn per feed in dagen. 0 = oneindig.
        // Laat de algemene instellingen bepalen hoe lang artikelen (behalve de evenementen) bewaard worden.
    static final int STANDAARD_BEWAARTERMIJNEN_ARTIKELEN = 0; // In dagen, bewaartermijn van artikelen



    public static ContentValues getReadContentValues() {
        ContentValues values = new ContentValues();
        values.put(EntryColumns.IS_READ, true);
        return values;
    }

    public static ContentValues getUnreadContentValues() {
        ContentValues values = new ContentValues();
        values.putNull(EntryColumns.IS_READ);
        return values;
    }

    public static boolean shouldShowReadEntries(Uri uri) {
        boolean alwaysShowRead = EntryColumns.FAVORITES_CONTENT_URI.equals(uri) || (FeedDataContentProvider.URI_MATCHER.match(uri) == FeedDataContentProvider.URI_SEARCH);
        return alwaysShowRead || PrefUtils.getBoolean(PrefUtils.SHOW_READ, true);
    }

    /**
     * Table containing the RSS feeds
     */
    public static class FeedColumns implements BaseColumns {
        public static final String TABLE_NAME = "feeds";

        public static final String URL = "url";
        public static final String NAME = "name";
        public static final String COOKIE_NAME = "cookiename";
        public static final String COOKIE_VALUE = "cookievalue";
        public static final String KEEP_TIME = "keeptime";
        public static final String NOTIFY = "notify";
        public static final String IS_GROUP = "isgroup";
        public static final String GROUP_ID = "groupid";
        public static final String LAST_UPDATE = "lastupdate";
        public static final String REAL_LAST_UPDATE = "reallastupdate";
        public static final String RETRIEVE_FULLTEXT = "retrievefulltext";
        public static final String ICON = "icon";   // name of the icon file in the res/drawable folder.
        public static final String ERROR = "error";
        public static final String PRIORITY = "priority";   // order of the feeds. The order is mostly handled 'out of sight' in FeedDataContentProvider.
        public static final String FETCH_MODE = "fetchmode";    // also used to determine if feed has to be refreshed. No if equals 99
        public static final String ICON_DRAWABLE = "icondrawable";    //  integer field to store the resource identifier to the icon drawable in the package
        public static final String[] PROJECTION_ID = new String[]{FeedColumns._ID};
        public static final String[] PROJECTION_GROUP_ID = new String[]{FeedColumns.GROUP_ID};
        public static final String[] PROJECTION_PRIORITY = new String[]{FeedColumns.PRIORITY};

        public static Uri CONTENT_URI(String feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId);
        }

        public static Uri CONTENT_URI(long feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId);
        }

        public static final String[][] COLUMNS = new String[][]{{_ID, TYPE_PRIMARY_KEY}, {URL, TYPE_TEXT_UNIQUE}, {NAME, TYPE_TEXT},{COOKIE_NAME, TYPE_TEXT},{COOKIE_VALUE, TYPE_TEXT}, {KEEP_TIME, TYPE_DATE_TIME},{IS_GROUP, TYPE_BOOLEAN},
                {GROUP_ID, TYPE_EXTERNAL_ID}, {LAST_UPDATE, TYPE_DATE_TIME}, {REAL_LAST_UPDATE, TYPE_DATE_TIME}, {RETRIEVE_FULLTEXT, TYPE_BOOLEAN},
                {ICON, "BLOB"}, {ERROR, TYPE_TEXT}, {PRIORITY, TYPE_INT}, {FETCH_MODE, TYPE_INT}, {ICON_DRAWABLE, TYPE_INT}, {NOTIFY, TYPE_BOOLEAN} };

        public static Uri GROUPS_CONTENT_URI(String groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId);
        }

        public static Uri GROUPS_CONTENT_URI(long groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId);
        }

        public static Uri FEEDS_FOR_GROUPS_CONTENT_URI(String groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId + "/feeds");
        }

        public static Uri FEEDS_FOR_GROUPS_CONTENT_URI(long groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId + "/feeds");
        }

        public static final Uri CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/feeds");

        public static final Uri RESET_SEQUENCE_URI = Uri.parse(CONTENT_AUTHORITY + "/reset_sequence/feeds");


        public static final Uri GROUPED_FEEDS_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/grouped_feeds");

        public static final Uri GROUPS_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/groups");


    }

    /**
     * Table containing filters
     */
    public static class FilterColumns implements BaseColumns {
        public static final String TABLE_NAME = "filters";

        public static final String FEED_ID = "feedid";
        public static final String FILTER_TEXT = "filtertext";
        public static final String IS_REGEX = "isregex";
        public static final String IS_APPLIED_TO_TITLE = "isappliedtotitle";
        public static final String IS_ACCEPT_RULE = "isacceptrule";

        public static Uri FILTERS_FOR_FEED_CONTENT_URI(String feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId + "/filters");
        }

        public static Uri FILTERS_FOR_FEED_CONTENT_URI(long feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId + "/filters");
        }

        public static final String[][] COLUMNS = new String[][]{{_ID, TYPE_PRIMARY_KEY}, {FEED_ID, TYPE_EXTERNAL_ID}, {FILTER_TEXT, TYPE_TEXT},
                {IS_REGEX, TYPE_BOOLEAN}, {IS_APPLIED_TO_TITLE, TYPE_BOOLEAN}, {IS_ACCEPT_RULE, TYPE_BOOLEAN}};

        public static final Uri CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/filters");
    }

    /**
     * Table containing the entries (= articles)
     */
    public static class EntryColumns implements BaseColumns {
        public static final String TABLE_NAME = "entries";

        public static final String FEED_ID = "feedid";
        public static final String TITLE = "title";
        public static final String ABSTRACT = "abstract";
        public static final String MOBILIZED_HTML = "mobilized";
        public static final String DATE = "date";
        public static final String FETCH_DATE = "fetch_date";
        public static final String IS_READ = "isread";
        public static final String LINK = "link";
        public static final String IS_FAVORITE = "favorite";
        public static final String ENCLOSURE = "enclosure";
        public static final String GUID = "guid";
        public static final String AUTHOR = "author";
        public static final String IMAGE_URL = "image_url";
        public static final String[] PROJECTION_ID = new String[]{EntryColumns._ID};
        public static final String WHERE_READ = EntryColumns.IS_READ + Constants.DB_IS_TRUE;
        public static final String WHERE_UNREAD = "(" + EntryColumns.IS_READ + Constants.DB_IS_NULL + Constants.DB_OR + EntryColumns.IS_READ + Constants.DB_IS_FALSE + ')';
        public static final String WHERE_NOT_FAVORITE = "(" + EntryColumns.IS_FAVORITE + Constants.DB_IS_NULL + Constants.DB_OR + EntryColumns.IS_FAVORITE + Constants.DB_IS_FALSE + ')';

        public static Uri ENTRIES_FOR_FEED_CONTENT_URI(String feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId + "/entries");
        }

        public static final String[][] COLUMNS = new String[][]{{_ID, TYPE_PRIMARY_KEY}, {FEED_ID, TYPE_EXTERNAL_ID}, {TITLE, TYPE_TEXT},
                {ABSTRACT, TYPE_TEXT}, {MOBILIZED_HTML, TYPE_TEXT}, {DATE, TYPE_DATE_TIME}, {FETCH_DATE, TYPE_DATE_TIME}, {IS_READ, TYPE_BOOLEAN}, {LINK, TYPE_TEXT},
                {IS_FAVORITE, TYPE_BOOLEAN}, {ENCLOSURE, TYPE_TEXT}, {GUID, TYPE_TEXT}, {AUTHOR, TYPE_TEXT}, {IMAGE_URL, TYPE_TEXT}};

        public static Uri ENTRIES_FOR_FEED_CONTENT_URI(long feedId) {
            return Uri.parse(CONTENT_AUTHORITY + "/feeds/" + feedId + "/entries");
        }

        public static Uri ENTRIES_FOR_GROUP_CONTENT_URI(String groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId + "/entries");
        }

        public static Uri ENTRIES_FOR_GROUP_CONTENT_URI(long groupId) {
            return Uri.parse(CONTENT_AUTHORITY + "/groups/" + groupId + "/entries");
        }

        public static Uri ALL_ENTRIES_CONTENT_URI(String entryId) {
            return Uri.parse(CONTENT_AUTHORITY + "/all_entries/" + entryId);
        }

        public static Uri CONTENT_URI(String entryId) {
            return Uri.parse(CONTENT_AUTHORITY + "/entries/" + entryId);
        }

        public static final Uri CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/entries");

        public static Uri CONTENT_URI(long entryId) {
            return Uri.parse(CONTENT_AUTHORITY + "/entries/" + entryId);
        }

        public static Uri PARENT_URI(String path) {
            return Uri.parse(CONTENT_AUTHORITY + path.substring(0, path.lastIndexOf('/')));
        }

        public static Uri SEARCH_URI(String search) {
            return Uri.parse(CONTENT_AUTHORITY + "/entries/search/" + (TextUtils.isEmpty(search) ? " " : Uri.encode(search))); // The space is mandatory here with empty search
        }


        public static final Uri ALL_ENTRIES_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/all_entries");


        public static final Uri FAVORITES_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/favorites");


    }

    /**
     * Table containing tasks for downloading images and full text belonging to items in RSS feed
     */
    public static class TaskColumns implements BaseColumns {
        public static final String TABLE_NAME = "tasks";

        public static final String ENTRY_ID = "entryid";
        public static final String IMG_URL_TO_DL = "imgurl_to_dl";
        public static final String NUMBER_ATTEMPT = "number_attempt";
        public static final String[] PROJECTION_ID = new String[]{EntryColumns._ID};

        public static Uri CONTENT_URI(String taskId) {
            return Uri.parse(CONTENT_AUTHORITY + "/tasks/" + taskId);
        }

        public static Uri CONTENT_URI(long taskId) {
            return Uri.parse(CONTENT_AUTHORITY + "/tasks/" + taskId);
        }

        public static final String[][] COLUMNS = new String[][]{{_ID, TYPE_PRIMARY_KEY}, {ENTRY_ID, TYPE_EXTERNAL_ID}, {IMG_URL_TO_DL, TYPE_TEXT},
                {NUMBER_ATTEMPT, TYPE_INT}, {"UNIQUE", "(" + ENTRY_ID + ", " + IMG_URL_TO_DL + ") ON CONFLICT IGNORE"}};

        public static final Uri CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + "/tasks");

    }



    /**
     * Add predefined Rss-feeds to the database.
     */

    public static void addPredefinedFeeds (Context context) {        // %2C is a comma
        /*
         * FeedDataContentProvider.addFeed(
         *      > context,
         *      > feed URL,
         *      > feed title,
         *      > retrieve full article or stick to RSS excerpt (true/false)
         *      > cookiename,
         *      > cookievalue,
         *      > time to keep articles before they are automatically deleted,
         *      > feed icon file name (without the .png extension)
         *  ");
         */
        FeedDataContentProvider.addFeed(context, "https://www.privacybarometer.nl/feed/", "Privacy Barometer", true, "logo_icon_pb");
        FeedDataContentProvider.addFeed(context, "https://www.bof.nl/feed/", "Bits of Freedom", true, "logo_icon_bof");
        FeedDataContentProvider.addFeed(context, "https://www.privacyfirst.nl/index.php?option=com_k2&view=itemlist&format=feed", "Privacy First", true, "logo_icon_pf");
        FeedDataContentProvider.addFeed(context, "https://www.vrijbit.nl/index.php?option=com_k2&view=itemlist&format=feed", "Vrijbit",  true, "logo_icon_vrijbit");
        FeedDataContentProvider.addFeed(context, "http://www.kdvp.nl/?format=feed&type=rss", "KDVP",  true, "logo_icon_kdvp");
        FeedDataContentProvider.addFeed(context, "https://autoriteitpersoonsgegevens.nl/nl/rss", "Autoriteit Persoonsgegevens",  false, "logo_icon_ap");
        FeedDataContentProvider.addFeed(context, "https://www.privacybarometer.nl/app/privacy_vandaag_tweetselectie_to_rss.php", "Privacy in het nieuws", false, "logo_icon_privacy_in_het_nieuws");
        FeedDataContentProvider.addFeed(context, "https://www.privacybarometer.nl/app/feed/" + BuildConfig.PRODUCT_FLAVOR, "Serviceberichten",  false, "logo_icon_serviceberichten_inverse"); // 61 is het aantal dagen dat items bewaard moeten worden.
   }





    /**
     * Add a predefined entry (= article)  to the database. This can be a welcome message
     *
     */

    /*

            It works, but not using it at the moment so put it in the comments


    public static void addPredefinedMessages () {
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        ContentValues values = new ContentValues();

        String mId = String.valueOf(SERVICE_CHANNEL_ID); // feedId van de systeemmessages, Zie ook addPredefinedFeeds() in regel 300 hierboven
        long mNow = System.currentTimeMillis();     // datumtijd van nu
        String title = "Welkom bij het Privacy Vandaag!";
        String content = "<p>Welkom bij Privacy Vandaag. Met deze app volgt u het belangrijkste nieuws over privacy en van de organisaties die zich inzetten voor privacy.</p>"
                 + "<p>De app is naar uw eigen wensen in te stellen. Zo kunt u bijvoorbeeld zelf instellen of u meldingen van nieuwe berichten wil krijgen. Kijkt u hiervoor bij de instellingen.</p>"
                + "<p>Mocht er nieuws over de app zijn, dan ontvangt u een bericht via dit kanaal.</p>"
                + "<p>Heeft u suggesties voor of opmerkingen over deze app, laat het ons weten. Mail uw suggesties naar <a href='mailto:info@privacyVandaag.nl'>info@privacyVandaag.nl</a>.</p>";
        String author = "redactie";
        String guid = "1 at www.privacyvandaag.nl/systeemmessage";
        String mainImageUrl = null;
        String entryLink = "";

        values.put(EntryColumns.TITLE, title);
        values.put(EntryColumns.ABSTRACT, content);
        values.put(EntryColumns.AUTHOR, author);
        values.put(EntryColumns.GUID, guid);
        values.put(EntryColumns.DATE, mNow);
        // values.put(EntryColumns.IMAGE_URL, mainImageUrl);
        // values.put(EntryColumns.LINK, entryLinkString);

        cr.insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(mId), values);


    }   // end predefined messages
    */

}
