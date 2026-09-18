package com.ihansuru.greetingtodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class CalendarReminderManager {
    private CalendarReminderManager() {}

    static void schedule(Context context, CalendarStore.Event event) {
        cancel(context, event.id);
        if (event.reminderMinutes < 0) return;
        long at = CalendarStore.nextReminderMillis(event, System.currentTimeMillis());
        if (at <= 0L) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(context, event.id);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (RuntimeException ignored) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi); } catch (RuntimeException ignoredAgain) {}
        }
    }

    static void scheduleAll(Context context) {
        for (CalendarStore.Event e : CalendarStore.load(context)) schedule(context, e);
    }

    static void cancel(Context context, long id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try { am.cancel(pending(context, id)); } catch (RuntimeException ignored) {}
    }

    private static PendingIntent pending(Context context, long id) {
        Intent i = new Intent(context, CalendarReminderReceiver.class)
                .setAction("com.ihansuru.greetingtodo.CALENDAR_REMINDER")
                .putExtra("event_id", id);
        int request = (int) (id ^ (id >>> 32));
        return PendingIntent.getBroadcast(context, request, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
