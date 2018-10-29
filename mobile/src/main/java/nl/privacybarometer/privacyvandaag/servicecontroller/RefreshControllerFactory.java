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

import android.os.Build;


/**
 * This factory returns the right refresh controller, depending on de Android version
 * on the device.
 *
 * This factory class is needed, because The JobScheduler is recommended to refresh the feeds periodically,
 * but this service is not backwards compatible and renders errors on older devices.
 *
 * To prevent this from happening, we make this factory that only returns the JobScheduler if the
 * device is ready for it. In other cases it returns an empty scheduler. See for a good example on
 * factory classes this page: http://alvinalexander.com/java/java-factory-pattern-example
 *
 *
 * Device with Android 5 (Lollipop) or higher, get the JobScheduler and JobSchedulerRefreshService.
 * Older devices get the AlarmManager and RefeshService.
 *
 * The JobScheduler option is implemented in this factory to prevent errors on older devices.
 * The AlarmManager option is still implemented on servela places in the package
 * and not yet moved to this factory. I do not know if I will make the effort, since this option will
 * be unused in the near future and can then be removed completely.
 *
 */

public class RefreshControllerFactory {
    public static final boolean JOB_SCHEDULER_READY = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

    public static RefreshServiceController getController() {
        if (JOB_SCHEDULER_READY)
            return new JobSchedulerController();
        else return new AlarmManagerController();
    }
}

