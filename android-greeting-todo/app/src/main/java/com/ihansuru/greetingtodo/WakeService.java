package com.ihansuru.greetingtodo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class WakeService extends Service {
    static final String ACTION_START = "com.ihansuru.greetingtodo.START";
    static final String ACTION_STOP = "com.ihansuru.greetingtodo.STOP";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
