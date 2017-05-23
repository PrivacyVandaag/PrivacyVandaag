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
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static nl.privacybarometer.privacyvandaag.activity.EditFeedsListActivity.MENU_HAS_BEEN_RESORTED;


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


    private static final int EMPTY_FEED_ID_FOR_SECTION_HEADERS = -1;
    // These values should be larger than MAX_REAL_FEED_ID. Max_FEED_ID seperates the real feeds from other items/pages.
    public static final int PSEUDO_FEED_ID_MEANING_FAVORITES = 99;
    public static final int PSEUDO_FEED_ID_MEANING_SEARCH = 98;
    public static final int PSEUDO_FEED_ID_MEANING_ALL_ENTRIES = 97;
    // All feeds in the app have a feedId. These are the feedId's in the database in the table that gholds the feeds.
    // Some menu-items behave like feeds but do not have articles of themselves, so they do not exist in the database.
    // These items get fake Id's to make them behave like feeds with a page in the ViewPager and a menuItem in the LeftDrawer
    // They also have a query to select and display articles from the database in a drawerMenuList.
    // Items like this are 'Favorites', 'Search' and 'All Articles'.
    // To distinguish these fake feeds from the real thing, they get Id's higer than MAX_REAL_FEED_ID.
    public final static int MAX_REAL_FEED_ID = 90; // values higer than MAX_REAL_FEED_ID are fake ID's for special pages like favorites and search




    private static final int EMPTY_CURSOR_POSITION = -1;
    private static final int START_SORTABLE_FEEDS_ITEMS = 3; // First 3 menu items are non-newsfeed items
    private static final int END_SORTABLE_FEEDS_ITEMS = 4;   // Last 3 menu items are non-newsfeed items


    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // 99 means 'Do not refresh feed'. Predefined feed is inactive.

    // Color settings for feeds in the menulist to account for inactive feeds.
    // TODO: Put it with other style-settings. Include preferences.
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

    private ArrayList<MenuItemObject> mMenuList;
    private int serviceChannelCursorPosition= EMPTY_CURSOR_POSITION;


    // Create the main list of menu items for the left drawer
    public DrawerAdapter(Context context, Cursor feedCursor) {
        mMenuList = new ArrayList<MenuItemObject>();
        mContext = context;
        mFeedsCursor = feedCursor;
        // Log.e (TAG, DatabaseUtils.dumpCursorToString(mFeedsCursor)); // See what's in the cursor

        // Create menuList that holds all the menu items and positions
        addFixedTopItems();
        addRssFeedItems();
        addFixedBottomItems ();

        // Update the numbers for the unread counters in Left Drawer Menu
        updateNumbers();
    }

    /**
     * Dataset in database has changed. New cursor is available.
     */
    public boolean setCursor(Cursor feedCursor, boolean rebuildViewPager) {
        // Log.e (TAG, "New cursor for Left Drawer");
        mFeedsCursor = feedCursor;

        // Update MenuList

        // ONLY if the menu has been resorted, it should be completely rebuild.
        // This value is set in EditFeedsListActivity.java
        if (PrefUtils.getBoolean (MENU_HAS_BEEN_RESORTED,false)) {
            // The items at the top and at the bottom are fixed, so no need to update them.
            // We only have to update the RSS feeds.
            // So we start with item 3 and end with item getCount - 4;
            updateRssFeedItems(START_SORTABLE_FEEDS_ITEMS, mMenuList.size() - END_SORTABLE_FEEDS_ITEMS);
            // reset the 'listener' whether menu has been resorted.
            PrefUtils.putBoolean (MENU_HAS_BEEN_RESORTED,false);
            rebuildViewPager = true;


        }
       // For debugging only
        /*
        for (MenuItemObject mObject : mMenuList){
            Log.e(TAG, "UPDATE Menu item :  " +  mObject.title);
            Log.e(TAG, "UPDATE Menu item :  " + mObject.feedId);
        }
        */

        // Update the numbers for the unread counters in Left Drawer Menu
        updateNumbers();
        notifyDataSetChanged();
        return rebuildViewPager;
    }



    //*** HANDLE THE LEFT DRAWER VIEWS *** HANDLE THE LEFT DRAWER VIEWS *** HANDLE THE LEFT DRAWER VIEWS

    // Start building the left drawer menu item voor item
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (mMenuList.get(position).isSectionHeader) {  // It's a header
            view = getSectionHeaderView(position, convertView, parent);
        } else {    // It's a menu item
            view = getItemView(position, convertView, parent);
        }
        return view;
    }


    /**
     * *****  VIEW MENU SECTION HEADER
     */
    private View getSectionHeaderView(int menuPosition, View convertView, ViewGroup parent) {
        // If we already have a View, use it. Inflating new ones is expensive.
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
        holderHeader.titleTxt.setText(mMenuList.get(menuPosition).title);
        holderHeader.titleTxt.setTextColor(NORMAL_TEXT_COLOR);
        holderHeader.titleTxt.setAllCaps(true);
        convertView.setPadding(0, 0, 0, 0);
        holderHeader.separator.setVisibility(View.VISIBLE);
        return convertView;
    }


    /**
     * *****   VIEW MENU ITEM
     */

    private View getItemView(int menuPosition, View convertView, ViewGroup parent) {
        // If we already have a View, use it. Inflating new ones is expensive.
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

        // Start displaying the feeds in left drawer menu
        holder.titleTxt.setText(mMenuList.get(menuPosition).title);
        holder.iconView.setImageResource(mMenuList.get(menuPosition).iconDrawable);

        // Set number of favorites for favorites channel
        if (mMenuList.get(menuPosition).feedId == PSEUDO_FEED_ID_MEANING_FAVORITES)
        {
            if (mFavoritesNumber != 0) {
                holder.unreadTxt.setText(String.valueOf(mFavoritesNumber));
            }
        }

        // retrieve the cursorPosition for the feed by looking at the menuPosition
        // cursorPosition is the specific row in the query results table where the
        // data concerning the current item can be found.
        int cursorPosition = mMenuList.get(menuPosition).cursorPosition;   // cursor positie is het nummer van de rij in de feeds Tabel.
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            if (cursorPosition != serviceChannelCursorPosition) { // if not the channel with app related service messages
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

                        // else there is an error reading the feed. Most likely it is a format error in the
                        // RSS feed itself. Check the original rss feed with a feed-validator on the internet.
                    } else {
                            //holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error)).append(COLON).append(mFeedsCursor.getString(POS_ERROR)));
                            holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error_fetching_feed)));
                            Log.e("Privacy Vandaag", mContext.getString(R.string.error_fetching_feed));
                            Log.e("Privacy Vandaag", "refresh fout: " + mFeedsCursor.getString(POS_ERROR));
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
                int mIconResourceId = mMenuList.get(menuPosition).iconDrawable;
                if (mMenuList.get(menuPosition).title.indexOf("toriteit") > 0) { // Exception for feedchannel AP, because the main logo is not visible in dark background
                    holder.iconView.setImageResource(R.drawable.logo_icon_ap_witte_achtergrond);
                } else if (mIconResourceId > 0) {
                    holder.iconView.setImageResource(mIconResourceId);
                } else {    // if image not available, take the apps logo
                    holder.iconView.setImageResource(R.drawable.ic_statusbar_pv);
                }
            }
        return convertView;
    }

    // Object of a view in the left drawer
    private static class ViewHolder {
        private ImageView iconView;
        private TextView titleTxt;
        private TextView stateTxt;
        private TextView unreadTxt;
        private ImageView noNotification;
        private View separator;
    }

    // *** END OF THE LEFT DRAWER VIEWS ***




    //*** MENU LOGIC *** MENU LOGIC *** MENU LOGIC *** MENU LOGIC *** MENU LOGIC *** MENU LOGIC

    // Sectie headers in the menu are not real items and are 'disabled'.
    @Override
    public boolean isEnabled(int menuPosition) {
        return ( ! mMenuList.get(menuPosition).isSectionHeader);
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int menuPosition) {
        return mMenuList.get(menuPosition).feedId;
    }

    // Number of items in the menu including the section headers
    @Override
    public int getCount() {
        return mMenuList.size();
    }

    // Get the domain of the feed to go to (through the browser) from the LeftDrawer context menu.
    public String getWebsite(int menuPosition) {
        int cursorPosition = mMenuList.get(menuPosition).cursorPosition;
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            String fullUrl = mFeedsCursor.getString(POS_URL);
            int xPos = fullUrl.indexOf("/", 8);
            return (xPos > 0) ? fullUrl.substring(0, xPos) : "";
        }
        return ""; // We'll never get here hopefully.
    }


    // Check whether notifications for the feed are on.
    public boolean getNotifyMode(int menuPosition) {
        int cursorPosition = mMenuList.get(menuPosition).cursorPosition;
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(cursorPosition)) {
            return (mFeedsCursor.getInt(POS_NOTIFY) > 0); // There is no boolean in SQLite, so int = 0 or 1
        }
        return true; // We'll never get here hopefully.
    }

    // Stel in of er meldingen / notificaties van nieuwe berichten van dit menu-item (feed) in de LeftDrawer
    // wel of niet moeten worden gemaakt.
    public boolean setNotifyMode(int menuPosition, boolean notifyMode) {
        final int newNotifyMode = (notifyMode) ? 1 : 0;
        // store the new value for this feed in the database. No need to use it in mMenuList
        ContentValues values = new ContentValues();
        ContentResolver cr = mContext.getContentResolver();
        values.put(FeedData.FeedColumns.NOTIFY, newNotifyMode);
        return (cr.update(FeedData.FeedColumns.CONTENT_URI(mMenuList.get(menuPosition).feedId), values, null, null) > 0);
    }


    // Stel in of berichten van dit menu-item (feed) in de LeftDrawer wel of niet opgehaald moeten worden.
    public boolean setFetchMode(int menuPosition, boolean fetchMode) {
        final int newFetchMode = (fetchMode) ? 0 : FETCHMODE_DO_NOT_FETCH;
        // set the new value for the menu item in the mMenulist.
        mMenuList.get(menuPosition).fetchMode = newFetchMode;
        mMenuList.get(menuPosition).hasViewPagerPage = fetchMode;
        // store the new value for this feed in the database.
        ContentValues values = new ContentValues();
        ContentResolver cr = mContext.getContentResolver();
        values.put(FeedData.FeedColumns.FETCH_MODE, newFetchMode); // 99 IS DO NOT FETCH this feed
        boolean success = (cr.update(FeedData.FeedColumns.CONTENT_URI(mMenuList.get(menuPosition).feedId), values, null, null) > 0);
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



/**
 * Part to create the main menu
 *
 * We want to create the following layout of the menu in the Left Drawer
 *
 * 0 - Section Header news
 * 1 -      query all entries
 * 2 - Section Header organisations
 * 3 -      feed organisation 1
 * 4 -      feed organisation 2
 * ...
 * ...
 * n -      feed organisation n
 * n+1 - Section Header overig
 * n+2 -    query favorites
 * n+3 -    query search
 * n+4 -    feed service messages
 *
 * We store only the feedId's in the mMenuList. The order of the drawerMenuList is the order of the menu.
 * See provider > FeedData.java for more info about the feeds
 *
 */


    /**
     * Add the fixed items to the top/beginning of the MenuList
     */
    private void addFixedTopItems() {
        // Add section header
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.menu_section_news))
        );
        // Add menu item with drawerMenuList of all entriews (articles)
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.all),false, PSEUDO_FEED_ID_MEANING_ALL_ENTRIES,-1, R.drawable.ic_statusbar_rss, EMPTY_CURSOR_POSITION,true)
        );
        // Add sectionn header for the drawerMenuList with individual feeds
        mMenuList.add (
                new MenuItemObject(mContext.getString(R.string.menu_section_organisations))
        );
    }

    /**
     * Add all the individual rss feeds to the MenuList
     */
    private void addRssFeedItems() {
        String feedName;
        int fetchMode;
        // start looping trough all available individual feeds
        if (mFeedsCursor != null ) {
            mFeedsCursor.moveToPosition(-1);
            // Just add items to the menu in the order they are in the cursor.
            // The query this cursor orders the result according the PRIORITY field ASC.
            // (see HomeActivity, line 600 and FeedDataContentProvider on line 310: URI_GROUPED_FEEDS)
            while (mFeedsCursor.moveToNext()) {
                feedName = mFeedsCursor.getString(POS_NAME);
                if (feedName.contains("Serviceberichten")) {
                    // It is the service channel! This should not be in this part of the menu.
                    // Store it's feedId temporarily aand add it in addFixedBottomItems() to the menuList.
                    serviceChannelCursorPosition = mFeedsCursor.getPosition();
                } else {
                    fetchMode = mFeedsCursor.getInt(POS_FETCH_MODE);
                    // add the feed to the menu
                    //   (String title, boolean isSectionHeader, int feed, int fetchMode, int iconResourceId,int cursorPosition, boolean hasViewPagerPage)
                    mMenuList.add(
                            new MenuItemObject(feedName, false, (int) mFeedsCursor.getLong(POS_ID),
                                    fetchMode, mFeedsCursor.getInt(POS_ICON_DRAWABLE),mFeedsCursor.getPosition(), (fetchMode != FETCHMODE_DO_NOT_FETCH) )
                    );

                }
            }
        }
    }


    /**
     * The items at the top and at the bottom of the navigation menu are fixed,
     * but the feeds in between can be sorted.
     * @param startIndex: menuposition where the drawerMenuList of sortable feeds starts
     * @param endIndex: menuposition where the lists of sortable feeds ends.
     */
    private void updateRssFeedItems (int startIndex,int endIndex) {
        int fetchMode;
        int cursorPosition;
        if (mFeedsCursor != null ) { // Check if cursor is not null
            mFeedsCursor.moveToPosition(-1);    // Set the cursor to the starting position

            for(int i=startIndex; i<endIndex; i++) {
                if (mFeedsCursor.moveToNext()) {    // iterate trhough the cursor
                    cursorPosition = mFeedsCursor.getPosition();
                    // Log.e (TAG, "Updating: " + mFeedsCursor.getString(POS_NAME) );
                    if ( cursorPosition != serviceChannelCursorPosition) {  // The channel with service messages is in the bottom menu
                        fetchMode = mFeedsCursor.getInt(POS_FETCH_MODE);
                        // update the menu item 'i' with new values from the cursor at position 'cursorPosition'.
                        mMenuList.get(i).update(mFeedsCursor.getString(POS_NAME),
                                false, (int) mFeedsCursor.getLong(POS_ID),
                                fetchMode, mFeedsCursor.getInt(POS_ICON_DRAWABLE),
                                cursorPosition, (fetchMode != FETCHMODE_DO_NOT_FETCH)
                        );
                    }
                }
            }
        }
    }



    /**
     * Add the fixed items to the bottom/end of the MenuList
     */
    private void addFixedBottomItems() {
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.menu_section_other))
        );
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.favorites),false, PSEUDO_FEED_ID_MEANING_FAVORITES,-1,R.drawable.rating_important,EMPTY_CURSOR_POSITION,true)
        );
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.search),false, PSEUDO_FEED_ID_MEANING_SEARCH,-1,R.drawable.action_search,EMPTY_CURSOR_POSITION,true)
        );
        if (serviceChannelCursorPosition > -1 ) {    // We've got a rss channel for service messages
            mFeedsCursor.moveToPosition(serviceChannelCursorPosition);
            mMenuList.add(
                    new MenuItemObject(mFeedsCursor.getString(POS_NAME), false, (int) mFeedsCursor.getLong(POS_ID),
                            mFeedsCursor.getInt(POS_FETCH_MODE),R.drawable.logo_icon_serviceberichten, serviceChannelCursorPosition,true)
            );
        }
    }


    // Get the complete drawerMenuList of menu items
    public ArrayList<MenuItemObject> drawerMenuList() {
        return mMenuList;
    }
    // Get feedId from item's drawer position
    public long getFeedId(int menuposition) {
        return mMenuList.get(menuposition).feedId;
    }
    // Check whether a menu item has it's own page in the ViewPager
    public boolean hasPage(int menuposition) {
        // TODO: No need to keep seperate variable. Can be replaced by ...viewPagerPagePosition > -1
        return mMenuList.get(menuposition).hasViewPagerPage;
    }
    // Get title from item's drawer position
    public String getTitle(int menuposition) {
        return mMenuList.get(menuposition).title;
    }
    // Get icon from item's drawer position
    public int getIconDrawable(int menuposition) {
        return mMenuList.get(menuposition).iconDrawable;
    }
    // Get ViewPager page position from item's drawer position
    public int getViewPagerPagePosition(int menuposition) {
        return mMenuList.get(menuposition).viewPagerPagePosition;
    }

    public int getMenuPositionFromPagePosition(int pagePosition) {
        for(int i = 0; i < mMenuList.size(); ++i) {
            if(mMenuList.get(i).viewPagerPagePosition == pagePosition) return i;
        }
        return -1;
    }
    // Check if feed is to be refreshed or not
    public boolean hasActiveFetchMode(int menuPosition) {
        return ( mMenuList.get(menuPosition).fetchMode != FETCHMODE_DO_NOT_FETCH );
    }
    // Check whether menu item gets a contextMenu in the left drawer.
    public boolean hasContextMenu (int menuPosition) {
        return (mMenuList.get(menuPosition).cursorPosition != serviceChannelCursorPosition) &&
                (mMenuList.get(menuPosition).feedId > 0 && mMenuList.get(menuPosition).feedId < MAX_REAL_FEED_ID);
    }
    // Has the menu-item that has a contextmenu also a website to go to from that contextmenu?
    public boolean hasWebsite (int menuPosition) {
        return ( ! mMenuList.get(menuPosition).title.contains("in het nieuws"));
    }
    // Not very efficient, but is needed only once in HomeActivity if app starts from notification
    public int getMenuPositionFromFeedId(int feedId) {
        // notifications can only come from feeds
        for(int i = START_SORTABLE_FEEDS_ITEMS; i < mMenuList.size(); i++) {
            if(mMenuList.get(i).feedId==feedId) return i;
        }
        return -1;
    }




    /**
     * Inner Class to store menu-items in.
     * Each menu-item is an object. The lot is stored in an array.
     */
    public class MenuItemObject { // has to be public!

        // TODO: Why do we have fetchMode and hasViewPagerPage? Both state the same.
        private String title;
        public boolean hasViewPagerPage;
        public int viewPagerPagePosition;
        public int feedId;
        private int fetchMode;
        private int iconDrawable;
        private int cursorPosition;
        private boolean isSectionHeader;

        // Simplified constructor for section headers. fetchMode set to -1 (is not used), iconId = -1 and hasViewPagerPage set to false
        private MenuItemObject  (String title) {
            this(title, true, EMPTY_FEED_ID_FOR_SECTION_HEADERS, -1, -1, EMPTY_CURSOR_POSITION ,false);  // call to main constructor
        }

        // Main constructor
        private MenuItemObject  (String title, boolean isSectionHeader, int feed, int fetchMode, int iconResourceId,int cursorPosition, boolean hasViewPagerPage) {
            this.title = title; // To avoid nullpointers, all items get a (empty) string
            this.isSectionHeader = isSectionHeader;    // Menu items that are no items but section header get true
            this.feedId = feed;    // id of the feed in the database
            this.fetchMode = fetchMode;    // should the RSS feed be followed? fetchMode == 99 means feed is inactive. Page with articels is hidden. No new articles ar fetched.
            this.hasViewPagerPage = hasViewPagerPage;    // item has it's own page in the ViewPager in HomeActivity (if it is not hidden through fetchMode 99)
            this.viewPagerPagePosition = -1;    // position of the page in the PageViewer is set in HomeActivity > ViewPager
            this.iconDrawable = iconResourceId;
            this.cursorPosition = cursorPosition;
        }

        private void update (String title, boolean isSectionHeader, int feed, int fetchMode, int iconResourceId,int cursorPosition, boolean hasViewPagerPage) {
            this.title = title; // To avoid nullpointers, all items get a (empty) string
            this.isSectionHeader = isSectionHeader;    // Menu items that are no items but section header get true
            this.feedId = feed;    // id of the feed in the database
            this.fetchMode = fetchMode;    // should the RSS feed be followed? fetchMode == 99 means feed is inactive. Page with articels is hidden. No new articles ar fetched.
            this.hasViewPagerPage = hasViewPagerPage;    // item has it's own page in the ViewPager in HomeActivity (if it is not hidden through fetchMode 99)
            this.viewPagerPagePosition = -1;    // position of the page in the PageViewer is set in HomeActivity > ViewPager
            this.iconDrawable = iconResourceId;
            this.cursorPosition = cursorPosition;
        }

    }   // End of inner class MenuItemObject

}