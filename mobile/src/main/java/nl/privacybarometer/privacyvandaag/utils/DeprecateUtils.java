package nl.privacybarometer.privacyvandaag.utils;

import android.support.annotation.ColorRes;
import android.text.Html;
import android.text.Spanned;
import android.view.View;

import static android.R.attr.id;

/**
 * This class replaces deprecated methods that can not be replaced by a single new method.
 * Ussually this means there is a different solution for new and for old SDK's.
 *
 *
 */

public class DeprecateUtils {

    // fromHtml is deprecated. Hier kijken we welke versie het toestel heeft en gebruiken de juiste methode.
    //@SuppressWarnings("deprecation")
    public static Spanned fromHtml(String html){
        Spanned result;
        // De nieuwe functie werkt alleen vanaf API 24 en hoger
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY);
        } else { // voor oudere API's houden we dus de oude functie.
            result = Html.fromHtml(html);
        }
        return result;
    }


    // getColor is deprecated. Hier kijken we welke versie het toestel heeft en gebruiken de juiste methode.
    //@SuppressWarnings("deprecation")
    public static int getColor(int id,View view) {
        int result;
        // De nieuwe functie werkt alleen vanaf API 23 en hoger
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            result = view.getResources().getColor(id, null);
        } else { // voor oudere API's houden we dus de oude functie.
            result = view.getResources().getColor(id);
        }
        return result;
    }





}
