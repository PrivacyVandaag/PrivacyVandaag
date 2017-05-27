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

package nl.privacybarometer.privacyvandaag.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.provider.FeedData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

public class NetworkUtils {
    private static final String TAG = NetworkUtils.class.getSimpleName() + " ~> ";
    public static final File IMAGE_FOLDER_FILE = new File(MainApplication.getContext().getCacheDir(), "images/");
    public static final String IMAGE_FOLDER = IMAGE_FOLDER_FILE.getAbsolutePath() + '/';
    public static final String TEMP_PREFIX = "TEMP__";
    public static final String ID_SEPARATOR = "__";

    private static final String FILE_FAVICON = "/favicon.ico";
    private static final String PROTOCOL_SEPARATOR = "://";

    private static final CookieManager COOKIE_MANAGER = new CookieManager() {{
        CookieHandler.setDefault(this);
    }};

    public static String getDownloadedOrDistantImageUrl(long entryId, String imgUrl) {
        File dlImgFile = new File(NetworkUtils.getDownloadedImagePath(entryId, imgUrl));
        if (dlImgFile.exists()) {
            return Uri.fromFile(dlImgFile).toString();
        } else {
            return imgUrl;
        }
    }

    public static String getDownloadedImagePath(long entryId, String imgUrl) {
        return IMAGE_FOLDER + entryId + ID_SEPARATOR + StringUtils.getMd5(imgUrl);
    }

    private static String getTempDownloadedImagePath(long entryId, String imgUrl) {
        return IMAGE_FOLDER + TEMP_PREFIX + entryId + ID_SEPARATOR + StringUtils.getMd5(imgUrl);
    }

    /**
     * Download images that are found in a full article (mobilized) in FetcherService.java
     * @param entryId
     * @param imgUrl
     * @throws IOException
     */

    private static final int MAX_IMAGE_FILE_SIZE = 200000; // 200 kB
    private static final int NEW_IMAGE_WIDTH = 800;    // 800 px
    private static final int JPG_IMAGE_QUALITY = 85;    // 100 is best quality (lossless).

    public static void downloadImage(long entryId, String imgUrl) throws IOException {
        String tempImgPath = getTempDownloadedImagePath(entryId, imgUrl);
        String finalImgPath = getDownloadedImagePath(entryId, imgUrl);

        final File tempImgFile = new File(tempImgPath);
        final File finalImgFile = new File(finalImgPath);

        // Check if files not already exist
        if ( ! (tempImgFile.exists()) &&  ! (finalImgFile.exists())) {
            HttpURLConnection imgURLConnection = null;
            try {
                IMAGE_FOLDER_FILE.mkdir(); // create images dir

                // Compute the real URL (without "&eacute;", ...)
                // Deprecated fromHTML alternative in DeprecateUtils
                String realUrl = DeprecateUtils.fromHtml(imgUrl).toString();
                //Log.e(TAG, "Real URL = " + realUrl);
                imgURLConnection = setupConnection(realUrl);

                FileOutputStream fileOutput = new FileOutputStream(tempImgPath);
                InputStream inputStream = imgURLConnection.getInputStream();

                byte[] buffer = new byte[2048];
                int bufferLength;
                while ((bufferLength = inputStream.read(buffer)) > 0) {
                    fileOutput.write(buffer, 0, bufferLength);
                }
                fileOutput.flush();
                fileOutput.close();
                inputStream.close();

                long length = tempImgFile.length(); //  in bytes. if divided by 1024 then size in KB
                // If image is to large, it reduces performance, mainly with smooth scrolling entrieslists
                // So we rescale larger images.
                if (length > MAX_IMAGE_FILE_SIZE) {
                    // Resize the image
                    Bitmap resizedImage =  UiUtils.scaleDownBitmap(tempImgPath,NEW_IMAGE_WIDTH);
                    // if resizing succesfull, save it as JPG as it is the most common format here.
                    if (resizedImage != null) {
                        try {
                            fileOutput = new FileOutputStream(finalImgPath);
                            resizedImage.compress(Bitmap.CompressFormat.JPEG, JPG_IMAGE_QUALITY , fileOutput);
                            fileOutput.flush();
                            fileOutput.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Saving resized image not succeeded. ");
                        }

                    }
                    // We wanted to scale down, but the image size in pixels is aalready within max boundaries
                    // so save anyway.
                    else {
                        if ( ! (tempImgFile.renameTo(new File(finalImgPath)))) {
                            Log.e(TAG, "renaming image not succeeded.");
                        }
                    }
                    // image hase been resized (or renamed) so we can try to delete the temporary
                    // file of the downloaded image.
                    tempImgFile.delete();
                }
                // The image is not too large, we didn't try to resize, but just save it.
                else {
                    if ( ! (tempImgFile.renameTo(new File(finalImgPath)))) {
                        Log.e(TAG, "renaming image not succeeded.");
                    }
                }

            } catch (IOException e) {
                tempImgFile.delete();
                throw e;
            } finally {
                if (imgURLConnection != null) {
                    imgURLConnection.disconnect();
                }
            }
        }
    }






