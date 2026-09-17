package com.ihansuru.greetingtodo;

import android.content.Context;
import android.provider.Settings;

final class OverlayManager {
    private OverlayManager() {}

    static boolean show(Context context) {
        Context app = context.getApplicationContext();
        if (!Settings.canDrawOverlays(app)) return false;
        return LockOverlayActivity.launch(app);
    }

    static void hide(Context context) {
        LockOverlayActivity.closeActive();
    }

    static boolean isVisible() {
        return LockOverlayActivity.isVisibleNow();
    }
}
