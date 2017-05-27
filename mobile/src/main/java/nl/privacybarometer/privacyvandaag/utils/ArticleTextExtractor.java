/**
 * Privacy Vandaag
 * <p/>
 * Copyright (c) 2015-2017 Privacy Barometer
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
 */

package nl.privacybarometer.privacyvandaag.utils;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;


/**
 * This class is thread safe.
 *
 * @author Alex P (ifesdjeen from jreadability)
 * @author Peter Karich
 */
public class ArticleTextExtractor {
    private static final String TAG = ArticleTextExtractor.class.getSimpleName() + " ~> ";
    // Interesting nodes
    private static final Pattern NODES = Pattern.compile("p|div|td|h1|h2|article|section");

    // Unlikely candidates
    private static final Pattern UNLIKELY = Pattern.compile("com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|"
            + "header|menu|re(mark|ply)|rss|sh(are|outbox)|sponsor"
            + "a(d|ll|gegate|rchive|ttachment)|(pag(er|ination))|popup|print|"
            + "login|si(debar|gn|ngle)");

    // Most likely positive candidates
    private static final Pattern POSITIVE = Pattern.compile("(^(body|content|h?entry|main|page|post|text|blog|story|haupt))"
            + "|arti(cle|kel)|instapaper_body");

    // Most likely negative candidates
    private static final Pattern NEGATIVE = Pattern.compile("nav($|igation)|user|com(ment|bx)|(^com-)|contact|"
            + "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|"
            + "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard");

    private static final Pattern NEGATIVE_STYLE =
            Pattern.compile("hidden|display: ?none|font-size: ?small");

    /**
     * @param input            extracts article text from given html string. wasn't tested
     *                         with improper HTML, although jSoup should be able to handle minor stuff.
     * @param contentIndicator a text which should be included into the extracted content, or null
     * @return extracted article, all HTML tags stripped
     */
    public static String extractContent(InputStream input, String contentIndicator, String link) throws Exception {
        return extractContent(Jsoup.parse(input, null, ""), contentIndicator, link);
    }

    /**
     * Extract the full article from a website.
     *
     * For the predefined feeds, we made specific selectors to cut the text correctly out of the webpage.
     * For other, unknown sites, there is a heuristic fall back method. It 'guesses' where the main
     * article is most likely to be found. In most cases, the result is fine, but in some cases it
     * cuts out too much or too little.
     *
     * @param doc
     * @param contentIndicator
     * @param link
     * @return
     */

