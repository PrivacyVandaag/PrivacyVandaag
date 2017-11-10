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

package nl.privacybarometer.privacyvandaag.utils;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.ColorRes;
import android.text.Html;
import android.text.Spanned;
import android.view.View;

import java.util.Locale;

import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.activity.BaseActivity;

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

    public static Locale locale (Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            return context.getResources().getConfiguration().locale;
        }
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
