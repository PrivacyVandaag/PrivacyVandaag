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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import nl.privacybarometer.privacyvandaag.Constants;

/**
 * BaseActivity used as a basis for all the other activities.
 *
 * TODO: Why is this used for all the activities? It consists only of a full screen listener
 * TODO: Only in EntryActivity fullscreen mode is possible.
 *
 */

// public abstract class BaseActivity extends ActionBarActivity {
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = BaseActivity.class.getSimpleName() + " ~> ";

    private static final String STATE_IS_NORMAL_FULLSCREEN = "STATE_IS_NORMAL_FULLSCREEN";
    private static final String STATE_IS_IMMERSIVE_FULLSCREEN = "STATE_IS_IMMERSIVE_FULLSCREEN";

    // Are we in full screen mode or not?
    // On Android devices < KITKAT (API 19)
    private boolean mIsNormalFullScreen;
    // On Android devices >= KITKAT (API 19)
    private boolean mIsImmersiveFullScreen;


    private View mDecorView;
    private OnFullScreenListener mOnFullScreenListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDecorView = getWindow().getDecorView();

        // Set a listener for devices with Android > KITKAT (API 19) if screen mode changes.
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mDecorView.setOnSystemUiVisibilityChangeListener
                    (new View.OnSystemUiVisibilityChangeListener() {

                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            // The screen mode has changed. What mode did we get into?
                            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                                // We went into normal screen mode
                                // If we were in fullscreen, we need to change the settings
                                if (mIsImmersiveFullScreen) {
                                    mIsImmersiveFullScreen = false;
                                    setImmersiveFullScreen(mIsImmersiveFullScreen);
                                    if (mOnFullScreenListener != null) {
                                        mOnFullScreenListener.onFullScreenDisabled();
                                    }
                                }
                            } else {
                                // We went into fullscreen mode
                                // If we were in normal screen mode, we need to change the settings
                                if (!mIsImmersiveFullScreen) {
                                    mIsImmersiveFullScreen = true;
                                    if (mOnFullScreenListener != null) {
                                       mOnFullScreenListener.onFullScreenEnabled(mIsImmersiveFullScreen, true);
                                    }
                                }
                            }
                        }
                    });
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // Lollipop
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); // Tweak to allow setting status bar color
        }
    }

    @Override
    protected void onResume() {
        if (Constants.NOTIF_MGR != null) {
            Constants.NOTIF_MGR.cancel(0);
        }

        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_IS_NORMAL_FULLSCREEN, mIsNormalFullScreen);
        outState.putBoolean(STATE_IS_IMMERSIVE_FULLSCREEN, mIsImmersiveFullScreen);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(STATE_IS_IMMERSIVE_FULLSCREEN)) {
            setImmersiveFullScreen(true);
        } else if (savedInstanceState.getBoolean(STATE_IS_NORMAL_FULLSCREEN)) {
            setNormalFullScreen(true);
        }

        super.onRestoreInstanceState(savedInstanceState);
    }

    public void setOnFullscreenListener(OnFullScreenListener listener) {
        mOnFullScreenListener = listener;
    }

    public boolean isFullScreen() {
        return mIsNormalFullScreen || mIsImmersiveFullScreen;
    }

    public boolean isNormalFullScreen() {
        return mIsNormalFullScreen;
    }

    public void setNormalFullScreen(boolean fullScreen) {
        setNormalFullScreen(fullScreen, false);
    }

    public boolean isImmersiveFullScreen() {
        return mIsImmersiveFullScreen;
    }

    /**
     * Change the settings of the screen mode
     * @param fullScreen
     */
    @SuppressLint("InlinedApi")
    public void setImmersiveFullScreen(boolean fullScreen) {
        if (fullScreen) {
            // Full screen mode for devices with Android >= KITKAT (API 19)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
                // Make the screen go into full screen mode
                mDecorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
            // Full screen mode for devices with Android < KITKAT (API 19)
            else {
                setNormalFullScreen(true, true);
            }
        } else {
            // Normal screen mode for devices with Android >= KITKAT (API 19).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().show();
                }
                mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            // Normal screen mode for devices with Android < KITKAT (API 19).
            else {
                setNormalFullScreen(false, true);
            }
        }
    }

    /**
     * Handle the screen mode for device with Android < KITKAT (API 19).
     * @param fullScreen    // Go to fullscreen or to normal screen?
     * @param isImmersiveFallback   // Should there be a cancel button?
     */
    private void setNormalFullScreen(boolean fullScreen, boolean isImmersiveFallback) {
        // Go to full screen for device with Android < KITKAT (API 19).
        if (fullScreen) {
            mIsNormalFullScreen = true;
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            if (mOnFullScreenListener != null) {
               // orig:  mOnFullScreenListener.onFullScreenEnabled(false, isImmersiveFallback);
                mOnFullScreenListener.onFullScreenEnabled(mIsNormalFullScreen, isImmersiveFallback);
            }
        }
        // Go to normal screen for device with Android < KITKAT (API 19).
        else {
            mIsNormalFullScreen = false;
            if (getSupportActionBar() != null) {
                getSupportActionBar().show();
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (mOnFullScreenListener != null) {
                mOnFullScreenListener.onFullScreenDisabled();
            }
        }
    }

    /**
     * Listener used in EntryFragment to detect changes
     */
    public interface OnFullScreenListener {
        public void onFullScreenEnabled(boolean isImmersive, boolean isImmersiveFallback);

        public void onFullScreenDisabled();
    }
}
