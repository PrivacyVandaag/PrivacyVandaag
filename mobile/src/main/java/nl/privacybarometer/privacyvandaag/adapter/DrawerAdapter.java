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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DrawerAdapter creates the left drawer menu.
 * Reads the information about feeds from the database and puts it in the left drawer menu.
 */
public class DrawerAdapter extends BaseAdapter {
    // These positions corresponds to the CursorLoader construction from HomeActivity on line 325
    // in order to set up the left drawer menu.
    private static final int POS_ID = 0;
    private static final int POS_URL = 1;
    private static final int POS_NAME = 2;
    private static final int POS_IS_GROUP = 3;
    private static final int POS_ICON = 4;  // To get the favicon-bitmap from the database, if icons are not included in resources but fetched from websites.
    private static final int POS_LAST_UPDATE = 5;
    private static final int POS_ERROR = 6;
    private static final int POS_UNREAD = 7;
    private static final int POS_FETCH_MODE = 8;    // FetchMode is needed to check if for active or inactive feeds (no refresh).
    private static final int POS_ICON_DRAWABLE = 9;    // To get the right icon of the feed from the resources.

    private static final int FETCHMODE_DO_NOT_FETCH = 99;   // 99 means 'Do not refresh feed'. Predefined feed is inactive.
    // Color settings for feeds in the menulist to account for inactive feeds. // TODO: Put it with other style-settings. Include preferences.
    private static final int DO_NOT_FETCH_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#FF4c4c4c");
    private static final int DO_NOT_FETCH_STATE_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#808080");
    private static final int DO_NOT_FETCH_TEXT_COLOR_DARK_THEME = Color.parseColor("#FFb2b2b2");
    private static final int NORMAL_STATE_TEXT_COLOR_LIGHT_THEME = Color.parseColor("#FFFFFF");
    private static final int NORMAL_TEXT_COLOR = Color.parseColor("#EEEEEE");
    private static final int GROUP_TEXT_COLOR = Color.parseColor("#BBBBBB");

    private static final String COLON = MainApplication.getContext().getString(R.string.colon);

    private static final int CACHE_MAX_ENTRIES = 100;
    private final Map<Long, String> mFormattedDateCache = new LinkedHashMap<Long, String>(CACHE_MAX_ENTRIES + 1, .75F, true) {
        @Override
        public boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    private final Context mContext;
    private Cursor mFeedsCursor;
    private int mAllUnreadNumber, mFavoritesNumber;

    // Create left drawer menu
    public DrawerAdapter(Context context, Cursor feedCursor) {
        mContext = context;
        mFeedsCursor = feedCursor;
        updateNumbers();
    }

    public void setCursor(Cursor feedCursor) {
        mFeedsCursor = feedCursor;
        updateNumbers();
        notifyDataSetChanged();
    }

    // Start building the left drawer menu
    public View getView(int position, View convertView, ViewGroup parent) {
           if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.item_drawer_list, parent, false);
                ViewHolder holder = new ViewHolder();
                holder.iconView = (ImageView) convertView.findViewById(android.R.id.icon);
                holder.titleTxt = (TextView) convertView.findViewById(android.R.id.text1);
                holder.stateTxt = (TextView) convertView.findViewById(android.R.id.text2);
                holder.unreadTxt = (TextView) convertView.findViewById(R.id.unread_count);
                holder.separator = convertView.findViewById(R.id.separator);
                convertView.setTag(R.id.holder, holder);
            }

            ViewHolder holder = (ViewHolder) convertView.getTag(R.id.holder);

            // default init
            holder.iconView.setImageDrawable(null);
            holder.titleTxt.setText("");
            holder.titleTxt.setTextColor(NORMAL_TEXT_COLOR);
            holder.titleTxt.setAllCaps(false);
            holder.stateTxt.setVisibility(View.GONE);
            holder.unreadTxt.setText("");
            convertView.setPadding(0, 0, 0, 0);
            holder.separator.setVisibility(View.GONE);

