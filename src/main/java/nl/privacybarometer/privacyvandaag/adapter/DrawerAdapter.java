/**
 * Privacy Vandaag
 * <p/>
 * Copyright (c) 2015-2017 Privacy Barometer
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

package nl.privacybarometer.privacyvandaag.adapter;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.MenuPrivacyVandaag;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * DrawerAdapter creates the left drawer menu.
 * Reads the information about feeds from the database and puts it in the left drawer menu.
 *
 * Look for the order of menu items at the end of this class.
 *
 * Layout is found in layout > section_header_drawer_list.xml en
 *      layout > item_drawer_list.xml
 */
public class DrawerAdapter extends BaseAdapter {
    private static final String TAG = DrawerAdapter.class.getSimpleName() + " ~> ";
    // These positions corresponds to the CursorLoader construction from HomeActivity on line 525
    // in order to set up the left drawer menu.
    // De POS_ posities komen overeen met de kolommen in antwoord op de query (=cursor)naar de database
    private static final int POS_ID = 0;
    private static final int POS_URL = 1;
    private static final int POS_NAME = 2;
    private static final int POS_IS_GROUP = 3;
    private static final int POS_ICON = 4;  // To get the favicon-bitmap from the database, if icons are not included in resources but fetched from websites.
    private static final int POS_LAST_UPDATE = 5;
    private static final int POS_ERROR = 6;
    private static final int POS_UNREAD = 7;
    private static final int POS_FETCH_MODE = 8;    // FetchMode is needed to check if for active or inactive feeds (no refresh).
    private static final int POS_ICON_DRAWABLE = 9;    // To get the right icon of the feed from the resources.
    private static final int POS_NOTIFY = 10;    // To get the right icon of the feed from the resources.


    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // 99 means 'Do not refresh feed'. Predefined feed is inactive.
    // Color settings for feeds in the menulist to account for inactive feeds. // TODO: Put it with other style-settings. Include preferences.
    private static final int DO_NOT_FETCH_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#FF4c4c4c");
    private static final int DO_NOT_FETCH_STATE_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#808080");
    private static final int DO_NOT_FETCH_TEXT_COLOR_DARK_THEME = Color.parseColor("#FFb2b2b2");
    private static final int NORMAL_STATE_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#FFFFFF");
    private static final int NORMAL_TEXT_COLOR = Color.parseColor("#EEEEEE");
    private static final int GROUP_TEXT_COLOR = Color.parseColor("#BBBBBB");


    private static final String COLON = MainApplication.getContext().getString(R.string.colon);

    private static final int CACHE_MAX_ENTRIES = 100;
    private final Map<Long, String> mFormattedDateCache = new LinkedHashMap<Long, String>(CACHE_MAX_ENTRIES + 1, .75F, true) {
        @Override
        public boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    private final Context mContext;
    private Cursor mFeedsCursor;
    private int mAllUnreadNumber, mFavoritesNumber;



    // Create left drawer menu
    public DrawerAdapter(Context context, Cursor feedCursor) {
        mContext = context;
        mFeedsCursor = feedCursor;
        updateNumbers();
    }

    public void setCursor(Cursor feedCursor) {
        mFeedsCursor = feedCursor;
        updateNumbers();
        notifyDataSetChanged();
    }


    // Start building the left drawer menu item voor item
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (MenuPrivacyVandaag.isSectionHeader(position)) {  // het gaat om een header
            view = getSectionHeaderView(position, convertView, parent);
        } else {    // Het gaat om een menu item
            view = getItemView(position, convertView, parent);
        }
        return view;
    }


    /**
     * *****   MENU SECTION HEADER   ******   MENU SECTION HEADER  ******   MENU SECTION HEADER  ******
     */
    private View getSectionHeaderView(int menuPosition, View convertView, ViewGroup parent) {
        // Inflate view - No use using convertView, because a header is always on its own
        if (convertView == null || convertView.getTag(R.id.holder_header) == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.section_header_drawer_list, parent, false);
            ViewHolder holderHeader = new ViewHolder();
            holderHeader.titleTxt = (TextView) convertView.findViewById(android.R.id.text1);
            holderHeader.separator = convertView.findViewById(R.id.separator);
            convertView.setTag(R.id.holder_header, holderHeader);
        }

        ViewHolder holderHeader = (ViewHolder) convertView.getTag(R.id.holder_header);

