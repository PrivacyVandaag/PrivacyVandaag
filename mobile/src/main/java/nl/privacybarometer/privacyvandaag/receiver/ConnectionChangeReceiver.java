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

package nl.privacybarometer.privacyvandaag.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshControllerFactory;
import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshServiceController;


import static nl.privacybarometer.privacyvandaag.servicecontroller.RefreshControllerFactory.JOB_SCHEDULER_READY;


/**
 * This receiver gets called if the device turns on line or off line.
 * If the device turns on line, the RefreshController is called to see if
 * it is time to refresh the feeds.
 *
 * This receiver is only necessary for pre Android version 5.0 Lollipop devices
 * As of Android 5 a different process is used for background services.
 * This new JobScheduler has internal checks for booting devices.
 * JobScheduler has internal listeners if connection becomes available.
 *
 * Therefore, this receiver is only registered in the AndroidManifest file for Android SDK < 21
 *
 */
public class ConnectionChangeReceiver extends BroadcastReceiver {
    private static final String TAG = ConnectionChangeReceiver.class.getSimpleName() + " ~> ";
    public static final String CONNECTION_CHANGED = "connectionChanged";
    public static final String CONNECTION_CHANGED1 = ConnectivityManager.CONNECTIVITY_ACTION;
    private boolean mConnection = false;


    @Override
    public void onReceive(Context context, Intent intent) {

        // Check whether the received broadcast is indeed the Connection Changed broadcast
        String intentAction = intent.getAction();
        if (intentAction != null && intentAction.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

            // If we have JobScheduler, we already set a parameter for connection changed in the Job.
            if (!JOB_SCHEDULER_READY) {

                // The if-then construction is created to only trigger on CHANGE of the connectivity state.
                // Connection changed. The device went off line.
                if (mConnection && intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                    mConnection = false;
                }

                // Connection changed and it is up.
                else if (!mConnection && !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                    mConnection = true;
                    // Get a RefreshServiceController to check whether a RefreshService should be started.
                    RefreshServiceController mRefreshServiceController = RefreshControllerFactory.getController();
                    mRefreshServiceController.setRefreshJob(context, false, CONNECTION_CHANGED);
                }
            }
        }
    }

}