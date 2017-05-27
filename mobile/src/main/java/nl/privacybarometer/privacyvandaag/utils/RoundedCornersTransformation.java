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