package nl.privacybarometer.privacyvandaag;

import android.util.Log;
import nl.privacybarometer.privacyvandaag.provider.FeedData;

/**
 * Static class in which the menu is defined.
 *
 * TODO: This class should be turned to a menu with cursor adapter and incorporated in DrawerAdapter.class
 * TODO: It gives more flexibility, for example if customization by the user is wanted.
 * TODO: This static menu is a quick fix to get the app in the store.
 *
 * TODO: Notice: In the feedtable there is already a field 'Priority' that gives the order of the feeds.
 * TODO: The query (cursor) for the DrawerAdapter loader is already sorted according the priority values.
 *
 * The menu is used to fill the Left Drawer (see HomeActivity and DrawerAdapter)
 * The menu is also used to fill the ViewPager (see HomeActivity near the bottom of the class)
 *
 */
public class MenuPrivacyVandaag {
    private static final String TAG = MenuPrivacyVandaag.class.getSimpleName() + " ~> ";
    // Initialiseer de menu items
    public static final int NUMBER_OF_MENU_ITEMS = 14; // Number of items in the menu including the section headers
    public static MenuItems[] menuItemsArray = new MenuItems[NUMBER_OF_MENU_ITEMS];



    /**
     * **** MENU-INDELING **** MENU-INDELING **** MENU-INDELING **** MENU-INDELING ****
     *
     * NB: feed Id's can be found in FeedData.java
     *
     * MenuPrivacyVandaag layout:
     * 0 - Section Header nieuws
     * 1 -      query all entries
     * 4 - Section Header organisaties
     * 3 -      feed privacy nieuws
     * 4 -      feed PrivacyBarometer
     * 5 -      feed Bits of Freedom
     * 6 -      feed Privacy First
     * 7 -      feed Vrijbit
     * 8 -      feed KdvP
     * 9 -      feed Autoriteit Persoonsgegevens
     *   - Section Header naslag  (not yet implemented)
     *   -     item  (not yet implemented)
     *          ...
     * 10 - Section Header overig
     * 11 -      query favorieten
     * 12 -      query zoeken
     * 13 -      feed service berichten
     *
     * See provider > FeedData.java for more info about the feeds
     *
     */


    private static final int FAKE_FEED_ID_FOR_SECTION_HEADERS = -1;
    // These values should be larger than MAX_FEED_ID. Max_FEED_ID seperates the real feeds from other items/pages.
    public static final int FAKE_FEED_ID_MEANING_FAVORITES = 99;
    public static final int  FAKE_FEED_ID_MEANING_SEARCH = 98;
    public static final int  FAKE_FEED_ID_MEANING_ALL_ENTRIES = 97;
    // All feeds in the app have a feedId. These are the feedId's in the database in the table that gholds the feeds.
    // Some menu-items behave like feeds but do not have articles of themselves, so they do not exist in the database.
    // These items get fake Id's to make them behave like feeds with a page in the ViewPager and a menuItem in the LeftDrawer
    // They also have a query to select and display articles from the database in a list.
    // Items like this are 'Favorites', 'Search' and 'All Articles'.
    // To distinguish these fake feeds from the real thing, they get Id's higer than MAX_FEED_ID.
    public final static int MAX_FEED_ID = 90; // values higer than MAX_FEED_ID are fake ID's for special pages like favorites and search


    // TODO: These values can all be retrieved from de menuItemsArray, so no need to hardcode it.
    private static final int MENU_POSITION_SERVICEBERICHTEN = 13;
    private static final int MENU_POSITION_ALL_ENTRIES = 1;
    private static final int MENU_POSITION_FAVORITES = 11;
    private static final int MENU_POSITION_SEARCH = 12;

    // Stel de paginavolgorde voor de PageViewer in. Welke pagina in de pageviewer?
    private static final int PAGE_POSITION_FAVORITES = 8;
    public static final int PAGE_POSITION_SEARCH = 9;   // wordt ook gebruikt in EntriesListFragment voor tip.
    // private static final int PAGE_POSITION_SERVICEBERICHTEN = 10;

