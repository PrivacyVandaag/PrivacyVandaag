# Privacy Vandaag
Modified general RSS-reader to follow some predefined feeds.
Forked from version 60 of the SpaRSS reader from Etuldan.

##Table of Contents
* [Summary](#summary)
* [Features](#features)
* [How to contribute ?](#how-to-contribute-)
* [Links](#links)
* [Hints for using this app for your own use](#hints-for-using-this-app-for-your-own-use)

## Summary
Privacy Vandaag is a light, modern, totally free (no ads) and opensource project which keeps you informed 
about developments on the privacy front in The Netherlands. The app follows actively seven sources, mainly privacy organisations, 
and notifies the user when new articles are published by those organisations. The articles are fetched and displayed 
in a mobile-optimized way. The project is based on the SpaRSS and Flym RSS reader.

The app has two release. One to distribute through Google Play store. The other through our own website. 
therefore it features its own update functionality including the possibility of service message.

## Features
* fully swipeable lists and articles-views
* quick open floating menu button 
* context menu for the feeds in the navigation menu (left drawer menu)
* immediately ready-for-use after installation. No tweaking necessary, all elements are included in the app.
* customize your notifications.
* switch between light or dark theme
* easy update for release distributed outside play store if a new version of the app becomes available. 
	User is notified through a service message.
* offline reading including images
* retrieve the full text of the feed when the content is truncated
* star your favorite entries
* search through your articles

## How to contribute ?
If you have any idea to improve Privacy Vandaag, feel free to add it [here](https://github.com/PrivacyVandaag/PrivacyVandaag/issues).  

## Links
GitHub project: https://github.com/PrivacyVandaag/PrivacyVandaag.
The app can be found in Google Play Store: https://play.google.com/store/apps/details?id=nl.privacybarometer.privacyvandaag.gplay


## Hints for using this app for your own use
Do you want to adapt this app to follow some interesting feeds of your own? Check where some major adjustments should be made:
* In /java/.../adapter/DrawerAdapter.java the navigation menu is defined.
* In /java/.../provider/FeedData.java the feeds that are to be followed are defined and added to the database.
* In /java/.../utils/ArticleTextExtractor.java the text of the full articles is cut out of the website. 
  For some websites this needs adjustments to get the cutting of the article right. Some special selections are made there for our use.
* In /res/values/strings.xml you can translate all the text used in the app
* In /res/drawable-xhdpi/ most of the icons are located. You can add or replace them by your own. 


Privacy Vandaag is a fork from [spaRSS](https://github.com/Etuldan/spaRSS)
