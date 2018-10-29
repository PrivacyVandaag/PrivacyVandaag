package nl.privacybarometer.privacyvandaag.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;

import nl.privacybarometer.privacyvandaag.R;
import nl.privacybarometer.privacyvandaag.activity.HomeActivity;


/**
 * Class to create notifications
 *
 * Here is the format we try to achieve: https://developer.android.com/training/notify-user/group
 *
 */
public class NotificationUtils extends ContextWrapper {
    private static final String TAG = NotificationUtils.class.getSimpleName() + " ~> ";
    public static final String NOTIFICATION_FEED_ID = "NotificationFeedId";
    private static final String NOTIFICATION_GROUP = "nl.privacyvandaag.NOTIFICATION_GROUP";

    private static int NOTIFICATION_SUMMARY_ID = 0; //use constant ID for notification used as group summary

    private static final String NOTIFICATION_CHANNEL_ID = "default";
    private static final String NOTIFICATION_CHANNEL_NAME = "Privacy Vandaag";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "Privacy Vandaag Artikelen";

    private ArrayList<NotificationData> allNotificationData = new ArrayList<>();


    // Constructor
    public NotificationUtils(Context context) {
        super(context);
        createChannel(); // Only necessary on Android 8 and later versions
    }


    // If we are on android 8 or higher, we need a channel ID.
    private void createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(NOTIFICATION_CHANNEL_DESCRIPTION);
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    // Create the (set of) notifications if necessary
    public void createNotifications (String feedId, ArrayList<NotificationData> notificationData, int logoId) {

        /*
        //test notifications
        Random r = new Random();
        int int1 = r.nextInt();
        String intStr = String.valueOf(int1);
        notificationData.add(new NotificationData(int1,"titel " + intStr,"Privacy Barometer","",logoId));

        r = new Random();
        int1 = r.nextInt();
        intStr = String.valueOf(int1);
        notificationData.add(new NotificationData(int1,"titel " + intStr,"Bits of Freedom","",logoId));
        // End test notifications */

        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_ENABLED, true) && notificationData != null) {

                // Store the complete list of notifications from each feed in order to create summary
                allNotificationData.addAll(notificationData);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

                // Loop through all new articles and create a notification for it.
                Notification message;
                for (NotificationData mNotification : notificationData) {
                    mNotification.feedLogo = logoId;    // add the logo of the feed to the item to be notified
                    message = buildNotification(feedId, mNotification);
                    if (message != null) {
                        notificationManager.notify(mNotification.itemId, message);
                    }
                }

                message = buildSummaryNotification(notificationData);
                notificationManager.notify(NOTIFICATION_SUMMARY_ID, message);
        }   // end if notifications are enabled
    }   // end method createNotifications


    /**
     * Build a summary notification message. This is the only notice on device with android SDK < 24
     */
    private Notification buildSummaryNotification(ArrayList<NotificationUtils.NotificationData> notificationData) {

        //String size = String.valueOf(notificationData.size());
        int size = allNotificationData.size();

        String text = getResources().getQuantityString(R.plurals.number_of_new_entries, size, size) + "Privacy Vandaag";

        // Create inbox-style summary notification
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        for (NotificationData nd : allNotificationData) {
            inboxStyle.addLine(nd.itemTitle);
        }
        inboxStyle.setBigContentTitle(text);
        inboxStyle.setSummaryText("nieuwe artikelen");

        // create the intent to open the article when notification is tapped.
        PendingIntent contentIntent = createPendingIntent(NOTIFICATION_SUMMARY_ID,"0");

        NotificationCompat.Builder notifBuilder = new
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                         .setContentIntent(contentIntent) //
                         .setSmallIcon(R.drawable.ic_statusbar_pv) //
                         .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) //
                         .setTicker(text) //
                         .setWhen(System.currentTimeMillis()) //
                         .setAutoCancel(true) //
                         .setContentTitle(getString(R.string.app_name)) //
                         //set content text to support devices running API level < 24
                         .setContentText(text)
                         //.setStyle(new NotificationCompat.BigTextStyle().bigText(text)) // allows for multiline text-message
                         .setStyle( inboxStyle )
                         .setLights(0xffffffff, 0, 0)
                         .setGroup(NOTIFICATION_GROUP)
                         //set this notification as the summary for the group
                         .setGroupSummary(true);

        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_VIBRATE, false)) {
            notifBuilder.setVibrate(new long[]{0, 1000});
        }

        String ringtone = PrefUtils.getString(PrefUtils.NOTIFICATIONS_RINGTONE, null);
        if (ringtone != null && ringtone.length() > 0) {
            notifBuilder.setSound(Uri.parse(ringtone));
        }

        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_LIGHT, false)) {
            notifBuilder.setLights(0xffffffff, 300, 1000);
        }
        return notifBuilder.build();

    }

    /**
     * Build one notification message
     */
    public Notification buildNotification (String feedId, NotificationData notificationData) {
        // Bepaal de ID van de feed. Dat wordt ook de ID van de melding. Elke feed krijgt eigen melding.
        int mNotificationId = 99;
        if (notificationData.itemId != 0) {
            mNotificationId = notificationData.itemId;
        }
        // create the intent to open the article when notification is tapped.
        PendingIntent contentIntent = createPendingIntent(mNotificationId,feedId);
        int icon = (notificationData.feedLogo != 0 ) ? notificationData.feedLogo : R.drawable.logo_icon_pv;

        // TODO: Use image from article when downloaded instead of main organisation logo.

        return new
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setSmallIcon(icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), icon))
                .setContentTitle(notificationData.itemTitle)
                .setContentText(notificationData.itemFeedName)
                .setGroup(NOTIFICATION_GROUP)
                .build();
    }

    /**
     * Each notification has a pending intent, i.e. if the users taps the notification, the app
     * should open and display the right article.
     * The pending intent contains the right information to complete this action.
     */
    private PendingIntent createPendingIntent(int mNotificationId, String feedId) {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        notificationIntent.putExtra(NOTIFICATION_FEED_ID, feedId);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        // mNotificationId = ID of the pendingIntent. So when an update is done, it only affects
        // the right notification. FLAG_UPDATE_CURRENT is nodig omdat anders de oude waarden blijven staan.
        return PendingIntent.getActivity(this, mNotificationId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    /**
     *     Data class to hold all information for a notification in the NotificationDataList
     */
    public static class NotificationData {
        public int itemId;
        String itemTitle;
        String itemFeedName;
        String itemMainImageUrl;
        int feedLogo;

        // Main constructor
        public NotificationData  (int id, String title, String feedName, String imgUrl, int logo) {
            itemId = id;
            itemTitle = title;
            itemFeedName = feedName;
            itemMainImageUrl = imgUrl;
            feedLogo = logo;
        }
    }



}