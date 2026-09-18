package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class NotePaperDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int baseColor;
    private final int borderColor;
    private final int pattern;
    private final float radius;

    NotePaperDrawable(Context context, int baseColor, int borderColor, int pattern, float radiusDp) {
        density = context.getResources().getDisplayMetrics().density;
        this.baseColor = baseColor;
        this.borderColor = borderColor;
        this.pattern = Math.max(0, Math.min(2, pattern));
        radius = radiusDp * density;
        fill.setStyle(Paint.Style.FILL);
        rule.setStyle(Paint.Style.STROKE);
        rule.setStrokeWidth(Math.max(1f, .75f * density));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, density));
    }

    @Override public void draw(Canvas canvas) {
        RectF r = new RectF(getBounds());
        fill.setColor(baseColor);
        canvas.drawRoundRect(r, radius, radius, fill);

        if (pattern != 0) {
            Path clip = new Path();
            clip.addRoundRect(r, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            rule.setColor(Color.argb(42, 80, 95, 120));
            float step = 24f * density;
            for (float y = r.top + step; y < r.bottom; y += step) {
                canvas.drawLine(r.left, y, r.right, y, rule);
            }
            if (pattern == 2) {
                for (float x = r.left + step; x < r.right; x += step) {
                    canvas.drawLine(x, r.top, x, r.bottom, rule);
                }
            }
            canvas.restore();
        }

        border.setColor(borderColor);
        canvas.drawRoundRect(r, radius, radius, border);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) { fill.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
