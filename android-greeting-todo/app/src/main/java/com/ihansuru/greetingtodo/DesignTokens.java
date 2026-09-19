package com.ihansuru.greetingtodo;

import android.graphics.Color;

final class DesignTokens {
    static final int PAPER = Color.rgb(247, 245, 241);
    static final int SURFACE = Color.rgb(255, 255, 255);
    static final int SURFACE_SOFT = Color.rgb(250, 249, 246);
    static final int INK = Color.rgb(41, 43, 49);
    static final int SECONDARY = Color.rgb(116, 119, 127);
    static final int MUTED = Color.rgb(164, 164, 168);
    static final int BORDER = Color.rgb(226, 222, 216);

    static final int TODO = Color.rgb(115, 150, 216);
    static final int CALENDAR = Color.rgb(153, 133, 212);
    static final int MEMO = Color.rgb(211, 154, 118);
    static final int IMAGE = Color.rgb(127, 168, 148);

    static final int TODO_SOFT = Color.rgb(238, 244, 254);
    static final int CALENDAR_SOFT = Color.rgb(244, 240, 253);
    static final int MEMO_SOFT = Color.rgb(252, 241, 233);
    static final int IMAGE_SOFT = Color.rgb(237, 246, 241);

    static final int DANGER = Color.rgb(181, 78, 74);

    private DesignTokens() {}

    static int accent(String key) {
        if ("todo".equals(key)) return TODO;
        if ("calendar".equals(key)) return CALENDAR;
        if ("memo".equals(key)) return MEMO;
        return IMAGE;
    }

    static int soft(String key) {
        if ("todo".equals(key)) return TODO_SOFT;
        if ("calendar".equals(key)) return CALENDAR_SOFT;
        if ("memo".equals(key)) return MEMO_SOFT;
        return IMAGE_SOFT;
    }

    static int blend(int a, int b, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(a) * (1f - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1f - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t));
    }

    static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
