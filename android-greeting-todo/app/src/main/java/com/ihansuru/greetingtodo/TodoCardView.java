package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoCardView extends View {
    interface HeaderTapListener { void onHeaderTap(); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private int headerHeight;
    private int rowHeight;
    private int pad;
    private boolean editorMode;
    private HeaderTapListener headerTapListener;

    public TodoCardView(Context context) { super(context); init(); }
    public TodoCardView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        headerHeight = dp(96);
        rowHeight = dp(44);
        pad = dp(18);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
    }

    void setEditorMode(boolean value) { editorMode = value; }
    void setHeaderTapListener(HeaderTapListener listener) { headerTapListener = listener; }
    void refresh() { requestLayout(); invalidate(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) width = dp(310);
        int count = Math.max(1, Prefs.items(getContext()).size());
        int desired = headerHeight + dp(18) + count * rowHeight + dp(12);
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = dp(23);

        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, w, h, paint);

        int accent = Prefs.todoColor(getContext());
        paint.setColor(accent);
        canvas.drawRect(0, 0, w, headerHeight + dp(10), paint);

        Path body = new Path();
        float y = headerHeight - dp(13);
        body.moveTo(0, y + dp(7));
        body.cubicTo(w * .27f, y + dp(25), w * .66f, y - dp(18), w, y + dp(3));
        body.lineTo(w, h);
        body.lineTo(0, h);
        body.close();
        paint.setColor(Color.argb(252, 255, 255, 255));
        canvas.drawPath(body, paint);

        drawHeader(canvas, w, accent);
        drawRows(canvas, w);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(24, 36, 47, 68));
        canvas.drawRoundRect(new RectF(.5f, .5f, w - .5f, h - .5f), r, r, paint);
        canvas.restore();
    }

    private void drawHeader(Canvas canvas, int w, int accent) {
        String date = new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date());
        int fg = contrast(accent);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(sp(20));
        text.setColor(fg);
        canvas.drawText(date, pad, dp(36), text);

        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        text.setTextSize(sp(12.5f));
        text.setColor(alpha(fg, 178));
        canvas.drawText("오늘 일정 " + Prefs.items(getContext()).size() + "개", pad, dp(60), text);

        float cx = w - dp(28);
        float cy = dp(31);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(alpha(fg, 184));
        canvas.drawCircle(cx, cy, dp(5.5f), paint);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8.0;
            canvas.drawLine(cx + (float) Math.cos(a) * dp(9), cy + (float) Math.sin(a) * dp(9),
                    cx + (float) Math.cos(a) * dp(13), cy + (float) Math.sin(a) * dp(13), paint);
        }
    }

    private void drawRows(Canvas canvas, int w) {
        List<String> items = Prefs.items(getContext());
        List<String> cats = Prefs.categories(getContext());
        float start = headerHeight + dp(5);
        float checkX = pad + dp(1);
        float textX = pad + dp(29);
        for (int i = 0; i < items.size(); i++) {
            float top = start + i * rowHeight;
            float cy = top + rowHeight * .53f;
            boolean checked = Prefs.checked(getContext(), i);
            if (i > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1);
                paint.setColor(Color.argb(16, 40, 54, 78));
                canvas.drawLine(textX, top, w - pad, top, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? Color.rgb(66, 139, 255) : Color.WHITE);
            canvas.drawCircle(checkX, cy, dp(8.5f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(checked ? Color.rgb(66, 139, 255) : Color.rgb(190, 200, 214));
            canvas.drawCircle(checkX, cy, dp(8.5f), paint);
            if (checked) {
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(dp(1.9f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(checkX - dp(3.6f), cy, checkX - dp(.8f), cy + dp(2.8f), paint);
                canvas.drawLine(checkX - dp(.8f), cy + dp(2.8f), checkX + dp(4.8f), cy - dp(3.8f), paint);
                paint.setStrokeCap(Paint.Cap.BUTT);
            }

            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            text.setTextSize(sp(14));
            text.setColor(checked ? Color.rgb(145, 152, 165) : Color.rgb(39, 49, 66));
            text.setStrikeThruText(checked);
            String title = ellipsize(items.get(i), text, w - textX - dp(78));
            canvas.drawText(title, textX, cy + dp(5), text);
            text.setStrikeThruText(false);

            drawPill(canvas, w - pad - dp(24), cy, i < cats.size() ? cats.get(i) : "업무");
        }
    }

    private void drawPill(Canvas canvas, float cx, float cy, String category) {
        int bg, fg;
        if ("개인".equals(category)) { bg = Color.rgb(220, 247, 235); fg = Color.rgb(54, 135, 99); }
        else if ("기타".equals(category)) { bg = Color.rgb(255, 237, 217); fg = Color.rgb(169, 106, 51); }
        else { bg = Color.rgb(238, 232, 255); fg = Color.rgb(109, 83, 175); }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        RectF r = new RectF(cx - dp(24), cy - dp(10), cx + dp(24), cy + dp(10));
        canvas.drawRoundRect(r, dp(10), dp(10), paint);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(sp(10));
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(fg);
        canvas.drawText(category, cx, cy + dp(3.5f), text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editorMode) return false;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float start = headerHeight + dp(5);
            if (event.getY() < start) {
                if (headerTapListener != null) headerTapListener.onHeaderTap();
                performClick();
                return true;
            }
            int index = (int) ((event.getY() - start) / rowHeight);
            if (index >= 0 && index < Prefs.items(getContext()).size()) {
                Prefs.toggleChecked(getContext(), index);
                invalidate();
                performClick();
                return true;
            }
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private String ellipsize(String value, Paint p, float max) {
        if (p.measureText(value) <= max) return value;
        int n = value.length();
        while (n > 0 && p.measureText(value.substring(0, n) + "…") > max) n--;
        return value.substring(0, Math.max(0, n)) + "…";
    }
    private int contrast(int color) {
        double y = .299 * Color.red(color) + .587 * Color.green(color) + .114 * Color.blue(color);
        return y > 188 ? Color.rgb(36, 47, 70) : Color.WHITE;
    }
    private int alpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