    private static String extractContent(Document doc, String contentIndicator, String link) {
        if (doc == null) throw new NullPointerException("missing document");
        final String bitsOfFreedom =  "bof.nl";
        final String privacyFirst =  "privacyfirst.nl";
        final String vrijbit =  "vrijbit.nl";
        final String privacyBarometer =  "privacybarometer.nl";
        final String kdvp =  "kdvp.nl";



        // ruim de webpagina alvast wat op door <style>...</style> en <script>...</script> te verwijderen
        prepareDocument(doc);

        // If we know the source of the article, we know exactly how to extract the article from the web page.
        // To extract content from a web page, we use Jsoup's doc.select(). Selectors work just like within jQuery.
        // With first() we retrieve the first element that matches the search criteria.
        // Please note: if the element is not found, NULL is returned,
        //              so we have check before we can get html() or attr()!!
        // With html() we get the inner html-text, including the child tags, AS A STRING.



        // Source of article is Bits of Freedom
        if (link.toLowerCase().contains(bitsOfFreedom)) {
            // The article is in div class="blog-item"> <div class="post_contents"> ... </div></div>
           // Old:  Element tmpElement = doc.select(".blog_item .post_contents").first();

            // First remove the links in margin of the text as they mess up the article text.
            Elements linksSelected= doc.select("span.linked-item");
            for (Element item : linksSelected) {
                item.remove();
            }
            // Get first part of the text seperately, as it is enclosed by <h3>. We change this to <b>
            Element tmpElement1 = doc.select(".post__content h3").first();
            String article = (tmpElement1 != null) ? "<b>" + tmpElement1.html() + "</b>" : null;
            // The article can be split in several div's with the class .post__message. We have to get them all.
            Elements tmpElements = doc.select(".post__content .post__message");
            for (int i = 0; i < tmpElements.size(); i++) {
                if (i != 0) {   // First element already selected in first selection above.
                    if (tmpElements.get(i) != null) article += tmpElements.get(i).html();
                }
            }
            // The text has some empty paragraphs. We remove them here
            if (article != null) article.replace("<p>&nbsp;</p>", "");

            // Get the main image.
            tmpElement1 = doc.select(".post__background .wp-post-image").first();
            String articleMainImageName = (tmpElement1 != null) ? tmpElement1.attr("src") : null; // Get imagename or make it null
            // Get the meta-data and copyright info belonging to the main info.
            tmpElement1 = doc.select(".post__content .post__credits").first();
            String articleMainImageMetaData = (tmpElement1 != null) ? tmpElement1.html() : null;
            // Combine the collected info.
            if (article != null) {
                if (articleMainImageName != null) {
                    article = "<img src=\"" + articleMainImageName + "\" alt=\"\">" + article;
                }
                // Add copyright notice \u00A9 = ©
                article += "<p> <br>\u00A9 Bits of Freedom <a href=\"https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.nl\">(CC BY-NC-SA 4.0)</a></p>";
                if (articleMainImageMetaData != null) {
                   // Log.e (TAG, "Metatdata: " + articleMainImageMetaData);
                    // Remove the prefix "Credits:" because we have  the text needs adjustment.
                    int offset = articleMainImageMetaData.indexOf("<br>");
                    // Log.e (TAG, "offset: " + offset);
                    if (offset > 0) {
                        articleMainImageMetaData = articleMainImageMetaData.substring(offset+4);
                    }
                    if ( ! articleMainImageMetaData.contains("Foto:")) {
                        articleMainImageMetaData = "Foto: " + articleMainImageMetaData;
                    }
                    article += "<p>" + articleMainImageMetaData + "</p>";
                }
            } else article = "";

            return article;
        }



        // Source of article is Privacy Barometer
        else if (link.toLowerCase().contains(privacyBarometer)) {
            String article;
            // Get the main image.
            Element tmpElement = doc.select(".foto_container img").first();
            String articleMainImageName = (tmpElement != null) ? tmpElement.attr("src") : null;
            // Sometimes the image is included within the article. Remove it, because we handle it separately.
            // The article is in div class="artikel"> ... </div>
            tmpElement = doc.select(".artikel").first();
            if (tmpElement != null) {  // Get text or make it null
                if (tmpElement.select(".foto_container").first() != null) {
                    tmpElement.select(".foto_container").first().remove();
                }
                article = tmpElement.html();
            } else article = null;
            // Get the meta-data and copyright info belonging to the main info.
            tmpElement = doc.select(".foto_metadata_container p").first();
            String articleMainImageMetaData = (tmpElement != null) ? tmpElement.html() : null;
            // In some places, the text needs adjustment.
            if ((articleMainImageMetaData != null) && ( ! articleMainImageMetaData.contains("Foto:"))) {
                articleMainImageMetaData = "Foto: " + articleMainImageMetaData;
            }

            // Combine the collected info.
            if (article != null) {
                if (articleMainImageName != null) {
                    article = "<img src=\"" + articleMainImageName + "\" alt=\"\">" + article;
                }
                // Add copyright notice \u00A9 = ©
                article += "<p> <br>\u00A9 Privacy Barometer <a href=\"https://creativecommons.org/licenses/by/4.0/\">(CC BY 4.0)</a></p>";
                if (articleMainImageMetaData != null) {
                    article += "<p>" + articleMainImageMetaData + "</p>";
                }
            } else article = "";

            return article;
        }



        // *** Source of article is Privacy First
        else if (link.toLowerCase().contains(privacyFirst)) {
            // Het artikel staat in div class="itemBody"><div class="itemFullText"> ... </div></div>
            Element tmpElement = doc.select(".itemBody .itemFullText").first();
            String article = (tmpElement != null) ? tmpElement.html() : null; // Get text or make it null
           // Log.e (TAG, "Ruwe tekst: " + article);

            // Do not fetch images! There are none or there are probably copyright issues.

            // Combine the collected info.
            if (article != null) {
                article += "<p> <br>\u00A9 Stichting Privacy First</p>";    // Add copyright notice \u00A9 = ©
            }
            else article = "";

            return article;
        }



        // *** Source of article is Vrijbit
        else if (link.toLowerCase().contains(vrijbit)) {
            // Het artikel staat in div class="itemBody"><div class="itemFullText"> ... </div></div>
            Element tmpElement1 = doc.select(".itemBody .itemIntroText").first();
            Element tmpElement2 = doc.select(".itemBody .itemFullText").first();
            String article = (tmpElement1 != null) ? tmpElement1.html() : null;
            if (tmpElement2 != null) article += tmpElement2.html();

            // Do not fetch images! There are none or there are probably copyright issues.

            // Combine the collected info.
            if (article != null) {
                article += "<p> <br>\u00A9 Vrijbit</p>"; // Add copyright notice \u00A9 = ©
            }
            else article = "";

            return article;
        }


        // *** Source of article is KDVP
        else if (link.toLowerCase().contains(kdvp)) {
            // Het artikel staat in div class="item_fulltext"> ... </div>
            Element tmpElement = doc.select(".item_fulltext").first();
            String article = (tmpElement != null) ? tmpElement.html() : null;

            // Do not fetch images! There are none or there are probably copyright issues.

            // Combine the collected info.
            if (article != null) {
                article += "<p> <br>\u00A9 KDVP</p>";   // Add copyright notice \u00A9 = ©
            }
            else article = "";

            return article;
        }





        // Vanwege copyright gebruiken we dit allen voor de sites van privacy organisaties.

        // We do not know the source. Use best-guess to extract article.
        else {
            // Gebruik de generieke methode om het artikel te vinden.
            // haal alle mogelijk relevante elementen uit de webpagina
            Collection<Element> nodes = getNodes(doc);

            // Weeg de verzamelde elementen. Die met de hoogste score is (waarschijnlijk) het artikel
            Element bestMatchElement = null;    // Hier komt straks het element (tag) met het artikel in te staan.
            int maxWeight = 0;
            for (Element entry : nodes) {
                int currentWeight = getWeight(entry, contentIndicator);
                if (currentWeight > maxWeight) {
                    maxWeight = currentWeight;
                    bestMatchElement = entry;   // Het meest waarschijnlijke element is degene met het hoogste gewicht.
                    if (maxWeight > 300) {
                        break;
                    }
                }
            }
            if (bestMatchElement != null) {
                return bestMatchElement.toString();
            }
        }
        return null;
    }

