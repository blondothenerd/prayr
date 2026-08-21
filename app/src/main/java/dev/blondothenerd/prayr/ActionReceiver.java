package dev.blondothenerd.prayr;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles Done, Snooze and driving Mute directly from notifications. */
public final class ActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DONE = "dev.blondothenerd.prayr.DONE";
    public static final String ACTION_SNOOZE = "dev.blondothenerd.prayr.SNOOZE";
    public static final String ACTION_MUTE = "dev.blondothenerd.prayr.MUTE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String prayerId = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_ID);
        if (ACTION_MUTE.equals(intent.getAction())) {
            AppSettings.setDrivingMuted(context, true);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancelAll();
            return;
        }
        if (prayerId == null) return;
        if (ACTION_DONE.equals(intent.getAction())) PrayerStore.markPrayed(context, prayerId, true);
        if (ACTION_SNOOZE.equals(intent.getAction())) NotificationScheduler.scheduleSnooze(context, prayerId);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(6000 + Math.abs(prayerId.hashCode() % 100000));
    }
}
