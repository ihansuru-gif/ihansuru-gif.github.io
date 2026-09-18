package com.ihansuru.greetingtodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean accepted = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!accepted) return;
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            CalendarReminderManager.scheduleAll(context);
        }
        if (!Prefs.enabled(context)) return;
        Intent service = new Intent(context, WakeService.class).setAction(WakeService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
            else context.startService(service);
        } catch (RuntimeException ignored) {}
    }
}
