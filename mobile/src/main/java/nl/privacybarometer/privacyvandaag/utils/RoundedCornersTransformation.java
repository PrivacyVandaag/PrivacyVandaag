/*
 * Copyright (c) 2015-2017 Privacy Vandaag / Privacy Barometer
 *
 * Copyright (c) 2013 aprock
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

package nl.privacybarometer.privacyvandaag.utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Add rounded corners to image.
 *
 * Is used in the list of entries (articles):
 *      adapter > EntriesCursorAdapter.java
 *
 * enables hardware accelerated rounded corners
 * simplified implementation from this source: https://gist.github.com/aprock/6213395
 * original idea here : http://www.curious-creature.org/2012/12/11/android-recipe-1-image-with-rounded-corners/
 *
 */
 public class RoundedCornersTransformation implements com.squareup.picasso.Transformation {
    private static final String TAG = RoundedCornersTransformation.class.getSimpleName() + " ~> ";
    private static final String KEY = "rounded_corners_icon_image";


    // constructor
    public RoundedCornersTransformation() { }

    @Override
    public Bitmap transform(final Bitmap source) {
        final int radius = 10;  // Radius of the rounded corner in dp
        final int size = source.getWidth(); // The image is already squared, so width = height

        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        Bitmap output = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawRoundRect(new RectF(0,0, size, size), radius, radius, paint);

        if (source != output) {
            source.recycle();
        }

        return output;
    }

    @Override
    public String key() {
        return KEY;
    }
}