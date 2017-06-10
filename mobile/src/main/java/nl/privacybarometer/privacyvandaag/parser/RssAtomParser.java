/**
 * Privacy Vandaag
 * <p/>
 * Copyright (c) 2015 Privacy Barometer
 * Copyright (c) 2015 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package nl.privacybarometer.privacyvandaag.parser;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import nl.privacybarometer.privacyvandaag.Constants;
import nl.privacybarometer.privacyvandaag.MainApplication;
import nl.privacybarometer.privacyvandaag.provider.FeedData;
import nl.privacybarometer.privacyvandaag.provider.FeedData.EntryColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FeedColumns;
import nl.privacybarometer.privacyvandaag.provider.FeedData.FilterColumns;
import nl.privacybarometer.privacyvandaag.service.FetcherService;
import nl.privacybarometer.privacyvandaag.utils.HtmlUtils;
import nl.privacybarometer.privacyvandaag.utils.NetworkUtils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.privacybarometer.privacyvandaag.provider.FeedData.SERVICE_CHANNEL_FEEDNAME;

/**
 * Parse the found and loaded RSS file. (RssAtomParser())
 * Fetch full article and images according to preferences.
 * Filter the found articles according to the settings of the filter by the user.
 * Store the results in the database.
 * Called from > service > FetcherService.Java line 544
 *
 * An item in RSS specs includes the following elements
 * <item>
 *     <title></title>
 *     <link></link>
 *     <description></description>
 *     <author></author>
 *     <pubDate></pubDate> (following RFC 822 specs: <pubDate>Wed, 02 Oct 2002 13:00:00 GMT</pubDate>)
 *     <guid></guid>  (Globally Unique ID)
 * </item>
 *
 * An entry according to ATOM specs includes the following elements
 * <entry>
 *     <title></title>
 *     <link></link>
 *     <summary></summary>
 *     <content></content> (full content)
 *     <author></author>    (has subelements like <name></name>)
 *     <updated></updated>  (following RFC 3339 specs: <updated>2003-12-13T18:30:02Z</updated>)
 *     <id></id>
 * </entry>
*/

public class RssAtomParser extends DefaultHandler {
    private static final String TAG = RssAtomParser.class.getSimpleName() + " ~> ";

    private static final String AND_SHARP = "&#";
    private static final String HTML_TEXT = "text/html";
    private static final String HTML_TAG_REGEX = "<(.|\n)*?>";

    private static final String ADD_READ_MORE_START = "<p>&nbsp;<br>Lees verder op de website";
    private static final String ADD_READ_MORE_MIDDLE = " van ";
    private static final String ADD_READ_MORE_END = ".</p>";

    private static final String TAG_RSS = "rss";
    private static final String TAG_RDF = "rdf";    // Resource Description Framework. Is this still used?
    private static final String TAG_FEED = "feed";
    private static final String TAG_ENTRY = "entry";
    private static final String TAG_ITEM = "item";
    private static final String TAG_UPDATED = "updated";
    private static final String TAG_TITLE = "title";
    private static final String TAG_LINK = "link";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_MEDIA_DESCRIPTION = "media:description";
    private static final String TAG_CONTENT = "content";
    private static final String TAG_MEDIA_CONTENT = "media:content";
    private static final String TAG_ENCODED_CONTENT = "encoded";
    private static final String TAG_SUMMARY = "summary";
    private static final String TAG_PUBDATE = "pubDate";
    private static final String TAG_PUBLISHED = "published";
    private static final String TAG_DATE = "date";
    private static final String TAG_LAST_BUILD_DATE = "lastBuildDate";
    private static final String TAG_ENCLOSURE = "enclosure";
    private static final String TAG_GUID = "guid";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_CREATOR = "creator";
    private static final String TAG_NAME = "name";

    private static final String ATTRIBUTE_URL = "url";
    private static final String ATTRIBUTE_HREF = "href";
    private static final String ATTRIBUTE_TYPE = "type";
    private static final String ATTRIBUTE_LENGTH = "length";
    private static final String ATTRIBUTE_REL = "rel";

    private static final String[][] TIMEZONES_REPLACE = {{"MEST", "+0200"}, {"EST", "-0500"}, {"PST", "-0800"}};

    // These are the time and date formats that should be found in the RSS feed.
    /*
    private static final DateFormat[] PUBDATE_DATE_FORMATS = {  // For RSS date time strings
            new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US),
            new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.US),
            new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss", Locale.US),
            new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'Z", Locale.US),
            new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'z", Locale.US)};
    */

