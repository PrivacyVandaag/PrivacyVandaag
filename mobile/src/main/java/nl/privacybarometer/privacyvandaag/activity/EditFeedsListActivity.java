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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

/**
 * Shows a list of feeds from the database in order to edit them.
 *
 * One important option is to re-order the feeds in the menu-list.
 * Read more about this in > fragment > EditFeedsListFragment.java.
 * There the drag and drop methods are called.
 *
 * The Drag and Drop methods are defined in > view > DragNDropExpandableListView.java
 *
 * The list of feeds is loaded using class
 *      > fragment > EditFeedsListFragment.java and
 *      > adapter > FeedsCursorAdapter.java
 * This class uses
 *      > res > layout > activity_edit_feeds.xml to get the fragments for the feedlist
 * In our case, one can press the feed to toggle it's state between active and inactive.
 */
public class EditFeedsListActivity extends AppCompatActivity {
    private static final String TAG = EditFeedsListActivity.class.getSimpleName() + " ~> ";
    public static final String MENU_HAS_BEEN_RESORTED = "menu_has_been_resorted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        // Log.e (TAG,"Left Drawer menu is being edited.");
        PrefUtils.putBoolean (MENU_HAS_BEEN_RESORTED,true);

        setContentView(R.layout.activity_edit_feeds);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return false;
    }
}