    /**
     * Making the menu items by putting the properties of an item in an object
     * Put all the menu objects in an array.
     * The order (index) of the items in the array is the order in which they will appear in the menu
     */
    public static void makeMenuItems() {

        for ( int i=0; i<menuItemsArray.length; i++) {
            menuItemsArray[i]=new MenuItems();  // Initialiseer elk item.
        }
        // fill the menu
        // addItem(String internMenuName, Menunaam voor headers, menuPos, pageViewerPos, isSectionHeader, feedId)
        menuItemsArray[0].addItem (">>> Sectie Nieuws",MainApplication.getContext().getString(R.string.menu_section_news),true,FAKE_FEED_ID_FOR_SECTION_HEADERS);
        menuItemsArray[1].addItem ("Privacynieuws van organisaties","",false,FAKE_FEED_ID_MEANING_ALL_ENTRIES);

        menuItemsArray[2].addItem (">>> Sectie Organisaties",MainApplication.getContext().getString(R.string.menu_section_organisations),true,FAKE_FEED_ID_FOR_SECTION_HEADERS);
        menuItemsArray[3].addItem ("Algemeen privacynieuws","",false,(int) FeedData.PB_TWITTER_CHANNEL_ID);
        menuItemsArray[4].addItem ("Privacy Barometer","",false,(int) FeedData.PRIVACYBAROMETER_CHANNEL_ID);
        menuItemsArray[5].addItem ("Bits of Freedom","",false,(int) FeedData.BITS_OF_FREEDOM_CHANNEL_ID);
        menuItemsArray[6].addItem ("PrivacyFirst","",false,(int) FeedData.PRIVACY_FIRST_CHANNEL_ID);
        menuItemsArray[7].addItem ("Vrijbit","",false,(int) FeedData.VRIJBIT_CHANNEL_ID);
        menuItemsArray[8].addItem ("KDVP","",false,(int) FeedData.KDVP_CHANNEL_ID);
        menuItemsArray[9].addItem ("Autoriteit Persoonsgegevens","",false,(int) FeedData.AUTORITEIT_PERSOONSGEGEVENS_CHANNEL_ID);
        menuItemsArray[10].addItem (">>> Sectie Overig",MainApplication.getContext().getString(R.string.menu_section_other),true,FAKE_FEED_ID_FOR_SECTION_HEADERS);
        menuItemsArray[11].addItem ("Favorieten","",false,FAKE_FEED_ID_MEANING_FAVORITES);
        menuItemsArray[12].addItem ("Zoeken","",false,FAKE_FEED_ID_MEANING_SEARCH);
        menuItemsArray[13].addItem ("Serviceberichten","",false,(int) FeedData.SERVICE_CHANNEL_ID);

        /*
         * Naslag items:
         * Wet bescherming persoonsgegevens
         * Overzicht alle wetten en wetsvoorstel zoals die op de PB staan
         *
         */

    }



    /**
     * Class om de menu-items in te bewaren.
     * Elk menu-item is een object. Het geheel wordt in een array bewaard.
     */
    public static class MenuItems { // has to be public!
        // public String name; // ONLY FOR DEBUGGING PURPOSES. REMOVE BEFORE PUBLICIZING APP!!!
        private String sectionTitle;
        public boolean hasViewPagerPage;
        public int viewPagerPagePosition;
        public int feedId;
        private boolean sectionHeader;

        void addItem (String name, String sectionTit, boolean isSectionHeader, int feed) {
           // this.name = name; // ONLY FOR DEBUGGING PURPOSES.
            this.sectionTitle = sectionTit; // To avoid nullpointers, all sectionTitle get an (empty) string
            this.sectionHeader = isSectionHeader;    // Menu items that are no items but section header get true
            this.feedId = feed;    // id of the feed in the database
            this.viewPagerPagePosition = 0;    // position of the page in the PageViewer is set in HomeActivity > ViewPager
        }
    }


    // Welke menu items zijn sectie titels en dus geen menukeuzes?
    public static boolean isSectionHeader (int menuPosition) {
        return menuItemsArray[menuPosition].sectionHeader;
    }

    // Welke tekst moet er komen te staan als sectie titel?
    public static String sectionTitle(int menuPosition) {
        return menuItemsArray[menuPosition].sectionTitle;
    }

