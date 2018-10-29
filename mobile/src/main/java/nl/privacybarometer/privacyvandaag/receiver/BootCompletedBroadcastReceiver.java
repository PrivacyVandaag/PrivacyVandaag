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

import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshControllerFactory;
import nl.privacybarometer.privacyvandaag.servicecontroller.RefreshServiceController;

import static nl.privacybarometer.privacyvandaag.servicecontroller.RefreshControllerFactory.JOB_SCHEDULER_READY;

/**
 * This class is called when device is (re)booted. It ensures that the background service
 * to check on new articles is started when a device is booted up.
 *
 * This receiver is only necessary for pre Android version 5.0 Lollipop devices
 * As of Android 5 a different process is used for background services.
 * This new JobScheduler has internal checks for booting devices.
 * JobScheduler has internal listeners if boot is completed.
 *
 * Therefore, this receiver is only registered in the AndroidManifest file for Android SDK < 21
 *
 */
public class  BootCompletedBroadcastReceiver extends BroadcastReceiver {
    public static final String BOOT_COMPLETED = "bootCompleted";

    @Override
    public void onReceive(Context context, Intent intent) {

        // Check whether the received broadcast is indeed the Boot Completed broadcast
        String intentAction = intent.getAction();
        if (intentAction != null && intentAction.equals(Intent.ACTION_BOOT_COMPLETED)) {

            // Check if we have a JobScheduler.
            // We already set a persistent job that survives rebooting the device.
            if (!JOB_SCHEDULER_READY) {
                // Get a RefreshServiceController and start the RefreshService.
                RefreshServiceController mRefreshServiceController = RefreshControllerFactory.getController();
                mRefreshServiceController.setRefreshJob(context, true, BOOT_COMPLETED);
            }
        }
    }


}