            // The first two items in the left drawer menu are fixed: 1. All entries and 2. Favorites (starred).
            if (position == 0 || position == 1) {
                holder.titleTxt.setText(position == 0 ? R.string.all : R.string.favorites);
                holder.iconView.setImageResource(position == 0 ? R.drawable.ic_statusbar_rss : R.drawable.rating_important);
                int unread = position == 0 ? mAllUnreadNumber : mFavoritesNumber;
                if (unread != 0) {
                    holder.unreadTxt.setText(String.valueOf(unread));
                }
            }
            // Start displaying the feeds in left drawer menu
            if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
                String feedName = (mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME));
                holder.titleTxt.setText(feedName);
                // If it is a group of feeds // TODO: remove the group function. Way too much for simple reader
                if (mFeedsCursor.getInt(POS_IS_GROUP) == 1) {
                    holder.titleTxt.setTextColor(GROUP_TEXT_COLOR);
                    holder.titleTxt.setAllCaps(true);
                    holder.separator.setVisibility(View.VISIBLE);
                }
                // else it is a single feed
                else {
                    holder.stateTxt.setVisibility(View.VISIBLE);
                    // Check whether this is an active feed or one that is not to be refreshed.
                    if (!(mFeedsCursor.getInt(POS_FETCH_MODE) == FETCHMODE_DO_NOT_FETCH)) {
                        if (mFeedsCursor.isNull(POS_ERROR)) { // if it is an active feed and no error
                            long timestamp = mFeedsCursor.getLong(POS_LAST_UPDATE);
                            // Date formatting is expensive, look at the cache
                            String formattedDate = mFormattedDateCache.get(timestamp);
                            if (formattedDate == null) {
                                formattedDate = mContext.getString(R.string.update) + COLON;
                                if (timestamp == 0) {
                                    formattedDate += mContext.getString(R.string.never);
                                } else {
                                    formattedDate += StringUtils.getDateTimeString(timestamp);
                                }
                                mFormattedDateCache.put(timestamp, formattedDate);
                            }
                            holder.stateTxt.setText(formattedDate);
                            holder.stateTxt.setTextColor(NORMAL_STATE_TEXT_COLOR_LIGHT_THEME);
                        }
                        // else there is an error reading the feed. Most likely it is a format error in the
                        // RSS feed itself. Check the original rss feed with a feed-validator on the internet.
                        else {
                            //holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error)).append(COLON).append(mFeedsCursor.getString(POS_ERROR)));
                            holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error_fetching_feed)));
                            Log.e("privacyvandaag", mContext.getString(R.string.error_fetching_feed));
                            Log.e("privacyvandaag","refresh fout: " + mFeedsCursor.getString(POS_ERROR));
                        }

                        // Do not get favicons from internet, get icons from package instead
                        int mIconResourceId = mFeedsCursor.getInt(POS_ICON_DRAWABLE);
                        if (feedName.indexOf("toriteit") > 0 ) { // Uitzondering voor AP  omdat gewone logo niet zichtbaar is.
                            holder.iconView.setImageResource(R.drawable.logo_icon_ap_witte_achtergrond);
                        }
                        else if (mIconResourceId > 0) {
                            holder.iconView.setImageResource(mIconResourceId);
                        } else {    // if image not available, take the apps logo
                            holder.iconView.setImageResource(R.drawable.ic_statusbar_pv);
                        }
                                    /* If the reader should use favicons from the feeds websites take the code below.
                                            // Get and display favicon if available
                                            final long feedId = mFeedsCursor.getLong(POS_ID);
                                            Bitmap bitmap = UiUtils.getFaviconBitmap(feedId, mFeedsCursor, POS_ICON);
                                            if (bitmap != null) {
                                                holder.iconView.setImageBitmap(bitmap);
                                            } else {
                                                holder.iconView.setImageResource(R.mipmap.ic_launcher);
                                            }
                                     */
                        // Get and display number of unread articles
                        int unread = mFeedsCursor.getInt(POS_UNREAD);
                        if (unread != 0) {
                            holder.unreadTxt.setText(String.valueOf(unread));
                        }
                    }
                    // If fetchMode==99, the user doesn't want to follow this predefined feed.
                    // The feed is not to be refreshed. State is therefore inactive.
                    else {
                        holder.stateTxt.setText(mContext.getString(R.string.dont_follow_feed));
                        holder.stateTxt.setTextColor(DO_NOT_FETCH_STATE_TEXT_COLOR_LIGHT_THEME);
                        holder.titleTxt.setTextColor(DO_NOT_FETCH_TEXT_COLOR_LIGHT_THEME);
                    }
                }
            }
        return convertView;
    }

    // If the feed is inactive (do not refresh), (clicking on) the item in the left drawer menu is disabled.
    @Override
    public boolean isEnabled(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
            return !(mFeedsCursor.getInt(POS_FETCH_MODE) == FETCHMODE_DO_NOT_FETCH);
        }
        return true;
    }


    /**
     * Important! The last feed in the feedlist is meant as a servicechannel for the app.
     * Don't display the RSS feed of this service channel. This channel provides information about the app to the user,
     * for example in case of errors, (security) issues or when an an update is available.
     * Because of the special nature of this service channel, it cannot be de-activated. The list
     * of feeds in the left drawer menu is therefore shortened with one, to keep this service channel from te list.
     * Therefore: return mFeedsCursor.getCount() + 1 instead of +2.
     */
    @Override
    public int getCount() {
        if (mFeedsCursor != null) {
          //  return mFeedsCursor.getCount() + 2;
            return mFeedsCursor.getCount() + 1;
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
            return mFeedsCursor.getLong(POS_ID);
        }
        return -1;
    }

    public byte[] getItemIcon(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
            return mFeedsCursor.getBlob(POS_ICON);
        }

        return null;
    }

    public int getIconResourceId(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
            return mFeedsCursor.getInt(POS_ICON_DRAWABLE);
        }
        return 0;
    }

    public String getItemName(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2)) {
            return mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME);
        }
        return null;
    }


    public boolean isItemAGroup(int position) {
        return mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 2) && mFeedsCursor.getInt(POS_IS_GROUP) == 1;

    }

    private void updateNumbers() {
        mAllUnreadNumber = mFavoritesNumber = 0;

        // Get the numbers of entries (should be in a thread, but it's way easier this way and it shouldn't be too slow).
        Cursor numbers = mContext.getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{FeedData.UNREAD_ALLE_ENTRIES_FROM_ACTIVE_FEEDS, FeedData.FAVORITES_NUMBER}, null, null, null);
        // If the total number of unread articles should be displayed, used the code beneath instead.
        // Cursor numbers = mContext.getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{FeedData.ALL_UNREAD_NUMBER, FeedData.FAVORITES_NUMBER}, null, null, null);

        if (numbers != null) {
            if (numbers.moveToFirst()) {
                mAllUnreadNumber = numbers.getInt(0);
                mFavoritesNumber = numbers.getInt(1);
            }
            numbers.close();
        }
    }

    private static class ViewHolder {
        public ImageView iconView;
        public TextView titleTxt;
        public TextView stateTxt;
        public TextView unreadTxt;
        public View separator;
    }
}