    // Koppel de menu-items aan de juiste feed in de database.
    // Als het geen feed is, maar een bepaalde query op de database, krijgt het een FAKE cursor positie
    // hIERMEE WORDT ONDER ANDERE de titel en het icon geplaatst
    public static int getCursorPosition (int menuPosition) {
        return menuItemsArray[menuPosition].feedId-1;
    }

    // Welke menu items krijgen een contextMenu in de left drawer?
    public static boolean hasContextMenu (int menuPosition) {
        return (menuItemsArray[menuPosition].feedId != FeedData.SERVICE_CHANNEL_ID) &&
                (menuItemsArray[menuPosition].feedId > 0 && menuItemsArray[menuPosition].feedId < MAX_FEED_ID);
    }

    // Has the menu-item that has a contextmenu also a website to go to from that contextmenu?
    public static boolean hasWebsite (int menuPosition) {
        // Log.e (TAG,"De feedID == " + menuItemsArray[menuPosition].feedId);
        // Log.e (TAG,"De PBTwitterChannel == " + FeedData.PB_TWITTER_CHANNEL_ID);
        return (menuItemsArray[menuPosition].feedId != FeedData.PB_TWITTER_CHANNEL_ID);
    }


    public static int getFeedIdFromMenuPosition (int menuPosition) {
        return menuItemsArray[menuPosition].feedId;
    }


    // Vindt de juiste menupositie als je het paginanummer krijgt door te swipen via de ViewPager
    public static int getMenuPositionFromPageNumber(int pagePosition) {
        for ( int i=0; i<menuItemsArray.length; i++) {
            if (menuItemsArray[i].viewPagerPagePosition ==pagePosition) return i;
        }
        return 1;
    }
    public static boolean isSearchPage (int pagePosition) {
        return (pagePosition == PAGE_POSITION_SEARCH);
    }
    public static boolean isFavoritesPage (int pagePosition) {
        return (pagePosition == PAGE_POSITION_FAVORITES);
    }

    // Not very efficient, but is needed only once in HomeActivity if app starts from notification
    public static int getMenuPositionFromFeedId(int feedId) {
        for ( int i=0; i<menuItemsArray.length; i++) {
            if (menuItemsArray[i].feedId==feedId) return i;
        }
        return 1;
    }

    public static boolean isEnabled(int menuPosition) {
        return !menuItemsArray[menuPosition].sectionHeader;
    }

    public static int getCount() {
        return NUMBER_OF_MENU_ITEMS;
    }

    // Vindt de juiste menupositie als je het paginanummer krijgt door te swipen via de ViewPager
    public static int getPageNumberFromMenuPosition(int menuPosition) {
        return menuItemsArray[menuPosition].viewPagerPagePosition;
    }
    public static boolean isFavorites (int menuPosition) {
        return (menuPosition == MENU_POSITION_FAVORITES);
    }

    public static boolean isSearch (int menuPosition) {
        return (menuPosition == MENU_POSITION_SEARCH);
    }

    public static boolean isServiceChannel (int menuPosition) {
        return (menuPosition == MENU_POSITION_SERVICEBERICHTEN);
    }

    public static boolean isAllEntries (int menuPosition) {
        return (menuPosition == MENU_POSITION_ALL_ENTRIES);
    }

    public static boolean hasViewPagerPage (int menuPosition) {
        return menuItemsArray[menuPosition].hasViewPagerPage;
    }



/*

    // Vindt de juiste feednummer als je het paginanummer krijgt door te swipen via de ViewPager
    // Dit hangt af van welke pagina's er wel of niet worden gevolgd!!
    public static int getCursorPositionFromPageNumber (int pagePosition) {
        for ( MenuItems a : menuItemsArray) {
            if (a != null) {
                if (a.viewPagerPagePosition == pagePosition) {
                    if (a.feedId < MAX_FEED_ID) return (int) a.feedId;
                    else return -1;
                }
            } else Log.e (TAG, "Nullpointer bij het opvragen van de FeedId van het object menuItem.");

        }
        return -1;
    }
//*/



}


