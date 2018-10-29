/*
 * Copyright (c) 2015-2017 Privacy Vandaag / Privacy Barometer
 *
 * Copyright (c) 2015 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
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

package nl.privacybarometer.privacyvandaag.activity;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import nl.privacybarometer.privacyvandaag.BuildConfig;
import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.R;

import nl.privacybarometer.privacyvandaag.adapter.DrawerAdapter;
import nl.privacybarometer.privacyvandaag.fragment.EntriesListFragment;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.service.FetcherService;
import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshServiceController;
import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshControllerFactory;

import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UpgradeActions;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import static nl.privacybarometer.privacyvandaag.utils.NotificationUtils.NOTIFICATION_FEED_ID;


/**
 * Main activity
 * including a ViewPager with fragment-lists of articles read from the database
 * and a left drawer menu with categories and feeds.
 *
 * The data is read from database using a loader in de background via LoaderManager()
 *
 * Some interesting points for adjustments in this package:
 *  - In /java/.../adapter/DrawerAdapter.java the navigation menu is defined.
 *  - In /java/.../provider/FeedData.java the feeds that are to be followed are defined and added to the database.
 *  - In /java/.../utils/ArticleTextExtractor.java the text of the full articles is cut out of the website. For some websites this needs adjustments to get the cutting of the article right. Some special selections are made there for our use.
 *  - In /res/values/strings.xml you can translate all the text used in the app
 *  - In /res/drawable-xhdpi/ most of the icons are located. You can add or replace them by your own.
 *
 */
