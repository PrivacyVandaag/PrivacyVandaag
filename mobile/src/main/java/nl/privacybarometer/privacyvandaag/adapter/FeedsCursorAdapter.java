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

package nl.privacybarometer.privacyvandaag.adapter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.utils.UiUtils;

import static android.graphics.BitmapFactory.*;

public class FeedsCursorAdapter extends CursorLoaderExpandableListAdapter {
    // positions of vars below are set at getCursorPositions(Cursor cursor) at line 171
    private int mIsGroupPos = -1;
    private int mNamePos = -1;
    private int mIdPos = -1;
    private int mLinkPos = -1;
    private int mIconPos = -1;
    private int mFetchModePos = -1;    // ModPrivacyVandaag: Added to check if DO-NOT-REFRESH is active
    private int mIconId = -1;    // ModPrivacyVandaag: Added to find reference to logo drawable resource

    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // ModPrivacyVandaag: 'Do not refresh feed' mode introduced.
    private static final int DO_NOT_FETCH_COLOR_LIGHT_THEME = Color.parseColor("#CCCCCC");
    private static final int NORMAL_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#6D6D6D");

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
        // ModPrivacyVandaag: if FetchMode is 99 then DO-NOT-REFRESH feed channel. activeFeedChannel = false;
        boolean activeFeedChannel =  !(cursor.getInt(mFetchModePos)==FETCHMODE_DO_NOT_FETCH);

        //   ModPrivacyVandaag: Use icons in package instead of fetching favicons from internet.
        // TODO: put reference to drawables in feed table of the database
        Drawable mDrawable;
        int mIconResourceId = cursor.getInt(mIconId);
        if (mIconResourceId > 0) {
            mDrawable = ContextCompat.getDrawable(context,mIconResourceId);
        } else {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_pv);
        }
/*
        String feedName = (cursor.getString(mNamePos));
        Drawable mDrawable;
        if (feedName.equals("Privacy Barometer")) {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_pb);
        } else if (feedName.equals("Privacy First")) {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_pf);
        } else if (feedName.equals("Bits of Freedom")) {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_bof);
        } else if (feedName.equals("CBP")) {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_cbp);
        } else {
            mDrawable = ContextCompat.getDrawable(context, R.drawable.logo_icon_pv);
        }
        */
        // in order to rescale the resources to the right size
        Bitmap bitmap = ((BitmapDrawable) mDrawable).getBitmap();
        Drawable d = new BitmapDrawable(context.getResources(), Bitmap.createScaledBitmap(bitmap, 50, 50, true));
        textView.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);

    /* ModPrivacyVandaag: Code replaced by code above
        final long feedId = cursor.getLong(mIdPos);
        Bitmap bitmap = UiUtils.getFaviconBitmap(feedId, cursor, mIconPos);
        if ((bitmap != null) && (activeFeedChannel)) {   // if a favicon is available, show it next to the feed name.
            textView.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(context.getResources(), bitmap), null, null, null);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
    */
        textView.setText((cursor.isNull(mNamePos) ? cursor.getString(mLinkPos) : cursor.getString(mNamePos)));
        // ModPrivacyVandaag: if FetchMode is 99 then DO-NOT-REFRESH is active. Add remark and change style
        if (activeFeedChannel) {
            textView.setTextColor(NORMAL_TEXT_COLOR_LIGHT_THEME);
        } else {    // inactive feed channel. Is not refreshed.
            textView.append(" - niet volgen");
            textView.setTextColor(DO_NOT_FETCH_COLOR_LIGHT_THEME);
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
            // ModPrivacyVandaag: Added to check if DO-NOT-REFRESH is active
            mFetchModePos = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
            // ModPrivacyVandaag: Added to get identifier to logo drawable resource
            mIconId = cursor.getColumnIndex(FeedColumns.ICON_DRAWABLE);
        }
    }
}