    public static synchronized void deleteEntriesImagesCache(Uri entriesUri, String selection, String[] selectionArgs) {
        if (IMAGE_FOLDER_FILE.exists()) {
            PictureFilenameFilter filenameFilter = new PictureFilenameFilter();

            Cursor cursor = MainApplication.getContext().getContentResolver().query(entriesUri, FeedData.EntryColumns.PROJECTION_ID, selection, selectionArgs, null);

            while (cursor.moveToNext()) {
                filenameFilter.setEntryId(cursor.getString(0));

                File[] files = IMAGE_FOLDER_FILE.listFiles(filenameFilter);
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            cursor.close();
        }
    }

    public static boolean needDownloadPictures() {
        String fetchPictureMode = PrefUtils.getString(PrefUtils.PRELOAD_IMAGE_MODE, Constants.FETCH_PICTURE_MODE_WIFI_ONLY_PRELOAD);

        boolean downloadPictures = false;
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_IMAGES, true)) {
            if (Constants.FETCH_PICTURE_MODE_ALWAYS_PRELOAD.equals(fetchPictureMode)) {
                downloadPictures = true;
            } else if (Constants.FETCH_PICTURE_MODE_WIFI_ONLY_PRELOAD.equals(fetchPictureMode)) {
                ConnectivityManager cm = (ConnectivityManager) MainApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
                    downloadPictures = true;
                }
            }
        }
        return downloadPictures;
    }

    public static String getBaseUrl(String link) {
        String baseUrl = link;
        int index = link.indexOf('/', 8); // this also covers https://
        if (index > -1) {
            baseUrl = link.substring(0, index);
        }

        return baseUrl;
    }

    public static byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];

        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            output.write(buffer, 0, n);
        }

        byte[] result = output.toByteArray();

        output.close();
        inputStream.close();
        return result;
    }

    /**
     * Get the Favicon from the website where the feed originates.
     * @param context
     * @param url   url of the website where the feed originates
     * @param id    ID of the feed in the database
     *
     * This method is no longer needed, because the icons of the feeds are included in the package.
     */
    public static void retrieveFavicon(Context context, URL url, String id) {
        boolean success = false;
        HttpURLConnection iconURLConnection = null;
        try {
            iconURLConnection = setupConnection(new URL(url.getProtocol() + PROTOCOL_SEPARATOR + url.getHost() + FILE_FAVICON));

            byte[] iconBytes = getBytes(iconURLConnection.getInputStream());
            if (iconBytes != null && iconBytes.length > 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                if (bitmap != null) {
                    if (bitmap.getWidth() != 0 && bitmap.getHeight() != 0) {
                        ContentValues values = new ContentValues();
                        values.put(FeedData.FeedColumns.ICON, iconBytes);
                        context.getContentResolver().update(FeedData.FeedColumns.CONTENT_URI(id), values, null, null);
                        success = true;
                    }
                    bitmap.recycle();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (iconURLConnection != null) {
                iconURLConnection.disconnect();
            }
        }
         if (!success) {
            // no icon found or error
            ContentValues values = new ContentValues();
            values.putNull(FeedData.FeedColumns.ICON);
            context.getContentResolver().update(FeedData.FeedColumns.CONTENT_URI(id), values, null, null);
        }
    }

    public static HttpURLConnection setupConnection(String url, String cookieName, String cookieValue) throws IOException {
        String cookie = cookieName + "=" + cookieValue;
        return setupConnection(new URL(url), cookie);
    }

    public static HttpURLConnection setupConnection(String url) throws IOException {
        return setupConnection(new URL(url), "");
    }

    public static HttpURLConnection setupConnection(URL url) throws IOException {
        return setupConnection(url, "");
    }

    public static HttpURLConnection setupConnection(URL url, String cookie) throws IOException {
        Proxy proxy = null;
        ConnectivityManager connectivityManager = (ConnectivityManager) MainApplication.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (PrefUtils.getBoolean(PrefUtils.PROXY_ENABLED, false)
                && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI || !PrefUtils.getBoolean(PrefUtils.PROXY_WIFI_ONLY, false))) {
            try {
                proxy = new Proxy("0".equals(PrefUtils.getString(PrefUtils.PROXY_TYPE, "0")) ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                        new InetSocketAddress(PrefUtils.getString(PrefUtils.PROXY_HOST, ""), Integer.parseInt(PrefUtils.getString(
                                PrefUtils.PROXY_PORT, "8080")))
                );
            } catch (Exception e) {
                proxy = null;
            }
        }

        if (proxy == null) {
            // Try to get the system proxy
            try {
                ProxySelector defaultProxySelector = ProxySelector.getDefault();
                List<Proxy> proxyList = defaultProxySelector.select(url.toURI());
                if (!proxyList.isEmpty()) {
                    proxy = proxyList.get(0);
                }
            } catch (Throwable ignored) {}
        }
        HttpURLConnection connection = null;
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        int status = 0;
        boolean first = true;
        while(first || status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            if(!first) {
                url = new URL(connection.getHeaderField("Location"));
            } else {
                first = false;
            }
            connection = proxy == null ? (HttpURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection(proxy);
            connection.setRequestProperty("Cookie", cookie);
            connection.setDoInput(true);
            connection.setDoOutput(false);
           // connection.setRequestProperty("User-agent", "Mozilla/5.0 (compatible) AppleWebKit Chrome Safari"); // some feeds need this to work properly
            connection.setRequestProperty("User-agent", "Mozilla/5.0 (Windows NT 6.1; rv:38.0) Gecko/20100101 Firefox/38.0 Light/38.0"); // some feeds need this to work properly
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("accept", "*/*");
            COOKIE_MANAGER.getCookieStore().removeAll(); // Cookie is important for some sites, but we clean them each times
          //  Log.i("Privacy Vandaag: ", url.toString());
            try {
                connection.connect();
            }  catch (IOException e) {
                Log.e("NetworkUtils error: ", e.toString() );
                Log.e("NetworkUtils error: ", "URL = " + url );
            }
            status = connection.getResponseCode();
          //  Log.e ("NetworkUtils >>", " connection status: " + status);
        }
        return connection;
    }

    private static class PictureFilenameFilter implements FilenameFilter {
        private static final String REGEX = "__[^\\.]*\\.[A-Za-z]*";

        private Pattern mPattern;

        public PictureFilenameFilter(String entryId) {
            setEntryId(entryId);
        }

        public PictureFilenameFilter() {
        }

        public void setEntryId(String entryId) {
            mPattern = Pattern.compile(entryId + REGEX);
        }

        @Override
        public boolean accept(File dir, String filename) {
            return mPattern.matcher(filename).find();
        }
    }
}
