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

package nl.privacybarometer.privacyvandaag.activity;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.utils.DeprecateUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;


/**
 * Shows background information about the app.
 *
 * An AsyncTask is included to check for update of the app and to download and install it.
 * This is only included with distribution outside google play store!
 *
 */
public class AboutActivity extends AppCompatActivity {
    private static final String TAG = AboutActivity.class.getSimpleName() + " ~> ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String title = getString(R.string.app_name_no_break);
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            title += " " + info.versionName + " (" + PrefUtils.getInt(PrefUtils.APP_VERSION_CODE, 0) + ")";
        } catch (NameNotFoundException e) {
            Log.e(TAG,"Error while fetching app version: " + e);
        }
        TextView titleView = (TextView) findViewById(R.id.about_title);
        titleView.setText(title);
        TextView aboutContentView = (TextView) findViewById(R.id.about_content);

        /*
            In Android, complex operations are simple and simple things are difficult to achieve.
            This is one of those difficult cases.

            In order to turn url's and emailaddress to active links, the following is needed.
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

}

