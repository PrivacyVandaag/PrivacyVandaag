/*
* Copyright 2015 Privacy Vandaag on the rounded corner transformation
* Copyright 2014 Julian Shen
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package nl.privacybarometer.privacyvandaag.utils;


import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.squareup.picasso.Transformation;

/**
 * Cut the images in the entrieslists to squares with rounded corners.
 *
 * Is used in the list of entries (articles):
 *      adapter > EntriesCursorAdapter.java
 */

public class NiceImageTransform implements Transformation {
    @Override
    public Bitmap transform(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());

        Bitmap circleBitmap;
        Canvas canvas;
        try {
            circleBitmap = Bitmap.createBitmap(size, size, source.getConfig());
            canvas = new Canvas(circleBitmap);
        } catch (Exception ignored) {
            return source;
        }

        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        Bitmap squaredBitmap;
        try {
            squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
        } catch (Exception ignored) {
            circleBitmap.recycle();
            return source;
        }

        if (squaredBitmap != source) {
            source.recycle();
        }
        Paint paint = new Paint();  // paint contains the shader that will texture the shape
        BitmapShader shader = new BitmapShader(squaredBitmap, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP);
        paint.setShader(shader);
        paint.setAntiAlias(true);

        // Cut the image to a circle
        // float r = size / 2f;
        // canvas.drawCircle(r, r, r, paint);

        // Create round corners to the image
        RectF rect = new RectF(0.0f, 0.0f, size, size); // rect contains the bounds of the shape
        int radius = Math.round(size/10);   // radius is the radius in pixels of the rounded corners
        canvas.drawRoundRect(rect, radius, radius, paint);

        squaredBitmap.recycle();
        return circleBitmap;
    }

    @Override
    public String key() {
        return "circle";
    }
}