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

package nl.privacybarometer.privacyvandaag.service;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;


@SuppressLint("NewApi")
public class JobSchedulerRefreshService extends JobService {
    private static final String TAG = JobSchedulerRefreshService.class.getSimpleName() + " ~> ";
    public static final String ACTION_FETCHER_SERVICE_FINISHED = "actionFetcherServiceFinished";
    public static final String NEEDS_RESCHEDULE = "needsReschedule";
    public static final String BNDL_PARAMS    = "params";

    // called from FetcherService when finished refreshing the feeds.
    private BroadcastReceiver downloadFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Unlikely, but race condition can occur with onStopJob, so try / catch block is necessary.
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(this); //Unregister receiver to avoid receiver leaks exception
            } catch (IllegalArgumentException e) {
                Log.e(TAG,"Illegal state on unregistering receiver " + e);
            }
            JobParameters jobParams = intent.getParcelableExtra(BNDL_PARAMS);
            boolean needsReschedule = intent.getBooleanExtra(NEEDS_RESCHEDULE, false);

            jobFinished(jobParams,needsReschedule);
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
    }


    // This method is called each time the job is executed from the JobScheduler
    public boolean onStartJob(JobParameters params) {
        // Check if we really should refresh. JobScheduler can be a little trigger happy
        // and can fire multiple times in short succession, even if refresh interval has not yet passed.

        // We set a back off policy to retry after 10 minutes if initial refresh failed.
        // So there definitely cannot be a new refresh service started within, let's say 5 minutes of the previous one.
        int refreshLockPeriod = 300000; // 5 minutes = 5 * 60 * 1000 milliseconds = 300.000

        long lastRefreshTime = PrefUtils.getLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);  // last refresh time set by FetcherService
        long elapsedRealTime = SystemClock.elapsedRealtime();

        // If the system rebooted, we need to reset the last refresh time value
        if (elapsedRealTime < lastRefreshTime) {    // system rebooted
            lastRefreshTime = 0;
            PrefUtils.putLong(PrefUtils.LAST_SCHEDULED_REFRESH, 0);
        }

        // Check if refreshLockPeriod period has passed since last refresh service.
        if ( (lastRefreshTime == 0) || (elapsedRealTime > (lastRefreshTime + refreshLockPeriod) ) ) { // We got a valid refresh service request
            // Set the callback receiver to receive word if the job is finished
            IntentFilter filter = new IntentFilter(ACTION_FETCHER_SERVICE_FINISHED);
            // Use LocalBroadcastManager to catch the intents only from this app
            LocalBroadcastManager.getInstance(this).registerReceiver(downloadFinishedReceiver, filter);

            final Intent intent = new Intent(this, FetcherService.class);
            intent.putExtra(BNDL_PARAMS, params)
                    .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                    .putExtra(Constants.FROM_AUTO_REFRESH, true);



            // Start the background intent service to poll sites for new articles.
            FetcherJobIntentService.enqueueWork(this,intent);

            // If true, the job stays active. We have to close it later ourselves
            // by calling jobFinished(params, needsRescheduled);
            // if set to false, the job is ended immediately at the return of this method.
            return true;   // Answers the question: "Is there still work (in the background) going on?"
        }
        // We are still within refreshLockPeriod, so end this service without refreshing feeds.
        else {
            // End the job immediately
            return false;   // Answers the question: "Is there still work (in the background) going on?"
        }
    }

    // This method is called when job is cancelled while not being finished normally.
    public boolean onStopJob(JobParameters params) {
        // Race condition occurred. Receiver was already unregistered, so try / catch block is necessary.
        try {
            unregisterReceiver(downloadFinishedReceiver);
        } catch (IllegalArgumentException ignore) {
        }
        // return false, so the job is rescheduled anyway.
        return false; // Answers the question: "Should this job be retried?"
    }



}
