package com.ihansuru.greetingtodo;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserManager;

public class WakeService extends Service {
    static final String ACTION_START = "com.ihansuru.greetingtodo.START";
    static final String ACTION_STOP = "com.ihansuru.greetingtodo.STOP";
    private static final String CHANNEL = "greeting_todo_monitor";
    private static final int NOTIFICATION_ID = 4107;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private boolean registered;
    private boolean shownThisWake;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        registerScreenEvents();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action) || !Prefs.enabled(this)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (!startForegroundSafe()) {
            Prefs.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        OverlayManager.hide(this);
        if (registered && receiver != null) {
            try { unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        }
        registered = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void registerScreenEvents() {
        if (registered) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || !Prefs.enabled(WakeService.this)) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    shownThisWake = false;
                    handler.removeCallbacksAndMessages(null);
                    OverlayManager.hide(WakeService.this);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    handler.postDelayed(() -> tryShow(), 240);
                } else if (Intent.ACTION_USER_PRESENT.equals(action)
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Intent.ACTION_USER_UNLOCKED.equals(action))) {
                    handler.postDelayed(() -> tryShow(), 120);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) filter.addAction(Intent.ACTION_USER_UNLOCKED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        registered = true;
    }

    private void tryShow() {
        if (shownThisWake || !Prefs.enabled(this) || !interactive() || !userUnlocked() || locked()) return;
        if (OverlayManager.show(this)) shownThisWake = true;
    }

    private boolean locked() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }
    private boolean interactive() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }
    private boolean userUnlocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true;
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        return um != null && um.isUserUnlocked();
    }

    private boolean startForegroundSafe() {
        try {
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop = PendingIntent.getService(this, 1,
                    new Intent(this, WakeService.class).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
            Notification n = b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("인사앱 실행 중")
                    .setContentText("화면을 켜거나 잠금 해제할 때 표시할 준비를 하고 있어요")
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "끄기", stop).build())
                    .build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, n);
            return true;
        } catch (RuntimeException e) { return false; }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "인사앱 화면 감지", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("화면을 켜거나 잠금 해제했을 때 설정한 이미지와 투두를 잠깐 표시합니다.");
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private void shutdown() {
        Prefs.setEnabled(this, false);
        handler.removeCallbacksAndMessages(null);
        OverlayManager.hide(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }
}
