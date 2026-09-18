package com.ihansuru.greetingtodo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class CalendarReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "greeting_calendar_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("event_id", -1L);
        CalendarStore.Event event = CalendarStore.get(context, id);
        if (event == null) return;

        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            CalendarReminderManager.schedule(context, event);
            return;
        }

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "일정 알림", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("인사앱 일정 시작 전 알림");
            nm.createNotificationChannel(ch);
        }

        PendingIntent open = PendingIntent.getActivity(
                context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        Notification n = b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(event.title == null || event.title.trim().isEmpty() ? "일정 알림" : event.title)
                .setContentText(CalendarStore.formatDay(event.startDay)
                        + (event.allDay ? "" : " " + CalendarStore.minuteLabel(event.startMinute)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        nm.notify((int) (id ^ (id >>> 32)), n);

        CalendarReminderManager.schedule(context, event);
    }
}
