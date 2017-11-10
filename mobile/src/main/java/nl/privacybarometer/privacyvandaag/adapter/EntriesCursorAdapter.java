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
 *
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nl.privacybarometer.privacyvandaag.adapter;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.utils.NetworkUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;
import nl.privacybarometer.privacyvandaag.utils.RoundedCornersTransformation;
import nl.privacybarometer.privacyvandaag.utils.StringUtils;

/**
 *      Main list with entries / articles for the Home Activity
 *      Is used in > fragment > EntriesListFragment.java
 *
 *     Cursor for this view is loaded in
 *     fragment > EntriesListFragment.java line 386 FeedDataContentProvider.URI_ENTRIES_FOR_FEED.
 *
 *     URI_ENTRIES_FOR_FEED is a switch/case number used among others in
 *     FeedDataContentProvider.java line 289 FeedData.ENTRIES_TABLE_WITH_FEED_INFO.
 *
 *     ENTRIES_TABLE_WITH_FEED_INFO is a query string used and defined in FeedData.java.
 *     If you want to adjust the query used, go to FeedData.java line 62.
 */

public class EntriesCursorAdapter extends ResourceCursorAdapter {

    private final Uri mUri;
    private final boolean mShowFeedInfo;
    private int mIdPos, mTitlePos, mMainImgPos, mDatePos, mIsReadPos, mLinkPos, mFavoritePos, mFeedIdPos, mFeedIconPos, mFeedNamePos;
    private int mIconIdPos;    // Added to find reference to logo drawable resource
    private final long mYesterdayMidnight;
    private final long mLastMidnight;
    private final long mComingMidnight;
    private final long mTomorrowMidnight;





    public EntriesCursorAdapter(Context context, Uri uri, Cursor cursor, boolean showFeedInfo) {
        super(context, R.layout.item_entry_list, cursor, 0);
        mUri = uri;
        mShowFeedInfo = showFeedInfo;

        /**
         * Determine last midnight so we can use it while displaying the articles publication time.
         * By doing it here, we have to do it only once per list of entries
         */
        mLastMidnight =  StringUtils.getLastMidnight();
        mYesterdayMidnight = mLastMidnight - Constants.DURATION_OF_ONE_DAY;
        mComingMidnight = mLastMidnight + Constants.DURATION_OF_ONE_DAY;
        mTomorrowMidnight = mComingMidnight + Constants.DURATION_OF_ONE_DAY;

        reinit(cursor); // This is where the cursor gets set. See line 260
    }


    /**
     * The listview of the articles with icon to the left and title to the right.
     * If a image is available from the full article, it will be shown instead of the icon.
     * The name of the feedchannel and de time & date are showen below the title.
     * @param view
     * @param context
     * @param cursor
     */
    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        final int mTextColorUnread = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? ContextCompat.getColor(context, R.color.light_theme_enabled_color) : ContextCompat.getColor(context, R.color.dark_theme_enabled_color);
        final int mTextColorRead = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? ContextCompat.getColor(context, R.color.light_theme_disabled_color) : ContextCompat.getColor(context, R.color.dark_theme_disabled_color);

        if (view.getTag(R.id.holder) == null) {
            ViewHolder holder = new ViewHolder();
            holder.titleTextView = (TextView) view.findViewById(android.R.id.text1);
            holder.dateTextView = (TextView) view.findViewById(android.R.id.text2);
            holder.mainImgView = (ImageView) view.findViewById(R.id.main_icon);
            holder.starImgView = (ImageView) view.findViewById(R.id.favorite_icon);
            view.setTag(R.id.holder, holder);
        }

        final ViewHolder holder = (ViewHolder) view.getTag(R.id.holder);

        String titleText = cursor.getString(mTitlePos);
        holder.titleTextView.setText(titleText);
        String feedName = cursor.getString(mFeedNamePos);

        final long feedId = cursor.getLong(mFeedIdPos);
        String mainImgUrl = cursor.getString(mMainImgPos);
        mainImgUrl = TextUtils.isEmpty(mainImgUrl) ? null : NetworkUtils.getDownloadedOrDistantImageUrl(cursor.getLong(mIdPos), mainImgUrl);

