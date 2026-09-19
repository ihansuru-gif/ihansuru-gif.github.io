package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class BookishIconView extends View {
    static final int TODO = 1;
    static final int CALENDAR = 2;
    static final int MEMO = 3;
    static final int IMAGE = 4;
    static final int SETTINGS = 5;
    static final int UP = 6;
    static final int DOWN = 7;
    static final int EDIT = 8;
    static final int LOCK = 9;
    static final int PLUS = 10;
    static final int SEARCH = 11;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type;
    private int color = DesignTokens.INK;

    BookishIconView(Context context, int type) {
        super(context);
        this.type = type;
        setWillNotDraw(false);
    }

    void setIconType(int value) {
        type = value;
        invalidate();
    }

    void setIconColor(int value) {
        color = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float s = Math.min(w, h) * .58f;
        float l = cx - s / 2f;
        float t = cy - s / 2f;
        float r = cx + s / 2f;
        float b = cy + s / 2f;

        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.65f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (type == TODO) {
            canvas.drawRoundRect(new RectF(l, t, r, b), dp(2.5f), dp(2.5f), paint);
            canvas.drawLine(l + s*.18f, cy, l + s*.38f, cy + s*.18f, paint);
            canvas.drawLine(l + s*.38f, cy + s*.18f, l + s*.74f, cy - s*.18f, paint);
        } else if (type == CALENDAR) {
            canvas.drawRoundRect(new RectF(l, t + s*.12f, r, b), dp(2.5f), dp(2.5f), paint);
            canvas.drawLine(l, t + s*.37f, r, t + s*.37f, paint);
            canvas.drawLine(l + s*.24f, t, l + s*.24f, t + s*.24f, paint);
            canvas.drawLine(r - s*.24f, t, r - s*.24f, t + s*.24f, paint);
        } else if (type == MEMO) {
            canvas.drawRoundRect(new RectF(l, t, r, b), dp(2.5f), dp(2.5f), paint);
            canvas.drawLine(l + s*.20f, t + s*.30f, r - s*.18f, t + s*.30f, paint);
            canvas.drawLine(l + s*.20f, t + s*.50f, r - s*.28f, t + s*.50f, paint);
            canvas.drawLine(l + s*.20f, t + s*.70f, r - s*.40f, t + s*.70f, paint);
        } else if (type == IMAGE) {
            canvas.drawRoundRect(new RectF(l, t, r, b), dp(2.5f), dp(2.5f), paint);
            canvas.drawCircle(r - s*.25f, t + s*.28f, s*.08f, paint);
            path.reset();
            path.moveTo(l + s*.12f, b - s*.18f);
            path.lineTo(l + s*.42f, cy);
            path.lineTo(l + s*.60f, cy + s*.14f);
            path.lineTo(r - s*.12f, b - s*.30f);
            canvas.drawPath(path, paint);
        } else if (type == SETTINGS) {
            canvas.drawCircle(cx, cy, s*.20f, paint);
            for (int i=0;i<8;i++) {
                double a = Math.PI*2*i/8.0;
                float x1 = cx + (float)Math.cos(a)*s*.31f;
                float y1 = cy + (float)Math.sin(a)*s*.31f;
                float x2 = cx + (float)Math.cos(a)*s*.45f;
                float y2 = cy + (float)Math.sin(a)*s*.45f;
                canvas.drawLine(x1,y1,x2,y2,paint);
            }
        } else if (type == UP || type == DOWN) {
            float dir = type == UP ? -1f : 1f;
            canvas.drawLine(cx, cy - dir*s*.26f, cx, cy + dir*s*.24f, paint);
            canvas.drawLine(cx, cy + dir*s*.24f, cx - s*.20f, cy + dir*s*.03f, paint);
            canvas.drawLine(cx, cy + dir*s*.24f, cx + s*.20f, cy + dir*s*.03f, paint);
        } else if (type == EDIT) {
            canvas.drawLine(l + s*.15f, b - s*.12f, r - s*.10f, t + s*.18f, paint);
            canvas.drawLine(r - s*.10f, t + s*.18f, r - s*.24f, t + s*.04f, paint);
            canvas.drawLine(l + s*.15f, b - s*.12f, l + s*.11f, b - s*.30f, paint);
        } else if (type == LOCK) {
            canvas.drawRoundRect(new RectF(l + s*.12f, cy - s*.02f, r - s*.12f, b), dp(2), dp(2), paint);
            canvas.drawArc(new RectF(l + s*.28f, t, r - s*.28f, cy + s*.20f), 180, 180, false, paint);
        } else if (type == PLUS) {
            canvas.drawLine(cx, t + s*.18f, cx, b - s*.18f, paint);
            canvas.drawLine(l + s*.18f, cy, r - s*.18f, cy, paint);
        } else if (type == SEARCH) {
            canvas.drawCircle(cx - s*.08f, cy - s*.08f, s*.24f, paint);
            canvas.drawLine(cx + s*.10f, cy + s*.10f, r - s*.06f, b - s*.06f, paint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
