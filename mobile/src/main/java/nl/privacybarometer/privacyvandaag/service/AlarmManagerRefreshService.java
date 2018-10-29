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

package nl.privacybarometer.privacyvandaag.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

public class AlarmManagerRefreshService extends Service {
    private static final String TAG = AlarmManagerRefreshService.class.getSimpleName() + " ~> ";
    public static final String TWO_HOURS = "7200000";
    public static final String ACTION_RESTART_TIMER = "actionRestartTimer";

    // Called if preference settings for refresh interval change
    private BroadcastReceiver restartTimerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            restartTimer(false);
        }
    };

    private AlarmManager mAlarmManager;
    private PendingIntent mTimerIntent;

    @Override
    public IBinder onBind(Intent intent) {
        onRebind(intent);
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true; // we want to use rebind
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        // Register a local broadcast receiver to get commands from HomeActivity
        IntentFilter filter = new IntentFilter(ACTION_RESTART_TIMER);
        LocalBroadcastManager.getInstance(this).registerReceiver(restartTimerReceiver , filter);

        // Start the timer.
        restartTimer(true);
    }

    private void restartTimer(boolean created) {
        // Set the PendingIntent for the timer
        if (mTimerIntent == null) {
            mTimerIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, RefreshAlarmReceiver.class), 0);
        } else {
            // We got already a timer set. Cancel it, because we are setting up a new, different one.
            mAlarmManager.cancel(mTimerIntent);
        }

        int refreshInterval = 7200000; // default is 2 hours = 2 * 60 * 60 * 1000 milliseconds = 7200.000
        try {
            // One hour is minimum refresh interval. 1 hour = 60 * 60 * 1000 milliseconds = 3600.000
            refreshInterval = Math.max(3600000, Integer.parseInt(PrefUtils.getString(PrefUtils.REFRESH_INTERVAL, TWO_HOURS)));
        } catch (Exception ignored) {
        }

        long elapsedRealTime = SystemClock.elapsedRealtime();   // elapsed time since boot
        // Hypothetical refresh time. Add 10 seconds as a hypothetical running time for FetcherService
        long initialRefreshTime = elapsedRealTime + 10000;

        if (created) {
            long lastRefreshTime = PrefUtils.getLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);

            // If the system rebooted, we need to reset the last refresh time value
            if (elapsedRealTime < lastRefreshTime) {    // system rebooted
                lastRefreshTime = 0;
                PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
            }

            if (lastRefreshTime > 0) {  // valid last refresh time.
                // this indicates a service restart by the system
                // take maximum of real or hypothtical last refresh time.
                initialRefreshTime = Math.max(initialRefreshTime, lastRefreshTime + refreshInterval);
            }
        }
        // Set the alarmclock
        mAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, initialRefreshTime, refreshInterval, mTimerIntent);
    }

    @Override
    public void onDestroy() {
        if (mTimerIntent != null) {
            mAlarmManager.cancel(mTimerIntent);
        }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(restartTimerReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        super.onDestroy();
    }


    /**
     * This is called when timer from AlarmManager goes off.
     *
     * This receiver is only necessary for pre Android version 5.0 Lollipop devices
     * As of Android 5 a different process is used for background services.
     * This new JobScheduler has internal checks for booting devices.
     * JobScheduler has internal listeners if connection becomes available.
     *
     * Therefore, this receiver is only registered in the AndroidManifest file for Android SDK < 21
     *
     */
    public static class RefreshAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.startService(new Intent(context, FetcherService.class)
                    .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                    .putExtra(Constants.FROM_AUTO_REFRESH, true)
            );
        }
    }
}