        // If a image is available from the full article, place it instead of the icon next to
        // the title of the article.
        if (mainImgUrl != null) {
            Picasso.with(context)   // Use Picasso library to handle images.
                    .load(mainImgUrl)  // Load (create) Bitmap image from filename
                    .fit()  // Resize the image to fit exactly the holder.mainImgView
                    //.resize(50,50)
                    //.onlyScaleDown()
                    .centerCrop()   // Crop the image to make it fit the holder.mainImgView
                    .transform (new RoundedCornersTransformation()) // Add rounded corners to the image
                    .into(holder.mainImgView);  // Place the image in the TextView in the EntriesList

        } else {
            Picasso.with(context).cancelRequest(holder.mainImgView);
            int mIconResourceId = cursor.getInt(mIconIdPos);
            if (mIconResourceId > 0) {
                // Use special icon for the service messages channel?
                   // holder.mainImgView.setImageResource(R.drawable.logo_icon_serviceberichten);
                // Use a different logo with white background for Autoriteit Persooonsgegevens in Dark Theme
                if ( feedName.contains("riteit persoons") && ( ! PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true))) {
                    holder.mainImgView.setImageResource(R.drawable.logo_icon_ap_witte_achtergrond);
                } else {
                    holder.mainImgView.setImageResource(mIconResourceId);
                }
            } else {
                holder.mainImgView.setImageResource(R.drawable.logo_icon_pv);
            }
        }

        // If the article is favorited, show the favorite image over the title.
        holder.isFavorite = cursor.getInt(mFavoritePos) == 1;
        holder.starImgView.setVisibility(holder.isFavorite ? View.VISIBLE : View.INVISIBLE);


        // Set the status text
        // Show name of the feedchannel if mShowFeedIfo is true ( See HomeActivity line 314 and line  358
        // and if name can be retrieved from database (mFeedNamePos)
        // Show time & date in the same string, next to the name of the feedchannel.
        if (mShowFeedInfo && mFeedNamePos > -1) {
            if (feedName != null) {
                holder.dateTextView.setText(feedName + Constants.COMMA_SPACE +
                    StringUtils.getDateTimeString(cursor.getLong(mDatePos), mYesterdayMidnight, mLastMidnight, mComingMidnight, mTomorrowMidnight));
            } else {
                holder.dateTextView.setText(StringUtils.getDateTimeString(cursor.getLong(mDatePos), mYesterdayMidnight, mLastMidnight, mComingMidnight, mTomorrowMidnight));
            }
        } else {
            holder.dateTextView.setText(StringUtils.getDateTimeString(cursor.getLong(mDatePos), mYesterdayMidnight, mLastMidnight, mComingMidnight, mTomorrowMidnight));
        }



        // set color read/ unread.
        if (cursor.isNull(mIsReadPos)) {
            holder.titleTextView.setEnabled(true);
            holder.dateTextView.setEnabled(true);
            holder.titleTextView.setTextColor(mTextColorUnread);
            holder.dateTextView.setTextColor(mTextColorUnread);
            holder.isRead = false;
        } else {
            holder.titleTextView.setEnabled(false);
            holder.dateTextView.setEnabled(false);
            holder.titleTextView.setTextColor(mTextColorRead);
            holder.dateTextView.setTextColor(mTextColorRead);
            holder.isRead = true;
        }

    }

    public void toggleReadState(final long id, View view) {
        final ViewHolder holder = (ViewHolder) view.getTag(R.id.holder);

        if (holder != null) { // should not happen, but I had a crash with this on PlayStore...
            holder.isRead = !holder.isRead;

            if (holder.isRead) {
                holder.titleTextView.setEnabled(false);
                holder.dateTextView.setEnabled(false);
            } else {
                holder.titleTextView.setEnabled(true);
                holder.dateTextView.setEnabled(true);
            }

            new Thread() {
                @Override
                public void run() {
                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    Uri entryUri = ContentUris.withAppendedId(mUri, id);
                    cr.update(entryUri, holder.isRead ? FeedData.getReadContentValues() : FeedData.getUnreadContentValues(), null, null);
                }
            }.start();
        }
    }

    public void toggleFavoriteState(final long id, View view) {
        final ViewHolder holder = (ViewHolder) view.getTag(R.id.holder);

        if (holder != null) { // should not happen, but I had a crash with this on PlayStore...
            holder.isFavorite = !holder.isFavorite;

            if (holder.isFavorite) {
                holder.starImgView.setVisibility(View.VISIBLE);
            } else {
                holder.starImgView.setVisibility(View.INVISIBLE);
            }

            new Thread() {
                @Override
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(EntryColumns.IS_FAVORITE, holder.isFavorite ? 1 : 0);

                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    Uri entryUri = ContentUris.withAppendedId(mUri, id);
                    cr.update(entryUri, values, null, null);
                }
            }.start();
        }
    }

    public void markAllAsRead(final long untilDate) {
        new Thread() {
            @Override
            public void run() {
                ContentResolver cr = MainApplication.getContext().getContentResolver();
                String where = EntryColumns.WHERE_UNREAD + Constants.DB_AND + '(' + EntryColumns.FETCH_DATE + Constants.DB_IS_NULL + Constants.DB_OR + EntryColumns.FETCH_DATE + "<=" + untilDate + ')';
                cr.update(mUri, FeedData.getReadContentValues(), where, null);
            }
        }.start();
    }

    /**
     * Generate a list with favorite items to share.
     * @return String with the list of items
     */
    public String getFavoritesList() {
        String list = null;
        Cursor cursor = this.getCursor();
        if ((cursor != null) && (cursor.moveToFirst()) ) {
                list = "";
                do {
                    list += cursor.getString(mTitlePos) + "\n" + cursor.getString(mLinkPos) + "\n\n";
                } while (cursor.moveToNext());
            }
        return list;
    }

    @Override
    public void changeCursor(Cursor cursor) {
        reinit(cursor);
        super.changeCursor(cursor);
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        reinit(newCursor);
        return super.swapCursor(newCursor);
    }

    @Override
    public void notifyDataSetChanged() {
        reinit(null);
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        reinit(null);
        super.notifyDataSetInvalidated();
    }

    /**
     * New data from database is available. Get the
     * @param cursor
     */

    private void reinit(Cursor cursor) {
        if (cursor != null && cursor.getCount() > 0) {

            // Define the positions of the cursor table.
            // See the loader in EntriesListFragment.java
            mIdPos = 0;
            mTitlePos = 1;
            mMainImgPos = 2;
            mDatePos = 3;
            mIsReadPos = 4;
            mLinkPos = 5;
            mFavoritePos = 6;
            mFeedNamePos = 7;
            mFeedIdPos = 8;
            mFeedIconPos = 9;
            mIconIdPos = 10;


            /*
            // Defined new return columns.
            // Why did the original programmers loaded the whole entrycolumns table??
            mIdPos = cursor.getColumnIndex(EntryColumns._ID);
            mTitlePos = cursor.getColumnIndex(EntryColumns.TITLE);
            mMainImgPos = cursor.getColumnIndex(EntryColumns.IMAGE_URL);
            mDatePos = cursor.getColumnIndex(EntryColumns.DATE);
            mIsReadPos = cursor.getColumnIndex(EntryColumns.IS_READ);
            mFavoritePos = cursor.getColumnIndex(EntryColumns.IS_FAVORITE);
            mFeedNamePos = cursor.getColumnIndex(FeedColumns.NAME);
            mFeedIdPos = cursor.getColumnIndex(EntryColumns.FEED_ID);
            mFeedIconPos = cursor.getColumnIndex(FeedColumns.ICON);
            mIconIdPos = cursor.getColumnIndex(FeedColumns.ICON_DRAWABLE);

            */
        }
    }

    private static class ViewHolder {
        public TextView titleTextView;
        public TextView dateTextView;
        public ImageView mainImgView;
        public ImageView starImgView;
        public boolean isRead, isFavorite;
    }
}