    private static final ThreadLocal<DateFormat[]> PUBDATE_DATE_FORMATS
            = new ThreadLocal<DateFormat[]>(){
        @Override
        protected DateFormat[] initialValue() {
            return new DateFormat[] {  // For RSS date time strings
                new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US),
                new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.US),
                new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss", Locale.US),
                new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'Z", Locale.US),
                new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'z", Locale.US)};
            };
    };




    private static final DateFormat[] UPDATE_DATE_FORMATS = {   // For ATOM date time strings
            new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSz", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd", Locale.US)};

    private static final int SIX_HOURS = 21600000;

    private final Date mRealLastUpdateDate;
    private final Date mKeepDateBorderTimeLocal;
    private final String mId;
    private final Uri mFeedEntriesUri;
    private final String mFeedName;
    private final String mFeedBaseUrl;
    private final Date mKeepDateBorder;
    private final FeedFilters mFilters;
    private final ArrayList<ContentProviderOperation> mInserts = new ArrayList<>();
    private final ArrayList<ArrayList<String>> mInsertedEntriesImages = new ArrayList<>();
    private long mNewRealLastUpdate;
    private boolean mEntryTagEntered = false;
    private boolean mTitleTagEntered = false;
    private boolean mUpdatedTagEntered = false;
    private boolean mLinkTagEntered = false;
    private boolean mDescriptionTagEntered = false;
    private boolean mPubDateTagEntered = false;
    private boolean mPublishedTagEntered = false;
    private boolean mDateTagEntered = false;
    private boolean mLastBuildDateTagEntered = false;
    private boolean mGuidTagEntered = false;
    private boolean mAuthorTagEntered = false;
    private StringBuilder mTitle;
    private StringBuilder mDateStringBuilder;
    private String mFeedLink;
    private Date mEntryDate;
    private Date mEntryUpdateDate;
    private Date mPreviousEntryDate;
    private Date mPreviousEntryUpdateDate;
    private StringBuilder mEntryLink;
    private StringBuilder mDescription;
    private StringBuilder mEnclosure;
    private int mNewCount = 0;
    private String mFeedTitle;
    private boolean mDone = false;
    private boolean mFetchImages = false;
    private boolean mRetrieveFullText = false;
    private boolean mCancelled = false;
    private long mNow = System.currentTimeMillis();
    private StringBuilder mGuid;
    private StringBuilder mAuthor, mTmpAuthor;

    private boolean futureDatesAreAllowed = true;   // Allow articles with time and date in the future. Usefull for announcement of events.

    /**
     * Constructor for the RSS Atom Parser.
     * This is the start of the reading and analyzing of the RSS feed.
     *
     * @param realLastUpdateTime    Datetime of last succesfull refresh of the feed
     * @param keepDateBorderTime    Datetime in the past beyond which the articles are deleted
     * @param id                    The ID of the feed that is to be refreshed
     * @param feedName              The name of the feed that is to be refreshed
     * @param url                   The URL of the feed that is to be refreshed
     * @param retrieveFullText      Should we retrieve the full article or just keep the RSS excerpt?
     */
    public RssAtomParser(long realLastUpdateTime, long keepDateBorderTime,
                         final String id, String feedName, String url, boolean retrieveFullText) {

        /*
         * Ik heb van de Date opbject in de method en long gemaakt met Unix TimeStamp,
         * zodat ik er nog zes uur vanaf kan trekken. Daarna, maak ik er een Date van.
         * Die zes uur haal ik eraf, zodat een artikel standaard vaker wordt opgehaald.
         * Het is onzinning om dingen dubbel te doen, maar het is helemaal stom als er artikelen ontbreken.
         *
         * Het is nu voorgekomen dat een artikel niet werd opgehaald omdat de redactie een stuk anti-dateerde.
         * Daardoor stopte de hele feed ermee. Nu wordt er standaadr iets verder teruggekeken.
         * Als de ververstijd op twee uur staat, wordt een artikel drie keer gecheckt in de AtomParser, omdat er
         * zes uur voor de laatste update wordt teruggekeken.
         * Hopelijk glipt er nu niks tussendoor.
         *
         *
         */
        mRealLastUpdateDate = new Date (realLastUpdateTime  - (long) SIX_HOURS);
        mNewRealLastUpdate = realLastUpdateTime - (long) SIX_HOURS;

        mId = id;
        mFeedName = feedName;
        mFeedEntriesUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(id);
        mRetrieveFullText = retrieveFullText;

        // Privacy Vandaag does not use filters
        mFilters = new FeedFilters(id);

        mFeedBaseUrl = NetworkUtils.getBaseUrl(url);

     //   Log.e(TAG, "Start Atom Parser for ~> " + mFeedName);
     //   Log.e(TAG, "Start Atom Parser for ~> " + mId);
     //   Log.e(TAG, "Keep Border Time = " + keepDateBorderTime);


        /* ** Determine beyond what date we should not retrieve articles
         * For this are two keepborderdates defined. One for all the feeds in the setting and one
         * per feed. Get the info from database and determin which keepborderdate should be used.
        */
        long keepDateBorderTimeLocal = 0;
        // Get the keep time of articles from database. This is defined per feed.
        Cursor cursor = MainApplication.getContext().getContentResolver()
                .query(FeedColumns.CONTENT_URI(mId),
                        new String[]{
                                FeedColumns._ID,        // get feed ID
                                FeedColumns.KEEP_TIME   // get keeptime
                        },null, null, null
                );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                // if keeptime is set to 1 day, we set this to six hours, else we set it to the correct hours.
                long keepTimeLocal = (cursor.getLong(1) == 1) ? (long) (Constants.DURATION_OF_ONE_DAY / 4)
                        : cursor.getLong(1) * Constants.DURATION_OF_ONE_DAY;
                // If we know the period we should keep the articles, we can calculate what datetime that is from now.
                keepDateBorderTimeLocal = keepTimeLocal > 0 ? System.currentTimeMillis() - keepTimeLocal : 0;
            }
            cursor.close();
        }
        // Take most recent keepborderdate. Die bij de feed is ingesteld of de generieke bewaartermijnen volgens de ingestelde voorkeuren
        mKeepDateBorderTimeLocal = new Date (keepDateBorderTimeLocal);
        if (keepDateBorderTimeLocal > keepDateBorderTime) {
            mKeepDateBorder = new Date(keepDateBorderTimeLocal);
        } else {
            mKeepDateBorder = new Date(keepDateBorderTime);
        }
        // Log.e(TAG, "Keep Border Time = " + mKeepDateBorder);
        // *** We got a date set now beyond we retrieve no articles.

    }

    private static String unescapeTitle(String title) {
        String result = title.replace(Constants.AMP_SG, Constants.AMP).replaceAll(HTML_TAG_REGEX, "").replace(Constants.HTML_LT, Constants.LT)
                .replace(Constants.HTML_GT, Constants.GT).replace(Constants.HTML_QUOT, Constants.QUOT)
                .replace(Constants.HTML_APOSTROPHE, Constants.APOSTROPHE);

        if (result.contains(AND_SHARP)) {
            return Html.fromHtml(result, null, null).toString();
        } else {
            return result;
        }
    }


    /**
     * Is called from the PARSER if a starttag is found
     * if a start tag is found, check which tag it is and start storing the information
     *
     * @param uri           This is the URI (unique resource ID) of the used namespace
     * @param localName     This is the found/parsed tagname without possible namespace, like <title>
     * @param qName         This is the found/parsed tagname including a possible namespace, like <dc:creator>...
     * @param attributes    These are the attributes in the found/parsed tag, like <guid isPermaLink="false">
     * @throws SAXException
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
       // Log.e(TAG + "~> ", "Start met de tag ~> " + localName);
        if (TAG_UPDATED.equals(localName)) {    // if the current tag is "updated"
            mUpdatedTagEntered = true;
            mDateStringBuilder = new StringBuilder();
        }
        // If the tag is "item", initialise a new article item
        else if (TAG_ENTRY.equals(localName) || TAG_ITEM.equals(localName)) {
            mEntryTagEntered = true;
            mDescription = null;
            mEntryLink = null;

            // Save some information from the previous item so it can be used if no date-info is found for this entry
            mPreviousEntryDate = mEntryDate;
            mPreviousEntryUpdateDate = mEntryUpdateDate;
            mEntryDate = null;
            mEntryUpdateDate = null;

            // Get the title of the feed channel from the already retrieved mTitle
            if (mFeedTitle == null && mTitle != null && mTitle.length() > 0) {
                mFeedTitle = mTitle.toString();
            }
            mTitle = null;
        }
        // If the tag is "title", get the title and store in mTitle
        else if (TAG_TITLE.equals(localName)) {
            if (mTitle == null) {
                mTitleTagEntered = true;
                mTitle = new StringBuilder();
            }
        }
        // If the tag is "link", get the link from attributes
        else if (TAG_LINK.equals(localName)) {
            if (mAuthorTagEntered) {
                return;
            }
            if (TAG_ENCLOSURE.equals(attributes.getValue("", ATTRIBUTE_REL))) {
                startEnclosure(attributes, attributes.getValue("", ATTRIBUTE_HREF));
            } else {
                // Get the link only if we don't have one or if its the good one (html)
                if (mEntryLink == null || HTML_TEXT.equals(attributes.getValue("", ATTRIBUTE_TYPE))) {
                    mEntryLink = new StringBuilder();

                    boolean foundLink = false;
                    String href = attributes.getValue("", ATTRIBUTE_HREF);
                    if (!TextUtils.isEmpty(href)) {
                        mEntryLink.append(href);
                        foundLink = true;
                        mLinkTagEntered = false;
                    } else {
                        mLinkTagEntered = true;
                    }

                    if (!foundLink) {
                        mLinkTagEntered = true;
                    }
                }
            }
        }
        // tag "description", initialise mDescription
        else if ((TAG_DESCRIPTION.equals(localName) && !TAG_MEDIA_DESCRIPTION.equals(qName))
                || (TAG_CONTENT.equals(localName) && !TAG_MEDIA_CONTENT.equals(qName))) {
            mDescriptionTagEntered = true;
            mDescription = new StringBuilder();
        } else if (TAG_SUMMARY.equals(localName)) {
            if (mDescription == null) {
                mDescriptionTagEntered = true;
                mDescription = new StringBuilder();
            }
        }
        // tags concerning time and date
        else if (TAG_PUBDATE.equals(localName)) {   // time and date of the item OR the feed channel
            mPubDateTagEntered = true;
            mDateStringBuilder = new StringBuilder();
        } else if (TAG_PUBLISHED.equals(localName)) {
            mPublishedTagEntered = true;
            mDateStringBuilder = new StringBuilder();
        } else if (TAG_DATE.equals(localName)) {
            mDateTagEntered = true;
            mDateStringBuilder = new StringBuilder();
        } else if (TAG_LAST_BUILD_DATE.equals(localName)) {
            mLastBuildDateTagEntered = true;
            mDateStringBuilder = new StringBuilder();
        }
        // tags for character settings and enclosure
        else if (TAG_ENCODED_CONTENT.equals(localName)) {
            mDescriptionTagEntered = true;
            mDescription = new StringBuilder();
        } else if (TAG_ENCLOSURE.equals(localName)) {
            startEnclosure(attributes, attributes.getValue("", ATTRIBUTE_URL));
        }
        // tag for the GUID (Globally Unique ID)
        else if (TAG_GUID.equals(localName)) {
            mGuidTagEntered = true;
            mGuid = new StringBuilder();
        }
        // tag for the author of the article
        else if (TAG_NAME.equals(localName) || TAG_AUTHOR.equals(localName) || TAG_CREATOR.equals(localName)) {
            mAuthorTagEntered = true;
            if (mTmpAuthor == null) {
                mTmpAuthor = new StringBuilder();
            }
        }
    }

    /**
     * Getting multimedia information stated as attributes in the enclosure-tag.
     * Is called from startElement() when the "enclosure" tag is found.
     *
     * @param attributes These are the attributes in a tag, like <guid isPermaLink="false">
     * @param url
     */
    private void startEnclosure(Attributes attributes, String url) {
        if (mEnclosure == null && url != null) { // fetch the first enclosure only
            mEnclosure = new StringBuilder(url);
            mEnclosure.append(Constants.ENCLOSURE_SEPARATOR);

            String value = attributes.getValue("", ATTRIBUTE_TYPE);

            if (value != null) {
                mEnclosure.append(value);
            }
            mEnclosure.append(Constants.ENCLOSURE_SEPARATOR);
            value = attributes.getValue("", ATTRIBUTE_LENGTH);
            if (value != null) {
                mEnclosure.append(value);
            }
        }
    }


    /**
     * Is called from the PARSER if characters are found
     * This reads the characters after a starttag is recognized and stop when an end tag is found
     * @param ch        the characters from the XML document
     * @param start     the start position in the array
     * @param length    the number of characters to read from the array
     * @throws SAXException
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (mTitleTagEntered) {
            mTitle.append(ch, start, length); // store title from the feed in mTitle using StringBuilder
        } else if (mLinkTagEntered) {
            mEntryLink.append(ch, start, length);  // store link to the article in mEntryLink using StringBuilder
        } else if (mDescriptionTagEntered) {
            mDescription.append(ch, start, length); // store the description of the article in mDescription using StringBuilder
        } else if (mUpdatedTagEntered || mPubDateTagEntered || mPublishedTagEntered || mDateTagEntered || mLastBuildDateTagEntered) {
            mDateStringBuilder.append(ch, start, length); // store pub date from article (or feed) in mDatStringBuilder using StringBuilder
        } else if (mGuidTagEntered) {
            mGuid.append(ch, start, length); // store GUID from the article in mGuid using StringBuilder
        } else if (mAuthorTagEntered) {
            mTmpAuthor.append(ch, start, length); // store the author from the article in mAuthor using StringBuilder
        }
    }

    /**
     * Is called from the PARSER if an endtag is found.
     *
     * Set the boolean of the active tag to false if the endtag is reached
     * The parser then knows that the next characters do not belong any longer to that tag.
     *
     *
     * @param uri           This is the URI (unique resource ID) of the used namespace
     * @param localName     This is the tagname without possible namespace, like </title>
     * @param qName         This is the tagname including a possible namespace, like </dc:creator>...
     * @throws SAXException
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {


        /** Als de pubDate in de feed in de toekomst ligt, wordt de ophaaltijd gebruikt.
         *  Maar de datum van evenementen die in het PrivacyVandaag worden aangekondigd,
         *  wordt weergegeven in de pubDate Tags.
         *  Er wordt een check hiervoor in parseUpdateDate() gedaan. Die check kan er uit en
         *  eens kijken wat er dan in de lijst gebeurt.
         *
         *  Zie ook ongeveer regel 570 en regel 600.
         *
         *  mEntryDate is de pubDate
         *  mEntryUpdateDate is de datum waarop de laatste update plaatsvond.
         *  mNow is de huidige systeemtijd.
         *
         */
     //   Log.e(TAG + "~> ", "Einde tag ~> " + localName);
        if (TAG_TITLE.equals(localName)) {
            mTitleTagEntered = false;
        } else if ((TAG_DESCRIPTION.equals(localName) && !TAG_MEDIA_DESCRIPTION.equals(qName)) || TAG_SUMMARY.equals(localName)
                || (TAG_CONTENT.equals(localName) && !TAG_MEDIA_CONTENT.equals(qName)) || TAG_ENCODED_CONTENT.equals(localName)) {
            mDescriptionTagEntered = false;
        } else if (TAG_LINK.equals(localName)) {
            mLinkTagEntered = false;
            if (mFeedLink == null && !mEntryTagEntered && TAG_LINK.equals(qName)) { // Skip <atom10:link> tags
                mFeedLink = mEntryLink.toString();  // The first link that is found, is the link from the feed channel
            }
        }

        /* endtag of time and date information
        // clean the time and date information before it is stored using the parse....Date() family

        Let's figure out what the datetime of the item is. We try to read several values for this
        and after that take the best suited value.
        */
        // If used, the update tag is of course the date time of latest update of the item.
        else if (TAG_UPDATED.equals(localName)) {
            mEntryUpdateDate = parseUpdateDate(mDateStringBuilder.toString());
            mUpdatedTagEntered = false;
        }
        // This is the ONLY tag officially allowed with an <item>
        // This value should be considered first at all times as an entry datetime!
        else if (TAG_PUBDATE.equals(localName)) {
            mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mPubDateTagEntered = false;
        }
        // These are fallback datetimes if pubdate is not available.
        // But they are not allowed with <item> so, is it wise to use this at all?
        // Privacy Vandaag does not use them, since it can only produce errors.
        else if (TAG_PUBLISHED.equals(localName)) {
            //mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mPublishedTagEntered = false;
        } else if (TAG_LAST_BUILD_DATE.equals(localName)) { // This fails as fallback. Is build datetime of whole channel
           // mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mLastBuildDateTagEntered = false;
        } else if (TAG_DATE.equals(localName)) {
            // mEntryDate = parseUpdateDate(mDateStringBuilder.toString());
            mDateTagEntered = false;
        }


        // ****  endtag of "item", so all the information of one item is read.
        // It can be analysed and stored in database
        else if (TAG_ENTRY.equals(localName) || TAG_ITEM.equals(localName)) {
            mEntryTagEntered = false;   // reset the boolean that an item is being read.
            boolean updateOnly = false;

            // Handle the datetime of the item
            // If it is an existing entry with an update, do not insert, but update the existing item
            if (mEntryUpdateDate != null && mEntryDate != null && (mEntryDate.before(mRealLastUpdateDate) || mEntryDate.before(mKeepDateBorder))) {
                updateOnly = true;
                if (mEntryUpdateDate.after(mEntryDate)) {
                    mEntryDate = mEntryUpdateDate;
                }
            }
            // If pubDate is empty, but an update datetime of the item is given, use that one.
            else if (mEntryDate == null && mEntryUpdateDate != null) {
                mEntryDate = mEntryUpdateDate;
            }


            // If we are sure a new item is read and the item is no older than the set mKeepDateBorder,
            // start cleaning it up and store it in the database

            // Please note that if one item is found that does not meet the following criteria
            // the process is cancelled. All the items that follow are discarded,
            // basically because they are already retrieved in the previous refresh of the feed
            // or they are too old.
            //  Orig: if (mTitle != null && (mEntryDate == null || (mEntryDate.after(mRealLastUpdateDate) && mEntryDate.after(mKeepDateBorder)))) {

            // Check if we have a title and datetime. If not, go to next item.
            if (mTitle != null && mEntryDate != null) {
                // Check if we are still after the last refresh datetime and afte the keep datetime.
                // If not, cancel reading the reamining items as they are (usually) older.
                if (mEntryDate.after(mRealLastUpdateDate) && mEntryDate.after(mKeepDateBorder)) {

                // Start met het analyseren en opschonen van het item om het eventueel in de database te stoppen.
                    ContentValues values = new ContentValues();
                    // mNewRealLastUpdate is the time and date of previous update of the feedchannel
                    if (mEntryDate != null && mEntryDate.getTime() > mNewRealLastUpdate) {
                        mNewRealLastUpdate = mEntryDate.getTime();
                    }

                    String improvedTitle = unescapeTitle(mTitle.toString().trim());
                    values.put(EntryColumns.TITLE, improvedTitle);

                    String improvedContent = null;
                    String mainImageUrl = null;
                    ArrayList<String> imagesUrls = null;
                    if (mDescription != null) {
                        // Improve the description
                        improvedContent = HtmlUtils.improveHtmlContent(mDescription.toString(), mFeedBaseUrl);
                        if (mFetchImages) {
                            imagesUrls = HtmlUtils.getImageURLs(improvedContent);
                            if (!imagesUrls.isEmpty()) {
                                mainImageUrl = HtmlUtils.getMainImageURL(imagesUrls);
                            }
                        } else {
                            mainImageUrl = HtmlUtils.getMainImageURL(improvedContent);
                        }

                        if (improvedContent != null) {
                            if ( ! (mFeedName.contains(SERVICE_CHANNEL_FEEDNAME))) {
                                // No additions to the content if it is the channel with service messages.

                                // As long as we display the short description of the article, add a read-further notice
                                improvedContent += ADD_READ_MORE_START;
                                if (mAuthor != null)
                                    improvedContent += ADD_READ_MORE_MIDDLE + mAuthor.toString();
                                improvedContent += ADD_READ_MORE_END;
                            }
                            values.put(EntryColumns.ABSTRACT, improvedContent);
                        }
                    }

                    if (mainImageUrl != null) {
                        values.put(EntryColumns.IMAGE_URL, mainImageUrl);
                    }

                    // Check the item with the filters. If the entry is not filtered out, it needs to be processed further before
                    // it is stored in the database.  The filtering can only be done here,
                    // because the filters look at the title and contents, so that needs to be available.
                    if (!mFilters.isEntryFiltered(improvedTitle, improvedContent)) {
                    //    Log.e(TAG, "Het item wordt niet uitgefilterd, dus we gaan door.");
                        if (mAuthor != null) {
                            values.put(EntryColumns.AUTHOR, mAuthor.toString());
                      //      Log.e(TAG, "auteur = " + mAuthor);
                        } else {    // Als er geen auteur is opgegeven, nemen we de naam van de Feed Channel
                            if (mFeedTitle != null) {
                                if (mFeedTitle.contains("RSS-feed")) { // Fix for RSS title Autoriteit Persoonsgegevens to be used as Author
                                    mFeedTitle = mFeedTitle.substring(9);
                                }
                                values.put(EntryColumns.AUTHOR, mFeedTitle);
                            }
                        }

                        // Handle the multimedia enclosures, if included in the item
                        String enclosureString = null;
                        StringBuilder existenceStringBuilder = new StringBuilder(EntryColumns.LINK).append(Constants.DB_ARG);
                        if (mEnclosure != null && mEnclosure.length() > 0) {
                            enclosureString = mEnclosure.toString();
                            values.put(EntryColumns.ENCLOSURE, enclosureString);
                            existenceStringBuilder.append(Constants.DB_AND).append(EntryColumns.ENCLOSURE).append(Constants.DB_ARG);
                        }

                        // Handle the GUID.
                        String guidString = null;
                        if (mGuid != null && mGuid.length() > 0) {
                            guidString = mGuid.toString();
                            values.put(EntryColumns.GUID, guidString);
                            existenceStringBuilder.append(Constants.DB_AND).append(EntryColumns.GUID).append(Constants.DB_ARG);
                        }

                        // Handle the link to the item on the web if available
                        String entryLinkString = ""; // don't set this to null as we need *some* value
                        if (mEntryLink != null && mEntryLink.length() > 0) {
                            entryLinkString = mEntryLink.toString().trim();
                            //    Log.e(TAG, "link to website = " + entryLinkString);
                            if (mFeedBaseUrl != null && !entryLinkString.startsWith(Constants.HTTP_SCHEME) && !entryLinkString.startsWith(Constants.HTTPS_SCHEME)) {
                                entryLinkString = mFeedBaseUrl
                                        + (entryLinkString.startsWith(Constants.SLASH) ? entryLinkString : Constants.SLASH + entryLinkString);
                            }
                        }

                        // Build selection arguments to query database if item already exists and we should update instead of insert.
                        String[] existenceValues = enclosureString != null ? (guidString != null ? new String[]{entryLinkString, enclosureString,
                                guidString} : new String[]{entryLinkString, enclosureString}) : (guidString != null ? new String[]{entryLinkString,
                                guidString} : new String[]{entryLinkString});

                        // First, try to update the feed
                        ContentResolver cr = MainApplication.getContext().getContentResolver();
                        boolean isUpdated = (!entryLinkString.isEmpty() || guidString != null)
                                && cr.update(mFeedEntriesUri, values, existenceStringBuilder.toString(), existenceValues) != 0;

                        // Insert it only if necessary
                        if ( ! isUpdated &&  ! updateOnly) {
                            values.put(EntryColumns.DATE, mEntryDate.getTime());
                            values.put(EntryColumns.LINK, entryLinkString);
                            mInsertedEntriesImages.add(imagesUrls);
                            mInserts.add(ContentProviderOperation.newInsert(mFeedEntriesUri).withValues(values).build());
                            mNewCount++;
                        }

                    } // end if the item is not filtered out by the filter settings
                      // else { Log.e(TAG, "item got filtered out"); }
                } // end if a new item is read that should be stored in the database, else cancel the parsing
                else {
                    // We reached already known items, so cancel parsing the feed.
                    cancel();
                }
            }
            // reset all the variables to have a clean start for the next item.
            mDescription = null;
            mTitle = null;
            mEnclosure = null;
            mGuid = null;
            mAuthor = null;
        }   // End checking and possible storing in database of an item in between the <item> tags.
        else if (TAG_RSS.equals(localName) || TAG_RDF.equals(localName) || TAG_FEED.equals(localName)) {
            mDone = true;   // End of RRS document. The PARSER will call the method endDocument();
        } else if (TAG_GUID.equals(localName)) {
            mGuidTagEntered = false;
        }
        // endtag for author. Clean up and handle multiple authors
        else if (TAG_NAME.equals(localName) || TAG_AUTHOR.equals(localName) || TAG_CREATOR.equals(localName)) {
            mAuthorTagEntered = false;

            if (mTmpAuthor != null && mTmpAuthor.indexOf("@") == -1) { // no email
                if (mAuthor == null) {
                    mAuthor = new StringBuilder(mTmpAuthor);
                } else { // this indicates multiple authors
                    boolean found = false;
                    for (String previousAuthor : mAuthor.toString().split(",")) {
                        if (previousAuthor.equals(mTmpAuthor.toString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mAuthor.append(Constants.COMMA_SPACE);
                        mAuthor.append(mTmpAuthor);
                    }
                }
            }
            mTmpAuthor = null;
        }
    }
    // end of method endElement();

    public String getFeedLink() {
        return mFeedLink;
    }

    public int getNewCount() {
        return mNewCount;
    }

    public boolean isDone() {
        return mDone;
    }

    public boolean isCancelled() {
        return mCancelled;
    }

    private void cancel() throws SAXException {
        if (!mCancelled) {
            mCancelled = true;
            mDone = true;
            endDocument();
         //   Log.e(TAG, "Einde RSS parser: Exception Finished");
            throw new SAXException("Finished");
        }
    }

    /**
     * Set the parameter if images are to be fetched.
     * @param fetchImages
     */
    public void setFetchImages(boolean fetchImages) {
        this.mFetchImages = fetchImages;
    }






    //******* DATE & TIME ***** DATE & TIME ***** DATE & TIME ***** DATE & TIME ***** DATE & TIME *****

    /**
     *  Main function for getting datetime stamp from tag in ATOM feed.
     *
     * @param dateStr
     * @return
     */

    private Date parseUpdateDate(String dateStr) {
        dateStr = improveDateString(dateStr);
        return parseUpdateDate(dateStr, true);
    }

    /**
     * Parse date time for date time string with ATOM specifications.
     *
     * @param dateStr
     * @param tryAllFormat
     * @return
     */
    private Date parseUpdateDate(String dateStr, boolean tryAllFormat) {
        // Walk through all possible time and date formats in the RSS feed and try for a match.
        for (DateFormat format : UPDATE_DATE_FORMATS) {
            try {
                if ( ! futureDatesAreAllowed) {
                    Date result = format.parse(dateStr);
                    // If the time is in the future, we take the current time of the device.
                    return (result.getTime() > mNow) ? new Date(mNow) : result;
                } else {    // If future time and date are allowed (if events are announced in the RSS feed)
                    return format.parse(dateStr);
                }
            } catch (ParseException ignored) {
                // just do nothing
            }
        }
        if (tryAllFormat) { // If the date isnt formatted as ATOM, try if it is formatted according RSS specs.
            return parsePubdateDate(dateStr, false);
        }
        else
            return null;
    }

    /**
     *  Main function for getting datetime stamp from tag in RSS feed.
     *
     * @param dateStr
     * @return
     */
    private Date parsePubdateDate(String dateStr) {

        // first, the day of the week is removed from the string, because not necessary.
        // also some unusual formatting is removed or replaced.
        dateStr = improveDateString(dateStr);

        // Then check in which format the date is written and convert it to UNIX timestamp?
        // If the dateStr is set in the future, it is set to mNow if futureDatesAreAllowed=false.
        return parsePubdateDate(dateStr, true);
    }


    /**
     * Parse date time for date time string with RSS specifications.
     *
     * @param dateStr = String mEntryDate = String pubDate
     * @param tryAllFormat
     * @return
     */
    private Date parsePubdateDate(String dateStr, boolean tryAllFormat) {
        // Iterate through all possible time and date formats in the RSS feed and try for a match.
        for (DateFormat format : PUBDATE_DATE_FORMATS.get()) {
            try {
                // If we do not allow pubDate being in the future. mNow is current system time of the device
                if ( ! futureDatesAreAllowed) {
                    Date result = format.parse(dateStr);
                    return (result.getTime() > mNow ? new Date(mNow) : result);
                }
                // If we do allow for pubDates being in future (advised in case of events or wrong time zone setting)
                else {
                    return format.parse(dateStr);
                }
            } catch (ParseException ignored) {
                // just do nothing
            }
        }

        if (tryAllFormat) { // If the date isn't formatted as RSS, try if it is formatted according ATOM specs.
            return parseUpdateDate(dateStr, false);
        }
        else
            return null;
    }

    /**
     * Remove some unnecessary formatting in date time string of pubDate
     *
     * @param dateStr the pubDate from the item in the RSS feed
     * @return   a clean date time string, most likely something like:
     *              07 Oct 2015 23:59:45 +0100
     *              7 Oct 2015 23:59:45 +0100
     *              07 Oct 2015 23:59:45 GMT
     */
    private String improveDateString(String dateStr) {
        // We remove the first part if necessary (the day display)
        // example of correct formatted tag:  <pubDate>Sat, 17 Oct 2015 22:00:00 +0000</pubDate>
        // This removes the "Sat, " in "Sat, 17 Oct 2015 09:50:00 +0000".
        int comma = dateStr.indexOf(", ");
        if (comma != -1) {   // If comma is found
            dateStr = dateStr.substring(comma + 2);
        }

        // Sometimes date time is given in format:  07 Oct 2015T23:59. Replace this with 07 Oct 2015 23:59
        // Sometimes the time zone is given with the military code Z (=UTC). Remove this as UTC is default.
        // Remove possible double spaces. Only single spaces between date time elements are allowed.
        // Remove possible spaces at the beginning or end of the pubDate string by using trim().
        dateStr = dateStr.replaceAll("([0-9])T([0-9])", "$1 $2").replaceAll("Z$", "").replaceAll("  ", " ").trim(); // remove useless characters

        // Replaces some US timezones in characters to numbers, like "EST" to "-0500".
        // For other timezones outside US this has no consequences.
        for (String[] timezoneReplace : TIMEZONES_REPLACE) {
            dateStr = dateStr.replace(timezoneReplace[0], timezoneReplace[1]);
        }
        return dateStr;
    }




    /****************************
     *
     * Error handling functions
     *
     *
     */

    @Override
    public void warning(SAXParseException e) throws SAXException {
        // ignore warnings
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        // ignore errors
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        // ignore errors
    }





    /**
     *  Is called from the PARSER if the end of the RSS document is reached
     *  or if an error occurred reading the feed.
     *
     *  Start the fetching of the full articles if that option is set.
     *  Start the fetching of the images if that option is set.
     *  Store the parsed results of the RSS feed in the database
     *
     */
    @Override
    public void endDocument() throws SAXException {
        ContentResolver cr = MainApplication.getContext().getContentResolver();

        try {
            if (!mInserts.isEmpty()) {
                ContentProviderResult[] results = cr.applyBatch(FeedData.AUTHORITY, mInserts);

                if (mFetchImages) {
                    for (int i = 0; i < results.length; ++i) {
                        ArrayList<String> images = mInsertedEntriesImages.get(i);
                        if (images != null) {

                            FetcherService.addImagesToDownload(results[i].uri.getLastPathSegment(), images);
                        }
                    }
                }

                if (mRetrieveFullText) {
                    long[] entriesId = new long[results.length];
                    for (int i = 0; i < results.length; i++) {
                        entriesId[i] = Long.valueOf(results[i].uri.getLastPathSegment());
                    }
                    // Get the full articles using seperate thread
                    FetcherService.addEntriesToMobilize(entriesId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
        }

        ContentValues values = new ContentValues();
        if (mFeedName == null && mFeedTitle != null) {
            values.put(FeedColumns.NAME, mFeedTitle.trim());
        }
        values.putNull(FeedColumns.ERROR);
        values.put(FeedColumns.LAST_UPDATE, System.currentTimeMillis() - 3000); // by precaution to not miss some feeds
        values.put(FeedData.FeedColumns.REAL_LAST_UPDATE, mNewRealLastUpdate);
        cr.update(FeedColumns.CONTENT_URI(mId), values, null, null);
      //  Log.e(TAG, "endDocument. We zijn klaar met het parsen van deze RSS feed.");

        super.endDocument();
    }


    /************
     *
     * Performing the filtering of feeds if filters are set.
     *
     */
    private class FeedFilters {

        private final ArrayList<Rule> mFilters = new ArrayList<>();

        public FeedFilters(String feedId) {
            ContentResolver cr = MainApplication.getContext().getContentResolver();
            Cursor c = cr.query(FilterColumns.FILTERS_FOR_FEED_CONTENT_URI(feedId), new String[]{FilterColumns.FILTER_TEXT, FilterColumns.IS_REGEX,
                    FilterColumns.IS_APPLIED_TO_TITLE, FilterColumns.IS_ACCEPT_RULE}, null, null, null);
            while (c.moveToNext()) {
                Rule r = new Rule();
                r.filterText = c.getString(0);
                r.isRegex = c.getInt(1) == 1;
                r.isAppliedToTitle = c.getInt(2) == 1;
                r.isAcceptRule = c.getInt(3) == 1;
                mFilters.add(r);
            }
            c.close();

        }

        public boolean isEntryFiltered(String title, String content) {

            boolean isFiltered = false;

            for (Rule r : mFilters) {

                boolean isMatch = false;
                if (r.isRegex) {
                    Pattern p = Pattern.compile(r.filterText);
                    if (r.isAppliedToTitle) {
                        Matcher m = p.matcher(title);
                        isMatch = m.find();
                    } else if (content != null) {
                        Matcher m = p.matcher(content);
                        isMatch = m.find();
                    }
                } else if ((r.isAppliedToTitle && title.contains(r.filterText)) || (!r.isAppliedToTitle && content != null && content.contains(r.filterText))) {
                    isMatch = true;
                }

                if (r.isAcceptRule) {
                    if (isMatch) {
                        // accept rules override reject rules, the rest of the rules must be ignored
                        isFiltered = false;
                        break;
                    }
                } else if (isMatch) {
                    isFiltered = true;
                    // no break, there might be an accept rule later
                }
            }

            return isFiltered;
        }

        private class Rule {
            public String filterText;
            public boolean isRegex;
            public boolean isAppliedToTitle;
            public boolean isAcceptRule;
        }
    }
}
