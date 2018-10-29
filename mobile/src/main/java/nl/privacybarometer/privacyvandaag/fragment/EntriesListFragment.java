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

package nl.privacybarometer.privacyvandaag.fragment;


import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import androidx.loader.content.CursorLoader;

//import android.content.CursorLoader;

import android.database.Cursor;


import android.net.Uri;
import android.os.Bundle;


import androidx.appcompat.widget.SearchView;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

// import com.melnykov.fab.FloatingActionButton;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.adapter.DrawerAdapter;
import nl.privacybarometer.privacyvandaag.adapter.EntriesCursorAdapter;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedDataContentProvider;
import nl.privacybarometer.privacyvandaag.service.FetcherService;
import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.ResetUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import java.util.Date;


/**
 * Genereert de centrale lijst met artikelen op basis van de keuze in het menu.
 *
 * Omdat de app nu gebruikt maakt van een ViewPager (zie HomeActivity onderaan)
 * kan er door de lijsten van rechts naar links en terug worden geswiped.
 * Elke lijst is dus vast aan een bepaalde pagina in de ViewPager gekoppeld.
 *
 * Zodra de app wordt geopend, wordt dus nu niet meer één lijst gemaakt met telkens
 * een andere inhoud afhankelijk vabn de keuze in de left drawer,
 * maar worden ineens alle lijsten gemaakt voor elke mogelijke keuze in de left drawer.
 *
 * Vervolgens wordt elke lijst (entriesListFragment) aan de juiste inhoud gekoppeld.
 * Dit gebeurt in de onActivityCreated functie verder naar onderen.
 * Technisch gezegd wordt daar de adapter met de mUri (query-opdracht) aan de EntriesListFragment gekoppeld.
 *
 * De setData functie waarmee je flexibel de inhoud van een lijst (entriesListFragment) kan
 * maken is dus niet meer standaard nodig en wordt alleen nog voor de zoekfunctie gebruikt
 *
 * LOADERS
 * Met de loaders worden de gegevens uit de database gehaald. Dit gebeurt in de achtergrond
 * en heeft een aparte thread. ZIe verderop voor meer toelichting
 *
 *
 * De adapter staat bij Adapter > EntriesCursorAdapter line 174 gebruikt.
 * - mEntriesCursorAdapter is de instance van de cursorAdapter voor het ophalen van de items uit de database
 * - mUri is de keuze voor welke lijst weergegeven moet worden.
 *
 * De layout staat in layout > fragment_entry_list.xml
 *
 * Op basis van bijbehorende classes / bestanden:
 * > fragment > SwipeRefreshFragment.java
 * > fragment > SwipeRefreshListFragment.java
 * > view > SwipeRefreshLayout.java
 *
 */


public class EntriesListFragment extends SwipeRefreshListFragment {
    private static final String TAG = EntriesListFragment.class.getSimpleName() + " ~> ";

    private static final String STATE_URI = "STATE_URI";
    private static final String STATE_SHOW_FEED_INFO = "STATE_SHOW_FEED_INFO";
    private static final String STATE_LIST_DISPLAY_DATE = "STATE_LIST_DISPLAY_DATE";

    private static final int ENTRIES_LOADER_ID = 1;
    private static final int NEW_ENTRIES_NUMBER_LOADER_ID = 2;

    public Uri mUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI("1");
    private boolean mShowFeedInfo = false;
    private EntriesCursorAdapter mEntriesCursorAdapter;
    private ListView mListView;
    private SearchView mSearchView;
    private long mListDisplayDate = new Date().getTime();
    public int mEntriesListFragmentNumber;
    public int mFeedId;

    private boolean startManualRefresh = false; // used to decide wether toast message has to be displayed. Set to true in case of manual swiperefresh


    /**
     * *** LOADERS *** LOADERS ***  LOADERS ***  LOADERS ***  LOADERS ***
     *
     * Loaders zijn er om gegevens uit de database te halen.
     * Je kan dat ook direct met een query doen, maar een loader heeft extra functies
     * en doet het ophalen van gegevens op de achtergrond zodat de UI er niet door blokkeert.
     *
     * Een LoaderManager kan meerdere loaders (= meerdere verschillende queries) bijhouden.
     * JEen opzet hiervoor  is om voor elke loader een eigen LoaderCallBack te maken.
     * Die LoaderCallBack bevat de query om de database te doorzoeken bij onCreateLoader
     * en bevat ook wat er moet gebeuren als de gegevens uit de database zijn opgehaald in onLoadFinished
     *
     * Zie verder hieronder bij mEntriesLoader (Loader 1) en bij mEntriesNumberLoader (Loader 2)
     */

