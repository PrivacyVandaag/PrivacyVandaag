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
package nl.privacybarometer.privacyvandaag.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
 * > view > BakedBezierInterpolator.java voor de voortgangsindicator
 *
 */
public abstract class SwipeRefreshFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private SwipeRefreshLayout mRefreshLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRefreshLayout = new SwipeRefreshLayout(inflater.getContext());
        inflateView(inflater, mRefreshLayout, savedInstanceState);

        return mRefreshLayout;
    }

    abstract public View inflateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Dit kleurenschema wordt gebruikt voor de voortgangsindicator als feeds worden ververst.
        /* Originele waarde
        mRefreshLayout.setColorScheme(android.R.color.holo_green_light,
                android.R.color.holo_green_dark,
                android.R.color.holo_green_light,
                android.R.color.holo_green_dark);
        */
        // Leuk om de eigen thema kleuren te gebruiken in de voortgangsindicator
        // LET OP!! Deze gaat over de EntryFragment.
        // Grote kans dat je die andere nodig hebt voor de EntriesListFragment.
        // Die vind je in SwipeRefreshListFragment.java op regel 80.
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