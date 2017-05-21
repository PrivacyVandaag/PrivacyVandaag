package nl.privacybarometer.privacyvandaag.adapter;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;

import nl.privacybarometer.privacyvandaag.R;

import static nl.privacybarometer.privacyvandaag.Constants.FETCHMODE_DO_NOT_FETCH;


/**
 * Class to create the main menu
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


public class MenuListAdapter {
    private static final String TAG = MenuListAdapter.class.getSimpleName() + " ~> ";
    private final Context mContext;
    private Cursor mFeedsCursor;

    // These positions corresponds to the CursorLoader construction from HomeActivity on line 525
    // in order to set up the left drawer menu.
    // NOTICE: These values are the same as in the DrawerAdapter.java,
    // because the same cursor / query is used!
    private static final int POS_ID = 0; // Position of the feedId in the resulting cursor table
    private static final int POS_NAME = 2; // Position of the feedName in the resulting cursor table
    private static final int POS_ICON_DRAWABLE = 9;    // To get the right icon of the feed from the resources.
    // FetchMode is needed to check if for active or inactive no-refreshable feeds.
    // If fetchMode is set to 99, the rss feed is not refreshed. The RSS feed is not checked,
    // the page in the ViewPager in HomeActivity is hidden and new articles will not be fetched.
    private static final int POS_FETCH_MODE = 8;

    // Some fixed values with the main menu
    private static final int FAKE_FEED_ID_FOR_SECTION_HEADERS = -1;
    // These values should be larger than MAX_REAL_FEED_ID. Max_FEED_ID seperates the real feeds from other items/pages.
    public static final int FAKE_FEED_ID_MEANING_FAVORITES = 99;
    public static final int  FAKE_FEED_ID_MEANING_SEARCH = 98;
    public static final int  FAKE_FEED_ID_MEANING_ALL_ENTRIES = 97;
    // All feeds in the app have a feedId. These are the feedId's in the database in the table that holds the feeds.
    // Some menu-items behave like feeds but do not have articles of themselves, so they do not exist in the database.
    // These items get fake Id's to make them behave like feeds with a page in the ViewPager and a menuItem in the LeftDrawer
    // They also have a query to select and display articles from the database in a drawerMenuList.
    // Items like this are 'Favorites', 'Search' and 'All Articles'.
    // To distinguish these fake feeds from the real thing, they get Id's higer than MAX_REAL_FEED_ID.
    public final static int MAX_FEED_ID = 90; // values higer than MAX_REAL_FEED_ID are fake ID's for special pages like favorites and search

    public ArrayList<MenuItemObject> mMenuList;
    public int serviceChannelCursorPosition= -1;



    // Create MenuListAdapter
    public MenuListAdapter(Context context, Cursor feedCursor) {
        mMenuList = new ArrayList<MenuItemObject>();
        mContext = context;
        mFeedsCursor = feedCursor;
       // Log.e (TAG, DatabaseUtils.dumpCursorToString(mFeedsCursor));
        addFixedTopItems();
        addRssFeedItems();
        addFixedBottomItems ();

        for (MenuItemObject mObject : mMenuList){
            Log.e(TAG, "Menu item :  " + mObject.feedId);
        }


    }

    // Update the MenuListAdapter with new cursor
    public void setCursor(Cursor feedCursor) {
        mFeedsCursor = feedCursor;
        // The items at the top and at the bottom are fixed, so no need to update them.
        // We only have to update the RSS feeds.
        // So we start with item 4 and end with item getCount - 3;
        updateRssFeedItems(3,mMenuList.size()-3);

        /* For debugging only
        for (MenuItemObject mObject : mMenuList){
            Log.e(TAG, "UPDATE Menu item :  " +  mObject.title);
            Log.e(TAG, "UPDATE Menu item :  " + mObject.feedId);
        }
        */
    }

    /**
     * The items at the top and at the bottom of the navigation menu are fixed,
     * but the feeds in between can be sorted.
     * @param startIndex: menuposition where the drawerMenuList of sortable feeds starts
     * @param endIndex: menuposition where the lists of sortable feeds ends.
     */
    private void updateRssFeedItems (int startIndex,int endIndex) {
        long feedId;
        if (mFeedsCursor != null ) { // Check if cursor is not null
            mFeedsCursor.moveToPosition(-1);    // Set the cursor to the starting position

            for(int i=startIndex; i<endIndex; i++) {
                if (mFeedsCursor.moveToNext()) {    // iterate trhough the cursor
                    if ( i != serviceChannelCursorPosition) {  // The channel with service messages is in the bottom menu
                        mMenuList.get(i).feedId = (int) mFeedsCursor.getLong(POS_ID); // place the cursor-data in the menulist


                    }
                }
            }
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

    // Get feedId from item's drawer position
    public boolean hasPage(int menuposition) {
        return mMenuList.get(menuposition).hasViewPagerPage;
    }







    /**
     * Add the fixed items to the top/beginning of the MenuList
     */
    private void addFixedTopItems() {
        Log.e(TAG, " addFixedTopItems ");
        // Add section header
        mMenuList.add(
            new MenuItemObject(mContext.getString(R.string.menu_section_news))
        );
        // Add menu item with drawerMenuList of all entriews (articles)
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.all),false,FAKE_FEED_ID_MEANING_ALL_ENTRIES,-1, R.drawable.ic_statusbar_rss, -1,true)
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
        Log.e (TAG," adding RSSFeedItems");
        // start looping trough all available individual feeds
        if (mFeedsCursor != null ) {
            mFeedsCursor.moveToPosition(-1);
            // Just add items to the menu in the order they are in the cursor.
            // The query this cursor orders the result according the PRIORITY field ASC.
            // (see HomeActivity, line 600 and FeedDataContentProvider on line 310: URI_GROUPED_FEEDS)
            while (mFeedsCursor.moveToNext()) {
                feedName = mFeedsCursor.getString(POS_NAME);
                Log.e (TAG," adding " + feedName);
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
     * Add the fixed items to the bottom/end of the MenuList
     */
    private void addFixedBottomItems() {
        Log.e(TAG, " addFixedBottomItems ");
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.menu_section_other))
        );
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.favorites),false,FAKE_FEED_ID_MEANING_FAVORITES,-1,R.drawable.rating_important,-1,true)
        );
        mMenuList.add(
                new MenuItemObject(mContext.getString(R.string.search),false,FAKE_FEED_ID_MEANING_SEARCH,-1,R.drawable.action_search,-1,true)
        );
        if (serviceChannelCursorPosition > -1 ) {    // We've got a rss channel for service messages
            mFeedsCursor.moveToPosition(serviceChannelCursorPosition);
            mMenuList.add(
                    new MenuItemObject(mFeedsCursor.getString(POS_NAME), false, (int) mFeedsCursor.getLong(POS_ID),
                            mFeedsCursor.getInt(POS_FETCH_MODE),R.drawable.logo_icon_serviceberichten, serviceChannelCursorPosition,true)
            );
        }
    }

    /**
     * Inner Class to store menu-items in.
     * Each menu-item is an object. The lot is stored in an array.
     */
    public class MenuItemObject { // has to be public!
        private String title;
        public boolean hasViewPagerPage;
        public int viewPagerPagePosition;
        public int feedId;
        public int fetchMode;
        private boolean sectionHeader;

        // Simplified constructor for section headers. fetchMode set to -1 (is not used), iconId = -1 and hasViewPagerPage set to false
        private MenuItemObject  (String title) {
            this(title, true, FAKE_FEED_ID_FOR_SECTION_HEADERS, -1, -1, -1,false);  // call to main constructor
        }

        // Main constructor
        private MenuItemObject  (String title, boolean isSectionHeader, int feed, int fetchMode, int iconResourceId,int cursorPosition, boolean hasViewPagerPage) {
            this.title = title; // To avoid nullpointers, all items get a (empty) string
            this.sectionHeader = isSectionHeader;    // Menu items that are no items but section header get true
            this.feedId = feed;    // id of the feed in the database
            this.fetchMode = fetchMode;    // should the RSS feed be followed? fetchMode == 99 means feed is inactive. Page with articels is hidden. No new articles ar fetched.
            this.hasViewPagerPage = hasViewPagerPage;    // item has it's own page in the ViewPager in HomeActivity (if it is not hidden through fetchMode 99)
            this.viewPagerPagePosition = -1;    // position of the page in the PageViewer is set in HomeActivity > ViewPager
        }



    }

}
