package dev.blondothenerd.prayr;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.time.LocalTime;

/** Receives alarms and displays the next eligible prayer or praise item. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "prayer_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (NotificationScheduler.ACTION_PLAN_DAY.equals(intent.getAction())) {
            NotificationScheduler.scheduleToday(context, true);
            NotificationScheduler.scheduleNextPlanner(context);
            return;
        }

        boolean custom = NotificationScheduler.ACTION_CUSTOM.equals(intent.getAction()) || intent.getBooleanExtra(NotificationScheduler.EXTRA_CUSTOM, false);
        String requestedId = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_ID);
        if (custom && requestedId != null) NotificationScheduler.scheduleNextCustomAfterFire(context, requestedId);
        if (AppSettings.drivingMuted(context) || AppSettings.isWithinDnd(context, LocalTime.now())) return;

        PrayerStore.resetIfNeeded(context);
        Prayer prayer = requestedId == null ? PrayerStore.nextUnprayed(context, AppSettings.selectionMode(context)) : PrayerStore.find(context, requestedId);
        if (prayer == null || prayer.healed || prayer.noReminder || (!custom && prayer.prayed)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel(context, manager);

        int notificationId = 6000 + Math.abs(prayer.id.hashCode() % 100000);
        Intent open = new Intent(context, MainActivity.class)
            .putExtra(NotificationScheduler.EXTRA_PRAYER_ID, prayer.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent donePending = actionPending(context, prayer.id, ActionReceiver.ACTION_DONE, notificationId + 1);
        String reason = prayer.reason.trim().isEmpty() ? "Take a quiet moment for them." : prayer.reason.trim();
        String title = prayer.isPraise() ? "Praise for " + prayer.name : "Pray for " + prayer.name;

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(AppSettings.primaryColor(context))
            .setContentTitle(title)
            .setContentText(reason)
            .setStyle(new Notification.BigTextStyle().bigText(reason))
            .setContentIntent(openPending)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(0, "Done", donePending).build());

        if (AppSettings.drivingMode(context)) {
            PendingIntent mutePending = actionPending(context, prayer.id, ActionReceiver.ACTION_MUTE, notificationId + 3);
            builder.addAction(new Notification.Action.Builder(0, "Mute reminders", mutePending).build());
        } else {
            PendingIntent snoozePending = actionPending(context, prayer.id, ActionReceiver.ACTION_SNOOZE, notificationId + 2);
            builder.addAction(new Notification.Action.Builder(0, "Remind me in " + AppSettings.snoozeMinutes(context) + " min", snoozePending).build());
        }
        manager.notify(notificationId, builder.build());
    }

    private PendingIntent actionPending(Context context, String prayerId, String action, int requestCode) {
        Intent intent = new Intent(context, ActionReceiver.class)
            .setAction(action)
            .setData(Uri.parse("prayr://action/" + action.substring(action.lastIndexOf('.') + 1) + "/" + prayerId))
            .putExtra(NotificationScheduler.EXTRA_PRAYER_ID, prayerId);
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }
}
