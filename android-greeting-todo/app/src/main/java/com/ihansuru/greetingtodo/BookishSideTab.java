package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BookishSideTab extends LinearLayout {
    private final String key;
    private final BookishIconView icon;
    private final TextView label;

    BookishSideTab(Context context, String key) {
        super(context);
        this.key = key;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(3), dp(6), dp(3), dp(6));
        setClickable(true);
        setFocusable(true);

        icon = new BookishIconView(context, iconType(key));
        icon.setIconColor(textColor(key));
        addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        label = new TextView(context);
        label.setText(label(key));
        label.setTextSize(10.5f);
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        label.setTextColor(textColor(key));
        label.setGravity(Gravity.CENTER);
        addView(label, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(22)));

        applySurface(false);
        setContentDescription(label(key) + " 띠지");
    }

    void setSelectedState(boolean selected) {
        applySurface(selected);
        setScaleX(selected ? 1.05f : 1f);
    }

    private void applySurface(boolean selected) {
        int accent = DesignTokens.accent(key);
        int fill = selected
                ? DesignTokens.blend(DesignTokens.soft(key), accent, .10f)
                : DesignTokens.soft(key);
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadii(new float[]{dp(12),dp(12),0,0,0,0,dp(12),dp(12)});
        d.setStroke(dp(1), DesignTokens.alpha(accent, selected ? 120 : 70));
        setBackground(d);
        setElevation(dp(selected ? 4 : 1));
    }

    private int iconType(String key) {
        if ("todo".equals(key)) return BookishIconView.TODO;
        if ("calendar".equals(key)) return BookishIconView.CALENDAR;
        if ("memo".equals(key)) return BookishIconView.MEMO;
        return BookishIconView.IMAGE;
    }

    private String label(String key) {
        if ("todo".equals(key)) return "투두";
        if ("calendar".equals(key)) return "일정";
        if ("memo".equals(key)) return "메모";
        return "이미지";
    }

    private int textColor(String key) {
        return DesignTokens.blend(DesignTokens.accent(key), DesignTokens.INK, .35f);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
