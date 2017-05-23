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

package nl.privacybarometer.privacyvandaag.fragment;

import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.adapter.FeedsCursorAdapter;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;
import nl.privacybarometer.privacyvandaag.view.DragNDropExpandableListView;
import nl.privacybarometer.privacyvandaag.view.DragNDropListener;

/**
 * Class to edit the feeds. It controls possible actionmode (contextmenu).
 * Different action is taken if a group of feeds or a specific feed is selected.
 * PrivacyVandaag doesn't need much editing possibilities, so much of the code is modified or not used.
 *
 * This class provides the method to re-order the items in de feedslist (menu in the left drawer).
 * It gives the user control over the order of the items
 * in the navigation menu in the Left Drawer.
 * The order of the items is stored in FeedColumns.PRIORITY.
 *
 * The Drag and Drop methods are called from this class
 * and defined in > view > DragNDropExpandableListView.java
 *
 * This class is called from the XML file
 *      > res > layout > activity_edit_feeds.xml and gets the fragments for the feedlist
 * This class is part of
 *      > activity > EditFeedsListActivity.java
 * This class uses
 *      > adapter > FeedsCursorAdapter.java
 */
public class EditFeedsListFragment extends ListFragment {
    // It is possible to set a feed to 'Do not refresh'. In that case the fetchmode is set to 99.
    private static final int FETCHMODE_DO_NOT_FETCH = 99;

    // definition of a custom ExpandableListView pointing to view > DragNDropExpandableListView.java
    // The expandableListView is not necessary since we do not use groups of feeds but only single items,
    // but this drag & drop utility is defined that way, so we live with it.
    private DragNDropExpandableListView mListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_edit_feed_list, container, false);

        mListView = (DragNDropExpandableListView) rootView.findViewById(android.R.id.list);
        mListView.setFastScrollEnabled(true);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // When a feed in the drawerMenuList is clicked, toggle the feed between "do-not-refresh" (99) and active (0).
        mListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                // if the indicator is visible, it is not a group but an single item
                if (v.findViewById(R.id.indicator).getVisibility() != View.VISIBLE) {
                    ContentValues values = new ContentValues();
                    ContentResolver cr = getActivity().getContentResolver();

                    Cursor cursor = cr.query(FeedColumns.CONTENT_URI(id), null, null, null, null);
                    int fetchMode = 0;
                    if (cursor.moveToFirst()) {
                        int fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
                        fetchMode = cursor.getInt(fetchModePosition);
                    }
                    cursor.close();
                    if (fetchMode==FETCHMODE_DO_NOT_FETCH) {
                        fetchMode=0;
                    } else {
                        fetchMode=FETCHMODE_DO_NOT_FETCH;
                    }
                    values.put(FeedColumns.FETCH_MODE, fetchMode); // 99 IS DO NOT FETCH this feed
                    cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                    return true;
                }
                return false;
            }
        }); // end setOnGroupClickListener

        // Adding a tip at the top of the screen about using this drawerMenuList and what your options are.
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_TIP_FEEDS, true)) {
            final TextView header = new TextView(mListView.getContext());
            header.setMinimumHeight(UiUtils.dpToPixel(70));
            int footerPadding = UiUtils.dpToPixel(10);
            header.setPadding(footerPadding, footerPadding, footerPadding, footerPadding);
            header.setText(R.string.tip_sentence_feeds);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setCompoundDrawablePadding(UiUtils.dpToPixel(5));
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_about, 0, R.drawable.ic_action_cancel, 0);
            header.setClickable(true);
            header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mListView.removeHeaderView(header);
                    PrefUtils.putBoolean(PrefUtils.DISPLAY_TIP_FEEDS, false);
                }
            });

            mListView.addHeaderView(header);
        } // end tip

        // Add database adapter to the listview
        mListView.setAdapter(new FeedsCursorAdapter(getActivity(), FeedColumns.GROUPS_CONTENT_URI));

        // Add the Drag & Drop listener to the listview to change the order of items in the drawerMenuList
        mListView.setDragNDropListener(new DragNDropListener() {
            boolean fromHasGroupIndicator = false;

            @Override
            public void onStopDrag(View itemView) {
            }

            @Override
            public void onStartDrag(View itemView) {
                fromHasGroupIndicator = itemView.findViewById(R.id.indicator).getVisibility() == View.VISIBLE;
            }

            @Override
            public void onDrop(final int flatPosFrom, final int flatPosTo) {
                final boolean fromIsGroup = ExpandableListView.getPackedPositionType(mListView.getExpandableListPosition(flatPosFrom)) == ExpandableListView.PACKED_POSITION_TYPE_GROUP;
                final boolean toIsGroup = ExpandableListView.getPackedPositionType(mListView.getExpandableListPosition(flatPosTo)) == ExpandableListView.PACKED_POSITION_TYPE_GROUP;

                final boolean fromIsFeedWithoutGroup = fromIsGroup && !fromHasGroupIndicator;
                final long packedPosTo = mListView.getExpandableListPosition(flatPosTo);
                final int packedGroupPosTo = ExpandableListView.getPackedPositionGroup(packedPosTo);
                moveItem(fromIsGroup, toIsGroup, fromIsFeedWithoutGroup, packedPosTo, packedGroupPosTo, flatPosFrom);
            }

            @Override
            public void onDrag(int x, int y, ListView listView) {
            }
        }); // end setDragNDropListener

        return rootView;
    }   // end onCreateView


    // Helper method to put the changed order by the Drag & Drop into the database.
    private void moveItem(boolean fromIsGroup, boolean toIsGroup, boolean fromIsFeedWithoutGroup, long packedPosTo, int packedGroupPosTo,
                          int flatPosFrom) {
        ContentValues values = new ContentValues();
        ContentResolver cr = getActivity().getContentResolver();

        if (fromIsGroup && toIsGroup) {
            values.put(FeedColumns.PRIORITY, packedGroupPosTo + 1);
            cr.update(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(flatPosFrom)), values, null, null);
        } else if (!fromIsGroup && toIsGroup) {
            values.put(FeedColumns.PRIORITY, packedGroupPosTo + 1);
            values.putNull(FeedColumns.GROUP_ID);
            cr.update(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(flatPosFrom)), values, null, null);
        } else if ((!fromIsGroup && !toIsGroup) || (fromIsFeedWithoutGroup && !toIsGroup)) {
            int groupPrio = ExpandableListView.getPackedPositionChild(packedPosTo) + 1;
            values.put(FeedColumns.PRIORITY, groupPrio);

            int flatGroupPosTo = mListView.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(packedGroupPosTo));
            values.put(FeedColumns.GROUP_ID, mListView.getItemIdAtPosition(flatGroupPosTo));
            cr.update(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(flatPosFrom)), values, null, null);
        }
    }

    @Override
    public void onDestroy() {
        getLoaderManager().destroyLoader(0); // This is needed to avoid an activity (memory) leak!
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.feed_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

}