    /**
     * Weights current element. By matching it with positive candidates and
     * weighting child nodes. Since it's impossible to predict which exactly
     * names, ids or class names will be used in HTML, major role is played by
     * child nodes
     *
     * @param e                Element to weight, along with child nodes
     * @param contentIndicator a text which should be included into the extracted content, or null
     */
    private static int getWeight(Element e, String contentIndicator) {
        int weight = calcWeight(e);
        weight += (int) Math.round(e.ownText().length() / 100.0 * 10);
        weight += weightChildNodes(e, contentIndicator);
        return weight;
    }

    /**
     * Weights a child nodes of given Element. During tests some difficulties
     * were met. For instance, not every single document has nested paragraph
     * tags inside of the major article tag. Sometimes people are adding one
     * more nesting level. So, we're adding 4 points for every 100 symbols
     * contained in tag nested inside of the current weighted element, but only
     * 3 points for every element that's nested 2 levels deep. This way we give
     * more chances to extract the element that has less nested levels,
     * increasing probability of the correct extraction.
     *
     * @param rootEl           Element, who's child nodes will be weighted
     * @param contentIndicator a text which should be included into the extracted content, or null
     */
    private static int weightChildNodes(Element rootEl, String contentIndicator) {
        int weight = 0;
        Element caption = null;
        List<Element> pEls = new ArrayList<>(5);
        for (Element child : rootEl.children()) {
            String text = child.text();
            int textLength = text.length();
            if (textLength < 20) {
                continue;
            }

            if (contentIndicator != null && text.contains(contentIndicator)) {
                weight += 100; // We certainly found the item
            }

            String ownText = child.ownText();
            int ownTextLength = ownText.length();
            if (ownTextLength > 200) {
                weight += Math.max(50, ownTextLength / 10);
            }

            if (child.tagName().equals("h1") || child.tagName().equals("h2")) {
                weight += 30;
            } else if (child.tagName().equals("div") || child.tagName().equals("p")) {
                weight += calcWeightForChild(ownText);
                if (child.tagName().equals("p") && textLength > 50)
                    pEls.add(child);

                if (child.className().toLowerCase().equals("caption"))
                    caption = child;
            }
        }

        // use caption and image
        if (caption != null)
            weight += 30;

        if (pEls.size() >= 2) {
            for (Element subEl : rootEl.children()) {
                if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
                    weight += 20;
                    // headerEls.add(subEl);
                }
            }
        }
        return weight;
    }

    private static int calcWeightForChild(String text) {
        return text.length() / 25;
//		return Math.min(100, text.length() / ((child.getAllElements().size()+1)*5));
    }

    private static int calcWeight(Element e) {
        int weight = 0;
        if (POSITIVE.matcher(e.className()).find())
            weight += 35;

        if (POSITIVE.matcher(e.id()).find())
            weight += 40;

        if (UNLIKELY.matcher(e.className()).find())
            weight -= 20;

        if (UNLIKELY.matcher(e.id()).find())
            weight -= 20;

        if (NEGATIVE.matcher(e.className()).find())
            weight -= 50;

        if (NEGATIVE.matcher(e.id()).find())
            weight -= 50;

        String style = e.attr("style");
        if (style != null && !style.isEmpty() && NEGATIVE_STYLE.matcher(style).find())
            weight -= 50;
        return weight;
    }

    /**
     * Prepares document. Currently only stipping unlikely candidates, since
     * from time to time they're getting more score than good ones especially in
     * cases when major text is short.
     *
     * @param doc document to prepare. Passed as reference, and changed inside
     *            of function
     */
    private static void prepareDocument(Document doc) {
        // stripUnlikelyCandidates(doc);
        removeScriptsAndStyles(doc);
    }

    /**
     * Removes unlikely candidates from HTML. Currently takes id and class name
     * and matches them against list of patterns
     *
     * @param doc document to strip unlikely candidates from
     */