public class HomeActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = HomeActivity.class.getSimpleName() + " ~> ";
    private static final String TITLE_SPACES = "  "; // Two spaces as margin between icon and title in toolbar.

    private EntriesListPagerAdapter mPagerAdapter;
    private ViewPager mPager;

    private static final String STATE_CURRENT_DRAWER_POS = "STATE_CURRENT_DRAWER_POS";
    private static final String FEED_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ')';
    private static final String WHERE_UNREAD_ONLY = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + "=" + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ") > 0" +
            " OR (" + FeedColumns.IS_GROUP + "=1 AND (SELECT " + Constants.DB_COUNT + " FROM " + FeedData.ENTRIES_TABLE_WITH_FEED_INFO +
            " WHERE " + EntryColumns.IS_READ + " IS NULL AND " + FeedColumns.GROUP_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID +
            ") > 0)";

    private static final int DRAWER_LOADER_ID = 0;
    public static final String ON_CREATE = "onCreate";

    private DrawerLayout mDrawerLayout;
    private View mLeftDrawer;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mTitle;
    private int mCurrentDrawerPos = 1;  // Set this to the page the app should go to on opening.
    private boolean isPageSelectionFromViewPager = true;
    private boolean rebuildViewPager = true;
    private int feedIdOnNewIntent = 0;


    // In order to scale icons for high res screens, we need to know the screendensity.
    // Also used inthe formula to determine if we are on a widescreen (tablet) or on a phone.
    private float screenDensity = 1f;

    private RefreshServiceController mRefreshServiceController = RefreshControllerFactory.getController();
    private Context mContext;

    // This listener checks if the preference settings have been changed by the user.
    // If it concerns refreshing or the floating action button some action should be taken.
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (PrefUtils.POSITION_FLOATING_MENU_BUTTON.equals(key)) {
                        FloatingActionButton fab = findViewById(R.id.fab);
                        CoordinatorLayout.LayoutParams paramsFab = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
                        if (PrefUtils.getBoolean(PrefUtils.POSITION_FLOATING_MENU_BUTTON, false)) {
                            paramsFab.gravity = Gravity.BOTTOM | Gravity.START;
                        } else {
                            paramsFab.gravity = Gravity.BOTTOM | Gravity.END;
                        }
                    }
                    // If there is a change in refresh settings.
                    // Check of automatisch verversen wordt ingeschakeld. Dan wordt namelijk
                    // direct nieuwe achtergrondservice gestart.
                    if (PrefUtils.REFRESH_ENABLED.equals(key)) {
                            mRefreshServiceController.setRefreshJob(mContext,true,PrefUtils.REFRESH_ENABLED);
                    }
                    if (PrefUtils.REFRESH_INTERVAL.equals(key)) {
                            //we have got a change in refresh settings, so recreate the jobscheduler!
                            // we need to override the current settings if the job already exists, so 'true'.
                            mRefreshServiceController.setRefreshJob(mContext,true,PrefUtils.REFRESH_INTERVAL);
                    }
                    if (PrefUtils.REFRESH_WIFI_ONLY.equals(key)) {
                            //we have got a change in refresh settings, so recreate the jobscheduler!
                            // we need to override the current settings if the job already exists, so 'true'.
                            mRefreshServiceController.setRefreshJob(mContext,true,PrefUtils.REFRESH_WIFI_ONLY);
                    }
                }
            };



    /*  *** ONCREATE ***   *** ONCREATE ***   *** ONCREATE *** */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Log.e(TAG, "onCreate PRODUCT_FLAVOR " + BuildConfig.PRODUCT_FLAVOR);
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        // We need to store the context, so it can be included as a parameter to the RefreshServiceController
        mContext=this;

        //*** Check whether upgrade took place and perform upgrade actions if necessary
        // Perform upgrade actions only if not fresh install
        if ( ! (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) ) {
            // read old versionCode of the app
            final int storedVersionCode = PrefUtils.getInt(PrefUtils.APP_VERSION_CODE, 0);
            if (BuildConfig.VERSION_CODE > storedVersionCode) { // UpgradeAction is always(!!) necessary
                if (UpgradeActions.startUpgradeActions(this, storedVersionCode))
                    PrefUtils.putInt(PrefUtils.APP_VERSION_CODE, BuildConfig.VERSION_CODE);
            }
        } else PrefUtils.putInt(PrefUtils.APP_VERSION_CODE, BuildConfig.VERSION_CODE);
        //*** end upgrade


        // Perform these actions only the first time the app is run.
        // There are also some one time only actions in selectDrawerItem(), so
        // FIRST_OPEN is not set to false yet.
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            // Add the sources for the newsfeeds to the database
            FeedData.addPredefinedFeeds(this);

            // To add a welcome message to one of the feeds in the database
            // We do not use this at the moment.
            // FeedData.addPredefinedMessages();
        }

        // Set the View for this Activity.
        setContentView(R.layout.activity_home);

        //*** ViewPager *** ViewPager *** ViewPager *** ViewPager ***
        // Get the view ID for the ViewPager
        mPager = findViewById(R.id.pager_container_home);

        // Create the listener for page swipes in the ViewPager.
        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            public void onPageSelected(int position) {
                // Log.e(TAG, "listening!!! > Page Swipe.");
                if (isPageSelectionFromViewPager) {  // Page changes by swiping on the screen.
                    // Get the menu item that has been selected by swiping through the pages.
                    int menuPosition = mDrawerAdapter.getMenuPositionFromPagePosition(position);
                    // We need to set the toolbar
                    selectDrawerItem(menuPosition, true);
                } else { // Page is selected by a click in left drawer, so no further action here.
                    isPageSelectionFromViewPager = true;
                }
            }
        });
        //*** End onCreate for the ViewPager ***


        //*** Settings for the toolbar.
        mTitle = getTitle();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }


        //*** Left drawer *** Left drawer *** Left drawer *** Left drawer ***
        mLeftDrawer = findViewById(R.id.left_drawer);
        mDrawerList = findViewById(R.id.drawer_list);
        mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mDrawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Has the slected menu-item a page to go to in the ViewPager?
            if (mDrawerAdapter!= null && mDrawerAdapter.hasPage(position)) {
                // false: the selection of the item is done by clicking an item in the menu, not by swiping through the pages.
                selectDrawerItem(position, false);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
            } else {    // If menu-items without a page are clicked, just remove the focus.
                mDrawerList.clearChoices();
                mDrawerList.requestLayout();
                mDrawerList.setItemChecked(mCurrentDrawerPos, true);
            }
            }
        });

        // Register a context menu to the menu items in the Left Drawer.
        registerForContextMenu(mDrawerList);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
            // changed because deprecated: mDrawerLayout.setDrawerListener(mDrawerToggle);
            mDrawerLayout.addDrawerListener(mDrawerToggle);

            if (PrefUtils.getBoolean(PrefUtils.LEFT_PANEL, false)) {
                mDrawerLayout.openDrawer(mLeftDrawer);
            }
        }

        if (savedInstanceState != null) {
            mCurrentDrawerPos = savedInstanceState.getInt(STATE_CURRENT_DRAWER_POS);
        }


        // Set the width of the left drawer menu at 80% of the screen with a maximum
        screenDensity = getResources().getDisplayMetrics().density;
        int newWidth = (getResources().getDisplayMetrics().widthPixels / 10) * 8;
        int maxWidth = (int) (screenDensity * 360);
        if (newWidth > maxWidth) newWidth = maxWidth; // max width for use in landscape orientation.
        boolean layoutWidescreen = !findViewById(R.id.activity_home).getTag().toString().equals("normal");
        if ( ! layoutWidescreen) {  // In normal mode (smartphone), the menu is in a drawerlayout and can be opened and closed
            DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) mLeftDrawer.getLayoutParams();
            params.width = newWidth;
            mLeftDrawer.setLayoutParams(params);
        }
        // Handle the view for widescreens (tablets), the menu is fixed in a linearlayout to the left side.
        else {
            LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) mLeftDrawer.getLayoutParams();
            params.width = newWidth;
            mLeftDrawer.setLayoutParams(params);

            // since drawer is fixed, remove menu button in toolbar / indicator of drawer state (open or closed)
            toolbar.setNavigationIcon(null);

            // Can't think of a nice header, so let's keep it empty for now.
            // TextView navBar = (TextView) findViewById(R.id.empty_bar);
            //navBar.setText("");

            ViewGroup.LayoutParams  params2 = findViewById(R.id.empty_ba).getLayoutParams();
            params2.width = newWidth;
            findViewById(R.id.empty_ba).setLayoutParams(params2);

            // mDrawerToggle.setDrawerIndicatorEnabled(false);
        }

        // If the app starts from a notification, it should go directly to the feed with new articles
        Bundle extras = getIntent().getExtras();
        if(extras != null) getFeedIdInExtras(extras);

        // Start the loaders for LeftDrawer and ViewPager to retrieve data from database
        getLoaderManager().initLoader(DRAWER_LOADER_ID, null, this);

       //*** End left drawer ***




        ///*** Floating button *** Floating button ***
        // Fixed floating button rechtsonder om het menu te openen of te sluiten.
        FloatingActionButton fab = findViewById(R.id.fab);
        CoordinatorLayout.LayoutParams paramsFab = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
        // Check the preferences if button should be to the left or to the right.
        if (PrefUtils.getBoolean(PrefUtils.POSITION_FLOATING_MENU_BUTTON, false)) {
            paramsFab.gravity = Gravity.BOTTOM | Gravity.START;
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrawerLayout != null) {
                    // seeFileSizes (); // For debugging of cache only!!! Not for production release!
                    if (mDrawerLayout.isDrawerOpen(mLeftDrawer)) { // If left menu drawer is open
                        mDrawerLayout.closeDrawer(mLeftDrawer);
                    } else {    // If left menu drawer is closed
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }
            }
        });
        //*** End floating button ***



        //*** Refresh *** Job Scheduler *** Refresh timer / Alarm Manager ***//
        // Start to check for new articles if preferences are set that way.
        // As of Android SDK 21 (5.0 Lollipop) the use of AlarmManager for these tasks is
        // deprecated. The new JobScheduler is recommended as it saves battery life.
        // The RefreshControllerFactory determines wheter AlarmManager or JobScheduler
        // has to be used. We store this in mRefreshServiceController.
        mRefreshServiceController.setRefreshJob(mContext,false,ON_CREATE);

        // Preferences say: Refresh as soon as app is opened.
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ON_OPEN_ENABLED, false)) {
            if ( ! PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
                startService(new Intent(HomeActivity.this, FetcherService.class).
                        setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }


        // Register the listener for change in preferences. Is destroyed in onDestroy()
        PrefUtils.registerOnPrefChangeListener(mPreferenceChangeListener);


    }
    /*  *** END ONCREATE *** */



    /*  *** CONTEXT MENU LEFT DRAWER ***   *** CONTEXT MENU LEFT DRAWER ***   *** CONTEXT MENU LEFT DRAWER *** */
    /**
     * The 'long-click' context-menu for menu items in the left drawer.
     * This menu appaers after a long-click on an item.
     * User has some feed specific options here.
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId()==R.id.drawer_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            int pos = info.position;    // the position of the item in the menu.
            if (mDrawerAdapter.hasContextMenu(pos)) {
                menu.setHeaderTitle(mDrawerAdapter.getTitle(pos));
                // Create the context menu depending on status of the feed.
                if (mDrawerAdapter.hasActiveFetchMode(info.position)) {
                    if (mDrawerAdapter.getNotifyMode(info.position)) menu.add(0, 1, 1, R.string.turn_notification_off);
                    else menu.add(0, 2, 1, R.string.turn_notification_on);
                    menu.add(0, 3, 2, R.string.unfollow_this_feed);
                } else {
                    menu.add(0, 4, 2, R.string.follow_this_feed);
                }
                if (mDrawerAdapter.hasWebsite(info.position)) {
                    menu.add(0, 5, 3, R.string.goto_website);
                }
            }
        }
    }
    /**
     * What has to be done if an option is selected in the contextMenu above.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        final boolean success;
        switch (item.getItemId()) {
            case 1: // turn notifications off
                success = mDrawerAdapter.setNotifyMode(info.position,false);
                if (!success) Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 2: // turn notifications on
                success = mDrawerAdapter.setNotifyMode(info.position,true);
                if (!success) Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 3: // Unfollow feed
                rebuildViewPager = mDrawerAdapter.setFetchMode(info.position,false);
                if (rebuildViewPager) {  // Renew ViewPager to remove this specific page.
                    getLoaderManager().restartLoader(DRAWER_LOADER_ID, null, this);
               } else Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 4: // Follow feed
                rebuildViewPager = mDrawerAdapter.setFetchMode(info.position,true);
                if (rebuildViewPager) {  // Renew ViewPager to add this specific page.
                    getLoaderManager().restartLoader(DRAWER_LOADER_ID, null, this);
                } else Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 5: // Start an intent to browser and go to website.
                String url = mDrawerAdapter.getWebsite(info.position);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
        }
        return true;
    }
    /*  *** END CONTEXT MENU LEFT DRAWER *** */



    private void getFeedIdInExtras (Bundle extras) {
        feedIdOnNewIntent = -1;
        if(extras != null) {
            if (extras.containsKey(NOTIFICATION_FEED_ID)) { // This is set with the PendingIntent in NotificationUtils.class
                // extract the extra data in the notification
                String feedIdString = extras.getString(NOTIFICATION_FEED_ID);
                if (feedIdString != null) {
                    try {
                        feedIdOnNewIntent = Integer.parseInt(feedIdString);
                    } catch (NumberFormatException nfe) {
                        Log.e(TAG, "Error: feed ID could not be extracted from notification.");
                    }

                }
            }
        }
    }

     @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Check whether feedId is given in the extras of the notification.
        Bundle extras = intent.getExtras();
        getFeedIdInExtras (extras);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        PrefUtils.unregisterOnPrefChangeListener(mPreferenceChangeListener);
        super.onDestroy();
    }


    /**
     * Close app
     */
    @Override
    public void finish() {
        // On wide screens (tablets) the left menu drawer is always open, so back button should finish the app immediately.
        // Is it a tablet?
        if ((getResources().getDisplayMetrics().widthPixels / screenDensity) > 700f) {
            // On wide screens (tablet) finish immediately
            super.finish();
        } else {    // on small screens if left menu drawer is open, close only left menu drawer.
            if (mDrawerLayout.isDrawerOpen(mLeftDrawer)) {
                mDrawerLayout.closeDrawer(mLeftDrawer);
            } else {    // If left menu drawer is already closed, finish the app.
                super.finish();
            }
        }
    }



    /**
     * De opties in het menu die samenhangen met de activity vind je hieronder.
     * De opties in het menu die samenhangen met de specifieke lijst die wordt weergegeven,
     * vind je in EntriesListFragment.java line 347.
     *
     * De layout van het menu vind je in > layout > entrylist.xml
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       // Log.e(TAG, "onOptionsItemSelected");
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.settings: {  // Click 'menu_settings' in the overflow toolbar menu ( layout > entrylist.xml ): go to preferences
                startActivity(new Intent(this, GeneralPrefsActivity.class));
                return true;
            }
            case R.id.edit_feeds: { // Start activity to sort or edit feeds drawerMenuList.
                startActivity(new Intent(this, EditFeedsListActivity.class));
                return true;
            }
            case R.id.about_this_app: {
                // 'Floss' is the build variant for distibution outside Google Play Store
                // The only difference is that is has an built-in update function in the AboutActivity class.
                if (BuildConfig.FLAVOR.equals("floss")) {
                    startActivity(new Intent(this, AboutActivity.class));
                }
                // For distributiuon through Google Play Store
                // Since apps in Play Store are updated through Google App, this variant has no update function in AboutActivity class
                else {
                    startActivity(new Intent(this, AboutActivity.class));
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }



    /*** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS ***
     *
     *     Configure the loader.
     *     There is only one loader, used for the menu in the LeftDrawer .
     *
     *     Method getLoaderManager().initLoader() starts the loader.
     *     In onCreateLoader the query for the database is defined.
     *     The query is performed in the background on a seperate thread.
     *     When the query has been executed, onLoadFinished is called with the results.
     *     Use getLoaderManager().restartLoader() to force refresh of the query, but that is
     *     in most cases not necessary.
     *
     *     onLoaderReset is called when the app is destroyed. All references to the cursor should be
     *     removed, in order to get this process destroyed when the app has finished.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle bundle) {
        //Log.e(TAG, "onCreateLoader");
        CursorLoader cursorLoader;
        if (loaderId== DRAWER_LOADER_ID) {
            // This query is extended with sort order on PRIORITY ASC in FeedDataContentProvider on line 310: URI_GROUPED_FEEDS
            cursorLoader = new CursorLoader(this, FeedColumns.GROUPED_FEEDS_CONTENT_URI,
                    new String[]{FeedColumns._ID, FeedColumns.URL, FeedColumns.NAME,
                            FeedColumns.IS_GROUP, FeedColumns.ICON, FeedColumns.LAST_UPDATE,
                            FeedColumns.ERROR, FEED_UNREAD_NUMBER, FeedColumns.FETCH_MODE,
                            FeedColumns.ICON_DRAWABLE, FeedColumns.NOTIFY},
                    PrefUtils.getBoolean(PrefUtils.SHOW_READ, true) ? "" : WHERE_UNREAD_ONLY, null, null
            );
            // Keep minimum interval of UPDATE_THROTTLE_DELAY (500 ms) between updates of the loader
            cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
            return cursorLoader;
        }
        else return null;
    }

    // If feeds are loaded from the database start displaying them.
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        // Log.e(TAG, "onLoadFinished");
        int mLoaderId = cursorLoader.getId();
        if (mLoaderId== DRAWER_LOADER_ID) {
            if (cursor.moveToFirst()) { // Do we have a non-empty cursor?
                cursor.moveToPosition(-1);  // Reset cursor position.
                // Log.e (TAG, DatabaseUtils.dumpCursorToString(cursor));   // Tool to see what's inside the cursor
                if (mDrawerAdapter != null) {
                    // Set the new cursor and check if the new data has consequences for the ViewPager
                    rebuildViewPager = mDrawerAdapter.setCursor(cursor,rebuildViewPager);
                } else {
                    mDrawerAdapter = new DrawerAdapter(this, cursor);
                    mDrawerList.setAdapter(mDrawerAdapter);
                    // We don't have any menu yet, we need to display it

                    rebuildViewPager = true; // New adapter, new menu, therefore new ViewPager.
                }


                // Log.e(TAG, "Loader finished. > Create ViewPager");
                if (rebuildViewPager) { // Do we have NEW relevant data to (re)build the ViewPager?
                    if (mPagerAdapter != null) {
                        mPagerAdapter.updateViewPager();
                    } else {
                        mPagerAdapter = new EntriesListPagerAdapter(getSupportFragmentManager());
                        mPager.setAdapter(mPagerAdapter);

                        int mPagePosition = mDrawerAdapter.getViewPagerPagePosition(mCurrentDrawerPos);
                        mPager.setCurrentItem(mPagePosition);
                    }

                    // We've got a mDrawerAdapter and a mViewPager, so let's go to the right page.
                    // Did we have a feed to go to by a notification
                    if (feedIdOnNewIntent > 0) {
                        mCurrentDrawerPos = mDrawerAdapter.getMenuPositionFromFeedId(feedIdOnNewIntent);
                        feedIdOnNewIntent = -1;
                    }
                    mDrawerList.post(new Runnable() {
                        @Override
                        public void run() {
                            // Start the app with the page / feed belonging to mCurrentDrawerPos
                            selectDrawerItem(mCurrentDrawerPos, false);
                        }
                    });
                } // else Log.e(TAG, "No need to (re)build the ViewPager.");

            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        int mLoaderId = cursorLoader.getId();
        if (mLoaderId == DRAWER_LOADER_ID) {
           mDrawerAdapter.setCursor(null,false);
        }
    }
    //*** End loader for Left Drawer






    /**
     *  The user has selected a new page with a list of entries (articles).
     *  This can be done by clicking in the LeftDrawer menu
     *  or by swiping on the screen through the ViewPager pages.
     * @param menuPosition  The reference to the menu item or page that has been selected
     */
    private void selectDrawerItem(int menuPosition, boolean isFromViewPager) {
        boolean doubleClick = false;
        // Log.e(TAG, "SELECT DRAWER");
        // Check if the same menu item has been clicked twice, because it results in wrong setting. See further down below.
        if (mCurrentDrawerPos == menuPosition) doubleClick = true;
        mCurrentDrawerPos = menuPosition;

        // Drawable mDrawable = null;
        Bitmap bitmap = null;
        // We have to scale the icon because of different screen resolutions on phones and tablets.
        // This is also done in the EntryFragment.java for the EnryView (the full text of the article is shown to be read by user).
        int bitmapSize = (int) screenDensity * 32; // Scale toolbar logo's to right size. Convert 32 normal px to 32 density pixels.
        BitmapDrawable mIcon = null;

        // Only onFirstOpen of the app.
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, false);

            // Open het menu (left drawer)
            if (mDrawerLayout != null) {
                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }, 500);
            }

            // Show popup with helptext on first run.
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcome_title);
            builder.setMessage(DeprecateUtils.fromHtml(getString(R.string.welcome_message)));
            builder.setPositiveButton("OK", null);
            builder.show();
        } //*** end onFirstOpen ***


        // Get title and icon for selected menu-item / page.
        assert getSupportActionBar() != null;   // trick to get rid of error message in editor. Not really necessary.
        Drawable mIconDrawable = null;
        mTitle = "";
        int mIconResourceId = -1;
        long feedId = 0;
        if (mDrawerAdapter != null) {
            mTitle = mDrawerAdapter.getTitle(mCurrentDrawerPos);
            mIconResourceId = mDrawerAdapter.getIconDrawable(mCurrentDrawerPos);
            feedId = mDrawerAdapter.getFeedId(mCurrentDrawerPos);
        }

        // If a notification is set for this feed, it can be removed now
        CancelPossibleNotification mCancelNotification = new CancelPossibleNotification((int) feedId);
        mCancelNotification.run();

        // Set title and icon in toolbar
        getSupportActionBar().setTitle(TITLE_SPACES + mTitle);  // TITLE_SPACES because margin cannot be set easily to icon.
        if (mIconResourceId > 0) {  // We have an icon. Let's display it in the toolbar
            mIconDrawable = ContextCompat.getDrawable(this, mIconResourceId);
            if (mIconDrawable != null) bitmap = ((BitmapDrawable) mIconDrawable).getBitmap();
            if (bitmap != null) {
                mIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, bitmapSize, bitmapSize, true));  // set filter 'true' for smoother image if scaling up
                getSupportActionBar().setIcon(mIcon);
            } else {
                getSupportActionBar().setIcon(mIconDrawable);
            }
        }

        // set the selected item in left drawer to 'checked'
        mDrawerList.setItemChecked(mCurrentDrawerPos, true);

        // If a item is selected by clicking a menu item, the right page in the ViewPager has to be set.
        if ( ! isFromViewPager) {
            // On changing the page, the onPageChangelistener should not come back here.
            // Therefore, set isPageSelectionFromViewPager to false.
            isPageSelectionFromViewPager = false;
            int mPagePosition = mDrawerAdapter.getViewPagerPagePosition(menuPosition);
            if (mPagePosition > -1) mPager.setCurrentItem(mPagePosition);
        }
        if ( doubleClick ) {    // clicking twice on same item in menu leads to no real page change, so reset the boolean
            isPageSelectionFromViewPager = true;
        }
        // New settings are done, refresh the menu.
        invalidateOptionsMenu();
    }



    /**
     * INNER CLASS TO REMOVE NOTIFICATIONS
     * Don't know if new runnable is needed, but the UI seemed to freeze a bit.
     * Because the variable in runnable has to be 'final', the runnable can only be used by
     * implementing abstract class 'runnable' in a new private class
     */
    private class CancelPossibleNotification implements Runnable {
        private final int mNotificationId;

        CancelPossibleNotification(final int mNotificationId) {
            this.mNotificationId = mNotificationId;
        }

        public void run() {
            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (mNotifyMgr != null) {
                mNotifyMgr.cancel(mNotificationId);
            }
        }
    }



    //*** VIEWPAGER *** VIEWPAGER *** VIEWPAGER *** VIEWPAGER *** VIEWPAGER ***

    /**
     * Hier wordt de ViewPager gemaakt, waarmee je door de verschillende feeds links/rechts kan swipen
     * Hiervoor wordt per feed / item in de LeftDrawer een aparte pagina gemaakt.
     * Elke pagina krijgt zijn eigen EntriesFragmentList met een eigen adapter naar de database
     * Als een feed / item in de LeftDrawer niet actief is, krijgt het ook geen eigen pagina in de ViewPager
     */
    public class EntriesListPagerAdapter extends FragmentStatePagerAdapter {
        private int mNumberOfPages = 0;
        private int mOldNumberOfPages = 0;
        private ArrayList<Integer> viewPagerPageFeedId = new ArrayList<>(); // list of pages with it's feedId's.

        // Initialise PageAdapter. We only get here once.
        private EntriesListPagerAdapter(FragmentManager fm) {
            super(fm);
            initItemsForViewPager();  // Initialise ViewPager.
            rebuildViewPager = false;
        }

        // The cursor with feeds inofmration is updated. Maybe it has consequences for the ViewPager
        private void updateViewPager() {
            initItemsForViewPager();  // Check if ViewPager needs an update
            // Update view only if number of pages has changed.
            if (mOldNumberOfPages != mNumberOfPages ) {
                notifyDataSetChanged();
                // If the ViewPager  has changed, the title and logo in toolbar needs to change with it
                // mCurrentDrawerPos = mDrawerAdapter.getMenuPositionFromPagePosition(mCurrentViewPagerPos);
                // Go to first page to prevent confusing page selection when pages are removed or added.
                selectDrawerItem(1, false);
            }
            rebuildViewPager = false;
        }

        /**
         * Initialise ViewPager. This is being called on starting the app,
         * but also if changes are made in the number of feeds to follow.
         * If a feed is being unfollow or followed, a page has to be added or removed from ViewPager.
         */
        private void initItemsForViewPager() {
            // Log.e(TAG, "(RE)build menu : HomeActivity.EntriesListPagerAdapter.initItemsForViewPager()");
            mOldNumberOfPages = mNumberOfPages;
            mNumberOfPages = 0; // mNumberOfPages is number of pages the ViewPager gets.
            viewPagerPageFeedId.clear(); // reset all the viewPagerPages and feedIds


            // Iterate through whole MenuList to see which MenuItem gets a page in the ViewPager
            // If the menuitem gest a page, link the feedId to it to display the entries drawerMenuList of that feed on the page
            // The MenuList is in the right order top to bottom in the left drawer and left to right in the ViewPager
            for (DrawerAdapter.MenuItemObject menuItem : mDrawerAdapter.drawerMenuList()) {
                if (menuItem.hasViewPagerPage) {
                    viewPagerPageFeedId.add(menuItem.feedId);
                    menuItem.viewPagerPagePosition = mNumberOfPages;
                    mNumberOfPages++;
                } else {    // This item gets no page
                    menuItem.viewPagerPagePosition = -1;
                }
            }
        }

        @Override
        public int getCount() {
            return mNumberOfPages;
        }

        /**
         * Each page gets its own list with entries (articles). See EntriesListFragment line 483 (or about).
         */
        @Override
        public Fragment getItem(int position) {
            // Log.e(TAG, "EntriesListPagerAdapter ~> getItem: " + position);
            // Each page gets own dataset and a number.
            // The dataset is set to the page in mEntriesListFragment.onActivityCreated.
            // Each dataset gets in init() a number in mEntriesListFragmentNumber=position
            // We set this in the method call, so we don't have to send the whole array.
            return EntriesListFragment.init(position,viewPagerPageFeedId.get(position));
        }

        /**
         * This method is only called on mPagerAdapter.notifyDataSetChanged();
         * This happens only when a page is added or removed.
         * ( when a user decides to follow or unfollow a specific feed).
         */
        @Override
        public int getItemPosition(@NonNull Object object) {
            // The object is a EntriesListFragment, but we do nothing with it, so commented out.
            // EntriesListFragment f = (EntriesListFragment) object;

            // Log.e (TAG, "fragmentNumber = " + f.mEntriesListFragmentNumber + " with feedId = " + f.mFeedId + " has been removed and will be renewed if necessary.");
            return POSITION_NONE;
        }
    }
    //*** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER ***



}