    //*** LOADER 1 *** LOADER 1 *** LOADER 1 *** LOADER 1 ***
    // Haalt alle gegevens over de artikelen voor de lijst op.
    // Wordt op verschillende plekken vanuit deze class gestart vanuit restartLoaders()
    private final LoaderManager.LoaderCallbacks<Cursor> mEntriesLoader =
            new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            String entriesOrder = PrefUtils.getBoolean(PrefUtils.DISPLAY_OLDEST_FIRST, false) ? Constants.DB_ASC : Constants.DB_DESC;
            /** To change the order with most recent articles at the bottom
            if (EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(FeedData.VRIJBIT_CHANNEL_ID).equals(mUri)) {
                entriesOrder = Constants.DB_ASC;
            }
             */

            String where = "(" + EntryColumns.FETCH_DATE + Constants.DB_IS_NULL +
                    Constants.DB_OR + EntryColumns.FETCH_DATE + "<=" + mListDisplayDate + ')';
            if (!FeedData.shouldShowReadEntries(mUri)) {
                where += Constants.DB_AND + EntryColumns.WHERE_UNREAD;
            }

            // Configure the query for the loader.
            CursorLoader cursorLoader = new CursorLoader(getActivity(),
                    mUri, // What is the source to be searched? (table)
                    // Defined new return columns.
                    // Why did the original programmers loaded the whole entrycolumns??
                    // These resulting columns should be corresponding with the cursor positions
                    // in EntriesCursorAdapter.java on line 325 (or about)
                    new String[]{
                            EntryColumns._ID,
                            EntryColumns.TITLE,
                            EntryColumns.IMAGE_URL,
                            EntryColumns.DATE,
                            EntryColumns.IS_READ,
                            EntryColumns.LINK,
                            EntryColumns.IS_FAVORITE,
                            FeedData.FeedColumns.NAME,
                            EntryColumns.FEED_ID,
                            FeedData.FeedColumns.ICON,
                            FeedData.FeedColumns.ICON_DRAWABLE
                    },
                    // null, // What columns do we get back from the query as a result? null = all available columns!
                    where, // selection criteria
                    null, // selection args
                    EntryColumns.DATE + entriesOrder    // order by
            );
            cursorLoader.setUpdateThrottle(150);       // Delay 150 ms: Do not restart the loader with new update.
            return cursorLoader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mEntriesCursorAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mEntriesCursorAdapter.swapCursor(Constants.EMPTY_CURSOR);
        }
    };  // *** End of Loader 1: mEntriesLoader


    //*** LOADER 2 *** LOADER 2 *** LOADER 2 *** LOADER 2 ***
    // Counts the number of unread articles in the database
    // Is started at different points within this class using restartLoaders()
    private int mNewEntriesNumber, mOldUnreadEntriesNumber = -1;
    private boolean mAutoRefreshDisplayDate = false;
    private final LoaderManager.LoaderCallbacks<Cursor> mEntriesNumberLoader = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            // mListDisplayDate is de grens. Oudere ongelezen berichten hadden we al (mOldUnreadEntriesNumber).
            // Nieuwere ongelezen berichten zijn net opgehaald (mNewEntriesNumber).
            // Zie verder bij onLoadFinished
            CursorLoader cursorLoader = new CursorLoader(getActivity(),
                    mUri,   // What is the source to be searched? (table)
                    new String[]{  // What columns do we get back from the query as a result? null = all available columns!
                            "SUM(" + EntryColumns.FETCH_DATE + '>' + mListDisplayDate + ")",
                            "SUM(" + EntryColumns.FETCH_DATE + "<=" + mListDisplayDate + Constants.DB_AND + EntryColumns.WHERE_UNREAD + ")"},
                    null,   // selection
                    null,   // selection args
                    null    // order by
            );
            cursorLoader.setUpdateThrottle(150); // Delay 150 ms: Do not restart the loader with new update.
            return cursorLoader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            data.moveToFirst();
            mNewEntriesNumber = data.getInt(0);
            mOldUnreadEntriesNumber = data.getInt(1);
            // Log.e(TAG, "Loader onLoadFinished ");
            if (mAutoRefreshDisplayDate && mNewEntriesNumber != 0 && mOldUnreadEntriesNumber == 0) {
                mListDisplayDate = new Date().getTime();
                restartLoaders();

            } else {
                // TODO: clean up this if-statement since toast message is no longer used
                if (startManualRefresh && mNewEntriesNumber == 0) {
                    // Log.e(TAG, "Loader onLoadFinished TOAST");
                    // Toast.makeText(getContext(), "OUD: Geen nieuwe artikelen.", Toast.LENGTH_SHORT).show();
                    startManualRefresh= false;
                }
                refreshUI();
            }

            mAutoRefreshDisplayDate = false;
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };   // *** End Loader 2: mEntriesNumberLoader





    /**
     *  SharedPreferences: Functie om te kijken of de 'voorkeuren' tijdens gebruik veranderen
     *
     *  1. Of de huidige lijst wordt ververst of juist niet. Als die stand verandert,
     *  moet de 'ophaal'-animatie worden aan of uitgezet.
     *
     *  2. Als de knop voor het verbergen van gelezen berichten wordt ingedrukt:
     *  Deze functie gebruiken we niet in het privacyVandaag
     *  De knop vind je in layout > view_hide_read_button.xml
     *  en wordt aangeorpen in > fragment > EntriesListFragment.java regel 280.
     *
     *  Hulpfuncties vind je in > utils > UiUtils.java
     *  (UiUtils.displayHideReadButtonAction() en UiUtils.updateHideReadButton())
     *
     * @param view
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          /*  if (PrefUtils.SHOW_READ.equals(key)) {
                getLoaderManager().restartLoader(ENTRIES_LOADER_ID, null, mEntriesLoader);
                UiUtils.updateHideReadButton(mHideReadButton);
            } else
            */
            if (PrefUtils.IS_REFRESHING.equals(key)) {
                refreshSwipeProgress();
            }
        }
    };

    // Define the button that is shown at the top when new articles are retrieved from the RSS feeds.
    // see also line 265 where the resource is set to it.
    private Button mRefreshListBtn;

    /**
     * Retrieving this instance's number from its arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
     //   Log.e(TAG, "onCreate");
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
        mEntriesListFragmentNumber = (getArguments() != null) ? getArguments().getInt("val") : 1;
        mFeedId = (getArguments() != null) ? getArguments().getInt("fid") : 1;

      //  Log.e(TAG, "onCreate is gedaan");
    }



    /**
     * Creating the view
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
   // @Override
    //public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    @Override
    public View onCreateViewSwipeable(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      //  Log.e(TAG, "onCreateViewSwipeable");
        View rootView = inflater.inflate(R.layout.fragment_entry_list, container, true); // orig was 'true'
        mListView = (ListView) rootView.findViewById(android.R.id.list);

        // Tip bij het zoekscherm
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_TIP, true) && (mUri.getPath().contains("search")) ) { // Alleen bij zoekscherm!
            final TextView header = new TextView(mListView.getContext());
            header.setMinimumHeight(UiUtils.dpToPixel(70));
            int footerPadding = UiUtils.dpToPixel(10);
            header.setPadding(footerPadding, footerPadding, footerPadding, footerPadding);
            header.setText(R.string.tip_sentence);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setCompoundDrawablePadding(UiUtils.dpToPixel(5));
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_about, 0, R.drawable.ic_action_cancel, 0);
            header.setClickable(true);
            header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mListView.removeHeaderView(header);
                    PrefUtils.putBoolean(PrefUtils.DISPLAY_TIP, false);
                }
            });
            mListView.addHeaderView(header);
        }


        UiUtils.addEmptyFooterView(mListView, 90);


        // set the button that is shown at the top of the listview when new articles are retrieved
        mRefreshListBtn = (Button) rootView.findViewById(R.id.refreshListBtn);
        mRefreshListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mNewEntriesNumber = 0;
                startManualRefresh= false;  // Er zijn nieuwe artikelen gevonden, dus deze kan op false om valse melding te voorkomen.
                mListDisplayDate = new Date().getTime();

                refreshUI();
                if (mUri != null) {
                    restartLoaders();
                }
            }
        });


        //**** SEARCH **** SEARCH **** SEARCH ****
        mSearchView = (SearchView) rootView.findViewById(R.id.searchView);  // Find search view in layout file
        if (savedInstanceState != null) {
            refreshUI(); // To hide/show the search bar
        }

        mSearchView.post(new Runnable() { // Do this AFTER the text has been restored from saveInstanceState
            @Override
            public void run() { // Hou het zoekveld in de gaten voor het inlezen van ingetoetste letters
                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String s) { // Lees de nieuw ingetoetste letter in
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
                        return false;
                    }

                    // Als de volgende letter is ingetoetst en/of de zoekterm dus is aangepast
                    // Met setData wordt telkens een nieuwe entriesCursorAdapter gemaakt die dus een
                    // nieuwe query op de database uitvoert.
                    @Override
                    public boolean onQueryTextChange(String s) {
                        setData(EntryColumns.SEARCH_URI(s), true);
                        return false;
                    }
                });
            }
        });
        // Einde zoeken



        // Stel de swipe in indien nodig.
       // disableSwipe();
        if ( mUri != null) {
           // Log.e("EntriesListFragment ~>", "Uri = " + mUri.toString());
        } else {
           // Log.e("EntriesListFragment ~>", "Uri = NULL ");
        }
        //*/
        restartLoaders();
       // Log.e(TAG, "Einde onCreateView");
        return rootView;



    }

    /*
    @Override
    public View onCreateViewSwipeable(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return null;
    }
*/




    /**
     * Bij elke nieuwe EntriesListFragment die aan een pagina wordt gekoppeld van de ViewPager
     * is een dataset nodig. Die wordt hieronder bepaald en gekoppeld
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Setting de adapter voor de ViewPager voor elke van de mogelijke lijsten
        // zoals die in de LeftDrawer als keuze verschijnen.
        // Elke pagina krijgt een eigen EntriesListFragment die de artikelen van een feed weergeeft
        // Het FeedId wordt meegegeven vanuit de ViewPager in HomeActivity
        // We hoeven hier dus alleen maar de bijbehorende URI van de query op de database te bepalen.

        // Log.e(TAG,"Dit mEntriesListFragmentNumber " + mEntriesListFragmentNumber + " krijgt feedID " + mFeedId);
        switch (mFeedId) {
            case DrawerAdapter.PSEUDO_FEED_ID_MEANING_SEARCH :
                mUri = EntryColumns.SEARCH_URI(getCurrentSearch());
                break;
            case DrawerAdapter.PSEUDO_FEED_ID_MEANING_FAVORITES :
                mUri = EntryColumns.FAVORITES_CONTENT_URI;
                break;
            case DrawerAdapter.PSEUDO_FEED_ID_MEANING_ALL_ENTRIES :
                mUri = EntryColumns.ALL_ENTRIES_CONTENT_URI;
                break;
            default:
                if (mFeedId != -1 ) {
                    mUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(mFeedId);
                } else {
                    mUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI("1");
                }
        }
        if (savedInstanceState != null) mListDisplayDate = savedInstanceState.getLong(STATE_LIST_DISPLAY_DATE);

/*
        mShowFeedInfo = true;
        // Maak een nieuwe adapter met de database op basis van de hierboven gekozen mUri (query)
        mEntriesCursorAdapter = new EntriesCursorAdapter(getActivity(),mUri,Constants.EMPTY_CURSOR, mShowFeedInfo);
        // Koppel de adapter (query met uit de database opgehaalde gegevens) aan de juiste entriesListFragment
        setListAdapter(mEntriesCursorAdapter);

        mListDisplayDate = new Date().getTime();

        // Start de loaders om de items voor de lijst (entries) op te halen
        if (mUri != null) {
            restartLoaders();
        }
        refreshUI();

*/

        setData(mUri, true);

    }


    /**
     * Set the adapter and get the data for the list using the URI to select (= query).
     */
     public void setData(Uri uri, boolean showFeedInfo) {
        mUri = uri;
        mShowFeedInfo = showFeedInfo;

        // Doe een nieuwe query op de database met de nieuwe keuze of nieuwe zoekterm (mUri)
        mEntriesCursorAdapter = new EntriesCursorAdapter(getActivity(), mUri, Constants.EMPTY_CURSOR, mShowFeedInfo);
        setListAdapter(mEntriesCursorAdapter);

        mListDisplayDate = new Date().getTime();

         // Start de loaders om de items voor de lijst (entries) op te halen
        if (mUri != null) { restartLoaders(); }
        refreshUI();
    }










    /**
     * Initialisatie van elke nieuwe entrieslist voor de ViewPager.
     * Wordt vanuit HomeActivity.java als eerste door de Viewpager aangeroepen
     *
     * De dataset die aan elke pagina moet worden gekoppeld, gebeurt hierboven bij onActivityCreated();
     *
     * @param positie
     * @return
     */
    public static EntriesListFragment init(int positie,int feedId) {

      //  Log.e(TAG, "init nieuwe pagina met positienummer " + positie);
        EntriesListFragment viewPagerFragmentList = new EntriesListFragment();

        // Supply val input as an argument.
        Bundle args = new Bundle();
        args.putInt("val", positie);
        args.putInt("fid", feedId);
        viewPagerFragmentList.setArguments(args);
    //    Log.e(TAG, "Pagina met positienummer " + positie + " is set");
        return viewPagerFragmentList;
    }




    @Override
    public void onStart() {
        super.onStart();
        refreshSwipeProgress();
        PrefUtils.registerOnPrefChangeListener(mPrefListener);

        if (mUri != null) {
            // If the drawerMenuList is empty when we are going back here, try with the last display date
            if (mNewEntriesNumber != 0 && mOldUnreadEntriesNumber == 0) {
                mListDisplayDate = new Date().getTime();
            } else {
                mAutoRefreshDisplayDate = true; // We will try to update the drawerMenuList after if necessary
            }

            restartLoaders();
        }
        //*/
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        if (id >= 0) { // should not happen, but I had a crash with this on PlayStore...
            // Log.e (TAG, "id = "+id +  " mUri = "+mUri);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mUri, id)));
                //startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mUri, 1)));
            }
            catch (Exception e)
            {
                // TODO: handle exception
                Log.e (TAG, e.getMessage());
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear(); // This is needed to remove a bug on Android 4.0.3

        inflater.inflate(R.menu.entry_list, menu);

                    /* We hebben de rerefsh button niet meer nodig, want we hebben swiperefresh.
                        De knop staat dus uit in de layout > entrylist.xml
                     */
                    /*
                    if (EntryColumns.FAVORITES_CONTENT_URI.equals(mUri)) {
                        menu.findItem(R.id.menu_refresh).setVisible(false);
                    } else {
                        menu.findItem(R.id.menu_share_starred).setVisible(false);
                    }
                    */
        // Maak de optie favorieten te delen alleen zichtbaar bij favorieten
        if (!EntryColumns.FAVORITES_CONTENT_URI.equals(mUri)) {
            menu.findItem(R.id.menu_share_starred).setVisible(false);
        }
        // Maak de optie artikelen te herladen onzichtbaar bij favorieten en zoeken
        if ((mUri.getPath().contains("search")) || ( EntryColumns.FAVORITES_CONTENT_URI.equals(mUri))) {
            menu.findItem(R.id.reset_app).setVisible(false);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Menu items in the tool bar of an entrieslist
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_share_starred: {
                if (mEntriesCursorAdapter != null) {
                    String favoritesList = mEntriesCursorAdapter.getFavoritesList();
                    if (favoritesList != null) {
                        startActivity(Intent.createChooser(
                                new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_favorites_title))
                                        .putExtra(Intent.EXTRA_TEXT, favoritesList).setType(Constants.MIMETYPE_TEXT_PLAIN), getString(R.string.menu_share)
                        ));
                    }
                }
                return true;
            }
            case R.id.reset_app: {  // option to reload articles of the specific feed or feeds
                // Dialogue popup to confirm to delete the existing entries first before reloading.
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.reset_title);
                builder.setCancelable(true);
                builder.setMessage(DeprecateUtils.fromHtml(getString(R.string.reset_message)));
                builder.setNegativeButton("Annuleer",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                builder.setPositiveButton("Herlaad",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                resetFeed();
                            }
                        });
                builder.show();

                return true;
            }
            /* We do not use this menu option since we can swipe to refresh
            case R.id.menu_refresh: {
                startRefresh();
                return true;
            }
            */
            case R.id.menu_all_read: {
                if (mEntriesCursorAdapter != null) {
                    mEntriesCursorAdapter.markAllAsRead(mListDisplayDate);
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Delete all entries (articles) from this feed, reread the RSS feed and reload the
     * full articles from website inclusing assets like images.
     */
    private void resetFeed() {
        String feedId = String.valueOf(mFeedId);
        new ResetUtils().resetLastUpdateTime(feedId);   // reset feed. Delete all existing tasks, images & entries
        if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {    // als er niet al verversd wordt!
            getContext().startService(new Intent(getContext(), FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS).putExtra(Constants.FEED_ID, feedId));
        }
    }


    @Override
    public void onStop() {
        PrefUtils.unregisterOnPrefChangeListener(mPrefListener);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(STATE_URI, mUri);
        outState.putBoolean(STATE_SHOW_FEED_INFO, mShowFeedInfo);
        outState.putLong(STATE_LIST_DISPLAY_DATE, mListDisplayDate);

        super.onSaveInstanceState(outState);
    }
    @Override
    public void onRefresh() {
        startRefresh();
    }




/*

    public void setData(Uri uri, boolean showFeedInfo) {
        Log.e(TAG, "setData");
        mUri = uri;
        mShowFeedInfo = showFeedInfo;

        // Doe een nieuwe query op de database met de nieuwe keuze of nieuwe zoekterm (mUri)
        mEntriesCursorAdapter = new EntriesCursorAdapter(getActivity(), mUri, Constants.EMPTY_CURSOR, mShowFeedInfo);
        setListAdapter(mEntriesCursorAdapter);

        mListDisplayDate = new Date().getTime();
        if (mUri != null) {
            restartLoaders();
        }
        refreshUI();
        Log.e(TAG, "Data is set");
    }
*/

    /**
     * Start het verversen van de feed na de swipe beweging.
     */
private void startRefresh() {
    if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
        if (mUri != null && FeedDataContentProvider.URI_MATCHER.match(mUri) == FeedDataContentProvider.URI_ENTRIES_FOR_FEED) {
            getActivity().startService(new Intent(getActivity(), FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS).putExtra(Constants.FEED_ID,
                    mUri.getPathSegments().get(1)));
        } else {
            getActivity().startService(new Intent(getActivity(), FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS));
        }
        // Log.e(TAG, "StartRefresh()");
        startManualRefresh= true;
    }

    refreshSwipeProgress();
}



    public String getCurrentSearch() {
        return mSearchView == null ? null : mSearchView.getQuery().toString();
    }


    /**
     * Via deze functie worden de loaders naar de database gestart en herstart.
     */
    private void restartLoaders() {
     //   Log.e(TAG, "restartLoaders");
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.restartLoader(ENTRIES_LOADER_ID, null, mEntriesLoader);   // Loader 1
        loaderManager.restartLoader(NEW_ENTRIES_NUMBER_LOADER_ID, null, mEntriesNumberLoader);  // Loader 2
    }

    /**
     * The display needs to be refreshed in some cases.
     * For instance to display the searchfield or to disable / enable the swipe functionality.
     * Or to display a button at top of the display if new articles have been fetched by the FetcherService.java
     */
    private void refreshUI() {
        if (mUri != null && FeedDataContentProvider.URI_MATCHER.match(mUri) == FeedDataContentProvider.URI_SEARCH) {
           mSearchView.setVisibility(View.VISIBLE);
            disableSwipe();
        } else {
           mSearchView.setVisibility(View.GONE);
           enableSwipe();
        }
        if (mUri != null && FeedDataContentProvider.URI_MATCHER.match(mUri) == FeedDataContentProvider.URI_FAVORITES) {
           disableSwipe();
        }
        // If newly fetched articles are found in the database, this button is shown at the top of the drawerMenuList.
        // mNewEntriesNumber is the result of the query performed by Loader 2: mEntriesNumberLoader
        if (mNewEntriesNumber > 0) {
            mRefreshListBtn.setText(getResources().getQuantityString(R.plurals.number_of_new_entries_refresh_button, mNewEntriesNumber, mNewEntriesNumber));
           mRefreshListBtn.setVisibility(View.VISIBLE);
        } else {
            mRefreshListBtn.setVisibility(View.GONE);
        }

    }

    private void refreshSwipeProgress() {
        if (PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
            showSwipeProgress();
        } else {
            hideSwipeProgress();
        }
    }


}