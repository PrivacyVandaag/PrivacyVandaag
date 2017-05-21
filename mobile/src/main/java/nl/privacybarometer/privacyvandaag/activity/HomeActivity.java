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

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;

import android.support.design.widget.FloatingActionButton;

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
import nl.privacybarometer.privacyvandaag.service.RefreshService;
import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UpgradeActions;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import static nl.privacybarometer.privacyvandaag.service.FetcherService.NOTIFICATION_FEED_ID;


/**
 * Main activity
 * including a ViewPager with fragment-lists of articles read from the database
 * and a left drawer menu with categories and feeds.
 *
 * The data is read from database using two loaders in de background via LoaderManager()
 */
public class HomeActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = HomeActivity.class.getSimpleName() + " ~> ";
    private static final String TITLE_SPACES = "  "; // Twee spaties als margin tussen icon en title in toolbar.

    private static final int VIEWPAGER_LOADER_ID = 1;
    private EntriesListPagerAdapter mPagerAdapter;
    private ViewPager mPager;

    private static final String STATE_CURRENT_DRAWER_POS = "STATE_CURRENT_DRAWER_POS";
    private static final String STATE_NUMBER_OF_PAGES = "STATE_NUMBER_OF_PAGES";
    private static final String FEED_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ')';
    private static final String WHERE_UNREAD_ONLY = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + "=" + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ") > 0" +
            " OR (" + FeedColumns.IS_GROUP + "=1 AND (SELECT " + Constants.DB_COUNT + " FROM " + FeedData.ENTRIES_TABLE_WITH_FEED_INFO +
            " WHERE " + EntryColumns.IS_READ + " IS NULL AND " + FeedColumns.GROUP_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID +
            ") > 0)";

    private static final int DRAWER_LOADER_ID = 0;
    private static final int SEARCH_DRAWER_POSITION = -1;
    //private static final String NOTIFICATION_FEED_ID = "NotificationFeedId";

    private EntriesListFragment mEntriesFragment;
    private DrawerLayout mDrawerLayout;
    private View mLeftDrawer;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mTitle;
    private int mCurrentDrawerPos = 1;  // Set this to the page the app shouold go to on opening.
    private int mCurrentViewPagerPos = 1;  // Set this to the page the app shouold go to on opening.
    private boolean isPageSelectionFromViewPager = true;


    private final SharedPreferences.OnSharedPreferenceChangeListener mPositionFabListener =
                        new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (PrefUtils.POSITION_FLOATING_MENU_BUTTON.equals(key)) {
                    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
                    CoordinatorLayout.LayoutParams paramsFab = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
                    if (PrefUtils.getBoolean(PrefUtils.POSITION_FLOATING_MENU_BUTTON, false)) {
                        paramsFab.gravity = Gravity.BOTTOM | Gravity.START;
                    } else {
                        paramsFab.gravity = Gravity.BOTTOM | Gravity.END;
                    }
                }
            }

    };

    private float screenDensity = 1f;

    /*  *** ONCREATE ***   *** ONCREATE ***   *** ONCREATE *** */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Log.e(TAG, "onCreate PRODUCT_FLAVOR " + BuildConfig.PRODUCT_FLAVOR);
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        //*** Check whether upgrade took place and perform upgrade actions if necessary
        final int versionCode = BuildConfig.VERSION_CODE;
        // Perform upgrade actions only if not fresh install
        if ( ! (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) ) {
            // read old versionCode of the app
            final int storedVersionCode = PrefUtils.getInt(PrefUtils.APP_VERSION_CODE, 0);
            //int storedVersionCode = 91;
            if (versionCode > storedVersionCode) { // UpgradeAction is always(!!) necessary
                if (UpgradeActions.startUpgradeActions(this, storedVersionCode))
                    PrefUtils.putInt(PrefUtils.APP_VERSION_CODE, versionCode);
            }
        } else PrefUtils.putInt(PrefUtils.APP_VERSION_CODE, versionCode);
        //*** einde upgrade

        // Perform these actions only on the first occassion the app is run.
        // There are also some actions in selectDrawerItem(), so FIRST_OPEN is not set to false yet.
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            // Add the sources for the newsfeeds to the database
            FeedData.addPredefinedFeeds(this);

            // To add a welcome message to one of the feeds in the database
            // FeedData.addPredefinedMessages();
        }

        // Set the View for this Activity.
        setContentView(R.layout.activity_home);

        //*** ViewPager *** ViewPager *** ViewPager *** ViewPager ***
        // Get the view ID for the ViewPager
        mPager = (ViewPager) findViewById(R.id.pager_container_home);
        // De ViewPager wordt verder gevuld als in onLoadFinished() bij regel 550 als de database geladen zijn.
        // Daar wordt dus de adapter aan de ViewPager gekoppeld.
        // Dan weten we immers ook welke items in het menu een eigen pagina moeten krijgen.
        // Start de loaders voor de gegevens uit de database voor de ViewPager.
        getLoaderManager().initLoader(VIEWPAGER_LOADER_ID, null, this);

        // Create the listener for page swipes in the ViewPager.
        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            public void onPageSelected(int position) {
                Log.e(TAG, "listening!!! > Page Swipe.");
                mCurrentViewPagerPos = position;
                if (isPageSelectionFromViewPager) {  // Als de paginakeuze uit left drawer komt, is dit al ingesteld.

                    int menuPosition = mDrawerAdapter.getMenuPositionFromPagePosition(position);
                    // Log.e(TAG,"selectDrawerItem wordt vanuit addOnPageChangeListener gestart.");
                    selectDrawerItem(menuPosition, true);
                } else {
                    // reset de boolean voor de volgende keer keuze van de gebruiker via leftDrawer of pager.
                    // Als startwaarde wordt 'true' ingesteld. Als er vanuit het LeftDrawer menu
                    // gekozen wordt, gaat deze waarde naar false.
                    // Log.e(TAG,"Zet isPageSelectionFromViewPager op true");
                    isPageSelectionFromViewPager = true;
                }
            }
        });
        //*** Einde onCreate voor de ViewPager ***


        //*** Instellingen voor de toolbar (menubalk boven in beeld).
        mTitle = getTitle();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        //*** Left drawer *** Left drawer *** Left drawer *** Left drawer ***
        mLeftDrawer = findViewById(R.id.left_drawer);
        mDrawerList = (ListView) findViewById(R.id.drawer_list);
        mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mDrawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Wordt het de feed wel gevolgd en heeft het dus een pagina in de ViewPager?
            if (mDrawerAdapter.hasPage(position)) {
                // false geeft aan dat de pagina-keuze niet van de ViewPager, maar vanuit het menu komt.
                selectDrawerItem(position, false);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
            } else {    // Voor de menu-items zonder pagina, halen we alleen de focus van het item.
                mDrawerList.clearChoices();
                mDrawerList.requestLayout();
                mDrawerList.setItemChecked(mCurrentDrawerPos, true);
            }
            }
        });

        // Maak een standaard context menu vast aan de feeds in de lijst.
        registerForContextMenu(mDrawerList);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
            // vervangen door onderstaande, want deprecated: mDrawerLayout.setDrawerListener(mDrawerToggle);
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
            DrawerLayout.LayoutParams params = (android.support.v4.widget.DrawerLayout.LayoutParams) mLeftDrawer.getLayoutParams();
            params.width = newWidth;
            mLeftDrawer.setLayoutParams(params);
        } else {    // in Widescreen mode (tablets), the menu is fixed in a linearlayout to the left side.


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

//            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }

        // If the app starts from a notification, it should go directly to the feed with new articles
        Bundle extras = getIntent().getExtras();
        if(extras != null) mCurrentDrawerPos = getDrawerPositionOnIntent(extras);

        // Start the loaders for LeftDrawer and ViewPager to retrieve data from database
        getLoaderManager().initLoader(DRAWER_LOADER_ID, null, this);

       //*** End left drawer ***




        ///*** FLOATING BUTTON *** FLOATING BUTTON ***
        // Vaste floating button rechtsonder om het menu te openen of te sluiten.
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        CoordinatorLayout.LayoutParams paramsFab = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
        // Check the preferences if button should be to the left or to the right.
        if (PrefUtils.getBoolean(PrefUtils.POSITION_FLOATING_MENU_BUTTON, false)) {
            paramsFab.gravity = Gravity.BOTTOM | Gravity.START;
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrawerLayout != null) {
                    if (mDrawerLayout.isDrawerOpen(mLeftDrawer)) { // If left menu drawer is open
                        mDrawerLayout.closeDrawer(mLeftDrawer);
                    } else {    // If left menu drawer is closed
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }
            }
        });
        //*** End floating button ***


        // Start het checken op nieuwe artikelen als dat zo staat ingesteld.
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
            // starts the service independent to this activity
            startService(new Intent(this, RefreshService.class));
        } else {
            stopService(new Intent(this, RefreshService.class));
        }
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ON_OPEN_ENABLED, false)) {
            if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
                startService(new Intent(HomeActivity.this, FetcherService.class).
                        setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }
    }
    /*  *** END ONCREATE *** */






    /*  *** CONTEXT MENU LEFT DRAWER ***   *** CONTEXT MENU LEFT DRAWER ***   *** CONTEXT MENU LEFT DRAWER *** */
    /**
     * Het 'long-click' context-menu voor de items in de left drawer.
     * Door lang te drukken op een item verschijnt dit context menu.
     * Hier kan je de instellingen per organisatie regelen.
     * Zie voor opties "onContextItemSelected(MenuItem item)" hieronder
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId()==R.id.drawer_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            int pos = info.position;    // the position of the item in the menu.
            if (mDrawerAdapter.hasContextMenu(pos)) {
                menu.setHeaderTitle(mDrawerAdapter.getTitle(pos));
                // Bepaal de inhoud van het context menu afhankelijk van de volg-status van de feed.
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
     * Wat er moet gebeuren als je een bepaalde keuze in het contextMenu van hierboven hebt gemaakt.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        final boolean success;
        switch (item.getItemId()) {
            case 1: // zet meldingen uit
                success = mDrawerAdapter.setNotifyMode(info.position,false);
                if (!success) Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 2: // zet meldingen aan
                success = mDrawerAdapter.setNotifyMode(info.position,true);
                if (!success) Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 3: // Stop met volgen
                success = mDrawerAdapter.setFetchMode(info.position,false);
                if (success) {  // Vernieuw de ViewPager en haal de bewuste pagina weg.
                    getLoaderManager().restartLoader(DRAWER_LOADER_ID, null, this);
               } else Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 4: // Begin met volgen
                success = mDrawerAdapter.setFetchMode(info.position,true);
                if (success) {  // Vernieuw de ViewPager en voeg een pagina toe.
                    getLoaderManager().restartLoader(DRAWER_LOADER_ID, null, this);
                } else Toast.makeText(this, R.string.error_option_execute, Toast.LENGTH_SHORT).show();
                break;
            case 5: // Start een intent naar de browser en ga naar de website.
                String url = mDrawerAdapter.getWebsite(info.position);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
        }
        return true;
    }
    /*  *** EINDE CONTEXT MENU LEFT DRAWER *** */






    /**
     * *** START FROM NOTIFICATIE OR DIFFERENT APP ***
     * If app is openend from notification,
     * select the right menu item and the right page in the ViewPager.
     *
     * @param extras
     * @return
     */
    private int getDrawerPositionOnIntent (Bundle extras ) {
        // Log.e(TAG, "getDrawerPositionOnIntent: App wordt vanuit een notificatie gestart");
        int mFeedId = 0;
        if(extras != null) {
            //  Log.e(TAG, "Extras are found");
            if (extras.containsKey(NOTIFICATION_FEED_ID)) {
                // extract the extra-data in the Notification
                String feedIdString = extras.getString(NOTIFICATION_FEED_ID);
                // Log.e(TAG, "Notif feedId = " + feedIdString);
                if (feedIdString != null) {
                    try {
                        // Doorgegeven FeedIdString begint bij 1, maar cursorPositie begint bij 0. Om de menupositie te bepalen geven we de cursorPositie door.
                        mFeedId = Integer.parseInt(feedIdString);
                        // Log.e(TAG, "mFeedId = " + mFeedId);
                    } catch (NumberFormatException nfe) {
                        Log.e(TAG, "Fout: notification ID kon niet worden gemaakt.");
                        mFeedId = 0;
                    }

                }
            }
        }
        return mDrawerAdapter.getMenuPositionFromFeedId(mFeedId);
    }


     @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Log.e(TAG, "via onNewIntent");
        // Check of er een bepaalde feed wordt meegegeven om direct naartoe te gaan.
        Bundle extras = intent.getExtras();
        if(extras != null) mCurrentDrawerPos = getDrawerPositionOnIntent(extras);
    }
    // *** EINDE START VANUIT NOTIFICATIE OF ANDERE APP ***



    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);
        super.onSaveInstanceState(outState);
    }

    // TODO: Put this in the onCreate() to keep it in line with the onDestroy();
    @Override
    protected void onStart() {
        super.onStart();
        PrefUtils.registerOnPrefChangeListener(mPositionFabListener);
    }

    @Override
    protected void onDestroy() {
        PrefUtils.unregisterOnPrefChangeListener(mPositionFabListener);
        super.onDestroy();
    }


    /**
     * Afsluiten van de app
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
        // Log.e(TAG, "onPostCreate");
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Log.e(TAG,"via onConfigChannged");
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }



    /*** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS *** LOADERS ***
     *
     *     Stel de loaders in.
     *     Er is een loader voor het menu in de LeftDrawer en
     *     er is een loader voor de pagina's in de ViewPager.
     *
     *     met getLoaderManager().initLoader() worden de loaders voor het eerst gestart.
     *     Bij onCreate wordt vervolgens de query voor de database meegegeven.
     *     Als de query is uitgevoerd, wordt onLoadFinished aangeroepen met de resultaten
     *     Als de data is gewijzigd, kan je met getLoaderManager().restartLoader() de resultaten laten vernieuwen.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle bundle) {
        Log.e(TAG, "onCreateLoader");
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
        /*
        else if (loaderId== VIEWPAGER_LOADER_ID) {
             Log.e(TAG, "CreateLoader Viewpager");
            cursorLoader = new CursorLoader(this,   // context
                    FeedColumns.CONTENT_URI, // table
                    new String[]{FeedData.FeedColumns.FETCH_MODE}, // columns
                    null, // selection
                    null, // selection args
                    FeedColumns._ID // order by
            );
            // Keep minimum interval of UPDATE_THROTTLE_DELAY (500 ms) between updates of the loader
            cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
            return cursorLoader;
        }
        */
        else return null;
    }

    // If feeds are loaded from the database start displaying them. ZIE HIERBOVEN!
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Log.e(TAG, "onLoadFinished");
        int mLoaderId = cursorLoader.getId();
        if (mLoaderId== DRAWER_LOADER_ID) {
            if (cursor.moveToFirst()) { // Do we have a non-empty cursor?
                cursor.moveToPosition(-1);  // Reset cursor position.

                Log.e(TAG, "Loader finished. > Create LeftDrawer");
                // Log.e (TAG, DatabaseUtils.dumpCursorToString(cursor));
                if (mDrawerAdapter != null) {
                    mDrawerAdapter.setCursor(cursor);
                } else {
                    mDrawerAdapter = new DrawerAdapter(this, cursor);
                    mDrawerList.setAdapter(mDrawerAdapter);
                    // We don't have any menu yet, we need to display it
                    mDrawerList.post(new Runnable() {
                        @Override
                        public void run() {
                            // Stel de menukeuze in met de laatst bekende menukeuze
                            selectDrawerItem(mCurrentDrawerPos, true);
                        }
                    });
                }

                Log.e(TAG, "Loader finished. > Create ViewPager");
                if (mPagerAdapter != null) {
                    mPagerAdapter.updateViewPager();
                } else {
                    mPagerAdapter = new EntriesListPagerAdapter(getSupportFragmentManager());
                    mPager.setAdapter(mPagerAdapter);

                    int mPagePosition = mDrawerAdapter.getViewPagerPagePosition(mCurrentDrawerPos);
                    mPager.setCurrentItem(mPagePosition);
                }

            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        int mLoaderId = cursorLoader.getId();
        // Log.e (TAG, "onLoaderReset: " + mLoaderId);
        if (mLoaderId == DRAWER_LOADER_ID) {
            mDrawerAdapter.setCursor(null);
        }
    }
    //*** End loader for Left Drawer






    /**
     *  The user has selected a new page with a list of entries (articles).
     *  This can be done by clicking in the LeftDrawer menu
     *  or by swiping through the ViewPager pages.
     * @param menuPosition  The reference to the menu item or page that has been selected
     */
    private void selectDrawerItem(int menuPosition, boolean isFromViewPager) {
        // Log.e(TAG, "SELECT DRAWER");
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

            // Laat een popup met helptekstje zien.
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcome_title);
            builder.setMessage(DeprecateUtils.fromHtml(getString(R.string.welcome_message)));
            builder.setPositiveButton("OK", null);
            builder.show();


        } //*** end onFirstOpen ***


        // Get title and icon for selected menu-item / page.
        assert getSupportActionBar() != null;   // trucje om de foutmelder in de editor uit te zetten. Niet nodig.
        Drawable mIconDrawable = null;
        mTitle = mDrawerAdapter.getTitle(mCurrentDrawerPos);
        int mIconResourceId = mDrawerAdapter.getIconDrawable(mCurrentDrawerPos);
        long feedId  = mDrawerAdapter.getFeedId(mCurrentDrawerPos);

        // If a notification is set for this feed, it can be removed now
        CancelPossibleNotification mCancelNotification = new CancelPossibleNotification((int) feedId);
        mCancelNotification.run();

        // Set title and icon in toolbar
        getSupportActionBar().setTitle(TITLE_SPACES + mTitle);  // TITLE_SPACES because margin cannot be set easily to icon.
        if (mIconResourceId > 0) {
            mIconDrawable = ContextCompat.getDrawable(this, mIconResourceId);
        }
        if (mIconDrawable != null) bitmap = ((BitmapDrawable) mIconDrawable).getBitmap();
        if (bitmap != null) {
            mIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, bitmapSize, bitmapSize, true));  // set filter 'true' for smoother image if scaling up
            getSupportActionBar().setIcon(mIcon);
        } else {
            getSupportActionBar().setIcon(mIconDrawable);
        }

        // set the selected item in left drawer to 'checked'
        mDrawerList.setItemChecked(mCurrentDrawerPos, true);

        // If a item is selected by clicking a menu item, the right page in the ViewPager has to be set.
        if ( ! isFromViewPager) {
            // Bij het wijzigen van de pagina, moet de onPageChangelistener niet weer hier terugkomen.
            // Zet daarom isPageSelectionFromViewPager op false.
            isPageSelectionFromViewPager = false;
            int mPagePosition = mDrawerAdapter.getViewPagerPagePosition(menuPosition);
            if (mPagePosition > -1) mPager.setCurrentItem(mPagePosition);
        }
        // Geef aan dat de instellingen zijn aangepast en het menu opnieuw gemaakt moet worden.
        invalidateOptionsMenu();
    }



    //*** INNER CLASS TO REMOVE NOTIFICATIONS ***
    /**
     * Aparte class om het verwijderen van de notificatie uit de UserThread te halen.
     * Ik weet niet of een nieuwe runnable echt nodig is, maar de UI leek een beetje te blijven hangen.
     * Omdat de variable in een runnable 'final' moet zijn, kan het alleen door de abstracte class 'runnable' in
     * een concrete class te implementeren.
     */
    private class CancelPossibleNotification implements Runnable {
        private final int mNotificationId;

        CancelPossibleNotification(final int mNotificationId) {
            this.mNotificationId = mNotificationId;
        }

        public void run() {
            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotifyMgr.cancel(mNotificationId);
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
        private ArrayList<Integer> viewPagerPageFeedId = new ArrayList<>(); // lijst van pagina's met behorend feedId.

        // Initialise PageAdapter. We only get here once.
        private EntriesListPagerAdapter(FragmentManager fm) {
            super(fm);
            initItemsForViewPager();  // Initialiseer de gegevens voor de ViewPager.
        }

        // The cursor with feeds inofmration is updated. Maybe it has consequences for the ViewPager
        private void updateViewPager() {
            initItemsForViewPager();  // Check if ViewPager needs an update
            // Update view only if number of pages has changed.
            if (mOldNumberOfPages != mNumberOfPages ) {
                notifyDataSetChanged();
                // If the ViewPager  has changed, the title and logo in toolbar needs to change with it
                // mCurrentDrawerPos = mDrawerAdapter.getMenuPositionFromPagePosition(mCurrentViewPagerPos);
                // Go to first page to prevent confusing page selection when p[ages are removed or added.
                selectDrawerItem(1, false);
            }
        }

        /**
         * Initialiseer de ViewPager. Deze wordt aangeroepen bij het starten van de app,
         * maar ook iedere keer als in het contextmenu in de drawer een feed gevolgd gaat worden
         * of juist niet. Dan moet er immers ook een pagina bij of af.
         */
        private void initItemsForViewPager() {
            Log.e(TAG, "Menu opnieuw opbouwen: HomeActivity.EntriesListPagerAdapter.initItemsForViewPager()");
            mOldNumberOfPages = mNumberOfPages;
            mNumberOfPages = 0; // mNumberOfPages is het aantal pagina's dat de ViewPager krijgt.
            viewPagerPageFeedId.clear(); // reset all the viewPagerPages and feedIds

                mNumberOfPages = 0; // mNumberOfPages is number of pages the ViewPager gets.

                /* For debugging only
                for (MenuListAdapter.MenuItemObject mObject : mMenuListAdapter.drawerMenuList()) {
                    // Log.e(TAG, "Menu item :  " +  mObject.sectionTitle);
                    Log.e(TAG, "Menu item from the viewpager:  " + mObject.feedId);
                    Log.e(TAG, "Menu fetchmode:  " + mObject.fetchMode);
                    Log.e(TAG, "Menu hasViewPagerPage:  " + mObject.hasViewPagerPage);
                }
                */

                // Iterate through whole MenuList to see which MenuItem gets a page in the ViewPager
                // If the menuitem gest a page, link the feedId to it to display the entries drawerMenuList of that feed on the page
                // The MenuList is in the right order top to bottom in the left drawer and left to right in the ViewPager
                for (DrawerAdapter.MenuItemObject menuItem : mDrawerAdapter.drawerMenuList()) {
                    if (menuItem.hasViewPagerPage) {
                        viewPagerPageFeedId.add(menuItem.feedId);
                        // In het menuItemArray slaan we ook het bijbehorende paginanummer op. Dat is soms
                        // handiger bij het terugzoeken.
                        // Je kan daarvoor de index terugvragen van viewPagerPageFeedId :
                        // menuItemsArray[i].viewPagerPagePosition = viewPagerPageFeedId.indexOf(menuItemsArray[i].feedId);
                        // Maar de index is gelijk aan het aantal pagina's - 1, dus we doen dit voor mNumberOfPages++;
                        menuItem.viewPagerPagePosition = mNumberOfPages;
                        mNumberOfPages++;
                    } else {    // This item gets no page
                        // Log.e (TAG, menuItemsArray[i].name + " krijgt GEEN pagina. ");
                        menuItem.viewPagerPagePosition = -1;
                    }
            }
            // Log.e(TAG, "Aantal pagina's is " + mNumberOfPages);
        }

        @Override
        public int getCount() {
            return mNumberOfPages;
        }

        /**
         * Maak voor elke pagina een aparte lijst met entries aan. Zie EntriesListFragment regel 483.
         */
        @Override
        public Fragment getItem(int position) {
            // Log.e(TAG, "EntriesListPagerAdapter ~> getItem: " + position);
            // Elke pagina krijgt een eigen dataset met een nummer.
            // De dataset wordt in mEntriesListFragment.onActivityCreated gekoppeld.
            // Elke dataset krijgt bij init() een nummer mee in mEntriesListFragmentNumber=position
            // Door hier al de bijbehorende feedId mee te geven, hoeft daar niet de hele array mee.
            return EntriesListFragment.init(position,viewPagerPageFeedId.get(position));
        }

        /**
         * Deze method wordt alleen aangeroepen bij mPagerAdapter.notifyDataSetChanged();
         * Dit gebeurt alleen als de gebruiker in het coontext-menu van de left drawer
         * aangeeft dat hij een feed juist wel of juist niet meer wil volgen. Zie regel 365.
         */
        @Override
        public int getItemPosition(Object object) {
            // Het object wat meekomt is het EntriesListFragment, maar daar doen we niks mee.
            EntriesListFragment f = (EntriesListFragment) object;
            // Log.e (TAG, "Het fragmentNummer = " + f.mEntriesListFragmentNumber + " met feedId = " + f.mFeedId + " is verwijderd en wordt vernieuwd.");
            return POSITION_NONE;
        }
    }
    //*** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER *** EINDE VIEWPAGER ***



}