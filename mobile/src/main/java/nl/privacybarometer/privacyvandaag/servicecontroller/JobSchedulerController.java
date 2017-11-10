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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import java.util.List;

import nl.privacybarometer.privacyvandaag.activity.HomeActivity;
import nl.privacybarometer.privacyvandaag.service.AlarmManagerRefreshService;
import nl.privacybarometer.privacyvandaag.service.JobSchedulerRefreshService;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import static android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static nl.privacybarometer.privacyvandaag.service.JobSchedulerRefreshService.BNDL_PARAMS;


/**
 * This class can be called from the RefreshControllerFactory depending of the Android version.
 *
 * This is the controller for the JobScheduler and can be used on Android 5.0 and above.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)    // Support for this class only on Android 5.0 and above.
class JobSchedulerController implements RefreshServiceController {
    private static final String TAG = JobSchedulerController.class.getSimpleName() + " ~> ";
    // random job ID. Since it should be unique and may collide with libraries using a JobScheduler.
    // As far as we know right now, the app does not use libraries with jobscheduler
    private static final int REFRESH_JOB_ID = 4287978;



    public void setRefreshJob(Context context, boolean resetJob, String trigger) {
        boolean isJobAlreadySet = false;

        if (trigger.equals(HomeActivity.ON_CREATE) ) {
            // Be sure to stop the ReferesService using AlaramManager.
            // No longer suited since we have JobScheduler
            // Check first if an AlarmManager is set!
            boolean alarmUp = (PendingIntent.getBroadcast(context, 0,
                    new Intent(context, AlarmManagerRefreshService.RefreshAlarmReceiver.class),
                    PendingIntent.FLAG_NO_CREATE) != null);
            if (alarmUp) {
                context.stopService(new Intent(context, AlarmManagerRefreshService.class));
            }
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);

        // Automatic refresh is enabled, so let's start a JobScheduler to do this
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
            // Let's check if our job already exists.
            List<JobInfo> mJobInfoList = scheduler.getAllPendingJobs();
            if (!mJobInfoList.isEmpty()) {
                for (JobInfo mJobInfo : mJobInfoList) {
                    // appendLog(TAG, "ID = " + mJobInfo.getId());
                    if (mJobInfo.getId() == REFRESH_JOB_ID) {
                        isJobAlreadySet = true;
                        break;
                    }
                }
            }

            // If job not exists or if we need to override current settings.
            if (!isJobAlreadySet || resetJob) {  // Is the job not already scheduled?
                // Check the preferences if refresh only over WiFi or any network.
                int networkType = (PrefUtils.getBoolean(PrefUtils.REFRESH_WIFI_ONLY, false)) ?
                        JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY;

                // task will be performed at every REFRESH_INTERVAL in millisec
                // default value is 2 hours = 2*60*60*1000 = 7.200.000 millisec.
                long refreshInterval = (long) Integer.parseInt(PrefUtils.getString(PrefUtils.REFRESH_INTERVAL, "7200000"));

                // If the task could not be performed, the job scheduler can request a retry.
                int initialBackoffMillis = 600000; // retry after 10 minutes = 10*60*1000 millisec = 600.000

                ComponentName serviceName = new ComponentName(context, JobSchedulerRefreshService.class);
                JobInfo jobInfo = new JobInfo.Builder(REFRESH_JOB_ID, serviceName)
                        .setPeriodic(refreshInterval)
                        .setPersisted(true)     // task remains active after reboot of device
                        .setRequiredNetworkType(networkType)
                        .setBackoffCriteria(initialBackoffMillis, BACKOFF_POLICY_EXPONENTIAL)
                        .build();
                int result = scheduler.schedule(jobInfo);
                // if (result == JobScheduler.RESULT_SUCCESS) { }

            }
        }
        // refresh is turned off so cancel all scheduled automatic refresh jobs.
        else {
            scheduler.cancelAll();
        }

    }   //*** End Job Scheduler for Android 5 and above.


    public void finishRefreshJob(Context context,Intent intent) {
        JobParameters jobParams = intent.getParcelableExtra(BNDL_PARAMS);
        if (jobParams != null) {    // check whether Fetcher Service is started from Job Scheduler
            Intent fetcherServiceFinishedIntent = new Intent(JobSchedulerRefreshService.ACTION_FETCHER_SERVICE_FINISHED);
            fetcherServiceFinishedIntent.putExtra(BNDL_PARAMS, jobParams);
            //Use LocalBroadcastManager to broadcast intent only within your app
            //LocalBroadcastManager.getInstance(MainApplication.getContext()).sendBroadcast(fetcherServiceFinishedIntent);
            LocalBroadcastManager.getInstance(context).sendBroadcast(fetcherServiceFinishedIntent);
        }
    }


}


