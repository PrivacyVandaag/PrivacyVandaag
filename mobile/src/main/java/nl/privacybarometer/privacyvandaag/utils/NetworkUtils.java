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

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class NetworkUtils {
    private static final String TAG = NetworkUtils.class.getSimpleName() + " ~> ";
    public static final File IMAGE_FOLDER_FILE = new File(MainApplication.getContext().getCacheDir(), "images/");
    public static final File PICASSO_FOLDER_FILE = new File(MainApplication.getContext().getCacheDir(), "picasso-cache/");
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

    /**
     * Determine the final filename (including path) in which the image is downloaded from website.
     */
    static String getDownloadedImagePath(long entryId, String imgUrl) {
        return IMAGE_FOLDER + entryId + ID_SEPARATOR + StringUtils.getMd5(imgUrl);
    }

    /**
     * Determine the temporary filename (including path) in which the image is downloaded from website.
     */
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
    private static final int TARGET_NEW_IMAGE_DIMENSION = 600;    // 600 px
    private static final int JPG_IMAGE_QUALITY = 80;    // 100 is best quality (lossless).

    public static void downloadImage(long entryId, String imgUrl) throws IOException {
        String tempImgPath = getTempDownloadedImagePath(entryId, imgUrl);
        String finalImgPath = getDownloadedImagePath(entryId, imgUrl);

        final File tempImgFile = new File(tempImgPath);
        final File finalImgFile = new File(finalImgPath);

        // Check if files not already exist
        if ( ! (tempImgFile.exists()) &&  ! (finalImgFile.exists())) {
            HttpURLConnection imgURLConnection = null;
            try {
                IMAGE_FOLDER_FILE.mkdir(); // create images dir if not exists

                // Compute the real URL (without "&eacute;", ...)
                // Deprecated fromHTML alternative in DeprecateUtils
                String realUrl = DeprecateUtils.fromHtml(imgUrl).toString();
                //Log.e(TAG, "Real URL = " + realUrl);
                imgURLConnection = setupConnection(realUrl);

                // Stream the image from website to temporary file.
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
                // Downloading is done. The image has been saved in app cache under temporary name.

                // Next, analyse the size of image and scale down if too large. If image is to large,
                // it reduces performance, mainly with smooth scrolling entrieslists. Also, it takes
                // up too much of the cache. So we scale down larger images.
                long length = tempImgFile.length(); // File size in bytes. if divided by 1024 then size in KB
                // Log.e(TAG,"file size = " + length );
                if (length > MAX_IMAGE_FILE_SIZE) {
                    // Log.e(TAG,"file size too large = " + length );
                    // If too large, resize the image to a new target size (width or height).
                    Bitmap resizedImage =  scaleDownBitmap(tempImgPath, TARGET_NEW_IMAGE_DIMENSION);
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
                        resizedImage.recycle(); // free up the memory the bitmap was using.
                    }
                    // Large file size, but still unable to scale down, because the image size in pixels
                    // is already within TARGET_NEW_IMAGE_DIMENSION. This happens most often with
                    // animated gifs, which have file sizes of MB's while the actual pixel width
                    // and height stay within the TARGET_NEW_IMAGE_DIMENSION.
                    // At the moment we think it is worth to keep the animated gifs at the cost of
                    // storage space. So we do not resize, just rename it under final image name.
                    else {
                        if ( ! (tempImgFile.renameTo(new File(finalImgPath)))) {
                            Log.e(TAG, "renaming image not succeeded.");
                        }
                    }
                    // image hase been resized (or just renamed) so we can try to delete the temporary
                    // file of the downloaded image.
                    tempImgFile.delete();
                }
                // The image is not too large, we didn't try to resize, but just rename it to final name.
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

    /**
     * Resize downloaded images if they are too large
     *
     * @param imagePath The file of the image
     * @param targetSize   The max width or height to scale down to.
     * @return
     */
    private static Bitmap scaleDownBitmap(String imagePath, int targetSize) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        // This option let us read the dimensions of the image without actually loading it.
        bmOptions.inJustDecodeBounds = true;
        // With the option above, decoding the file gives us the dimensions
        BitmapFactory.decodeFile(imagePath, bmOptions);
        int imageWidth = bmOptions.outWidth;
        int imageHeight = bmOptions.outHeight;
        // factor to scale down the image with. The decoder always uses a power of 2 value!
        int inSampleSize = 1;

        // Only resize if image is larger than target size
        // Check to be sure if targetSize > 0 to prevent divide by zero
        if ( ((imageWidth > targetSize) || (imageHeight > targetSize)) && (targetSize > 0))  {
            // get the largest dimension by comparing width and heigth, and work with that value.
            final int imageSize = Math.max(imageWidth, imageHeight);
            final int imageHalfSize = imageSize / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps the
            // imageSize larger than the target height or width.
            while ((imageHalfSize / inSampleSize) >= targetSize) {
                inSampleSize *= 2;
            }
            // Log.e (TAG, "The scale down factor = " + inSampleSize);
            // Set the scale down factor of the image
            bmOptions.inSampleSize = inSampleSize;
            // Now we have the value necessary to scale the image down, so let's load not only
            // the dimensions but also the actual image.
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inPurgeable = false; //As of LOLLIPOP (API 21), this is ignored.
            return BitmapFactory.decodeFile(imagePath, bmOptions);
        }
        return null;
    }




    // TODO: This is no longer necessary, since we got a new delete method beneath.
    // TODO: This method is probably never called anymore.
    // TODO: Delete also helper methods end objects, like PictureFilenameFilter.
    public static synchronized void deleteEntriesImagesCache(Uri entriesUri, String selection, String[] selectionArgs) {
        if (IMAGE_FOLDER_FILE.exists()) {
            PictureFilenameFilter filenameFilter = new PictureFilenameFilter();

            Cursor cursor = MainApplication.getContext().getContentResolver().query(
                    entriesUri, FeedData.EntryColumns.PROJECTION_ID, selection, selectionArgs, null);

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

    /**
     * Go through the directory with stored images and remove those that are created beyond the
     * keepDateBorderTime. These images can safely be deleted, because the articles will get
     * deleted next in the FetcherService.java where this method is being called.
     *
     * The favorited entries/ articles have to be excluded from this deleting process.
     *
     * This replaces the old deleteEntriesImagesCache() aboce. Is that still necessary?
     *
     * @param keepDateBorderTime
     */
    public static synchronized void deleteEntriesImagesCache(long keepDateBorderTime) {
        if (IMAGE_FOLDER_FILE.exists()) {
            // We need to exclude favorite entries images from this cleanup.
            // Get the Id's of the favorited items first and put them in an array.
            HashSet<Long> favIds = new HashSet<>();
            Cursor cursor = MainApplication.getContext().getContentResolver()
                    .query(FeedData.EntryColumns.FAVORITES_CONTENT_URI, FeedData.EntryColumns.PROJECTION_ID, null, null, null);
            if (cursor != null) {   // notice that an empty cursor != null
                while (cursor.moveToNext()) {
                    favIds.add(cursor.getLong(0));  // Put the favorited articles in an array.
                }
                cursor.close();
            }

            // Get the list of files (images) and loop through them.
            File[] files = IMAGE_FOLDER_FILE.listFiles();
            if (files != null) {
                for (File file : files) {
                    // If the file (image) is older than the keep datetime.
                    if (file.lastModified() < keepDateBorderTime) {
                        boolean isAFavoriteEntryImage = false;
                        // The images are named after the entry ID of the entry to which it belongs.
                        // So, check if the ID in the name of the image file is in the favorites array.
                        for (Long favId : favIds) {
                            if (file.getName().startsWith(favId + ID_SEPARATOR)) {
                                isAFavoriteEntryImage = true;
                                break;
                            }
                        }
                        // If it doesn't belong to a favorite entry, delete the image.
                        if (!isAFavoriteEntryImage) {
                            file.delete();
                        }
                    }
                    // Some temporary image files are not deleted properly.
                    // So, we delete TEMP files here to be sure
                    if (file.getName().startsWith(TEMP_PREFIX)) {
                        file.delete();
                    }
                }
            }
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
     * Before feeds are refreshed, check if SSL should be patched.
     * This check is done with Google patch service provided in the ProviderInstaller class
     *
     * We need this dependency: implementation 'com.google.android.gms:play-services-auth:16.0.1'
     *
     * @param context
     */
    public static void updateConnectionSecurity (Context context) {
        //Log.e(TAG,"try SSL security patches sync");
        try {
            ProviderInstaller.installIfNeeded(MainApplication.getContext());
        } catch (GooglePlayServicesRepairableException e) {
            Log.e(TAG,"error 1 syncing SSL security patches");
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e(TAG,"error 2 syncing SSL security patches");
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


        HttpsURLConnection connection = null;
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        int status = 0;
        boolean first = true;
        while(first || status == HttpsURLConnection.HTTP_MOVED_TEMP || status == HttpsURLConnection.HTTP_MOVED_PERM || status == HttpsURLConnection.HTTP_SEE_OTHER) {
            if(!first) {
                url = new URL(connection.getHeaderField("Location"));
            } else {
                first = false;
            }
            connection = proxy == null ? (HttpsURLConnection) url.openConnection() : (HttpsURLConnection) url.openConnection(proxy);
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
