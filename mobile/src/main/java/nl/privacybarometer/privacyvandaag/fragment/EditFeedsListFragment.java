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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
//import android.support.v7.app.ActionBarActivity;
//import android.support.v7.internal.widget.AdapterViewCompat;
import android.support.v7.view.ActionMode;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
//import android.widget.AdapterView;
//import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.adapter.FeedsCursorAdapter;
import nl.privacybarometer.privacyvandaag.parser.OPML;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;
import nl.privacybarometer.privacyvandaag.view.DragNDropExpandableListView;
import nl.privacybarometer.privacyvandaag.view.DragNDropListener;

import java.io.File;
import java.io.FilenameFilter;


/**
 * Class to edit the feeds. It controls actionmode and contextmenu.
 * Different action if a group of feeds or a specific feed is selected.
 * PrivacyVandaag doesn't need much edit possibilities, so much of the code is modified or not used.
 */
public class EditFeedsListFragment extends ListFragment {

    private static final int REQUEST_PICK_OPML_FILE = 1;
    // ModPrivacyVandaag: It is possible to set a feed to 'Do not refresh'. In that case the fetchmode is set to 99.
    private static final int FETCHMODE_DO_NOT_FETCH = 99;


    // START OF CONTEXT MENU FOR EACH LIST ITEM
    private final ActionMode.Callback mFeedActionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.feed_context_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        /* ModPrivacyVandaag: Hieronder het menu dat verschijnt als je in het scherm
        om de feed aan te passen lang op een feed klikt.
         De optie om de feed te wijzigen heb ik verwijderd, onder andere hier
         en door het weghalen van de keuze uit action bar in > menu > feed_context_menu.xml
        */
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            @SuppressWarnings("unchecked")
            Pair<Long, String> tag = (Pair<Long, String>) mode.getTag();
            final long feedId = tag.first;
            final String title = tag.second;

            switch (item.getItemId()) {
                case R.id.menu_edit:
                    startActivity(new Intent(Intent.ACTION_EDIT).setData(FeedColumns.CONTENT_URI(feedId)));

                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.menu_delete:
                    new AlertDialog.Builder(getActivity()) //
                            .setIcon(android.R.drawable.ic_dialog_alert) //
                            .setTitle(title) //
                            .setMessage(R.string.question_delete_feed) //
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            ContentResolver cr = getActivity().getContentResolver();
                                            cr.delete(FeedColumns.CONTENT_URI(feedId), null, null);
                                        }
                                    }.start();
                                }
                            }).setNegativeButton(android.R.string.no, null).show();

                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (int i = 0; i < mListView.getCount(); i++) {
                mListView.setItemChecked(i, false);
            }
        }
    };

    // hieronder Group actions
    private final ActionMode.Callback mGroupActionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.edit_context_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            @SuppressWarnings("unchecked")
            Pair<Long, String> tag = (Pair<Long, String>) mode.getTag();
            final long groupId = tag.first;
            final String title = tag.second;

            switch (item.getItemId()) {
                case R.id.menu_edit:
                    final EditText input = new EditText(getActivity());
                    input.setSingleLine(true);
                    input.setText(title);
                    new AlertDialog.Builder(getActivity()) //
                            .setTitle(R.string.edit_group_title) //
                            .setView(input) //
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            String groupName = input.getText().toString();
                                            if (!groupName.isEmpty()) {
                                                ContentResolver cr = getActivity().getContentResolver();
                                                ContentValues values = new ContentValues();
                                                values.put(FeedColumns.NAME, groupName);
                                                cr.update(FeedColumns.CONTENT_URI(groupId), values, null, null);
                                            }
                                        }
                                    }.start();
                                }
                            }).setNegativeButton(android.R.string.cancel, null).show();

                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.menu_delete:
                    new AlertDialog.Builder(getActivity()) //
                            .setIcon(android.R.drawable.ic_dialog_alert) //
                            .setTitle(title) //
                            .setMessage(R.string.question_delete_group) //
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            ContentResolver cr = getActivity().getContentResolver();
                                            cr.delete(FeedColumns.GROUPS_CONTENT_URI(groupId), null, null);
                                        }
                                    }.start();
                                }
                            }).setNegativeButton(android.R.string.no, null).show();

                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }

        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (int i = 0; i < mListView.getCount(); i++) {
                mListView.setItemChecked(i, false);
            }
        }
    };


    // END CONTEXT MENU FOR EACH LIST ITEM


    // definition is a custom ExpandableListView pointing to view > DragNDropExpandableListView.java
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

        /* ModPrivacyVandaag: Weghalen van de mogelijkheid om de feed te wijzigen. */
