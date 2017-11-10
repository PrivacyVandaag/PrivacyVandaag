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

/**
 * Interface for the specific RefreshServiceController that is used on a specific device.
 * The right RefreshServiceController is selected by the RefreshControllerFactory.
 * The selection is based on the android version of the device.
 */
public interface RefreshServiceController {

    void setRefreshJob(Context context, boolean resetJob, String trigger);
    void finishRefreshJob (Context context,Intent intent);

}
