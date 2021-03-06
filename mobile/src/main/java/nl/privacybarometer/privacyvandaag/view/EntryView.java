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
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 *
 * Copyright (c) 2010-2012 Stefan Handschuh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nl.privacybarometer.privacyvandaag.view;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.utils.FileUtils;
import nl.privacybarometer.privacyvandaag.utils.HtmlUtils;
import nl.privacybarometer.privacyvandaag.utils.PrefUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

/**
 * Styling of the entry (article).
 * Called from > fragment > EntryFragment
 */

public class EntryView extends WebView {
    private static final String TAG = EntryView.class.getSimpleName() + " ~> ";
    // Nodig om de tijd in het artikel goed weer te geven. In database zit de GMT tijd.
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("CET");

    private static final String TEXT_HTML = "text/html";
    private static final String HTML_IMG_REGEX = "(?i)<[/]?[ ]?img(.|\n)*?>";

    // Special layoutcolors are defined in > res > values > colors.xml !!
    // Neem voor de link kleur de accent kleur, neem voor de buttonkleur de color_primary
    // Let op: dit zijn HTML/CSS kleurinstellingen, dus geen android resources oid!!
    // Link kleur = dark-primary-color
    private static final String LINK_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#512DA8" : "#a78ddb"; // "#D1C4E9";
   // Buttonkleur = default-primary-color
    private static final String BUTTON_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#673AB7"  : "#673AB7";

    // Standaard kleuren onafhankelijk van gebruikt kleurenpalet
    private static final String BACKGROUND_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#f6f6f6" : "#181b1f";
    private static final String QUOTE_BACKGROUND_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#e6e6e6" : "#383b3f";
    private static final String QUOTE_LEFT_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#a6a6a6" : "#686b6f";
    // De textkleur is hetzelfde als gedefinieerd in colors.xml. #DD000000 = #222222
    private static final String TEXT_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#222222" : "#C0C0C0";
    private static final String SUBTITLE_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "#666666" : "#8c8c8c";
    private static final String SUBTITLE_BORDER_COLOR = PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? "solid #aaa" : "solid #303030";
    private static final String FONT = "sans-serif";  // "sans-serif-light";
    private static final String CSS = "<head><style type='text/css'> "
          //  + "body {max-width:100%; margin: 0.3cm; font-family: sans-serif-light; color: " + TEXT_COLOR + "; background-color:" + BACKGROUND_COLOR + "; line-height: 170%} "
            + "body {max-width:100%; margin:0.3cm 0.5cm; font-family:" + FONT + "; color:" + TEXT_COLOR + "; background-color:" + BACKGROUND_COLOR + "; line-height: 170%} "
            + "* {max-width: 100%; word-break: break-word} "
            + "div {clear:both} "
            + "div.main {margin:0 auto; max-width:650px} "
            + "h1 {font-size:140%;font-weight:bold;line-height:130%;margin:0.8em 0 0.6em 0} "
            + "h2 {font-size:120%;font-weight:;font-color:#000;line-height:130%;margin:1.2em 0 0 0} "
            + "h3 {font-weight:bold;margin: 1em 0 0 0} "
            + "a {color: "+ LINK_COLOR + ";text-decoration:none;font-weight:bold} "
            + "h1 a {color: inherit; text-decoration: none} "
            + "img {height:auto;max-width:650px; width:100%; display:block; margin:0 auto 1em auto} "
            + "pre {white-space:pre-wrap} "
            + "blockquote {border-left: thick solid " + QUOTE_LEFT_COLOR + "; background-color:" + QUOTE_BACKGROUND_COLOR + "; margin: 0.5em 0 0.5em 0em; padding: 0.5em} "
            + "p {margin: 0.4em 0 1.8em 0} "
           // + "p.subtitle {color:" + SUBTITLE_COLOR + ";border-top:1.5px " + SUBTITLE_BORDER_COLOR + ";border-bottom:1.5px " + SUBTITLE_BORDER_COLOR + "; padding-top:2px; padding-bottom:2px; font-weight:normal;margin:0 0 1em 0} "
            + "p.subtitle {color:" + SUBTITLE_COLOR + ";border-top:1px solid rgba(0, 0, 0, 0.1);border-bottom:1px solid rgba(0, 0, 0, 0.1); padding-top:2px; padding-bottom:2px; font-weight:normal;margin:0 0 1em 0} "
            + "ul, ol {margin: 0 0 1em 0.8em; padding: 0 0 0 1em} "
            + "ul li, ol li {margin: 0 0 0.5em 0; padding: 0} "
            + "dt {float: left; clear:left; width: 6em} /* events calendar */ "
            + "dd {float: left; margin-left:0; clear:right}  /* events calendar */ "
            + "dl {display:block; clear:both; margin-top: 0; margin-bottom: 4em}  /* events calendar */ "
            + "div.button-section {padding: 0.4cm 0; margin: 0; text-align: center; display:block; float:none} "
            + ".button-section p {margin: 0.1cm 0 0.2cm 0} "
            + ".button-section p.marginfix {margin: 0.5cm 0 0.5cm 0} "
            + ".button-section input, .button-section a {font-family:" + FONT + "; font-size: 100%; color: #FFFFFF; background-color: " + BUTTON_COLOR + "; text-decoration: none; border: none; border-radius:0.2cm; padding: 0.3cm} "
            + "</style><meta name='viewport' content='width=device-width'/></head>";
    private static final String BODY_START = "<body><div class='main'>";
    private static final String BODY_END = "</div></body>";
    private static final String TITLE_START = "<h1><a href='";
    private static final String TITLE_MIDDLE = "'>";
    private static final String TITLE_END = "</a></h1>";
    private static final String SUBTITLE_START = "<p class='subtitle'>";
    private static final String SUBTITLE_END = "</p>";
    private static final String BUTTON_SECTION_START = "<div class='button-section'>";
    private static final String BUTTON_SECTION_END = "</div>";
    private static final String BUTTON_START = "<p><input type='button' value='";
    private static final String BUTTON_MIDDLE = "' onclick='";
    private static final String BUTTON_END = "'/></p>";
    // the separate 'marginfix' selector in the following is only needed because the CSS box model treats <input> and <a> elements differently
    private static final String LINK_BUTTON_START = "<p class='marginfix'><a href='";
    private static final String LINK_BUTTON_MIDDLE = "'>";
    private static final String LINK_BUTTON_END = "</a></p>";
    private static final String IMAGE_ENCLOSURE = "[@]image/";
    private static final String NOTE_ON_CBP_ARTICLES = "<p style='font-style:italic'>Vanwege restricties op de server van het CBP is het volledige bericht alleen via de browser te bekijken.</p>";

