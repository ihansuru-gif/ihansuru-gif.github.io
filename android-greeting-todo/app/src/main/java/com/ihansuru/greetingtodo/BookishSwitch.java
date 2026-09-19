package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class BookishSwitch extends View {
    interface Listener { void onChanged(boolean checked); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private boolean enabled = true;
    private int accent = DesignTokens.TODO;
    private Listener listener;

    BookishSwitch(Context context) {
        super(context);
        setClickable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("토글");
    }

    void setAccent(int color) {
        accent = color;
        invalidate();
    }

    void setChecked(boolean value) {
        checked = value;
        invalidate();
    }

    boolean isChecked() { return checked; }

    void setListener(Listener value) { listener = value; }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        super.setEnabled(value);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(46), dp(28));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = dp(2);
        float radius = h / 2f;

        int track = checked ? accent : Color.rgb(214, 213, 210);
        if (!enabled) track = DesignTokens.blend(track, DesignTokens.PAPER, .55f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(track);
        canvas.drawRoundRect(new RectF(0,0,w,h), radius, radius, paint);

        float knob = h - pad*2;
        float cx = checked ? w - pad - knob/2f : pad + knob/2f;
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(dp(2.2f), 0, dp(.7f), Color.argb(42,0,0,0));
        canvas.drawCircle(cx, h/2f, knob/2f, paint);
        paint.clearShadowLayer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            checked = !checked;
            invalidate();
            performClick();
            if (listener != null) listener.onChanged(checked);
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
