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

/**
 * Parse the found and loaded RSS file. (RssAtomParser())
 * Fetch full article and images according to preferences.
 * Filter the found articles according to the settings of the filter by the user.
 * Store the results in the database.
 */
public class RssAtomParser extends DefaultHandler {
    private static final String TAG = RssAtomParser.class.getSimpleName();

    private static final String AND_SHARP = "&#";
    private static final String HTML_TEXT = "text/html";
    private static final String HTML_TAG_REGEX = "<(.|\n)*?>";

    private static final String TAG_RSS = "rss";
    private static final String TAG_RDF = "rdf";
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

    private static final DateFormat[] PUBDATE_DATE_FORMATS = {new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss", Locale.US),
            new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'Z", Locale.US), new SimpleDateFormat("d' 'MMM' 'yy' 'HH:mm:ss' 'z", Locale.US)};

    private static final DateFormat[] UPDATE_DATE_FORMATS = {new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", Locale.US), new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSz", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd", Locale.US)};

    private final Date mRealLastUpdateDate;
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

    public RssAtomParser(Date realLastUpdateDate, long keepDateBorderTime, final String id, String feedName, String url, boolean retrieveFullText) {
        mKeepDateBorder = new Date(keepDateBorderTime);
        mRealLastUpdateDate = realLastUpdateDate;
        mNewRealLastUpdate = realLastUpdateDate.getTime();
        mId = id;
        mFeedName = feedName;
        mFeedEntriesUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(id);
        mRetrieveFullText = retrieveFullText;

        mFilters = new FeedFilters(id);

        mFeedBaseUrl = NetworkUtils.getBaseUrl(url);

      //  Log.e(TAG, "Start Atom Parser voor ~> " + mFeedName);
      //  Log.e(TAG, "Start Atom Parser voor ~> " + mId);
      //  Log.e(TAG, "Keep Border Time = " + keepDateBorderTime);

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
     *
     * @param uri           This is the URI (unique resource ID) of the used namespace
     * @param localName     This is the tagname without possible namespace, like <title>
     * @param qName         This is the tagname including a possible namespace, like <dc:creator>...
     * @param attributes    These are the attributes in a tag, like <guid isPermaLink="false">
     * @throws SAXException
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (TAG_UPDATED.equals(localName)) {
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
        else if (TAG_PUBDATE.equals(localName)) {
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
            mTitle.append(ch, start, length);
        } else if (mLinkTagEntered) {
            mEntryLink.append(ch, start, length);
        } else if (mDescriptionTagEntered) {
            mDescription.append(ch, start, length);
        } else if (mUpdatedTagEntered || mPubDateTagEntered || mPublishedTagEntered || mDateTagEntered || mLastBuildDateTagEntered) {
            mDateStringBuilder.append(ch, start, length);
        } else if (mGuidTagEntered) {
            mGuid.append(ch, start, length);
        } else if (mAuthorTagEntered) {
            mTmpAuthor.append(ch, start, length);
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
        if (TAG_TITLE.equals(localName)) {
            mTitleTagEntered = false;
        } else if ((TAG_DESCRIPTION.equals(localName) && !TAG_MEDIA_DESCRIPTION.equals(qName)) || TAG_SUMMARY.equals(localName)
                || (TAG_CONTENT.equals(localName) && !TAG_MEDIA_CONTENT.equals(qName)) || TAG_ENCODED_CONTENT.equals(localName)) {
            mDescriptionTagEntered = false;
        } else if (TAG_LINK.equals(localName)) {
            mLinkTagEntered = false;
            // Log.e(TAG, " de url =  "+ mEntryLink);
            if (mFeedLink == null && !mEntryTagEntered && TAG_LINK.equals(qName)) { // Skip <atom10:link> tags
                mFeedLink = mEntryLink.toString();
            }
        }
        // endtag of time and date information
        // clean the time and date information before it is stored using the parse....Date() family
        else if (TAG_UPDATED.equals(localName)) {
            mEntryUpdateDate = parseUpdateDate(mDateStringBuilder.toString());
            mUpdatedTagEntered = false;
        } else if (TAG_PUBDATE.equals(localName)) {
            mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mPubDateTagEntered = false;
        } else if (TAG_PUBLISHED.equals(localName)) {
            mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mPublishedTagEntered = false;
        } else if (TAG_LAST_BUILD_DATE.equals(localName)) {
            mEntryDate = parsePubdateDate(mDateStringBuilder.toString());
            mLastBuildDateTagEntered = false;
        } else if (TAG_DATE.equals(localName)) {
            mEntryDate = parseUpdateDate(mDateStringBuilder.toString());
            mDateTagEntered = false;
        }


        // endtag of "item", so all the information of one item is read and can be stored in database
        else if (TAG_ENTRY.equals(localName) || TAG_ITEM.equals(localName)) {
            mEntryTagEntered = false;
            boolean updateOnly = false;

            // Handle the time and date of the item
            // If it is an old entry, do not insert!! > Old mEntryDate but recent update date => we need to not insert it!
            if (mEntryUpdateDate != null && mEntryDate != null && (mEntryDate.before(mRealLastUpdateDate) || mEntryDate.before(mKeepDateBorder))) {
                updateOnly = true;
                if (mEntryUpdateDate.after(mEntryDate)) {
                    mEntryDate = mEntryUpdateDate;
                }
            }
            // If pubDate is empty, but an update time and date is given, use that one
            else if (mEntryDate == null && mEntryUpdateDate != null) {
                mEntryDate = mEntryUpdateDate;
            }
            // If no time and date info is found with this item, use the time and date we retrieved from previous item or feedchannel
            else if (mEntryDate == null && mEntryUpdateDate == null) {
                mEntryDate = mPreviousEntryDate;
                mEntryUpdateDate = mPreviousEntryUpdateDate;
            }
            // By now, mEntryDate holds the best possible entryDate

            // If we are sure a new item is read and the item is no older than the set mKeepDateBorder,
            // start cleaning it up and store it in the database
             if ((mTitle != null) && (mEntryDate == null || (mEntryDate.after(mRealLastUpdateDate) && mEntryDate.after(mKeepDateBorder)))) {
                ContentValues values = new ContentValues();

                if (mEntryDate != null && mEntryDate.getTime() > mNewRealLastUpdate) {
                    mNewRealLastUpdate = mEntryDate.getTime();
                }

                String improvedTitle = unescapeTitle(mTitle.toString().trim());
                values.put(EntryColumns.TITLE, improvedTitle);
               // Log.e(TAG, "De title van dit item is: "+ improvedTitle);
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

                    if (mAuthor != null) {
                        values.put(EntryColumns.AUTHOR, mAuthor.toString());
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
                        if (mFeedBaseUrl != null && !entryLinkString.startsWith(Constants.HTTP_SCHEME) && !entryLinkString.startsWith(Constants.HTTPS_SCHEME)) {
                            entryLinkString = mFeedBaseUrl
                                    + (entryLinkString.startsWith(Constants.SLASH) ? entryLinkString : Constants.SLASH + entryLinkString);
                        }
                    }

                    String[] existenceValues = enclosureString != null ? (guidString != null ? new String[]{entryLinkString, enclosureString,
                            guidString} : new String[]{entryLinkString, enclosureString}) : (guidString != null ? new String[]{entryLinkString,
                            guidString} : new String[]{entryLinkString});

                    // First, try to update the feed
                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    boolean isUpdated = (!entryLinkString.isEmpty() || guidString != null)
                            && cr.update(mFeedEntriesUri, values, existenceStringBuilder.toString(), existenceValues) != 0;

                    // Insert it only if necessary
                    if (!isUpdated && !updateOnly) {
                        // We put the date only for new entry (no need to change the past, you may already read it)
                        if (mEntryDate != null) {
                            values.put(EntryColumns.DATE, mEntryDate.getTime());
                        } else {
                            values.put(EntryColumns.DATE, mNow--); // -1 to keep the good entries order
                        }

                        values.put(EntryColumns.LINK, entryLinkString);

                        // We cannot update, we need to insert it
                        mInsertedEntriesImages.add(imagesUrls);
                        mInserts.add(ContentProviderOperation.newInsert(mFeedEntriesUri).withValues(values).build());

                        mNewCount++;

                    }

                    // No date, but we managed to update an entry => we already parsed the following entries and don't need to continue
                    if (isUpdated && mEntryDate == null) {
                        cancel();
                    }
                }
            } else {
                cancel();
            }
            mDescription = null;
            mTitle = null;
            mEnclosure = null;
            mGuid = null;
            mAuthor = null;
        }    // Einde om een item te beoordelen en eventueel het item in de database op te slaan.
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
    }    // end of method endElement();

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

            throw new SAXException("Finished");
        }
    }

    public void setFetchImages(boolean fetchImages) {
        this.mFetchImages = fetchImages;
    }


    /******* DATE & TIME ***** DATE & TIME ***** DATE & TIME ***** DATE & TIME ***** DATE & TIME *****
     *
     * All kinds of time and date checking and handling
     *
     */
    private Date parseUpdateDate(String dateStr) {
        dateStr = improveDateString(dateStr);
        return parseUpdateDate(dateStr, true);
    }

    /**
     * NOTICE: This function is UpdateDate.... This is NOT the mainstream date handler.
     * See also the method parsePubdateDate() below
     *
     * @param dateStr
     * @param tryAllFormat
     * @return
     */
    private Date parseUpdateDate(String dateStr, boolean tryAllFormat) {
        // Walk through all possible time and date formats in the RSS feed and try for a match.
        for (DateFormat format : UPDATE_DATE_FORMATS) {
            try {
                Date result = format.parse(dateStr);
                return (result.getTime() > mNow ? new Date(mNow) : result);
            } catch (ParseException ignored) {
            } // just do nothing
        }

        if (tryAllFormat)
            return parsePubdateDate(dateStr, false);
        else
            return null;
    }

    /**
     * NOTICE: this function is PubdateDate.... This is the date handler used in most cases.
     * Only in some cases the above method parseUpdateDate() is used.
     *
     * @param dateStr
     * @return
     */
    private Date parsePubdateDate(String dateStr) {
        // first, the day of the week is removed from the string, because not necessary.
        dateStr = improveDateString(dateStr);
        // Then check in which format the date is written and convert it to UNIX timestamp?
        // If the dateStr is set in the future, it is set to mNow if futereDatesAreAllowed=false.
        return parsePubdateDate(dateStr, true);
    }

    /**
     * Hoofdfunctie om de datumtijd Tag uit de RSS bestand te lezen.
     * Eerste worden de mogelijke formats doorlopen.
     * Daarna wordt de tijd teruggestuurd.
     *
     *
     * @param dateStr
     * @param tryAllFormat
     * @return
     */
    private Date parsePubdateDate(String dateStr, boolean tryAllFormat) {
        // Walk through all possible time and date formats in the RSS feed and try for a match.
        for (DateFormat format : PUBDATE_DATE_FORMATS) {
            try {
                Date result = format.parse(dateStr);
                return (result.getTime() > mNow ? new Date(mNow) : result);
            } catch (ParseException ignored) {
            } // just do nothing
        }

        if (tryAllFormat)
            return parseUpdateDate(dateStr, false);
        else
            return null;
    }

    private String improveDateString(String dateStr) {
        // We remove the first part if necessary (the day display)
        // for example, this removes the "Sat, " in "Sat, 17 Oct 2015 09:50:00 +0000".
        int coma = dateStr.indexOf(", ");
        if (coma != -1) {
            dateStr = dateStr.substring(coma + 2);
        }

        dateStr = dateStr.replaceAll("([0-9])T([0-9])", "$1 $2").replaceAll("Z$", "").replaceAll("  ", " ").trim(); // fix useless char

        // Replace bad timezones
        // Only for United States timezones. Not usefull in this way for European feeds.
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
     *
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
