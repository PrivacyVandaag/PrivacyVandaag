/*
 * Copyright (c) 2015-2017 Privacy Vandaag / Privacy Barometer
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

package nl.privacybarometer.privacyvandaag.servicecontroller;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.activity.HomeActivity;
import nl.privacybarometer.privacyvandaag.service.AlarmManagerRefreshService;
import nl.privacybarometer.privacyvandaag.service.FetcherService;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import static nl.privacybarometer.privacyvandaag.receiver.BootCompletedBroadcastReceiver.BOOT_COMPLETED;
import static nl.privacybarometer.privacyvandaag.receiver.ConnectionChangeReceiver.CONNECTION_CHANGED;
import static nl.privacybarometer.privacyvandaag.service.AlarmManagerRefreshService.ACTION_RESTART_TIMER;

/**
 * This class can be called from the RefreshControllerFactory depending of the Android version.
 *
 * This controller is used if no JobScheduler is available and we have to use the
 * AlarmManager to set the periodic refreshing of feeds in the background.
 */
class AlarmManagerController implements RefreshServiceController {
    private static final String TAG = AlarmManagerController.class.getSimpleName() + " ~> ";

    public void setRefreshJob(Context context, boolean resetJob, String trigger) {

        if (trigger.equals(PrefUtils.REFRESH_ENABLED) || trigger.equals(HomeActivity.ON_CREATE) ) {
            if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
                context.startService(new Intent(context, AlarmManagerRefreshService.class));
            } else {
                PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0); // reset last refresh time to 0
                context.stopService(new Intent(context, AlarmManagerRefreshService.class));
            }
        }
        else if (trigger.equals(PrefUtils.REFRESH_INTERVAL)) {
            Intent restartTimerIntent = new Intent(ACTION_RESTART_TIMER);
            LocalBroadcastManager.getInstance(context).sendBroadcast(restartTimerIntent);
        }
        // We have a new connection, check if we should refresh the feeds.
        else if (trigger.equals(CONNECTION_CHANGED)) {
            if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false) && PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
                int time = 7200000;
                try {
                    time = Math.max(60000, Integer.parseInt(PrefUtils.getString(PrefUtils.REFRESH_INTERVAL, AlarmManagerRefreshService.TWO_HOURS)));
                } catch (Exception ignored) {
                }

                long lastRefreshTime = PrefUtils.getLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
                long elapsedRealTime = SystemClock.elapsedRealtime();

                if (elapsedRealTime < lastRefreshTime) { // device had been rebooted. reset last scheduled auto-refresh time.
                    lastRefreshTime = 0;
                    PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
                }
                if (lastRefreshTime == 0 || elapsedRealTime - lastRefreshTime > time) { // We should perform auto-refresh now
                    context.startService(new Intent(context, FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS).putExtra(Constants.FROM_AUTO_REFRESH, true));
                }
            }

        }
        // Device (re)booted. Let's start the periodically AlarmManagerRefreshService.
        else if (trigger.equals(BOOT_COMPLETED)) {
            PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
            if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
                context.startService(new Intent(context, AlarmManagerRefreshService.class));
            }

        }


        // If trigger is REFRESH_WIFI_ONLY we do not need to take action here. Only with JobScheduler


    }

    public void finishRefreshJob(Context context,Intent intent) {
        // No need to actively finish services here. Only necessary with JobScheduler.
    }


}