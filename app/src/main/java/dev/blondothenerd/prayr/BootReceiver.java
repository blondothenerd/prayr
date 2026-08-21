package dev.blondothenerd.prayr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores all reminder plans after a restart, app update or clock change. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationScheduler.refresh(context);
    }
}
