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

package nl.privacybarometer.privacyvandaag.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.activity.HomeActivity;
import nl.privacybarometer.privacyvandaag.parser.RssAtomParser;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.TaskColumns;
import nl.privacybarometer.privacyvandaag.utils.ArticleTextExtractor;
import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.HtmlUtils;
import nl.privacybarometer.privacyvandaag.utils.NetworkUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FetcherService extends IntentService {
    private static final String TAG = FetcherService.class.getSimpleName() + " ~> ";
    public static final String ACTION_REFRESH_FEEDS = "nl.privacyVandaag.app.REFRESH";
    public static final String ACTION_MOBILIZE_FEEDS = "nl.privacyVandaag.app.MOBILIZE_FEEDS";
    public static final String ACTION_DOWNLOAD_IMAGES = "nl.privacyVandaag.app.DOWNLOAD_IMAGES";

    private static final int THREAD_NUMBER = 3;
    private static final int MAX_TASK_ATTEMPT = 3;

    private static final String DEFAULT_FETCH_OLD_NEWS_TIME = "31";   // Number of days in the past the articles have to be fetched.

    private static final int FETCHMODE_DIRECT = 1;
    private static final int FETCHMODE_REENCODE = 2;
    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // ModPrivacyVandaag: 'Do not refresh feed' mode introduced.

    private static final String CHARSET = "charset=";
    private static final String CONTENT_TYPE_TEXT_HTML = "text/html";
    private static final String HREF = "href=\"";

    private static final String HTML_BODY = "<body";
    private static final String ENCODING = "encoding=\"";

    public static final String NOTIFICATION_FEED_ID = "NotificationFeedId";

    /* Allow different positions of the "rel" attribute w.r.t. the "href" attribute */
    private static final Pattern FEED_LINK_PATTERN = Pattern.compile(
            "[.]*<link[^>]* ((rel=alternate|rel=\"alternate\")[^>]* href=\"[^\"]*\"|href=\"[^\"]*\"[^>]* (rel=alternate|rel=\"alternate\"))[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private final Handler mHandler;

    public FetcherService() {
        super(FetcherService.class.getSimpleName());
        HttpURLConnection.setFollowRedirects(true);
        mHandler = new Handler();
    }

    // Should the full article be retrieved ( = mobilised) or not?
    public static boolean hasMobilizationTask(long entryId) {
        Cursor cursor = MainApplication.getContext().getContentResolver().query(
                TaskColumns.CONTENT_URI, // What is the source to be searched? (table)
                TaskColumns.PROJECTION_ID,  // What columns should be returnen in the result
                TaskColumns.ENTRY_ID + '=' + entryId + Constants.DB_AND + TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, // What selection should the query make
                null,   // selection args
                null    // order by
        );

        boolean result = (cursor.getCount() > 0);
        cursor.close();

        return result;
    }

    // Add a task to be executed for fetching each image found in the text.
    public static void addImagesToDownload(String entryId, ArrayList<String> images) {
        if (images != null && !images.isEmpty()) {
            ContentValues[] values = new ContentValues[images.size()];
            for (int i = 0; i < images.size(); i++) {
                values[i] = new ContentValues();
                values[i].put(TaskColumns.ENTRY_ID, entryId);
                values[i].put(TaskColumns.IMG_URL_TO_DL, images.get(i));
            }

            MainApplication.getContext().getContentResolver().bulkInsert(TaskColumns.CONTENT_URI, values);
        }
    }

    public static void addEntriesToMobilize(long[] entriesId) {
        ContentValues[] values = new ContentValues[entriesId.length];
        for (int i = 0; i < entriesId.length; i++) {
            values[i] = new ContentValues();
            values[i].put(TaskColumns.ENTRY_ID, entriesId[i]);
        }

        MainApplication.getContext().getContentResolver().bulkInsert(TaskColumns.CONTENT_URI, values);
    }

    /**
     * This is called for every task that must be performed by the FetcherService.
     * This includes:
     * - refresh feeds to check on new articles
     * - retrieve full articles by using the links provided by the RSS feeds. This is called 'mobilization' of an item
     * - download the images belonging to the retrieved articles
     *
     *  if item are to be mobilized and images are te be retrieved, the request to do so is stored in the database.
     *  If mobilisation was previously not succesfull, this service will try again each time the feed is refreshed.
     *  // TODO: In case of an error, the error will keep coming back.
     *
     *
     * @param intent
     */
    @Override
    public void onHandleIntent(Intent intent) {
        if (intent == null) { // No intent, we quit
            return;
        }

        boolean isFromAutoRefresh = intent.getBooleanExtra(Constants.FROM_AUTO_REFRESH, false);

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        // Connectivity issue, we quit
        if (networkInfo == null || networkInfo.getState() != NetworkInfo.State.CONNECTED) {
            if (ACTION_REFRESH_FEEDS.equals(intent.getAction()) && !isFromAutoRefresh) {
                // Display a toast in that case
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FetcherService.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return;
        }

        boolean skipFetch = isFromAutoRefresh && PrefUtils.getBoolean(PrefUtils.REFRESH_WIFI_ONLY, false)
                && networkInfo.getType() != ConnectivityManager.TYPE_WIFI;
        // We need to skip the fetching process, so we quit
        if (skipFetch) {
            return;
        }

        if (ACTION_MOBILIZE_FEEDS.equals(intent.getAction())) {
            mobilizeAllEntries();
            downloadAllImages();
        } else if (ACTION_DOWNLOAD_IMAGES.equals(intent.getAction())) {
            downloadAllImages();
        } else { // == Constants.ACTION_REFRESH_FEEDS
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, true);    // turn the refresh animation on

            // If the refreshFeeds is called automatically from the timer, store the time to restart the timer with.
            if (isFromAutoRefresh) {
                PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, SystemClock.elapsedRealtime());
            }

            // Set the time in the past before which we will delete old entries. More recent articles are to be kept in the database
            long keepTime = Long.parseLong(PrefUtils.getString(PrefUtils.KEEP_TIME, DEFAULT_FETCH_OLD_NEWS_TIME)) * Constants.DURATION_OF_ONE_DAY;
            long keepDateBorderTime = keepTime > 0 ? System.currentTimeMillis() - keepTime : 0;
            deleteOldEntries(keepDateBorderTime); // check for ancient articles and delete them

            // Check if the refreshfeed is started by the user for one specific feed.
            String feedId = intent.getStringExtra(Constants.FEED_ID);

            // Start refreshing the feed or the feeds. This can be called by the user
            // or by the auto refresh timer in RefreshService.java
            // The number of newly retrieved articles is stored in newCount
            int newCount = ((feedId == null) ?
                    refreshFeeds(keepDateBorderTime, isFromAutoRefresh) : // There is no feedId, so refresh all feeds
                    refreshFeed(feedId, keepDateBorderTime, isFromAutoRefresh));  // There is one specific feed to be refreshed

            // We found no new articles. Display a short Toast message saying so.
            if ((newCount == 0) && !isFromAutoRefresh) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FetcherService.this, R.string.no_new_entries, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            mobilizeAllEntries();   // Get the full articles of the old en new RSS-items found.
            downloadAllImages();    // Get the images we don not have already.

            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);
        }
    }

    /**
     * Retrieve the full article by following the link provided with the retrieved RSS-item.
     */
    private void mobilizeAllEntries() {
        // Log.e (TAG,"mobilizeAllEntries()");
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.NUMBER_ATTEMPT},
                TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null);

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long taskId = cursor.getLong(0);
                long entryId = cursor.getLong(1);
                int nbAttempt = 0;
                if (!cursor.isNull(2)) {
                    nbAttempt = cursor.getInt(2);
                }

                boolean success = false;

                Uri entryUri = EntryColumns.CONTENT_URI(entryId);
                Cursor entryCursor = cr.query(entryUri, null, null, null, null);

                // Log.e (TAG,"mobilizeAllEntries() ~ mobilising " + entryId);
                if (entryCursor != null) {
                    if (entryCursor.moveToFirst()) {
                        if (entryCursor.isNull(entryCursor.getColumnIndex(EntryColumns.MOBILIZED_HTML))) { // Check of artikel niet al ingelezen is
                            int linkPos = entryCursor.getColumnIndex(EntryColumns.LINK);
                            int abstractHtmlPos = entryCursor.getColumnIndex(EntryColumns.ABSTRACT);
                            int feedIdPos = entryCursor.getColumnIndex(EntryColumns.FEED_ID);
                            HttpURLConnection connection = null;

                            try {
                                String link = entryCursor.getString(linkPos);
                                String feedId = entryCursor.getString(feedIdPos);
                                String cookieName = "";
                                String cookieValue = "";
                                Cursor cursorFeed = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null);
                                if (cursorFeed != null) {
                                    cursorFeed.moveToNext();
                                    int cookieNamePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_NAME);
                                    int cookieValuePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_VALUE);
                                    cookieName = cursorFeed.getString(cookieNamePosition);
                                    cookieValue = cursorFeed.getString(cookieValuePosition);
                                    cursorFeed.close();
                                }
                                // Take substring from RSS content and use it
                                // to try to find a text indicator for better content extraction
                                String contentIndicator = null;
                                String text = entryCursor.getString(abstractHtmlPos);
                                if (!TextUtils.isEmpty(text)) {
                                    text = DeprecateUtils.fromHtml(text).toString();
                                    if (text.length() > 60) {
                                        contentIndicator = text.substring(20, 40);
                                    }
                                }

                                // Maak verbinding met de webpagina waar het artikel staat en haal het artikel op.
                                // Filter het deel waar het met de artikel tekst uit de geheel ingelezen webpagina.
                                connection = NetworkUtils.setupConnection(link, cookieName, cookieValue);
                                String mobilizedHtml = ArticleTextExtractor.extractContent(connection.getInputStream(), contentIndicator, link);
                                // Nabewerking, afbeeldingen en opslaan als het artikel gevonden en is ingelzen
                                if (mobilizedHtml != null) {
                                    //Log.e (TAG, "Starting to mobilize entry");
                                    mobilizedHtml = HtmlUtils.improveHtmlContent(mobilizedHtml, NetworkUtils.getBaseUrl(link));
                                    ContentValues values = new ContentValues();
                                    values.put(EntryColumns.MOBILIZED_HTML, mobilizedHtml);

                                    ArrayList<String> imgUrlsToDownload = null;
                                    if (NetworkUtils.needDownloadPictures()) {  // Check instellingen of afbeelding opgehaald moet worden.
                                        imgUrlsToDownload = HtmlUtils.getImageURLs(mobilizedHtml);
                                    }

                                    String mainImgUrl;
                                    // Get the image to display with article.
                                    // If images are not yet selected from the text and stored in the database,
                                    // we select the first correct image from the text..
                                    if (imgUrlsToDownload != null) {
                                        mainImgUrl = HtmlUtils.getMainImageURL(imgUrlsToDownload);  // Pak de eerste als Hoofdafbeelding
                                    } else {
                                        mainImgUrl = HtmlUtils.getMainImageURL(mobilizedHtml);
                                    }

                                    if (mainImgUrl != null) {
                                        // Log.e (TAG,"Image URL = " + mainImgUrl);
                                        values.put(EntryColumns.IMAGE_URL, mainImgUrl);
                                    }

                                    if (cr.update(entryUri, values, null, null) > 0) {
                                        success = true;
                                        operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                                        if (imgUrlsToDownload != null && !imgUrlsToDownload.isEmpty()) {
                                            addImagesToDownload(String.valueOf(entryId), imgUrlsToDownload);
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                                if (connection != null) {
                                    connection.disconnect();
                                }
                            } finally {
                                if (connection != null) {
                                    connection.disconnect();
                                }
                            }
                        } else { // We already mobilized it
                            success = true;
                            operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                        }
                    }
                    entryCursor.close();
                }

                if (!success) {
                    Log.e(TAG,"Mobilisation not succeeded. Better luck next time. # of attempts = " + nbAttempt);
                    if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                        operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                    } else {
                        ContentValues values = new ContentValues();
                        values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1);
                        operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build());
                    }
                }
            }

            cursor.close();
        }
        if (!operations.isEmpty()) {
            try {
                cr.applyBatch(FeedData.AUTHORITY, operations);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Download all images belonging to the articles that are earlier retrieved.
     */
    private void downloadAllImages() {
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.IMG_URL_TO_DL,
                TaskColumns.NUMBER_ATTEMPT}, TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NOT_NULL, null, null);

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long taskId = cursor.getLong(0);
                long entryId = cursor.getLong(1);
                String imgPath = cursor.getString(2);
                int nbAttempt = 0;
                if (!cursor.isNull(3)) {
                    nbAttempt = cursor.getInt(3);
                }

                try {
                    NetworkUtils.downloadImage(entryId, imgPath);

                    // If we are here, everything is OK
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                } catch (Exception e) {
                    Log.e(TAG, "Afbeelding downloaden lukt niet in een keer. Poging: " + nbAttempt);
                    if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                        operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                    } else {
                        ContentValues values = new ContentValues();
                        values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1);
                        operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build());
                    }
                }
            }

            cursor.close();
        }
        if (!operations.isEmpty()) {
            try {
                cr.applyBatch(FeedData.AUTHORITY, operations);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Verwijder eerst de mogelijk verouderde items voordat een feed wordt ververst.
     * Verwijder eerst de items met de bewaartermijn die in de voorkeuren is ingesteld.
     * Verwijder daarna de items volgens de specifieke bewaartermijn per feed.
     *
     * @param keepDateBorderTime
     */
    private void deleteOldEntries(long keepDateBorderTime) {
        if (keepDateBorderTime > 0) {
            String where = EntryColumns.DATE + '<' + keepDateBorderTime + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE;
            // Delete the entries, the cache files will be deleted by the content provider
            MainApplication.getContext().getContentResolver().delete(EntryColumns.CONTENT_URI, where, null);
        }
        Cursor cursor = MainApplication.getContext().getContentResolver().query(FeedColumns.CONTENT_URI, new String[]{FeedColumns._ID, FeedColumns.KEEP_TIME}, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long feedid = cursor.getLong(0);
                long keepTimeLocal = cursor.getLong(1) == 1 ? (long) (Constants.DURATION_OF_ONE_DAY / 4) : cursor.getLong(1) * Constants.DURATION_OF_ONE_DAY; // Als het op 1 dag staat, doen we een halve dag.
                long keepDateBorderTimeLocal = keepTimeLocal > 0 ? System.currentTimeMillis() - keepTimeLocal : 0;
                if (keepDateBorderTimeLocal > 0) {
                    String where = EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + EntryColumns.FEED_ID + "=" + String.valueOf(feedid);
                    MainApplication.getContext().getContentResolver().delete(EntryColumns.CONTENT_URI, where, null);
                }
            }
            cursor.close();
        }

    }


    /**
     * Method to refresh all feeds in order to check whether new articles are available.
     * Called from this same file on line 227 onHandleIntent();
     *
     * @param keepDateBorderTime
     * @param isFromAutoRefresh
     * @return
     */
    private int refreshFeeds(final long keepDateBorderTime, final boolean isFromAutoRefresh) {
        ContentResolver cr = getContentResolver();
        // Get all the feeds from the database that have to be checked on new articles.
        final Cursor cursor = cr.query(FeedColumns.CONTENT_URI, FeedColumns.PROJECTION_ID, null, null, null);
        int nbFeed = cursor.getCount(); // number of feeds to be checked.


        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });

        // Start refreshing all selected feeds
        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor);
        while (cursor.moveToNext()) {
            final String feedId = cursor.getString(0);
            completionService.submit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    int result = 0;
                    try {
                        result = refreshFeed(feedId, keepDateBorderTime, isFromAutoRefresh);   // ModPrivacyVandaag: refresh feed en return number of new articles found
                    } catch (Exception e) {
                        Log.e(TAG, "Error refreshing feed " + e.getMessage());
                    }
                    return result;
                }
            });
        }
        cursor.close();

        /**
         * While scanning the source websites for new articles, it is unknown
         * how fast they will respond and in what order.
         * completionService comes in handy as it watches the incoming results of these concurrent tasks.
         * It gives a callback as soon as a result is available, no matter in what order the check of feeds started.
         * So we can process the result as soon as it becomes available.
         * The only thing we need to do is to make sure we check on the completionService for all the running tasks.
         * Therefore, we kept track of the number of feeds we check in nbFeed so we know
         * how many completionService takes we have to do.
         *
         * The loop blocks (in the background) until all results are in. So at the end of the loop,
         * we are certain that all tasks are completed.
         */
        int globalResult = 0;
        for (int i = 0; i < nbFeed; i++) {
            try {
                // Future and FutureTask are used to perform asynchronous, concurrent tasks.
                // Using Futures is an alternative method for using callbacks.
                Future<Integer> f = completionService.take();   // The refreshFeed die is afgerond wordt in de Future geplaatst
                globalResult += f.get();    // Geeft het aantal gevonden artikel dat met deze refreshFeed is gevonden.
            } catch (Exception e) {
                Log.e(TAG, "Error counting new articles " + e.getMessage());
            }
        }

        executor.shutdownNow(); // To purge all threads

        return globalResult;        // The number of new articles from a refresh of the feeds.
    }


    private int refreshFeed(String feedId, long keepDateBorderTime, boolean isFromAutoRefresh) {
        RssAtomParser handler = null;
        String feedTitle = "";
        boolean buildNotification = false;

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null);

        if ((cursor != null) && cursor.moveToFirst()) {
            int urlPosition = cursor.getColumnIndex(FeedColumns.URL);
            int idPosition = cursor.getColumnIndex(FeedColumns._ID);
            int titlePosition = cursor.getColumnIndex(FeedColumns.NAME);
            int fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
            int realLastUpdatePosition = cursor.getColumnIndex(FeedColumns.REAL_LAST_UPDATE);
            int iconPosition = cursor.getColumnIndex(FeedColumns.ICON); // Only used if favicons from the website are retrieved
            int retrieveFullscreenPosition = cursor.getColumnIndex(FeedColumns.RETRIEVE_FULLTEXT);
            int buildNotificationPosition = cursor.getColumnIndex(FeedColumns.NOTIFY);

            /* ModPrivacyVandaag: if Fetchmode = 99, do not refresh this feed. */
            int fetchMode = cursor.getInt(fetchModePosition);
            if (fetchMode == FETCHMODE_DO_NOT_FETCH) {
                cursor.close();
                return 0;
            }

            buildNotification = (cursor.getInt(buildNotificationPosition)>0);


            feedTitle = cursor.getString(titlePosition);    // used for notifications
            String id = cursor.getString(idPosition);
            HttpURLConnection connection = null;
            try {
                String feedUrl = cursor.getString(urlPosition);
                connection = NetworkUtils.setupConnection(feedUrl); // Setup the internet connection to the website
                String contentType = connection.getContentType();   // C
                // Check if it is the first time a feed is read. If that's the case, do not
                // get all articles from the past, but just the articles from the past period
                // according to the DEFAULT_FETCH_OLD_NEWS_TIME (in days) setting
                long lastRetrieveTime = cursor.getLong(realLastUpdatePosition);
              //  Log.e(TAG, "lastRetrieveTime = " + lastRetrieveTime);
                if (lastRetrieveTime == 0)
                    lastRetrieveTime = System.currentTimeMillis() - (Long.parseLong(DEFAULT_FETCH_OLD_NEWS_TIME) * Constants.DURATION_OF_ONE_DAY);

// >>>>>> Get the RSS feed for this URL and parse it
                handler = new RssAtomParser(lastRetrieveTime, keepDateBorderTime, id, cursor.getString(titlePosition), feedUrl,
                        cursor.getInt(retrieveFullscreenPosition) == 1);
                handler.setFetchImages(NetworkUtils.needDownloadPictures());

                if (fetchMode == 0) {
                    if (contentType != null && contentType.startsWith(CONTENT_TYPE_TEXT_HTML)) {    // Check if the retrieved file is "text/html"
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream())); // Read the file from the website.

                        String line;
                        int posStart = -1;

                        // Try to read the link to self in the RSS XML file and put it in the database, so we have the official RSS source.
                        while ((line = reader.readLine()) != null) {
                            if (line.contains(HTML_BODY)) {
                                break;
                            } else {
                                Matcher matcher = FEED_LINK_PATTERN.matcher(line);

                                if (matcher.find()) { // not "while" as only one link is needed
                                    line = matcher.group();
                                    posStart = line.indexOf(HREF);

                                    if (posStart > -1) {
                                        String url = line.substring(posStart + 6, line.indexOf('"', posStart + 10)).replace(Constants.AMP_SG,
                                                Constants.AMP);

                                        ContentValues values = new ContentValues();

                                        if (url.startsWith(Constants.SLASH)) {
                                            int index = feedUrl.indexOf('/', 8);

                                            if (index > -1) {
                                                url = feedUrl.substring(0, index) + url;
                                            } else {
                                                url = feedUrl + url;
                                            }
                                        } else if (!url.startsWith(Constants.HTTP_SCHEME) && !url.startsWith(Constants.HTTPS_SCHEME)) {
                                            url = feedUrl + '/' + url;
                                        }
                                        values.put(FeedColumns.URL, url);
                                        cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                                        connection.disconnect();
                                        connection = NetworkUtils.setupConnection(url);
                                        contentType = connection.getContentType();
                                        break;
                                    }
                                }
                            }
                        }
                        // this indicates a badly configured feed
                        if (posStart == -1) {
                            connection.disconnect();
                            connection = NetworkUtils.setupConnection(feedUrl);
                            contentType = connection.getContentType();
                        }
                    }

                    // Determine the character set of the RSS XML file and decide if re-encoding is necessary before parsing.
                    // The standard character set is UTF-8
                    if (contentType != null) {
                        int index = contentType.indexOf(CHARSET);
                        if (index > -1) {
                            int index2 = contentType.indexOf(';', index);

                            try {
                                Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8));
                                fetchMode = FETCHMODE_DIRECT;   // We can read this character-set
                            } catch (UnsupportedEncodingException ignored) {
                                fetchMode = FETCHMODE_REENCODE; // We cannot read this character set and need to re-encode the RSS file.
                            }
                        } else {
                            fetchMode = FETCHMODE_REENCODE;
                        }

                    } else {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                        char[] chars = new char[20];

                        int length = bufferedReader.read(chars);

                        String xmlDescription = new String(chars, 0, length);

                        connection.disconnect();
                        connection = NetworkUtils.setupConnection(connection.getURL());

                        int start = xmlDescription.indexOf(ENCODING);

                        if (start > -1) {
                            try {
                                Xml.findEncodingByName(xmlDescription.substring(start + 10, xmlDescription.indexOf('"', start + 11)));
                                fetchMode = FETCHMODE_DIRECT;
                            } catch (UnsupportedEncodingException ignored) {
                                fetchMode = FETCHMODE_REENCODE;
                            }
                        } else {
                            // absolutely no encoding information found
                            fetchMode = FETCHMODE_DIRECT;
                        }
                    }

                    ContentValues values = new ContentValues();
                    values.put(FeedColumns.FETCH_MODE, fetchMode);
                    cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                }

                // Parse the retrieved RSS file depending on the characterset.
                // If the character set is not supported, we try a different approach in reading the file and subsequentially parsing it.
                switch (fetchMode) {
                    default:
                    case FETCHMODE_DIRECT: {    // We can read the file, so get it and parse it!
                        if (contentType != null) {
                            int index = contentType.indexOf(CHARSET);
                            int index2 = contentType.indexOf(';', index);

                            InputStream inputStream = connection.getInputStream();
                            // Parse the RSS file
                            Xml.parse(inputStream,
                                    Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8)),
                                    handler);
                        } else {
                            InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                            Xml.parse(reader, handler);
                        }
                        break;
                    }
                    case FETCHMODE_REENCODE: { // We do not support the character set so we read it and try to convert it.
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        InputStream inputStream = connection.getInputStream();

                        byte[] byteBuffer = new byte[4096];

                        int n;
                        while ((n = inputStream.read(byteBuffer)) > 0) {
                            outputStream.write(byteBuffer, 0, n);
                        }

                        String xmlText = outputStream.toString();

                        int start = (xmlText != null) ? xmlText.indexOf(ENCODING) : -1;
                        if (start > -1) {
                            // Can we read the file now? Parse it!
                            Xml.parse(
                                    new StringReader(new String(outputStream.toByteArray(),
                                            xmlText.substring(start + 10, xmlText.indexOf('"', start + 11)))), handler
                            );
                        } else {
                            // use content type
                            if (contentType != null) {
                                int index = contentType.indexOf(CHARSET);
                                if (index > -1) {
                                    int index2 = contentType.indexOf(';', index);
                                    try {
                                        StringReader reader = new StringReader(new String(outputStream.toByteArray(), index2 > -1 ? contentType.substring(
                                                index + 8, index2) : contentType.substring(index + 8)));
                                        Xml.parse(reader, handler);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error reading string " + e.getMessage());
                                    }
                                } else {
                                    StringReader reader = new StringReader(xmlText);
                                    Xml.parse(reader, handler);
                                }
                            }
                        }
                        break;
                    }
                }
                connection.disconnect();


            } catch (FileNotFoundException e) {
                if (handler == null || (!handler.isDone() && !handler.isCancelled())) {
                    ContentValues values = new ContentValues();

                    // resets the fetch mode to determine it again later
                    values.put(FeedColumns.FETCH_MODE, 0);

                    values.put(FeedColumns.ERROR, getString(R.string.error_feed_error));
                    cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                }
            } catch (Throwable e) {
                if (handler == null || (!handler.isDone() && !handler.isCancelled())) {
                    ContentValues values = new ContentValues();

                    // resets the fetch mode to determine it again later
                    values.put(FeedColumns.FETCH_MODE, 0);

                    values.put(FeedColumns.ERROR, e.getMessage() != null ? e.getMessage() : getString(R.string.error_feed_process));
                    cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                    handler = null; // If an error has occurred, reset the new articles counter for this feed to avoid notifications.
                }
            } finally {
                            /* check and optionally find favicon */
                            /* No longer needed, because the icons of the feeds are included in the package */
                            /*
                                try {
                                    if (handler != null && cursor.getBlob(iconPosition) == null) {
                                        String feedLink = handler.getFeedLink();
                                        if (feedLink != null) {
                                            NetworkUtils.retrieveFavicon(this, new URL(feedLink), id);
                                        } else {
                                            NetworkUtils.retrieveFavicon(this, connection.getURL(), id);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            */
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        cursor.close();

        //  FeedData.addPredefinedMessages(); // ONLY FOR TESTING!!!!

        int newArticles = (handler != null) ? handler.getNewCount() : 0;

        // Log.e(TAG, "Test notification is verzonden"); // ONLY FOR TESTING !!!!
        // if (newArticles == 0 ) newArticles =1;      // ONLY FOR TESTING !!!!
        // buildNotification(feedId, feedTitle, 1);    // ONLY FOR TESTING !!!!

        // Notify only when app is not open. If refresh is called from AutoRefresh then the app
        // is not running and / or not all newschannels can be in the foreground at the same time.
        if (buildNotification && (newArticles > 0) && (isFromAutoRefresh)) {
            buildNotification(feedId, feedTitle, newArticles);
        }
        return newArticles;
    }

    /**
     * Method to build notifications for the user that new articles have arrived.
     *
     * @param feedId
     * @param feedTitle
     * @param newArticles
     */
    public void buildNotification(String feedId, String feedTitle, int newArticles) {
        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_ENABLED, true)) {
            // Bepaal de ID van de feed. Dat wordt ook de ID van de melding. Elke feed krijgt eigen melding.
            int mNotificationId = 1;
            try {

                mNotificationId = Integer.parseInt(feedId);
                // Log.e(TAG, "notification wordt gemaakt voor feed " + mNotificationId);
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "notification ID kon niet worden gemaakt.");
            }


            String text = getResources().getQuantityString(R.plurals.number_of_new_entries, newArticles, newArticles) + feedTitle;

            // Maak de Intent, zodat de smartphone weet welke app (HomeActivity.class)
            // en welke feed (in de extra's: feedId) geopend moet worden als op de melding wordt geklikt.
            // LET OP: FeedId is een string en mNotificationId is een int van dezelfde waarde!!

            Intent notificationIntent = new Intent(FetcherService.this, HomeActivity.class);
            notificationIntent.putExtra(NOTIFICATION_FEED_ID, feedId);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            /**
             * mNotificationId = ID of the pendingIntent. So when an update is done, it only affects the right notification.
             * FLAG_UPDATE_CURRENT is nodig omdat anders de oude waarden blijven staan.
             */
            PendingIntent contentIntent = PendingIntent.getActivity(FetcherService.this, mNotificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(MainApplication.getContext()) //
                    .setContentIntent(contentIntent) //
                    .setSmallIcon(R.drawable.ic_statusbar_pv) //
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) //
                    .setTicker(text) //
                    .setWhen(System.currentTimeMillis()) //
                    .setAutoCancel(true) //
                    .setContentTitle(getString(R.string.app_name)) //
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text)) // allows for multiline text-message
                    .setLights(0xffffffff, 0, 0);

            if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_VIBRATE, false)) {
                notifBuilder.setVibrate(new long[]{0, 1000});
            }

            String ringtone = PrefUtils.getString(PrefUtils.NOTIFICATIONS_RINGTONE, null);
            if (ringtone != null && ringtone.length() > 0) {
                notifBuilder.setSound(Uri.parse(ringtone));
            }

            if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_LIGHT, false)) {
                notifBuilder.setLights(0xffffffff, 300, 1000);
            }

            //  if (Constants.NOTIF_MGR != null) {
            //      Constants.NOTIF_MGR.notify(0, notifBuilder.getNotification());
            //  }

            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotifyMgr.notify(mNotificationId, notifBuilder.build());
        }

    }   // end buildNotification()
}