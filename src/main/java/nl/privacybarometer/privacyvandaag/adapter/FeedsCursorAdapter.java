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

package nl.privacybarometer.privacyvandaag.adapter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

public class FeedsCursorAdapter extends CursorLoaderExpandableListAdapter {
    // positions of vars below are set at getCursorPositions(Cursor cursor) at line 171
    private int mIsGroupPos = -1;
    private int mNamePos = -1;
    private int mIdPos = -1;
    private int mLinkPos = -1;
    private int mIconPos = -1;  // Necessary if favicons retrieved from internet are used.
    private int mFetchModePos = -1;    // Added to check if DO-NOT-REFRESH is active
    private int mIconId = -1;    // Necessary to find reference to logo drawable if included in resources

    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // ModPrivacyVandaag: 'Do not refresh feed' mode introduced.
    private static final int DO_NOT_FETCH_COLOR_TEXT_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? Color.parseColor("#CCCCCC") : Color.parseColor("#5D5D5D");
    private static final int ACTIVE_TEXT_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? Color.parseColor("#6D6D6D") : Color.parseColor("#CCCCCC");


    public FeedsCursorAdapter(Activity activity, Uri groupUri) {
        super(activity, groupUri, R.layout.item_feed_list, R.layout.item_feed_list);
    }

    @Override
    protected void onCursorLoaded(Context context, Cursor cursor) {
        getCursorPositions(cursor);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        view.findViewById(R.id.indicator).setVisibility(View.INVISIBLE);

        TextView textView = ((TextView) view.findViewById(android.R.id.text1));
        // if FetchMode is 99 then DO-NOT-REFRESH feed channel. activeFeedChannel = false;
        boolean activeFeedChannel =  !(cursor.getInt(mFetchModePos)==FETCHMODE_DO_NOT_FETCH);

        // Use icons in package instead of fetching favicons from internet. See comment below.
        Drawable mDrawable;
        int mIconResourceId = cursor.getInt(mIconId);
        if (mIconResourceId > 0) {
            mDrawable = ContextCompat.getDrawable(context,mIconResourceId);
        } else {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_pv);
        }
        mDrawable.setBounds(0, 0, 50, 50);  // define the size of the drawable
        textView.setCompoundDrawables(mDrawable, null, null, null);


                    //  Code not needed since we no longer use favicons retrieved from the internet,
                    //but use the logo's included in the resource directory of the app using the code above.
                    /*
                        final long feedId = cursor.getLong(mIdPos);
                        Bitmap bitmap = UiUtils.getFaviconBitmap(feedId, cursor, mIconPos);
                        if ((bitmap != null) && (activeFeedChannel)) {   // if a favicon is available, show it next to the feed name.
                            textView.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(context.getResources(), bitmap), null, null, null);
                        } else {
                            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                        }
                    */
        textView.setText((cursor.isNull(mNamePos) ? cursor.getString(mLinkPos) : cursor.getString(mNamePos)));
        // if FetchMode is 99 then DO-NOT-REFRESH is active. Add remark and change style
        if (activeFeedChannel) {
            textView.setTextColor(ACTIVE_TEXT_COLOR);
        } else {    // inactive feed channel. Is not refreshed.
            textView.append(" - niet volgen");
            textView.setTextColor(DO_NOT_FETCH_COLOR_TEXT_COLOR);
        }
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        ImageView indicatorImage = (ImageView) view.findViewById(R.id.indicator);

        if (cursor.getInt(mIsGroupPos) == 1) {
            indicatorImage.setVisibility(View.VISIBLE);

            TextView textView = ((TextView) view.findViewById(android.R.id.text1));
            textView.setEnabled(true);
            textView.setText(cursor.getString(mNamePos));
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            textView.setText(cursor.getString(mNamePos));

            if (isExpanded)
                indicatorImage.setImageResource(R.drawable.group_expanded);
            else
                indicatorImage.setImageResource(R.drawable.group_collapsed);
        } else {
            bindChildView(view, context, cursor);
            indicatorImage.setVisibility(View.GONE);
        }
    }

    @Override
    protected Uri getChildrenUri(Cursor groupCursor) {
        return FeedColumns.FEEDS_FOR_GROUPS_CONTENT_URI(groupCursor.getLong(mIdPos));
    }

    @Override
    public void notifyDataSetChanged() {
        getCursorPositions(null);
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged(Cursor data) {
        getCursorPositions(data);
    }

    @Override
    public void notifyDataSetInvalidated() {
        getCursorPositions(null);
        super.notifyDataSetInvalidated();
    }

    private synchronized void getCursorPositions(Cursor cursor) {
        if (cursor != null && mIsGroupPos == -1) {
            mIsGroupPos = cursor.getColumnIndex(FeedColumns.IS_GROUP);
            mNamePos = cursor.getColumnIndex(FeedColumns.NAME);
            mIdPos = cursor.getColumnIndex(FeedColumns._ID);
            mLinkPos = cursor.getColumnIndex(FeedColumns.URL);
            mIconPos = cursor.getColumnIndex(FeedColumns.ICON);
            // Added to check if DO-NOT-REFRESH is active
            mFetchModePos = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
            // Added to get identifier to logo drawable resource
            mIconId = cursor.getColumnIndex(FeedColumns.ICON_DRAWABLE);
        }
    }



    }
