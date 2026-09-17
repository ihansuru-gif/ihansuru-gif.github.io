package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class LockTodoWidget extends FrameLayout {
    interface Callback {
        void onComplete(int index);
        void onGear();
        void onAdd(String text);
        void onInteractionChanged(boolean active);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final ArrayList<RectF> completeRects = new ArrayList<>();
    private final RectF gearRect = new RectF();

    private EditText quickInput;
    private TextView plusButton;
    private Callback callback;

    private float uiScale = 1f;
    private float textScale = 1f;
    private int headerHeight;
    private int rowHeight;
    private int inputHeight;
    private int pad;
    private int gap;
    private int cornerRadius;
    private float downX;
    private float downY;

    LockTodoWidget(Context context) {
        super(context);
        init();
    }

    LockTodoWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        quickInput = new EditText(getContext());
        quickInput.setSingleLine(true);
        quickInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        quickInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        quickInput.setHint("할 일 입력…");
        quickInput.setTextColor(Color.rgb(38, 48, 67));
        quickInput.setHintTextColor(Color.rgb(160, 168, 182));
        quickInput.setPadding(dp(13), 0, dp(10), 0);
        quickInput.setBackground(rounded(Color.rgb(248, 249, 253), dp(14), Color.rgb(229, 233, 240), dp(1)));
        quickInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (callback != null) callback.onInteractionChanged(hasFocus);
        });
        quickInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitQuickInput();
                return true;
            }
            return false;
        });
        addView(quickInput);

        plusButton = new TextView(getContext());
        plusButton.setText("+");
        plusButton.setGravity(Gravity.CENTER);
        plusButton.setTextColor(Color.WHITE);
        plusButton.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        plusButton.setBackground(rounded(Color.rgb(97, 111, 248), dp(14), Color.TRANSPARENT, 0));
        plusButton.setElevation(dp(2));
        plusButton.setOnClickListener(v -> submitQuickInput());
        addView(plusButton);
    }

    void setCallback(Callback value) {
        callback = value;
    }

    void refresh() {
        uiScale = Prefs.todoScale(getContext());
        textScale = Prefs.textScale(getContext());
        quickInput.setTextSize(14f * textScale);
        plusButton.setTextSize(28f * textScale);
        requestLayout();
        invalidate();
    }

    void clearInputFocus() {
        quickInput.clearFocus();
    }

    private void submitQuickInput() {
        String value = quickInput.getText().toString().trim();
        if (value.isEmpty()) return;
        if (callback != null) callback.onAdd(value);
        quickInput.setText("");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) width = dp(330);

        uiScale = Prefs.todoScale(getContext());
        textScale = Prefs.textScale(getContext());
        headerHeight = dp(112 * uiScale);
        rowHeight = dp(54 * uiScale);
        inputHeight = dp(46 * uiScale);
        pad = dp(17 * uiScale);
        gap = dp(10 * uiScale);
        cornerRadius = dp(26 * uiScale);

        int count = Prefs.items(getContext()).size();
        int rowsHeight = count == 0 ? dp(68 * uiScale) : count * rowHeight;
        int desiredHeight = headerHeight + dp(16 * uiScale) + rowsHeight + gap + inputHeight + pad;
        int resolved = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, resolved);

        int plusSize = inputHeight;
        int quickWidth = Math.max(dp(120), width - pad * 2 - plusSize - gap);
        quickInput.measure(
                MeasureSpec.makeMeasureSpec(quickWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(inputHeight, MeasureSpec.EXACTLY));
        plusButton.measure(
                MeasureSpec.makeMeasureSpec(plusSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(plusSize, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int plusSize = inputHeight;
        int inputTop = height - pad - inputHeight;
        int inputRight = width - pad - plusSize - gap;
        quickInput.layout(pad, inputTop, inputRight, inputTop + inputHeight);
        plusButton.layout(width - pad - plusSize, inputTop, width - pad, inputTop + plusSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        clipPath.reset();
        clipPath.addRoundRect(new RectF(0, 0, w, h), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(dp(14), 0, dp(6), Color.argb(40, 38, 50, 82));
        canvas.drawRoundRect(new RectF(dp(1), dp(1), w - dp(1), h - dp(1)), cornerRadius, cornerRadius, paint);
        paint.clearShadowLayer();

        int accent = Prefs.todoColor(getContext());
        paint.setColor(accent);
        canvas.drawRect(0, 0, w, headerHeight + dp(18 * uiScale), paint);

        Path body = new Path();
        float waveY = headerHeight - dp(13 * uiScale);
        body.moveTo(0, waveY + dp(6 * uiScale));
        body.cubicTo(
                w * .26f, waveY + dp(27 * uiScale),
                w * .67f, waveY - dp(18 * uiScale),
                w, waveY + dp(3 * uiScale));
        body.lineTo(w, h);
        body.lineTo(0, h);
        body.close();
        paint.setColor(Color.WHITE);
        canvas.drawPath(body, paint);

        drawHeader(canvas, w, accent);
        drawRows(canvas, w, h);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(24, 35, 49, 76));
        canvas.drawRoundRect(new RectF(.5f, .5f, w - .5f, h - .5f), cornerRadius, cornerRadius, paint);
        canvas.restore();
    }

    private void drawHeader(Canvas canvas, int w, int accent) {
        String date = new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date());
        List<String> items = Prefs.items(getContext());
        String weather = Prefs.weatherSummary(getContext());
        String sub = "오늘 일정 " + items.size() + "개";
        if (!weather.isEmpty()) sub += "  ·  " + weather;

        int fg = contrast(accent);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sp(22f * textScale * uiScale));
        textPaint.setColor(fg);
        canvas.drawText(date, pad, dp(42 * uiScale), textPaint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(sp(13.5f * textScale * uiScale));
        textPaint.setColor(alpha(fg, 186));
        canvas.drawText(sub, pad, dp(72 * uiScale), textPaint);

        float cx = w - pad - dp(18 * uiScale);
        float cy = dp(38 * uiScale);
        drawGear(canvas, cx, cy, fg, accent);
        float hit = dp(27 * uiScale);
        gearRect.set(cx - hit, cy - hit, cx + hit, cy + hit);
    }

    private void drawRows(Canvas canvas, int w, int h) {
        List<String> items = Prefs.items(getContext());
        List<String> categories = Prefs.categories(getContext());
        completeRects.clear();

        float startY = headerHeight + dp(11 * uiScale);
        float bottomLimit = h - pad - inputHeight - gap;

        if (items.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(sp(15f * textScale * uiScale));
            textPaint.setColor(Color.rgb(111, 121, 139));
            float centerY = startY + Math.max(dp(34), (bottomLimit - startY) / 2f);
            canvas.drawText("오늘 할 일 끝!", w / 2f, centerY, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            float top = startY + i * rowHeight;
            float cy = top + rowHeight * .50f;
            if (top + rowHeight > bottomLimit + dp(4)) break;

            if (i > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(.75f));
                paint.setColor(Color.argb(18, 37, 49, 69));
                canvas.drawLine(pad, top, w - pad, top, paint);
            }

            float completeSize = dp(34 * uiScale);
            float completeCx = w - pad - completeSize / 2f;
            float pillW = dp(56 * uiScale);
            float pillCx = completeCx - completeSize / 2f - dp(8 * uiScale) - pillW / 2f;
            float textLeft = pad;
            float textRight = pillCx - pillW / 2f - dp(10 * uiScale);

            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setTextSize(sp(15.2f * textScale * uiScale));
            textPaint.setTextColor(Color.rgb(36, 46, 64));
            textPaint.setTextAlign(Paint.Align.LEFT);
            String title = ellipsize(items.get(i), textPaint, Math.max(dp(56), textRight - textLeft));
            canvas.drawText(title, textLeft, cy + dp(5 * uiScale), textPaint);

            String category = i < categories.size() ? categories.get(i) : "업무";
            drawPill(canvas, pillCx, cy, pillW, category);
            drawCompleteButton(canvas, completeCx, cy, completeSize);

            completeRects.add(new RectF(
                    completeCx - completeSize * .72f,
                    cy - completeSize * .72f,
                    completeCx + completeSize * .72f,
                    cy + completeSize * .72f));
        }
    }

    private void drawPill(Canvas canvas, float cx, float cy, float width, String category) {
        int bg;
        int fg;
        if ("개인".equals(category)) {
            bg = Color.rgb(220, 247, 235);
            fg = Color.rgb(49, 139, 100);
        } else if ("기타".equals(category)) {
            bg = Color.rgb(255, 237, 218);
            fg = Color.rgb(171, 108, 51);
        } else {
            bg = Color.rgb(239, 233, 255);
            fg = Color.rgb(104, 82, 171);
        }
        float halfH = dp(12 * uiScale);
        RectF rect = new RectF(cx - width / 2f, cy - halfH, cx + width / 2f, cy + halfH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        canvas.drawRoundRect(rect, halfH, halfH, paint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sp(10.8f * textScale * uiScale));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(fg);
        canvas.drawText(category, cx, cy + dp(4 * uiScale), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCompleteButton(Canvas canvas, float cx, float cy, float size) {
        float r = size / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(248, 250, 253));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.rgb(201, 210, 223));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStrokeWidth(dp(2.1f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(80, 145, 246));
        canvas.drawLine(cx - r * .42f, cy, cx - r * .10f, cy + r * .30f, paint);
        canvas.drawLine(cx - r * .10f, cy + r * .30f, cx + r * .48f, cy - r * .36f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawGear(Canvas canvas, float cx, float cy, int fg, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(alpha(fg, 230));
        canvas.save();
        canvas.translate(cx, cy);
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f);
            canvas.drawRoundRect(
                    new RectF(-dp(2.4f * uiScale), -dp(14 * uiScale), dp(2.4f * uiScale), -dp(8.5f * uiScale)),
                    dp(1.8f), dp(1.8f), paint);
            canvas.restore();
        }
        canvas.drawCircle(0, 0, dp(9.8f * uiScale), paint);
        paint.setColor(accent);
        canvas.drawCircle(0, 0, dp(4.1f * uiScale), paint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (dx * dx + dy * dy > dp(12) * dp(12)) return true;

            if (gearRect.contains(event.getX(), event.getY())) {
                if (callback != null) callback.onGear();
                performClick();
                return true;
            }
            for (int i = 0; i < completeRects.size(); i++) {
                if (completeRects.get(i).contains(event.getX(), event.getY())) {
                    if (callback != null) callback.onComplete(i);
                    performClick();
                    return true;
                }
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private String ellipsize(String value, Paint p, float maxWidth) {
        if (p.measureText(value) <= maxWidth) return value;
        int end = value.length();
        while (end > 0 && p.measureText(value.substring(0, end) + "…") > maxWidth) end--;
        return value.substring(0, Math.max(0, end)) + "…";
    }

    private int contrast(int color) {
        double luma = .299 * Color.red(color) + .587 * Color.green(color) + .114 * Color.blue(color);
        return luma > 190 ? Color.rgb(42, 51, 76) : Color.WHITE;
    }

    private int alpha(int color, int value) {
        return Color.argb(value, Color.red(color), Color.green(color), Color.blue(color));
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