    private final JavaScriptObject mInjectedJSObject = new JavaScriptObject();
    private EntryViewManager mEntryViewMgr;

    public EntryView(Context context) {
        super(context);
        init();
    }

    public EntryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EntryView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setListener(EntryViewManager manager) {
        mEntryViewMgr = manager;
    }

    public void setHtml(long entryId, String title, String link, String contentText, String enclosure,
                        String author, long timestamp, boolean preferFullText) {
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_IMAGES, true)) {
            contentText = HtmlUtils.replaceImageURLs(contentText, entryId);
            // Log.e(TAG,"Id of the entry = " + entryId);
            if (getSettings().getBlockNetworkImage()) {
                // setBlockNetworkImage(false) calls postSync, which takes time, so we clean up the html first and change the value afterwards
                loadData("", TEXT_HTML, Constants.UTF8);
                getSettings().setBlockNetworkImage(false);
            }
        } else {
            contentText = contentText.replaceAll(HTML_IMG_REGEX, "");
            getSettings().setBlockNetworkImage(true);
        }


        // String baseUrl = "";
        // try {
        // URL url = new URL(mLink);
        // baseUrl = url.getProtocol() + "://" + url.getHost();
        // } catch (MalformedURLException ignored) {
        // }

        // do not put 'null' to the base url...
        loadDataWithBaseURL("", generateHtmlContent(title, link, contentText, enclosure, author, timestamp, preferFullText),
                TEXT_HTML, Constants.UTF8, null);
    }

    /**
     * Build the HTML page for the webview of the article.
     *
     */
    private String generateHtmlContent(String title, String link, String contentText, String enclosure,
                                       String author, long timestamp, boolean preferFullText) {
        // Start with <head> including stylesheet (CSS) </head> and <body> open tag.
        StringBuilder content = new StringBuilder(CSS).append(BODY_START);

        if (link == null) {
            link = "";
        }

        // Add the title
        content.append(TITLE_START).append(link).append(TITLE_MIDDLE).append(title).append(TITLE_END).append(SUBTITLE_START);

        // Add date and time
        Date date = new Date(timestamp);
        Context context = getContext();
        StringBuilder dateStringBuilder = new StringBuilder(DateFormat.getLongDateFormat(context).format(date))
                .append(' ').append(DateFormat.getTimeFormat(context).format(date));

        // Add the author
        if (author != null && !author.isEmpty()) {
            dateStringBuilder.append(" &mdash; ").append(author);
        }

        // Add the main content
        content.append(dateStringBuilder).append(SUBTITLE_END).append(contentText);

        // Add button to view article on website and other options.
        content.append(BUTTON_SECTION_START);
        /* The app always retrieves full-tekst whenever possible. A extra button to request it is not necessary. */
        /*
                content.append(BUTTON_START);
                if (!preferFullText) {
                    content.append(context.getString(R.string.get_full_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickFullText();");
                } else {
                    content.append(context.getString(R.string.original_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickOriginalText();");
                }
                content.append(BUTTON_END);
        */
        if (enclosure != null && enclosure.length() > 6 && !enclosure.contains(IMAGE_ENCLOSURE)) {
            content.append(BUTTON_START).append(context.getString(R.string.see_enclosure)).append(BUTTON_MIDDLE)
                    .append("injectedJSObject.onClickEnclosure();").append(BUTTON_END);
        }
        if (link.length() > 0) {
            content.append(LINK_BUTTON_START).append(link).append(LINK_BUTTON_MIDDLE).append(context.getString(R.string.see_link)).append(LINK_BUTTON_END);
        }
        content.append(BUTTON_SECTION_END).append(BODY_END);
       //  Log.e("EntryView >>>>", content.toString());
        return content.toString();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {
        // For scrolling
        setHorizontalScrollBarEnabled(false);
        getSettings().setUseWideViewPort(false);

        // For color
        setBackgroundColor(Color.parseColor(BACKGROUND_COLOR));

        // Setting font size. This is relative to preference settings of the user.
        int fontSize = Integer.parseInt(PrefUtils.getString(PrefUtils.FONT_SIZE, "0"));
        getSettings().setTextZoom(100 + (fontSize * 20));

        // For javascript
        getSettings().setJavaScriptEnabled(true);
        addJavascriptInterface(mInjectedJSObject, mInjectedJSObject.toString());

        // For HTML5 video
        setWebChromeClient(new WebChromeClient() {
            private View mCustomView;
            private WebChromeClient.CustomViewCallback mCustomViewCallback;

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // if a view already exists then immediately terminate the new one
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                FrameLayout videoLayout = mEntryViewMgr.getVideoLayout();
                if (videoLayout != null) {
                    mCustomView = view;

                    setVisibility(View.GONE);
                    videoLayout.setVisibility(View.VISIBLE);
                    videoLayout.addView(view);
                    mCustomViewCallback = callback;

                    mEntryViewMgr.onStartVideoFullScreen();
                }
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();

                if (mCustomView == null) {
                    return;
                }

                FrameLayout videoLayout = mEntryViewMgr.getVideoLayout();
                if (videoLayout != null) {
                    setVisibility(View.VISIBLE);
                    videoLayout.setVisibility(View.GONE);

                    // Hide the custom view.
                    mCustomView.setVisibility(View.GONE);

                    // Remove the custom view from its container.
                    videoLayout.removeView(mCustomView);
                    mCustomViewCallback.onCustomViewHidden();

                    mCustomView = null;

                    mEntryViewMgr.onEndVideoFullScreen();
                }
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Context context = getContext();
                try {
                    if (url.startsWith(Constants.FILE_SCHEME)) {
                        File file = new File(url.replace(Constants.FILE_SCHEME, ""));
                        File extTmpFile = new File(context.getExternalCacheDir(), "tmp_img.jpg");
                        FileUtils.copy(file, extTmpFile);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(extTmpFile), "image/jpeg");
                        context.startActivity(intent);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        context.startActivity(intent);
                    }
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(context, R.string.cant_open_link, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
    }

    public interface EntryViewManager {
        public void onClickOriginalText();

        public void onClickFullText();

        public void onClickEnclosure();

        public void onStartVideoFullScreen();

        public void onEndVideoFullScreen();

        public FrameLayout getVideoLayout();
    }

    private class JavaScriptObject {
        @Override
        @JavascriptInterface
        public String toString() {
            return "injectedJSObject";
        }

        @JavascriptInterface
        public void onClickOriginalText() {
            mEntryViewMgr.onClickOriginalText();
        }

        @JavascriptInterface
        public void onClickFullText() {
            mEntryViewMgr.onClickFullText();
        }

        @JavascriptInterface
        public void onClickEnclosure() {
            mEntryViewMgr.onClickEnclosure();
        }
    }
}