/*
        mListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
             //   startActivity(new Intent(Intent.ACTION_EDIT).setData(FeedColumns.CONTENT_URI(id)));
                Toast.makeText(getActivity(), "clicked child" , Toast.LENGTH_LONG).show();
                return true;
            }
        });
*/



        /* ModPrivacyVandaag: Remove option to edit feed. Instead, make the click change the refresh
            status of the feed. FetchMode 99 = DO NOT REFRESH
            See also: > service > FetcherService and > adapter > FeedsCursorAdapter
        */
        mListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                if (v.findViewById(R.id.indicator).getVisibility() != View.VISIBLE) { // This is no a real group
                 //   startActivity(new Intent(Intent.ACTION_EDIT).setData(FeedColumns.CONTENT_URI(id)));


                    String title = ((TextView) v.findViewById(android.R.id.text1)).getText().toString();
                    TextView txt_view = (TextView)v.findViewById(android.R.id.text1);

                    ContentValues values = new ContentValues();
                    ContentResolver cr = getActivity().getContentResolver();

                    //Cursor cursor = cr.query(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(groupPosition)), null, null, null, null);
                    Cursor cursor = cr.query(FeedColumns.CONTENT_URI(id), null, null, null, null);
                    int fetchMode = 0;
                    if (cursor.moveToFirst()) {
                        int fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
                        fetchMode = cursor.getInt(fetchModePosition);
                     //  Toast.makeText(getActivity(), "clicked and fetchmode = " + fetchMode, Toast.LENGTH_LONG).show();
                    }
                    cursor.close();
                    if (fetchMode==FETCHMODE_DO_NOT_FETCH) {
                        fetchMode=0;
                    //    Toast.makeText(getActivity(), "clicked and refresh ("+fetchMode+") " + title + " (id:"+id+") "+ " at position: " +  groupPosition, Toast.LENGTH_LONG).show();
                    //    txt_view.setText("refresh again");
                    } else {
                        fetchMode=FETCHMODE_DO_NOT_FETCH;
                    //    Toast.makeText(getActivity(), "clicked and NO refresh ("+fetchMode+") " + title + " (id:"+id+") "+  " at position: " +  groupPosition, Toast.LENGTH_LONG).show();
                    //    txt_view.setText("DO NOT REFRESH");
                    }


                    values.put(FeedColumns.FETCH_MODE, fetchMode); // 99 IS DO NOT FETCH this feed

                    //cr.update(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(groupPosition)), values, null, null);
                    cr.update(FeedColumns.CONTENT_URI(id), values, null, null);
                    //cr.notifyChange(FeedColumns.GROUPS_CONTENT_URI, null);



                // End of this modification by PrivacyVandaag


                    return true;
                }
                return false;
            }
        });



        // ModPrivacyVandaag: Added a tip about using this list and what your options are.
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
        }
        // end tip

        /* ModPrivacyVandaag: Disable long click to enter context menu to edit or delete feed. */
        // The longclick starts context menu (through call of actionMode).
        /*
        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ActionBarActivity activity = (ActionBarActivity) getActivity();
                if (activity != null) {
                    String title = ((TextView) view.findViewById(android.R.id.text1)).getText().toString();
                    Matcher m = Pattern.compile("(.*) \\([0-9]+\\)$").matcher(title);
                    if (m.matches()) {
                        title = m.group(1);
                    }

                    long feedId = mListView.getItemIdAtPosition(position);
                    ActionMode actionMode;
                    if (view.findViewById(R.id.indicator).getVisibility() == View.VISIBLE) { // This is a group
                        actionMode = activity.startSupportActionMode(mGroupActionModeCallback);
                    } else { // This is a feed
                        actionMode = activity.startSupportActionMode(mFeedActionModeCallback);
                    }
                    actionMode.setTag(new Pair<>(feedId, title));

                    mListView.setItemChecked(position, true);
                }
                return true;
            }
        });
        */






        mListView.setAdapter(new FeedsCursorAdapter(getActivity(), FeedColumns.GROUPS_CONTENT_URI));


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

                        View toView = mListView.getChildAt(flatPosTo - mListView.getFirstVisiblePosition());
                        boolean toIsFeedWithoutGroup = toIsGroup && toView.findViewById(R.id.indicator).getVisibility() != View.VISIBLE;

                        final long packedPosTo = mListView.getExpandableListPosition(flatPosTo);
                        final int packedGroupPosTo = ExpandableListView.getPackedPositionGroup(packedPosTo);

                        if ((fromIsFeedWithoutGroup || !fromIsGroup) && toIsGroup && !toIsFeedWithoutGroup) {
                            new AlertDialog.Builder(getActivity()) //
                                    .setTitle(R.string.to_group_title) //
                                    .setMessage(R.string.to_group_message) //
                                    .setPositiveButton(R.string.to_group_into, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            ContentValues values = new ContentValues();
                                            values.put(FeedColumns.PRIORITY, 1);
                                            values.put(FeedColumns.GROUP_ID, mListView.getItemIdAtPosition(flatPosTo));

                                            ContentResolver cr = getActivity().getContentResolver();
                                            cr.update(FeedColumns.CONTENT_URI(mListView.getItemIdAtPosition(flatPosFrom)), values, null, null);
                                            cr.notifyChange(FeedColumns.GROUPS_CONTENT_URI, null);
                                        }
                                    }).setNegativeButton(R.string.to_group_above, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    moveItem(fromIsGroup, toIsGroup, fromIsFeedWithoutGroup, packedPosTo, packedGroupPosTo, flatPosFrom);
                                }
                            }).show();
                        } else {
                            moveItem(fromIsGroup, toIsGroup, fromIsFeedWithoutGroup, packedPosTo, packedGroupPosTo, flatPosFrom);
                        }
                    }

                    @Override
                    public void onDrag(int x, int y, ListView listView) {
                    }
                });

                return rootView;
            }

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
                getLoaderManager().destroyLoader(0); // This is needed to avoid an activity leak!
                super.onDestroy();
            }

            @Override
            public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
                inflater.inflate(R.menu.feed_list, menu);
                super.onCreateOptionsMenu(menu, inflater);
            }

            @Override
            public boolean onOptionsItemSelected(final MenuItem item) {

            /* ModPrivacyVandaag: Onderstaande keuzes verdwijnen uit het menu */
           /*
        switch (item.getItemId()) {

            case R.id.menu_add_feed: {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.menu_add_feed)
                        .setItems(new CharSequence[]{getString(R.string.add_custom_feed), getString(R.string.google_news_title)}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    startActivity(new Intent(Intent.ACTION_INSERT).setData(FeedColumns.CONTENT_URI));
                                } else {
                                    startActivity(new Intent(getActivity(), AddGoogleNewsActivity.class));
                                }
                            }
                        });
                builder.show();
                return true;
            }
            case R.id.menu_add_group: {
                final EditText input = new EditText(getActivity());
                input.setSingleLine(true);
                new AlertDialog.Builder(getActivity()) //
                        .setTitle(R.string.add_group_title) //
                        .setView(input) //
                                // .setMessage(R.string.add_group_sentence) //
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new Thread() {
                                    @Override
                                    public void run() {
                                        String groupName = input.getText().toString();
                                        if (!groupName.isEmpty()) {
                                            ContentResolver cr = getActivity().getContentResolver();
                                            ContentValues values = new ContentValues();
                                            values.put(FeedColumns.IS_GROUP, true);
                                            values.put(FeedColumns.NAME, groupName);
                                            cr.insert(FeedColumns.GROUPS_CONTENT_URI, values);
                                        }
                                    }
                                }.start();
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            }
            case R.id.menu_import: {
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                        || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {

                    // First, try to use a file app
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("file/*");
                        startActivityForResult(intent, REQUEST_PICK_OPML_FILE);
                    } catch (Exception unused) { // Else use a custom file selector
                        displayCustomFilePicker();
                    }
                } else {
                    Toast.makeText(getActivity(), R.string.error_external_storage_not_available, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.menu_export: {
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                        || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {

                    new Thread(new Runnable() { // To not block the UI
                        @Override
                        public void run() {
                            try {
                                final String filename = Environment.getExternalStorageDirectory().toString() + "/spaRSS_"
                                        + System.currentTimeMillis() + ".opml";

                                OPML.exportToFile(filename);
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getActivity(), String.format(getString(R.string.message_exported_to), filename),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            } catch (Exception e) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getActivity(), R.string.error_feed_export, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                } else {
                    Toast.makeText(getActivity(), R.string.error_external_storage_not_available, Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
       // */

                return super.onOptionsItemSelected(item);

            }

            @Override
            public void onActivityResult(int requestCode, int resultCode, final Intent data) {
                if (requestCode == REQUEST_PICK_OPML_FILE) {
                    if (resultCode == Activity.RESULT_OK) {
                        new Thread(new Runnable() { // To not block the UI
                            @Override
                            public void run() {
                                try {
                                    OPML.importFromFile(data.getData().getPath()); // Try to read it by its path
                                } catch (Exception e) {
                                    try { // Try to read it directly as an InputStream (for Google Drive)
                                        OPML.importFromFile(MainApplication.getContext().getContentResolver().openInputStream(data.getData()));
                                    } catch (Exception unused) {
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getActivity(), R.string.error_feed_import, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                            }
                        }).start();
                    } else {
                        displayCustomFilePicker();
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
            }

            private void displayCustomFilePicker() {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                builder.setTitle(R.string.select_file);

                try {
                    final String[] fileNames = Environment.getExternalStorageDirectory().list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return new File(dir, filename).isFile();
                        }
                    });
                    builder.setItems(fileNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, final int which) {
                            new Thread(new Runnable() { // To not block the UI
                                @Override
                                public void run() {
                                    try {
                                        OPML.importFromFile(Environment.getExternalStorageDirectory().toString() + File.separator
                                                + fileNames[which]);
                                    } catch (Exception e) {
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getActivity(), R.string.error_feed_import, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }
                    });
                    builder.show();
                } catch (Exception unused) {
                    Toast.makeText(getActivity(), R.string.error_feed_import, Toast.LENGTH_LONG).show();
                }
            }
        }
