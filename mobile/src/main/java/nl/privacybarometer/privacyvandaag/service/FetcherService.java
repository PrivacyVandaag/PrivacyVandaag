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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
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
import nl.privacybarometer.privacyvandaag.utils.HtmlUtils;
import nl.privacybarometer.privacyvandaag.utils.NetworkUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

public class FetcherService extends IntentService {
    private static final String TAG = FetcherService.class.getSimpleName() + " ~> ";

    public static final String ACTION_REFRESH_FEEDS = "nl.privacybarometer.privacyvandaag.REFRESH";
    public static final String ACTION_MOBILIZE_FEEDS = "nl.privacybarometer.privacyvandaag.MOBILIZE_FEEDS";
    public static final String ACTION_DOWNLOAD_IMAGES = "nl.privacybarometer.privacyvandaag.DOWNLOAD_IMAGES";

    private static final int THREAD_NUMBER = 3;
    private static final int MAX_TASK_ATTEMPT = 3;

    private static final int FETCHMODE_DIRECT = 1;
    private static final int FETCHMODE_REENCODE = 2;
    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // ModPrivacyVandaag: 'Do not refresh feed' mode introduced.

    private static final String CHARSET = "charset=";
    private static final String CONTENT_TYPE_TEXT_HTML = "text/html";
    private static final String HREF = "href=\"";

    private static final String HTML_BODY = "<body";
    private static final String ENCODING = "encoding=\"";

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

