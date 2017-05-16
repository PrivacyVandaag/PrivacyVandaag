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

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import nl.privacybarometer.privacyvandaag.BuildConfig;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import static android.text.util.Linkify.EMAIL_ADDRESSES;
import static nl.privacybarometer.privacyvandaag.utils.NetworkUtils.setupConnection;

/**
 * Shows background information about the app.
 *
 * An AsyncTask is included to check for update of the app and to download and install it.
 * This is only included with ditribution outside google play store!
 *
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String title = getString(R.string.app_name_no_break);
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            title += " " + info.versionName + " (" + PrefUtils.getInt(PrefUtils.APP_VERSION_CODE, 0) + ")";
        } catch (NameNotFoundException e) {
            Log.e("AboutAcitivty >> ","Error while fetching app version: " + e);
        }
        TextView titleView = (TextView) findViewById(R.id.about_title);
        titleView.setText(title);
        TextView aboutContentView = (TextView) findViewById(R.id.about_content);

        /*
            In Android, complex operations are simple and simple things are difficult to achieve.
            This is one of those difficult cases.

            In order to make active links of webpages AND emailaddress, the following is needed.
            First the html - formatted string is retrieved from the resources.
            To linkify the included webpages / emailaddress, linkify.ALL is used.
            However, linkify removes all the previous HTML styles. So, we have to retrieve
            these styles from the original string from the resources and copy them one by one
            to the linkified SpannableString
            From: http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml

         */
        Spanned htmlStyledText = DeprecateUtils.fromHtml(getString(R.string.about_us_content));
        URLSpan[] currentSpans = htmlStyledText.getSpans(0, htmlStyledText.length(), URLSpan.class);

        SpannableString linkifiedHtmlStyledText = new SpannableString(htmlStyledText);
        Linkify.addLinks(linkifiedHtmlStyledText, Linkify.ALL);

        for (URLSpan span : currentSpans) {
            int end = htmlStyledText.getSpanEnd(span);
            int start = htmlStyledText.getSpanStart(span);
            linkifiedHtmlStyledText.setSpan(span, start, end, 0);
        }

        // By now we have linkified the html styled string resource. We can set it to the TextView
        aboutContentView.setText(linkifiedHtmlStyledText);
        // activate the links in the TextView
        aboutContentView.setMovementMethod(LinkMovementMethod.getInstance());

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    /**
     * Update procedure for distribution of app outside google play store
     *
     */

    // Called when the user touches the check_for_update button
    public void checkForUpdate(View view) {
        TextView contentView = (TextView) findViewById(R.id.update_feedback);
        contentView.setVisibility(View.VISIBLE);
        contentView.setText(getString(R.string.checking_for_update));
        int currentVersionCode = BuildConfig.VERSION_CODE;
        new CheckLatestVersion().execute(currentVersionCode);   // AsyncTask to check if a newer release of app is available.
    }

    // new AsyncTask to check if a newer release of the app is available
    private class CheckLatestVersion extends AsyncTask<Integer, Void, String> {

        // Contact website and read response
        @Override
        protected String doInBackground(Integer... currentVersion) {
            String response = "";
            try {
                URL url = new URL("https://www.privacybarometer.nl/app/checkmeestrecenteversie.php?c="+currentVersion[0]);
                HttpURLConnection connection = setupConnection(url);
                InputStream input = new BufferedInputStream(connection.getInputStream());
                BufferedReader r = new BufferedReader(new InputStreamReader(input));
                String line;
                if ((line = r.readLine()) != null) {
                    response = line;
                }
                input.close();
            } catch (Exception e) {
                Log.e("Privacy Vandaag","Error while checking for update: " + e.getMessage());
            }
            return response;
        }

        // Handle response from website. If update is available, make button to update visible
        @Override
        protected void onPostExecute(String result) {
            TextView contentView = (TextView) findViewById(R.id.update_feedback);
            Button buttonCheck = (Button) findViewById(R.id.check_for_update);
            buttonCheck.setVisibility(View.GONE);
            int strLength  = result.length();

            if(strLength > 3 && strLength < 12) {  // Simple check if the return string is likely to be a newer version code.
                contentView.setText("Versie " + result + " is nu beschikbaar!");
                Button buttonUpdate = (Button) findViewById(R.id.do_update);
                buttonUpdate.setText("Updaten naar versie " + result + ".");
                buttonUpdate.setVisibility(View.VISIBLE);
            }
            else {
                contentView.setText(getString(R.string.already_latest_version));
            }
        }
    }

    // If update is available, update button turns visible. When clicked, updateApp() starts */
    public void updateApp(View view) {  // Do something in response to button click
        TextView contentView = (TextView) findViewById(R.id.update_feedback);
        contentView.setText("Downloading meest recente versie...");
        new DownloadFileAndInstall().execute("");   // Call new AsyncTask to download and install new releas
    }

    // new AsyncTask to download and install new release
    private class DownloadFileAndInstall extends AsyncTask<String, Void, String> {

        // Download update package and store in app's storage space.
        @Override
        protected String doInBackground(String... sUrl) {
            String fileName = "privacyvandaag.apk";
            String externalPath = "https://www.privacybarometer.nl/app/" + fileName;

            // This is the directory connected to the app. As of api 19 no write permission for this directory is needed.
            String localPath = getExternalFilesDir(null) + "/" + fileName;
            try {    // This is the way to setup connection like the way the feeds are read. Check NetworkUtils for details.
                URL url = new URL(externalPath);
                HttpURLConnection connection = setupConnection(url);
                InputStream input = new BufferedInputStream(connection.getInputStream());
                OutputStream output = new FileOutputStream(localPath);
                byte data[] = new byte[1024];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();
            } catch (Exception e) {
                Log.e("PrivacyVandaag", "Error trying to download update package");
                Log.e("PrivacyVandaag", e.getMessage());
                localPath="";
            }
            return localPath;
        }

        // After download, begin installation by opening the resulting file
        @Override
        protected void onPostExecute(String path) {
            TextView contentView = (TextView) findViewById(R.id.update_feedback);
            if (path.equals("")) {
                contentView.setText(getString(R.string.download_failed));
            } else{
                contentView.setText(getString(R.string.installing_update) + " " + path);
                File updatePackage = new File(path);
                try {
                    Intent i = new Intent();
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.setAction(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.fromFile(updatePackage), "application/vnd.android.package-archive");
                    Log.i("PrivacyVandaag", "About to install new .apk");
                    getApplicationContext().startActivity(i);
                } catch (Exception e) {
                    Log.e("PrivacyVandaag", "Error trying to install the update package");
                    Log.e("PrivacyVandaag", e.getMessage());
                    contentView.setText(getString(R.string.installation_failed));
                }
            }
        }
    }


}