//    protected void stripUnlikelyCandidates(Document doc) {
//        for (Element child : doc.select("body").select("*")) {
//            String className = child.className().toLowerCase();
//            String id = child.id().toLowerCase();
//
//            if (NEGATIVE.matcher(className).find()
//                    || NEGATIVE.matcher(id).find()) {
//                child.remove();
//            }
//        }
//    }
    private static Document removeScriptsAndStyles(Document doc) {
        Elements scripts = doc.getElementsByTag("script");
        for (Element item : scripts) {
            item.remove();
        }

        Elements noscripts = doc.getElementsByTag("noscript");
        for (Element item : noscripts) {
            item.remove();
        }

        Elements styles = doc.getElementsByTag("style");
        for (Element style : styles) {
            style.remove();
        }

        return doc;
    }

    /**
     * Return first element from COLLECTION OF ELEMENTS that has className or null
     */
    private static Element getElementFromCollectionOfElementsByClassName(Collection<Element> els, String className) {
        Elements elsSelected=null;
        for (Element item : els) {
            elsSelected = item.getElementsByClass (className);
        }
        if (elsSelected != null) return elsSelected.iterator().next();
        else return null;
    }
    /**
     * Return first element from ELEMENT that has className or null
     */
    private static Element getElementFromElementByClassName(Element el, String className) {
        Elements elsSelected;
        elsSelected = el.getElementsByClass (className);
        if (elsSelected != null) return elsSelected.iterator().next();
        else return null;
    }


    /**
     * @return a set of all important nodes
     */
    private static Collection<Element> getNodes(Document doc) {
        Collection<Element> nodes = new HashSet<>(64);
        for (Element el : doc.select("body").select("*")) {
            if (NODES.matcher(el.tagName()).matches()) {
                nodes.add(el);
            }
        }
        return nodes;
    }
}