    public static boolean hasMobilizationTask(long entryId) {
        Cursor cursor = MainApplication.getContext().getContentResolver().query(TaskColumns.CONTENT_URI, TaskColumns.PROJECTION_ID,
                TaskColumns.ENTRY_ID + '=' + entryId + Constants.DB_AND + TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null);

        boolean result = cursor != null && cursor.getCount() > 0 ;
        if (cursor != null) cursor.close();

        return result;
    }

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
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, true);

            if (isFromAutoRefresh) {
                PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, SystemClock.elapsedRealtime());
            }

            /* ModPrivacyVandaag: De defaultwaarde voor het bewaren van artikelen is 30.
                Dus moet de defaultwaarde bij het ophalen ook die waarde hebben, om te voorkomen dat de
                voorkeuren (nog) niet goed ingesteld of gelezen worden.
                In onderstaande dus bij de getString "30" geplaatst. Origineel was "4"
             */
            long keepTime = Long.parseLong(PrefUtils.getString(PrefUtils.KEEP_TIME, "30")) * 86400000l;
            long keepDateBorderTime = keepTime > 0 ? System.currentTimeMillis() - keepTime : 0;

            deleteOldEntries(keepDateBorderTime);

            String feedId = intent.getStringExtra(Constants.FEED_ID);
            int newCount = (feedId == null ? refreshFeeds(keepDateBorderTime) : refreshFeed(feedId, keepDateBorderTime));  // number of new articles found after refresh all the feeds

            // notification for new articles.
            if (newCount > 0 && isFromAutoRefresh && PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_ENABLED, true)) {
                int mNotificationId = 2;    // een willekeurige ID, want we doen er nu niks mee
                Intent notificationIntent = new Intent(FetcherService.this, HomeActivity.class);
                // Voorlopig doen we niets met aantallen, want wekt verwarring omdat aantal in de melding kan afwijken van totaal nieuw.
                newCount += PrefUtils.getInt(PrefUtils.NOTIFICATIONS_PREVIOUS_COUNT, 0); // Tel aantal van bestaande melding erbij op
                String text = getResources().getQuantityString(R.plurals.number_of_new_entries, newCount, newCount);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                PendingIntent contentIntent = PendingIntent.getActivity(FetcherService.this, mNotificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(MainApplication.getContext()) //
                        .setContentIntent(contentIntent) // Wat moet er gebeuren als er op de melding geklikt wordt.
                        .setSmallIcon(R.drawable.ic_statusbar_pv) //
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) //
                        .setTicker(text) //
                        .setWhen(System.currentTimeMillis()) // Set het tijdstip van de melding
                        .setAutoCancel(true) // Melding verwijderen als erop geklikt wordt.
                        .setContentTitle(getString(R.string.app_name))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))  // Style: Tekst over meerdere regels
                        .setContentText(text) // Tekst van de melding
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

                NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotifyMgr.notify(mNotificationId, notifBuilder.build());

            }

            PrefUtils.putInt(PrefUtils.NOTIFICATIONS_PREVIOUS_COUNT, newCount);
            mobilizeAllEntries();
            downloadAllImages();

            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);
        }
    }

    private void mobilizeAllEntries() {
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.NUMBER_ATTEMPT},
                TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null);

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        while (cursor != null && cursor.moveToNext()) {
            long taskId = cursor.getLong(0);
            long entryId = cursor.getLong(1);
            int nbAttempt = 0;
            if (!cursor.isNull(2)) {
                nbAttempt = cursor.getInt(2);
            }

            boolean success = false;

            Uri entryUri = EntryColumns.CONTENT_URI(entryId);
            Cursor entryCursor = cr.query(entryUri, null, null, null, null);

            if (entryCursor != null && entryCursor.moveToFirst()) {
                if (entryCursor.isNull(entryCursor.getColumnIndex(EntryColumns.MOBILIZED_HTML))) { // If we didn't already mobilized it
                    int linkPos = entryCursor.getColumnIndex(EntryColumns.LINK);
                    int abstractHtmlPos = entryCursor.getColumnIndex(EntryColumns.ABSTRACT);
                    int feedIdPos = entryCursor.getColumnIndex(EntryColumns.FEED_ID);
                    HttpURLConnection connection = null;



                    try {
                        String link = entryCursor.getString(linkPos);
                        String feedId = entryCursor.getString(feedIdPos);
                        Cursor cursorFeed = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null);
                        cursorFeed.moveToNext();
                        int cookieNamePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_NAME);
                        int cookieValuePosition = cursorFeed.getColumnIndex(FeedColumns.COOKIE_VALUE);
                        String cookieName = cursorFeed.getString(cookieNamePosition);
                        String cookieValue = cursorFeed.getString(cookieValuePosition);
                        cursorFeed.close();

                        // Take substring from RSS content and use it
                        // to try to find a text indicator for better content extraction
                        String contentIndicator = null;
                        String text = entryCursor.getString(abstractHtmlPos);
                        if (!TextUtils.isEmpty(text)) {
                            text = Html.fromHtml(text).toString();
                            if (text.length() > 60) {
                                contentIndicator = text.substring(20, 40);
                            }
                        }
                        connection = NetworkUtils.setupConnection(link,cookieName, cookieValue);
                        String mobilizedHtml = ArticleTextExtractor.extractContent(connection.getInputStream(), contentIndicator);
                        if (mobilizedHtml != null) {
                            mobilizedHtml = HtmlUtils.improveHtmlContent(mobilizedHtml, NetworkUtils.getBaseUrl(link));
                            ContentValues values = new ContentValues();
                            values.put(EntryColumns.MOBILIZED_HTML, mobilizedHtml);

                            ArrayList<String> imgUrlsToDownload = null;
                            if (NetworkUtils.needDownloadPictures()) {
                                imgUrlsToDownload = HtmlUtils.getImageURLs(mobilizedHtml);
                            }

                            String mainImgUrl;
                            if (imgUrlsToDownload != null) {
                                mainImgUrl = HtmlUtils.getMainImageURL(imgUrlsToDownload);
                            } else {
                                mainImgUrl = HtmlUtils.getMainImageURL(mobilizedHtml);
                            }

                            if (mainImgUrl != null) {
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
            if (entryCursor != null) entryCursor.close();

            if (!success) {
                if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                } else {
                    ContentValues values = new ContentValues();
                    values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1);
                    operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build());
                }
            }
        }

        if (cursor != null) cursor.close();

        if (!operations.isEmpty()) {
            try {
                cr.applyBatch(FeedData.AUTHORITY, operations);
            } catch (Throwable ignored) {
            }
        }
    }

    private void downloadAllImages() {
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{TaskColumns._ID, TaskColumns.ENTRY_ID, TaskColumns.IMG_URL_TO_DL,
                TaskColumns.NUMBER_ATTEMPT}, TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NOT_NULL, null, null);

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        while (cursor != null && cursor.moveToNext()) {
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
                if (nbAttempt + 1 > MAX_TASK_ATTEMPT) {
                    operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                } else {
                    ContentValues values = new ContentValues();
                    values.put(TaskColumns.NUMBER_ATTEMPT, nbAttempt + 1);
                    operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(taskId)).withValues(values).build());
                }
            }
        }

        if (cursor != null) cursor.close();

        if (!operations.isEmpty()) {
            try {
                cr.applyBatch(FeedData.AUTHORITY, operations);
            } catch (Throwable ignored) {
            }
        }
    }

    private void deleteOldEntries(long keepDateBorderTime) {
        if (keepDateBorderTime > 0) {
            String where = EntryColumns.DATE + '<' + keepDateBorderTime + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE;
            // Delete the entries, the cache files will be deleted by the content provider
            MainApplication.getContext().getContentResolver().delete(EntryColumns.CONTENT_URI, where, null);
        }
        Cursor cursor = MainApplication.getContext().getContentResolver().query(FeedColumns.CONTENT_URI, new String[]{FeedColumns._ID, FeedColumns.KEEP_TIME},null, null, null);
        while (cursor != null && cursor.moveToNext()) {
            long feedid = cursor.getLong(0);
            long keepTimeLocal = cursor.getLong(1) * 86400000l;
            long keepDateBorderTimeLocal = keepTimeLocal > 0 ? System.currentTimeMillis() - keepTimeLocal : 0;
            if(keepDateBorderTimeLocal > 0) {
                String where = EntryColumns.DATE + '<' + keepDateBorderTimeLocal + Constants.DB_AND + EntryColumns.WHERE_NOT_FAVORITE + Constants.DB_AND + EntryColumns.FEED_ID + "=" + String.valueOf(feedid);
                MainApplication.getContext().getContentResolver().delete(EntryColumns.CONTENT_URI, where, null);
            }
        }
        if (cursor != null) cursor.close();

    }

    private int refreshFeeds(final long keepDateBorderTime) {
        ContentResolver cr = getContentResolver();
        final Cursor cursor = cr.query(FeedColumns.CONTENT_URI, FeedColumns.PROJECTION_ID, null, null, null);
        int nbFeed = (cursor != null) ? cursor.getCount() : 0;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });

        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor);
        while (cursor != null && cursor.moveToNext()) {
            final String feedId = cursor.getString(0);
            completionService.submit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    int result = 0;
                    try {
                        result = refreshFeed(feedId, keepDateBorderTime);
                    } catch (Exception e) {
                        Log.e (TAG, "Error refreshing feed " + e.getMessage());
                    }
                    return result;
                }
            });
        }
        if (cursor != null) cursor.close();

        int globalResult = 0;
        for (int i = 0; i < nbFeed; i++) {
            try {
                Future<Integer> f = completionService.take();       // ModPrivacyVandaag: the count of new articles after a feed is refreshed
                globalResult += f.get();
            } catch (Exception e) {
                Log.e (TAG, "Error counting new articles " + e.getMessage());
            }
        }

        executor.shutdownNow(); // To purge all threads

        return globalResult;        // ModPrivacyVandaag: As far as I can see: this contains the number of new articles from a refresh of the feeds.
    }

    private int refreshFeed(String feedId, long keepDateBorderTime) {
        RssAtomParser handler = null;

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null);

        if (cursor.moveToFirst()) {
            int urlPosition = cursor.getColumnIndex(FeedColumns.URL);
            int idPosition = cursor.getColumnIndex(FeedColumns._ID);
            int titlePosition = cursor.getColumnIndex(FeedColumns.NAME);
            int fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
            int realLastUpdatePosition = cursor.getColumnIndex(FeedColumns.REAL_LAST_UPDATE);
            int iconPosition = cursor.getColumnIndex(FeedColumns.ICON);
            int retrieveFullscreenPosition = cursor.getColumnIndex(FeedColumns.RETRIEVE_FULLTEXT);

            /* ModPrivacyVandaag: if Fetchmode = 99, do not refresh this feed. */
            int fetchMode = cursor.getInt(fetchModePosition);
            if (fetchMode==FETCHMODE_DO_NOT_FETCH) {
                cursor.close();
                return 0;
            }
            // end of this added block of code; commented out initialize of fetchmode on line 520
            String id = cursor.getString(idPosition);
            HttpURLConnection connection = null;
            try {
                String feedUrl = cursor.getString(urlPosition);
                connection = NetworkUtils.setupConnection(feedUrl);
                String contentType = connection.getContentType();
                handler = new RssAtomParser(new Date(cursor.getLong(realLastUpdatePosition)), keepDateBorderTime, id, cursor.getString(titlePosition), feedUrl,
                        cursor.getInt(retrieveFullscreenPosition) == 1);
                handler.setFetchImages(NetworkUtils.needDownloadPictures());
                // Log.e (TAG,"feedUrl = "+feedUrl);

                if (fetchMode == 0) {
                    if (contentType != null && contentType.startsWith(CONTENT_TYPE_TEXT_HTML)) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                        String line;
                        int posStart = -1;

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

                    if (contentType != null) {
                        int index = contentType.indexOf(CHARSET);
                        if (index > -1) {
                            int index2 = contentType.indexOf(';', index);

                            try {
                                Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8));
                                fetchMode = FETCHMODE_DIRECT;
                            } catch (UnsupportedEncodingException ignored) {
                                fetchMode = FETCHMODE_REENCODE;
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

                switch (fetchMode) {
                    default:
                    case FETCHMODE_DIRECT: {
                        if (contentType != null) {
                            int index = contentType.indexOf(CHARSET);
                            int index2 = contentType.indexOf(';', index);

                            InputStream inputStream = connection.getInputStream();
                            Xml.parse(inputStream,
                                    Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8)),
                                    handler);
                        } else {
                            InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                            Xml.parse(reader, handler);
                        }
                        break;
                    }
                    case FETCHMODE_REENCODE: {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        InputStream inputStream = connection.getInputStream();

                        byte[] byteBuffer = new byte[4096];

                        int n;
                        while ((n = inputStream.read(byteBuffer)) > 0) {
                            outputStream.write(byteBuffer, 0, n);
                        }

                        String xmlText = outputStream.toString();

                        int start = xmlText != null ? xmlText.indexOf(ENCODING) : -1;

                        if (start > -1) {
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
                                        Log.e ("Privacy Vandaag: ", "Error reading string " + e.getMessage());
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

        int newArticles = (handler != null) ? handler.getNewCount() : 0;
        //Log.e(TAG, "Test notification is gegeven voor feedID " + feedId);
        //if (newArticles == 0 ) newArticles =2;      // ONLY FOR TESTING !!!!

        // Check of meldingen voor deze feed aanstaat, anders newArticles op 0 zetten
        if (newArticles  > 0 ) {
            boolean notifyFeed = true;
            switch (Integer.parseInt(feedId)) {
                case 1: // feedID Privacy Barometer
                    notifyFeed = PrefUtils.getBoolean(PrefUtils.NOTIFY_PRIVACYBAROMETER, true);
                    break;
                case 2: // feedID Bits of Freedom
                    notifyFeed = PrefUtils.getBoolean(PrefUtils.NOTIFY_BITSOFFREEDOM, true);
                    break;
                case 3: // feedID Privacy First
                    notifyFeed = PrefUtils.getBoolean(PrefUtils.NOTIFY_PRIVACYFIRST, true);
                    break;
                case 4: // feedID Autoriteit Persoonsgegevens
                    notifyFeed = PrefUtils.getBoolean(PrefUtils.NOTIFY_AUTORITEITPERSOONSGEGEVENS, true);
            }
            if (!notifyFeed) newArticles = 0;   // geen melding als de meldingen voor deze feed uitstaan.
        }
       //Log.e(TAG, "Nieuwe artikelen is " + newArticles);

        return newArticles;
    }

}
