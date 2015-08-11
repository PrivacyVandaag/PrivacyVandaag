/**
 * spaRSS
 * <p/>
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


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.widget.Toolbar;
import android.text.Html;
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
import java.net.URLConnection;


import nl.privacybarometer.privacyvandaag.BuildConfig;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import static nl.privacybarometer.privacyvandaag.utils.NetworkUtils.setupConnection;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String title;
        PackageManager manager = this.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            title = "Privacy Vandaag " + info.versionName;
        } catch (NameNotFoundException unused) {
            title = "Privacy Vandaag";
        }
        TextView titleView = (TextView) findViewById(R.id.about_title);
        titleView.setText(title);

        TextView content1View = (TextView) findViewById(R.id.about_copyright);
        content1View.setText(Html.fromHtml(getString(R.string.about_us_copyright)));

        TextView contentView = (TextView) findViewById(R.id.about_content);
        contentView.setText(Html.fromHtml(getString(R.string.about_us_content)));
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
 * ************************* update procedure outside google play store ****************************
 */

    /* ModPrivacyVandaag: Added to check for updates of the app,
    because this app will probably never see Google's Play Store
     */

    // Called when the user touches the check_for_update button
    public void checkForUpdate(View view) {  // Do something in response to button click
        TextView contentView = (TextView) findViewById(R.id.update_feedback);
        contentView.setVisibility(View.VISIBLE);
        contentView.setText("Controleren ...");
        int currentVersionCode = BuildConfig.VERSION_CODE;
        new CheckLatestVersion().execute(currentVersionCode);   // Check if a newer release of the app is available
    }

    // new AsyncTask to check if a newer release of the app is available
    private class CheckLatestVersion extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... currentVersion) {
            String response = "";
            try {
 /* oud
                URL url = new URL("https://www.privacybarometer.nl/app/checkmeestrecenteversie.php?c="+currentVersion[0]);
                URLConnection connection = url.openConnection();
                connection.connect();
                InputStream input = new BufferedInputStream(url.openStream());
 */
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
                Log.e("YourApp", "Well that didn't work out so well...");
                Log.e("YourApp", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {   // If new release available, make button to update visible
            TextView contentView = (TextView) findViewById(R.id.update_feedback);
            Button buttonCheck = (Button) findViewById(R.id.check_for_update);
            buttonCheck.setVisibility(View.GONE);
            int strLength  = result.length();

            if(strLength > 3 && strLength < 12) {  // Simple check if the return string is likely to be a newer version code.
                contentView.setText("Versie " + result + " is nu beschikbaar!");
                // Set and show update button
                Button buttonUpdate = (Button) findViewById(R.id.do_update);
                buttonUpdate.setText("Updaten naar versie " + result + ".");
                buttonUpdate.setVisibility(View.VISIBLE);
            }
            else {
                contentView.setText("U heeft de meest recente versie.");
            }
        }
    }

    /** Called when the user touches the do_update button */
    public void updateApp(View view) {  // Do something in response to button click
        TextView contentView = (TextView) findViewById(R.id.update_feedback);
        contentView.setText("Downloading meest recente versie...");
        new DownloadFileAndInstall().execute("");   // Call new AsyncTask to download and install new releas
    }

    // new AsyncTask to download and install new release
    private class DownloadFileAndInstall extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... sUrl) {
            String fileName = "privacyvandaag.apk";
            String externalPath = "https://www.privacybarometer.nl/app/" + fileName;
          //  String localPath = Environment.getExternalStorageDirectory().getPath() + "/" + fileName;
            // String localPath = Context.getExternalFilesDir() + "/" + fileName;
           // String state = Environment.getExternalStorageState();
           // File extFiles = getExternalFilesDir(null);
           // File locFiles = getFilesDir();

            // This is the directory connected to the app. As of api 19 no write permission for this directory is needed.
            String localPath = getExternalFilesDir(null) + "/" + fileName;
             try {
                // setup connection     //    URL url = new URL("https://www.privacybarometer.nl/app/privacyvandaag.apk");
                URL url = new URL(externalPath);
/* oud
                URLConnection connection = url.openConnection();
                connection.connect();
                InputStream input = new BufferedInputStream(url.openStream());
*/
                // This is the way to setup connection like the way the feeds are read. Check NetworkUtils for details.
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

        // begin the installation by opening the resulting file
        @Override
        protected void onPostExecute(String path) {
            TextView contentView = (TextView) findViewById(R.id.update_feedback);
            if (path.equals("")) {
                contentView.setText("Download is niet gelukt. Controleer de verbinding en probeer opnieuw.");
            } else{
                contentView.setText("Download is gereed. Installeren vanaf " + path);
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
                    contentView.setText("Installeren is helaas niet gelukt. Probeer opnieuw of installeer de update handmatig. Kijk op https://www.privacybarometer.nl/app voor meer informatie.");
                }
            }
        }
    }
}

