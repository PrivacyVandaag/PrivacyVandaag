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
 */

package nl.privacybarometer.privacyvandaag.activity;

import android.app.LoaderManager;
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
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;

import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import nl.privacybarometer.privacyvandaag.BuildConfig;
import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.adapter.DrawerAdapter;
import nl.privacybarometer.privacyvandaag.fragment.EntriesListFragment;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.service.FetcherService;
import nl.privacybarometer.privacyvandaag.service.RefreshService;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;
import nl.privacybarometer.privacyvandaag.provider.UpgradeActions;

/**
 * Main activity
 * including fragment-list of articles read from the database
 * and a left drawer menu with categories and feeds.
 */
public class HomeActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = HomeActivity.class.getSimpleName() + " ~> ";
    private static final String STATE_CURRENT_DRAWER_POS = "STATE_CURRENT_DRAWER_POS";
    private static final String FEED_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ')';
    private static final String WHERE_UNREAD_ONLY = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + "=" + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ") > 0" +
            " OR (" + FeedColumns.IS_GROUP + "=1 AND (SELECT " + Constants.DB_COUNT + " FROM " + FeedData.ENTRIES_TABLE_WITH_FEED_INFO +
            " WHERE " + EntryColumns.IS_READ + " IS NULL AND " + FeedColumns.GROUP_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID +
            ") > 0)";

    private static final int LOADER_ID = 0;
    private static final int SEARCH_DRAWER_POSITION = -1;

    private EntriesListFragment mEntriesFragment;
    private DrawerLayout mDrawerLayout;
    private View mLeftDrawer;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter;
    private ActionBarDrawerToggle mDrawerToggle;


    private final SharedPreferences.OnSharedPreferenceChangeListener mShowReadListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PrefUtils.SHOW_READ.equals(key)) {
                getLoaderManager().restartLoader(LOADER_ID, null, HomeActivity.this);
            }
        }
    };
    private CharSequence mTitle;
    private int mCurrentDrawerPos;

    private boolean mCanQuit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        /**
         * Check of er een upgrade heeft plaatsgevonden en voer zonodig acties uit.
         * We gebruiken dit nu hier omdat het bijwerken vanuit provider > DatabaseHelper.onUpgrade()
         * niet overweg kan met de aanpassingen Daarvoor moet namelijk eerst de database goed zijn geinitialiseerd.
         *
         */
        final int versionCode = BuildConfig.VERSION_CODE;
        final int storedVersionCode = PrefUtils.getInt(PrefUtils.APP_VERSION_CODE,0);
        if (versionCode > storedVersionCode) {
        // if (versionCode > -1 ) { // Voor test doeleinden!
            if (UpgradeActions.startUpgradeActions(this)) PrefUtils.putInt(PrefUtils.APP_VERSION_CODE, versionCode);
        } //*** einde upgrade




        // Entries are the articles from the rss feeds
        mEntriesFragment = (EntriesListFragment) getFragmentManager().findFragmentById(R.id.entries_list_fragment);

        mTitle = getTitle();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLeftDrawer = findViewById(R.id.left_drawer);
        mDrawerList = (ListView) findViewById(R.id.drawer_list);
        mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mDrawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem(position);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
            }
        });

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
            mDrawerLayout.setDrawerListener(mDrawerToggle);

            if (PrefUtils.getBoolean(PrefUtils.LEFT_PANEL, false)) {
                mDrawerLayout.openDrawer(mLeftDrawer);
            }
        }
        if (savedInstanceState != null) {
            mCurrentDrawerPos = savedInstanceState.getInt(STATE_CURRENT_DRAWER_POS);
        }

        getLoaderManager().initLoader(LOADER_ID, null, this);

        // Start refreshing the feeds if the preferences are set that way.
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
            // starts the service independent to this activity
            startService(new Intent(this, RefreshService.class));
        } else {
            stopService(new Intent(this, RefreshService.class));
        }
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ON_OPEN_ENABLED, false)) {
            if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
                startService(new Intent(HomeActivity.this, FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }

        PrefUtils.putInt(PrefUtils.NOTIFICATIONS_PREVIOUS_COUNT, 0);  // Reset the counter for new articles, used in service > FetcherService.class
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PrefUtils.registerOnPrefChangeListener(mShowReadListener);
        PrefUtils.putInt(PrefUtils.NOTIFICATIONS_PREVIOUS_COUNT, 0);  // Reset the counter for new articles, used in service > FetcherService.class
    }

    @Override
    protected void onPause() {
        PrefUtils.unregisterOnPrefChangeListener(mShowReadListener);
        super.onPause();
    }

    @Override
    public void finish() {
        // On wide screens (tablets) the left menu drawer is always open,
        // so back button should finish the app immediately.
        // TODO: There should be an easier way to check if the wide-layout for tablets is used.
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);
        float density  = getResources().getDisplayMetrics().density;
        float dpWidth  = outMetrics.widthPixels / density;
         if (dpWidth >= 700f) {  // on wide screen (tablet) finish immediately
            super.finish();
        } else {    // on small screens if left menu drawer is open, close only left menu drawer.
            if (mDrawerLayout.isDrawerOpen(mLeftDrawer)) {
                mDrawerLayout.closeDrawer(mLeftDrawer);
            } else {    // If left menu drawer is already closed, finish the app.
                super.finish();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // We reset the current drawer position
        selectDrawerItem(0);
    }

    /**
     * Het algemene menu en wat er gebeurt als men erop klikt.
     * Specifieke items per kanaal/feed worden ingesteld in fragment > EntriesListFragment.java
     * De menu indeling vind je in res > menu >entry_list.xml
     *
     * @param item  Het item in het menu waarop geklikt wordt.
     * @return is altijd true.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {

            case R.id.search: {    // optie zat eerst in het 'general preferences'. Zie ook xml > general_preferences.xml
                selectDrawerItem(SEARCH_DRAWER_POSITION);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
                return true;
            }

            case R.id.settings: {  // Click 'menu_settings' in the overflow toolbar menu ( layout > entrylist.xml ): go to preferences
                startActivity(new Intent(this, GeneralPrefsActivity.class));
                return true;
            }
            case R.id.edit_feeds: { // Start het scherm om de organisaties te sorteren of te (ont-)volgen.
                startActivity(new Intent(this, EditFeedsListActivity.class));
                return true;
            }
            case R.id.about_this_app: {    // optie zat eerst in het 'general preferences'. Zie ook xml > general_preferences.xml
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     *  Click action on the button to hide the read articles
     *
     *  Deze functie gebruiken we niet in het PrivacyVandaag.
     *  De knop hebben we uit de layout gehaald.
     *  De knop vind je in layout > view_hide_read_button.xml
     *  en wordt aangeroepen in > fragment > EntriesListFragment.java regel 280.
     *
     *  Hulpfuncties vind je in > utils > UiUtils.java
     *  (UiUtils.displayHideReadButtonAction() en UiUtils.updateHideReadButton())
     *
     *
     */
    /*
    public void onClickHideRead(View view) {
        if (!PrefUtils.getBoolean(PrefUtils.SHOW_READ, true)) {
            PrefUtils.putBoolean(PrefUtils.SHOW_READ, true);
        } else {
            PrefUtils.putBoolean(PrefUtils.SHOW_READ, false);
        }
    }
    */

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

    // Get the feeds from database
    // including FETCH_MODE, because FETCH-MODE=99 means a feed that is inactive (not to be refreshed)
    // including ICON_DRAWABLE to get the resource identifier to the feed logo in the resources.
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        CursorLoader cursorLoader = new CursorLoader(this, FeedColumns.GROUPED_FEEDS_CONTENT_URI, new String[]{FeedColumns._ID, FeedColumns.URL, FeedColumns.NAME,
                FeedColumns.IS_GROUP, FeedColumns.ICON, FeedColumns.LAST_UPDATE, FeedColumns.ERROR, FEED_UNREAD_NUMBER, FeedColumns.FETCH_MODE, FeedColumns.ICON_DRAWABLE},
                PrefUtils.getBoolean(PrefUtils.SHOW_READ, true) ? "" : WHERE_UNREAD_ONLY, null, null
        );
        cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
        return cursorLoader;
    }

    // If feeds are loaded from the database
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (mDrawerAdapter != null) {
            mDrawerAdapter.setCursor(cursor);
        } else {
            mDrawerAdapter = new DrawerAdapter(this, cursor);
            mDrawerList.setAdapter(mDrawerAdapter);

            // We don't have any menu yet, we need to display it
            mDrawerList.post(new Runnable() {
                @Override
                public void run() {
                    selectDrawerItem(mCurrentDrawerPos);
                }
            });
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mDrawerAdapter.setCursor(null);
    }

    /* This is where feeds are placed in left drawer on start of the app if dBase is empty */
    private void selectDrawerItem(int position) {
        mCurrentDrawerPos = position;
        Drawable mDrawable=null;
        Bitmap bitmap=null;
        BitmapDrawable mIcon = null;

        Uri newUri;
        boolean showFeedInfo = true;

        switch (position) {
            case SEARCH_DRAWER_POSITION:
                newUri = EntryColumns.SEARCH_URI(mEntriesFragment.getCurrentSearch());
                break;
            case 0:
                newUri = EntryColumns.ALL_ENTRIES_CONTENT_URI;
                //   Rescale the app-icon from the resources to the right size for use in the actionBar
                    mDrawable = ContextCompat.getDrawable(this, R.drawable.ic_statusbar_pv);
                    bitmap = ((BitmapDrawable) mDrawable).getBitmap();
                    if (bitmap != null) {
                        mIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, 36, 36, true));
                    }
                break;
            case 1:
                newUri = EntryColumns.FAVORITES_CONTENT_URI;
                break;
            default:
                long feedOrGroupId = mDrawerAdapter.getItemId(position);
                mTitle = mDrawerAdapter.getItemName(position);
                if (mDrawerAdapter.isItemAGroup(position)) {    // TODO: remove groups from the app. Not going to use this.
                    newUri = EntryColumns.ENTRIES_FOR_GROUP_CONTENT_URI(feedOrGroupId);
                } else {
                    // Get icons from resources instead of fetching favicons from websites on the internet.
                    // Rescale the resources to the right size for use as drawer icon
                    int mIconResourceId = mDrawerAdapter.getIconResourceId(position);
                    if (mIconResourceId > 0) {
                        mDrawable = ContextCompat.getDrawable(this, mIconResourceId);
                        bitmap = ((BitmapDrawable) mDrawable).getBitmap();
                        if (bitmap != null) {
                            mIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, 36, 36, true));
                        }
                    }
                                    //  If favicons from websites are used, they are feteched and stored as bitmaps in database
                                    /*
                                    byte[] iconBytes = mDrawerAdapter.getItemIcon(position);
                                    Bitmap bitmap = UiUtils.getScaledBitmap(iconBytes, 24);
                                    if (bitmap != null) {
                                       mIcon = new BitmapDrawable(getResources(), bitmap);
                                    }
                                    */
                    newUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedOrGroupId);
                    showFeedInfo = false;
                }
                break;
        }
        if (!newUri.equals(mEntriesFragment.getUri())) {
            mEntriesFragment.setData(newUri, showFeedInfo);
        }

        mDrawerList.setItemChecked(position, true);

        // First run of the app => we open the drawer for you
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, false);
            if (mDrawerLayout != null) {
                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }, 500);
            }
                                // Dialog popup that can be shown at first run after installation.
                                /*
                                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                builder.setTitle(R.string.welcome_title);
                                builder.show();
                                */

            /* On first run after installation, try to add these feeds in database */
            FeedData.addPredefinedFeeds(this);
        }   // end of settings on first run of the app.

        // Set title and icon in left drawer menu
        switch (mCurrentDrawerPos) {
            case SEARCH_DRAWER_POSITION:
                getSupportActionBar().setTitle(android.R.string.search_go);
                getSupportActionBar().setIcon(R.drawable.action_search);
                break;
            case 0:
                getSupportActionBar().setTitle(R.string.all);
                if (mIcon != null) {
                    getSupportActionBar().setIcon(mIcon);
                } else {
                    getSupportActionBar().setIcon(null);
                }
                break;
            case 1:
                getSupportActionBar().setTitle(R.string.favorites);
                getSupportActionBar().setIcon(R.drawable.rating_important);
                break;
            default:
                getSupportActionBar().setTitle(mTitle);
                if (mIcon != null) {
                    getSupportActionBar().setIcon(mIcon);
                } else {
                    getSupportActionBar().setIcon(null);
                }
                break;
        }

        // Put the good menu
        invalidateOptionsMenu();
    }
}