        // Set the title
        holderHeader.titleTxt.setText(MenuPrivacyVandaag.sectionTitle(menuPosition));
        holderHeader.titleTxt.setTextColor(NORMAL_TEXT_COLOR);
        holderHeader.titleTxt.setAllCaps(true);
        convertView.setPadding(0, 0, 0, 0);
        holderHeader.separator.setVisibility(View.VISIBLE);
        return convertView;  // No need to return the convertView, because the next element is always an item, so convertView is useless
    }


    /**
     * *****   MENU ITEM   ******   MENU ITEM   ******   MENU ITEM   ******   MENU ITEM   ******
     */

    private View getItemView(int menuPosition, View convertView, ViewGroup parent) {
        // Als er nog geen view in de ViewHolder zit, maak het object dan aan.
        if (convertView == null || convertView.getTag(R.id.holder) == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_drawer_list, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.iconView = (ImageView) convertView.findViewById(android.R.id.icon);
            holder.titleTxt = (TextView) convertView.findViewById(android.R.id.text1);
            holder.stateTxt = (TextView) convertView.findViewById(android.R.id.text2);
            holder.unreadTxt = (TextView) convertView.findViewById(R.id.unread_count);
            holder.noNotification = (ImageView) convertView.findViewById(R.id.no_notification);
            holder.separator = convertView.findViewById(R.id.separator);
            convertView.setTag(R.id.holder, holder);
        }

        ViewHolder holder = (ViewHolder) convertView.getTag(R.id.holder);

        // default init
        holder.iconView.setImageDrawable(null);
        holder.titleTxt.setText("");
        holder.titleTxt.setTextColor(NORMAL_TEXT_COLOR);
        holder.titleTxt.setAllCaps(false);
        holder.stateTxt.setVisibility(View.GONE);
        holder.noNotification.setVisibility(View.GONE);
        holder.unreadTxt.setText("");
        convertView.setPadding(0, 0, 0, 0);
        holder.separator.setVisibility(View.GONE);

        // retrieve the cursorPosition for the feed by looking at the menuPosition
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);   // cursor positie is het nummer van de rij in de feeds Tabel.
        // Start displaying the feeds in left drawer menu
        if (MenuPrivacyVandaag.isFavorites(menuPosition)) { // het gaat om de favorieten
            holder.titleTxt.setText(R.string.favorites);
            holder.iconView.setImageResource(R.drawable.rating_important);
            int unread = mFavoritesNumber;
            if (unread != 0) {
                holder.unreadTxt.setText(String.valueOf(unread));
            }
        } else if (MenuPrivacyVandaag.isSearch(menuPosition)) { // het gaat om zoeken
            holder.titleTxt.setText(R.string.search);
            holder.iconView.setImageResource(R.drawable.action_search);

        } else if (MenuPrivacyVandaag.isAllEntries(menuPosition)) { // het gaat om de verzamelde stroom artikelen
            holder.titleTxt.setText(R.string.all);
            holder.iconView.setImageResource(R.drawable.ic_statusbar_rss);

        } else if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            // cursorPosition is the specific row in the query results table where the
            // data concerning the current item can be found.
            String feedName = (mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME));
            holder.titleTxt.setText(feedName);
            // If it is a group of feeds // TODO: remove the group function. We do not use this.
            if (mFeedsCursor.getInt(POS_IS_GROUP) == 1) {
                holder.titleTxt.setTextColor(GROUP_TEXT_COLOR);
                holder.titleTxt.setAllCaps(true);
                holder.separator.setVisibility(View.VISIBLE);
            }
            // else it is a single feed
            else {
                if (!MenuPrivacyVandaag.isServiceChannel(menuPosition)) { // if not the channel with app related service messages
                    holder.stateTxt.setVisibility(View.VISIBLE);
                    // Check whether this is an active feed or one that is not to be refreshed.
                    if (!(mFeedsCursor.getInt(POS_FETCH_MODE) == FETCHMODE_DO_NOT_FETCH)) {
                        if (mFeedsCursor.isNull(POS_ERROR)) { // if it is an active feed and no error
                            long timestamp = mFeedsCursor.getLong(POS_LAST_UPDATE);
                            // Date formatting is expensive, look at the cache
                            String formattedDate = mFormattedDateCache.get(timestamp);
                            if (formattedDate == null) {
                                formattedDate = mContext.getString(R.string.update) + COLON;
                                if (timestamp == 0) {
                                    formattedDate += mContext.getString(R.string.never);
                                } else {
                                    formattedDate += StringUtils.getDateTimeStringSimple(timestamp);
                                }
                                mFormattedDateCache.put(timestamp, formattedDate);
                            }
                            holder.stateTxt.setText(formattedDate);
                            holder.stateTxt.setTextColor(NORMAL_STATE_TEXT_COLOR_LIGHT_THEME);
                            // Get and display number of unread articles
                            int unread = mFeedsCursor.getInt(POS_UNREAD);
                            if (unread != 0) {
                                holder.unreadTxt.setText(String.valueOf(unread));
                            }

                        }
                        // else there is an error reading the feed. Most likely it is a format error in the
                        // RSS feed itself. Check the original rss feed with a feed-validator on the internet.
                        else {
                            //holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error)).append(COLON).append(mFeedsCursor.getString(POS_ERROR)));
                            holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error_fetching_feed)));
                            Log.e("PrivacyVandaag", mContext.getString(R.string.error_fetching_feed));
                            Log.e("PrivacyVandaag", "refresh fout: " + mFeedsCursor.getString(POS_ERROR));
                        }

                        // Check of de meldingen aan of uitstaan
                        // If the article is favorited, show the favorite image over the title.
                        boolean mNotify = mFeedsCursor.getInt(POS_NOTIFY) > 0;
                        holder.noNotification.setVisibility(mNotify ? View.GONE : View.VISIBLE);
                        holder.iconView.setAlpha(1f);
                    }
                    // Feed is inactief. Zet alles uit en/of op donker
                    else {
                        holder.stateTxt.setText(mContext.getString(R.string.dont_follow_feed));
                        holder.iconView.setAlpha(0.3f);
                        holder.unreadTxt.setText("");
                        holder.stateTxt.setTextColor(DO_NOT_FETCH_STATE_TEXT_COLOR_LIGHT_THEME);
                        holder.titleTxt.setTextColor(DO_NOT_FETCH_TEXT_COLOR_LIGHT_THEME);
                        holder.noNotification.setVisibility(View.GONE);
                    }
                }

                // Do not get favicons from internet, get icons from package instead
                int mIconResourceId = mFeedsCursor.getInt(POS_ICON_DRAWABLE);
                if (feedName.indexOf("toriteit") > 0) { // Exception for feedchannel AP, because the main logo is not visible in dark background
                    holder.iconView.setImageResource(R.drawable.logo_icon_ap_witte_achtergrond);
                } else if (mIconResourceId > 0) {
                    holder.iconView.setImageResource(mIconResourceId);
                } else {    // if image not available, take the apps logo
                    holder.iconView.setImageResource(R.drawable.ic_statusbar_pv);
                }
                            /* If the reader should use favicons from the feeds websites take the code below.
                                    // Get and display favicon if available
                                    final long feedId = mFeedsCursor.getLong(POS_ID);
                                    Bitmap bitmap = UiUtils.getFaviconBitmap(feedId, mFeedsCursor, POS_ICON);
                                    if (bitmap != null) {
                                        holder.iconView.setImageBitmap(bitmap);
                                    } else {
                                        holder.iconView.setImageResource(R.mipmap.ic_launcher);
                                    }
                             */
            }
        }
        return convertView;
    }


    // De sectie-titels in het menu zijn geen echte items en worden 'disabled'.
    @Override
    public boolean isEnabled(int menuPosition) {
        return MenuPrivacyVandaag.isEnabled(menuPosition);
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int menuPosition) {
        // retrieve the cursorPosition for the feed by looking at the menuPosition
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return mFeedsCursor.getLong(POS_ID);
        }
        return -1;
    }

    @Override
    public int getCount() {
        return MenuPrivacyVandaag.getCount();
}

    // Haal het logo uit de database. Hebben we niet nodig, want de logo's zijn als .png's
    //direct beschikbaar. Zie de map res > drawables.
    /*
    public byte[] getItemIcon(int menuPosition) {
        int cursorPosition = getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return mFeedsCursor.getBlob(POS_ICON);
        }
        return null;
    }
    */

    // Haal het resource ID voor het logo uit de database
    public int getIconResourceId(int menuPosition) {
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return mFeedsCursor.getInt(POS_ICON_DRAWABLE);
        }
        return 0;
    }

    // Haal de naam (titel) van de feed uit de database
    public String getItemName(int menuPosition) {
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME);
        }
        return null;
    }

    // Haal de URL van de website op om naar toe te gaan vanuit het LeftDrawer menu.
    public String getWebsite(int menuPosition) {
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            String fullUrl = mFeedsCursor.getString(POS_URL);
            int xPos = fullUrl.indexOf("/", 8);
            return (xPos > 0) ? fullUrl.substring(0, xPos) : "";
        }
        return ""; // Als het goed is, komen we hier nooit.
    }


    // Check of er meldingen / notifications van een feed moeten worden gemaakt.
    public boolean getNotifyMode(int menuPosition) {
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return (mFeedsCursor.getInt(POS_NOTIFY) > 0); // Er bestaat geen boolean in SQLite, dus int = 0 of 1
        }
        return true; // Als het goed is, komen we hier nooit.
    }

    // Stel in of er meldingen / notificaties van nieuwe berichten van dit menu-item (feed) in de LeftDrawer
    // wel of niet moeten worden gemaakt.
    public boolean setNotifyMode(Context mContext, int menuPosition, boolean notifyMode) {
        final int newNotifyMode = (notifyMode) ? 1 : 0;
        final long feedId =  (long) MenuPrivacyVandaag.getFeedIdFromMenuPosition(menuPosition);
        ContentValues values = new ContentValues();
        ContentResolver cr = mContext.getContentResolver();
        values.put(FeedData.FeedColumns.NOTIFY, newNotifyMode);
        return (cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null) > 0);
    }

    // Check of de berichten van dit menu-item (feed) in de LeftDrawer opgehaald / gevolgd moeten worden
    public boolean getFetchMode(int menuPosition) {
        int cursorPosition = MenuPrivacyVandaag.getCursorPosition(menuPosition);
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return !(mFeedsCursor.getInt(POS_FETCH_MODE) == FETCHMODE_DO_NOT_FETCH);
        }
        return true; // Als het goed is, komen we hier nooit.
    }

    // Stel in of berichten van dit menu-item (feed) in de LeftDrawer wel of niet opgehaald moeten worden.
    public boolean setFetchMode(Context mContext, int menuPosition, boolean fetchMode) {
        final int newFetchMode = (fetchMode) ? 0 : FETCHMODE_DO_NOT_FETCH;
        final long feedId =  (long) MenuPrivacyVandaag.getFeedIdFromMenuPosition(menuPosition);
        ContentValues values = new ContentValues();
        ContentResolver cr = mContext.getContentResolver();
        values.put(FeedData.FeedColumns.FETCH_MODE, newFetchMode); // 99 IS DO NOT FETCH this feed
       // boolean success = (cr.update(FeedData.FeedColumns.CONTENT_URI(menuItemsArray[menuPosition].feedId), values, null, null) > 0);
        boolean success = (cr.update(FeedData.FeedColumns.CONTENT_URI(feedId), values, null, null) > 0);
        cr.notifyChange(FeedData.FeedColumns.CONTENT_URI, null);
        return success;


    }

    // Update de nummers van aantal ongelezen artikelen en aantal favoriete artikelen.
    private void updateNumbers() {
        mAllUnreadNumber = mFavoritesNumber = 0;
        // Get the numbers of entries (should be in a thread, but it's way easier this way and it shouldn't be too slow).
        Cursor numbers = mContext.getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{FeedData.UNREAD_ALLE_ENTRIES_FROM_ACTIVE_FEEDS, FeedData.FAVORITES_NUMBER}, null, null, null);
        // If the total number of unread articles should be displayed, used the code beneath instead.
        // Cursor numbers = mContext.getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{FeedData.ALL_UNREAD_NUMBER, FeedData.FAVORITES_NUMBER}, null, null, null);
        if (numbers != null) {
            if (numbers.moveToFirst()) {
                mAllUnreadNumber = numbers.getInt(0);
                mFavoritesNumber = numbers.getInt(1);
            }
            numbers.close();
        }
    }

    // Het object van een item in de left drawer
    private static class ViewHolder {
        private ImageView iconView;
        private TextView titleTxt;
        private TextView stateTxt;
        private TextView unreadTxt;
        private ImageView noNotification;
        private View separator;
    }


}