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
package nl.privacybarometer.privacyvandaag.fragment;


import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.view.SwipeRefreshLayout;

/**
 *
 * Onderdeel van de centrale lijst met artikelen zoals gemaakt door
 * > fragment > EntriesListFragment.java
 * Bijbehorende bestanden:
 * > fragment > SwipeRefreshFragment.java
 * > fragment > SwipeRefreshListFragment.java
 * > view > SwipeRefreshLayout.java
 *
 */
public abstract class SwipeRefreshListFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private SwipeRefreshLayout mRefreshLayout;
    private ListView mListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRefreshLayout = new SwipeRefreshLayout(inflater.getContext()) {
            @Override
            public boolean canChildScrollUp() {
                return mListView != null && mListView.getFirstVisiblePosition() != 0;
            }
        };
        onCreateViewSwipeable(inflater, mRefreshLayout, savedInstanceState);

        mListView = (ListView) mRefreshLayout.findViewById(android.R.id.list);
        if (mListView != null) {
            // HACK to be able to know when we are on the top of the drawerMenuList (for the swipe refresh)
            mListView.addHeaderView(new View(mListView.getContext()));
        }

        return mRefreshLayout;
    }

    abstract public View onCreateViewSwipeable(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

  @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Kleuren die gebruikt worden in de voortgangs indicator (die bewegende streep bovenaan)
      /*
        mRefreshLayout.setColorScheme(android.R.color.holo_green_light,
                android.R.color.holo_green_dark,
                android.R.color.holo_green_light,
                android.R.color.holo_green_dark);
        mRefreshLayout.setOnRefreshListener(this);
        */

      mRefreshLayout.setColorScheme(R.color.light_theme_color_primary_light,
              R.color.light_theme_color_primary_dark,
              R.color.light_theme_color_primary_light,
              R.color.light_theme_color_primary_dark);
      mRefreshLayout.setOnRefreshListener(this);






    }

    /**
     * It shows the SwipeRefreshLayout progress
     */
    public void showSwipeProgress() {
        mRefreshLayout.setRefreshing(true);
    }

    /**
     * It shows the SwipeRefreshLayout progress
     */
    public void hideSwipeProgress() {
        mRefreshLayout.setRefreshing(false);
    }

    /**
     * Enables swipe gesture
     */
    public void enableSwipe() {
        mRefreshLayout.setEnabled(true);
    }

    /**
     * Disables swipe gesture. It prevents manual gestures but keeps the option tu show
     * refreshing programatically.
     */
    public void disableSwipe() {
        mRefreshLayout.setEnabled(false);
    }

    /**
     * Get the refreshing status
     */
    public boolean isRefreshing() {
        return mRefreshLayout.isRefreshing();
    }
}