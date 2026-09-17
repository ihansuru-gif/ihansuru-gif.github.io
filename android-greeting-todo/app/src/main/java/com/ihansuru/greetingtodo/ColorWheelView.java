package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {
    interface Listener { void onHue(float hue); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float hue = 232f;
    private Listener listener;

    public ColorWheelView(Context c) { super(c); init(); }
    public ColorWheelView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { setClickable(true); }
    void setHue(float h) { hue = normalize(h); invalidate(); }
    void setListener(Listener l) { listener = l; }

    @Override protected void onMeasure(int w, int h) {
        int desired = dp(172);
        int size = Math.min(resolveSize(desired, w), resolveSize(desired, h));
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float outer = Math.min(getWidth(), getHeight()) / 2f - dp(8);
        float stroke = dp(24), radius = outer - stroke / 2f;
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        for (int d = 0; d < 360; d += 3) {
            paint.setColor(Color.HSVToColor(new float[]{d, .82f, 1f}));
            canvas.drawArc(oval, d - 90, 3.6f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, Math.max(1, radius - stroke / 2 - dp(5)), paint);
        paint.setColor(Color.HSVToColor(new float[]{hue, .36f, .98f}));
        canvas.drawCircle(cx, cy, Math.max(dp(17), radius - stroke / 2 - dp(17)), paint);

        double a = Math.toRadians(hue - 90);
        float mx = cx + (float) Math.cos(a) * radius;
        float my = cy + (float) Math.sin(a) * radius;
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(dp(3), 0, dp(1), Color.argb(70, 0, 0, 0));
        canvas.drawCircle(mx, my, dp(8), paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(54, 74, 112));
        canvas.drawCircle(mx, my, dp(8), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
            if (Math.hypot(dx, dy) > Math.min(getWidth(), getHeight()) * .18) {
                hue = normalize((float) Math.toDegrees(Math.atan2(dy, dx)) + 90f);
                invalidate();
                if (listener != null) listener.onHue(hue);
            }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) { performClick(); return true; }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private float normalize(float v) { v %= 360f; return v < 0 ? v + 360f : v; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
