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

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

import android.view.MenuItem;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.fragment.EntryFragment;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

/**
 * This activity shows an article to read and allows the reader to swype to the next article.
 * It uses the > fragment > EntryFragment.java class to get the article and display it.
 * The options in the toolbar are defined in res > menu > entry.xml
 * and handled in fragment > EntryFragment.java
 */

public class EntryActivity extends BaseActivity {

    private EntryFragment mEntryFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_entry);

        mEntryFragment = (EntryFragment) getFragmentManager().findFragmentById(R.id.entry_fragment);
        if (savedInstanceState == null) { // Put the data only the first time (the fragment will save its state)
            mEntryFragment.setData(getIntent().getData());
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_ENTRIES_FULLSCREEN, false)) {
            setImmersiveFullScreen(true);
        }
    }

    /**
     * When in toolbar / actionbar 'home' (back arrow) is pressed, destroy this activity
     * and start HomeActivity again.
     * @param item = the pressed item ('home')
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Bundle b = getIntent().getExtras();
            //if (b != null && b.getBoolean(Constants.INTENT_FROM_WIDGET, false)) {
            if (b != null) {
                Intent intent = new Intent(this, HomeActivity.class);
                startActivity(intent);
            }
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mEntryFragment.setData(intent.getData());
    }

    /**
     * Testing the back-key, because is gives a SQLite error (14): cannot open file / open(/NotificationPermissions.db)
     * App doesn't crash, but it still generates an error in the debugger
     * Unfortunately, still unable to understand what's going on.
     *
     * As I understand, it is something in the Samsung S4 mini, not something in the app.
     */
    /*

    @Override
    public void onBackPressed() {
        Log.e ("PrivacyVandaag", "backpressed");
        finish();

    }
    */
}