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
    private final ArrayList<RectF> categoryRects = new ArrayList<>();
    private final ArrayList<RectF> rowRects = new ArrayList<>();
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

    private Runnable reorderArm;
    private int pendingReorder = -1;
    private int activeReorder = -1;
    private boolean reordering;

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
        quickInput.setHint("할 일을 추가하세요");
        quickInput.setTextColor(DesignTokens.INK);
        quickInput.setHintTextColor(DesignTokens.MUTED);
        quickInput.setPadding(dp(13), 0, dp(10), 0);
        quickInput.setBackground(rounded(
                DesignTokens.SURFACE_SOFT, dp(12), DesignTokens.BORDER, dp(1)));
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
        plusButton.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        plusButton.setBackground(rounded(
                DesignTokens.TODO, dp(12), Color.TRANSPARENT, 0));
        plusButton.setOnClickListener(v -> submitQuickInput());
        addView(plusButton);
    }

    void setCallback(Callback value) { callback = value; }

    void refresh() {
        uiScale = Prefs.todoScale(getContext());
        textScale = Prefs.textScale(getContext());
        quickInput.setTextSize(13.5f * textScale);
        plusButton.setTextSize(24f * textScale);
        requestLayout();
        invalidate();
    }

    void clearInputFocus() { quickInput.clearFocus(); }

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
        headerHeight = dp(88 * uiScale);
        rowHeight = dp(50 * uiScale);
        inputHeight = dp(44 * uiScale);
        pad = dp(16 * uiScale);
        gap = dp(8 * uiScale);
        cornerRadius = dp(20 * uiScale);

        int count = Prefs.items(getContext()).size();
        int rowsHeight = count == 0 ? dp(64 * uiScale) : count * rowHeight;
        int desiredHeight = headerHeight + rowsHeight + gap + inputHeight + pad;
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
        paint.setColor(DesignTokens.SURFACE);
        paint.setShadowLayer(dp(8), 0, dp(3), Color.argb(24, 30, 30, 30));
        canvas.drawRoundRect(new RectF(dp(1), dp(1), w - dp(1), h - dp(1)),
                cornerRadius, cornerRadius, paint);
        paint.clearShadowLayer();

        paint.setColor(DesignTokens.TODO);
        canvas.drawRoundRect(new RectF(0, 0, w, dp(5 * uiScale)),
                dp(3), dp(3), paint);

        drawHeader(canvas, w);
        drawRows(canvas, w, h);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(DesignTokens.BORDER);
        canvas.drawRoundRect(new RectF(.5f, .5f, w - .5f, h - .5f),
                cornerRadius, cornerRadius, paint);
        canvas.restore();
    }

    private void drawHeader(Canvas canvas, int w) {
        String date = new SimpleDateFormat("M월 d일 E요일", Locale.KOREAN)
                .format(new Date()).replace("요일요일", "요일");
        List<String> items = Prefs.items(getContext());
        String weather = Prefs.weatherSummary(getContext());

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sp(18f * textScale * uiScale));
        textPaint.setColor(DesignTokens.INK);
        canvas.drawText(date, pad, dp(35 * uiScale), textPaint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(sp(12.5f * textScale * uiScale));
        textPaint.setColor(DesignTokens.SECONDARY);
        String sub = "오늘 할 일  " + items.size();
        if (!weather.isEmpty()) sub += "   ·   " + weather;
        canvas.drawText(sub, pad, dp(61 * uiScale), textPaint);

        float cx = w - pad - dp(15 * uiScale);
        float cy = dp(35 * uiScale);
        drawGear(canvas, cx, cy);
        float hit = dp(25 * uiScale);
        gearRect.set(cx - hit, cy - hit, cx + hit, cy + hit);
    }

    private void drawRows(Canvas canvas, int w, int h) {
        List<String> items = Prefs.items(getContext());
        List<String> categories = Prefs.categories(getContext());
        completeRects.clear();
        categoryRects.clear();
        rowRects.clear();

        float startY = headerHeight;
        float bottomLimit = h - pad - inputHeight - gap;

        if (items.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setTextSize(sp(13.5f * textScale * uiScale));
            textPaint.setColor(DesignTokens.SECONDARY);
            float centerY = startY + Math.max(dp(30), (bottomLimit - startY) / 2f);
            canvas.drawText("아직 등록된 할 일이 없어요.", w / 2f, centerY, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            float top = startY + i * rowHeight;
            float cy = top + rowHeight * .50f;
            if (top + rowHeight > bottomLimit + dp(4)) break;

            rowRects.add(new RectF(pad, top, w - pad, top + rowHeight));

            if (i == activeReorder && reordering) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(DesignTokens.alpha(DesignTokens.TODO, 22));
                canvas.drawRoundRect(new RectF(pad, top + dp(3), w - pad, top + rowHeight - dp(3)),
                        dp(10), dp(10), paint);
            }

            if (i > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(.75f));
                paint.setColor(Color.rgb(238, 235, 231));
                canvas.drawLine(pad, top, w - pad, top, paint);
            }

            float completeSize = dp(30 * uiScale);
            float completeCx = w - pad - completeSize / 2f;
            float pillW = dp(48 * uiScale);
            float pillCx = completeCx - completeSize / 2f - dp(7 * uiScale) - pillW / 2f;
            float textLeft = pad;
            float textRight = pillCx - pillW / 2f - dp(9 * uiScale);

            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setTextSize(sp(14.2f * textScale * uiScale));
            textPaint.setColor(DesignTokens.INK);
            textPaint.setTextAlign(Paint.Align.LEFT);
            String title = ellipsize(items.get(i), textPaint, Math.max(dp(54), textRight - textLeft));
            canvas.drawText(title, textLeft, cy + dp(5 * uiScale), textPaint);

            String category = i < categories.size() ? categories.get(i) : "업무";
            drawPill(canvas, pillCx, cy, pillW, category);
            drawCompleteButton(canvas, completeCx, cy, completeSize);

            float pillHalfH = dp(14 * uiScale);
            categoryRects.add(new RectF(
                    pillCx - pillW / 2f - dp(4),
                    cy - pillHalfH,
                    pillCx + pillW / 2f + dp(4),
                    cy + pillHalfH));
            completeRects.add(new RectF(
                    completeCx - completeSize * .75f,
                    cy - completeSize * .75f,
                    completeCx + completeSize * .75f,
                    cy + completeSize * .75f));
        }
    }

    private void drawPill(Canvas canvas, float cx, float cy, float width, String category) {
        int bg;
        int fg;
        if ("개인".equals(category)) {
            bg = DesignTokens.IMAGE_SOFT;
            fg = Color.rgb(74, 126, 102);
        } else if ("기타".equals(category)) {
            bg = DesignTokens.MEMO_SOFT;
            fg = Color.rgb(160, 105, 71);
        } else {
            bg = DesignTokens.CALENDAR_SOFT;
            fg = Color.rgb(112, 92, 172);
        }
        float halfH = dp(11 * uiScale);
        RectF rect = new RectF(cx - width / 2f, cy - halfH, cx + width / 2f, cy + halfH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        canvas.drawRoundRect(rect, halfH, halfH, paint);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(sp(9.8f * textScale * uiScale));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(fg);
        canvas.drawText(category, cx, cy + dp(3.5f * uiScale), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCompleteButton(Canvas canvas, float cx, float cy, float size) {
        float r = size / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DesignTokens.SURFACE);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.rgb(195, 200, 207));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStrokeWidth(dp(1.8f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(DesignTokens.TODO);
        canvas.drawLine(cx - r * .40f, cy, cx - r * .10f, cy + r * .28f, paint);
        canvas.drawLine(cx - r * .10f, cy + r * .28f, cx + r * .45f, cy - r * .34f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawGear(Canvas canvas, float cx, float cy) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(DesignTokens.SECONDARY);
        canvas.drawCircle(cx, cy, dp(7 * uiScale), paint);
        canvas.drawCircle(cx, cy, dp(2.3f * uiScale), paint);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            float x1 = cx + (float)Math.cos(a) * dp(8 * uiScale);
            float y1 = cy + (float)Math.sin(a) * dp(8 * uiScale);
            float x2 = cx + (float)Math.cos(a) * dp(11 * uiScale);
            float y2 = cy + (float)Math.sin(a) * dp(11 * uiScale);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            pendingReorder = hitRow(x, y);
            if (pendingReorder >= 0
                    && hitRect(categoryRects, x, y) < 0
                    && hitRect(completeRects, x, y) < 0
                    && !gearRect.contains(x, y)) {
                armReorder(pendingReorder);
            } else {
                pendingReorder = -1;
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (reordering) {
                int target = hitRowForDrag(y);
                if (target >= 0 && target != activeReorder) {
                    Prefs.moveItem(getContext(), activeReorder, target);
                    activeReorder = target;
                    invalidate();
                }
                return true;
            }
            float dx = x - downX;
            float dy = y - downY;
            if (dx * dx + dy * dy > dp(10) * dp(10)) cancelReorderArm();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            cancelReorderArm();
            if (reordering) {
                finishReorder();
                performClick();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) return true;

            float dx = x - downX;
            float dy = y - downY;
            if (dx * dx + dy * dy > dp(12) * dp(12)) return true;

            if (gearRect.contains(x, y)) {
                if (callback != null) callback.onGear();
                performClick();
                return true;
            }

            int categoryIndex = hitRect(categoryRects, x, y);
            if (categoryIndex >= 0) {
                Prefs.cycleCategory(getContext(), categoryIndex);
                refresh();
                performClick();
                return true;
            }

            int completeIndex = hitRect(completeRects, x, y);
            if (completeIndex >= 0) {
                if (callback != null) callback.onComplete(completeIndex);
                performClick();
                return true;
            }

            performClick();
            return true;
        }
        return true;
    }

    private void armReorder(int index) {
        cancelReorderArm();
        reorderArm = () -> {
            reorderArm = null;
            if (pendingReorder < 0) return;
            reordering = true;
            activeReorder = pendingReorder;
            if (callback != null) callback.onInteractionChanged(true);
            invalidate();
        };
        postDelayed(reorderArm, 320L);
    }

    private void cancelReorderArm() {
        if (reorderArm != null) {
            removeCallbacks(reorderArm);
            reorderArm = null;
        }
        pendingReorder = -1;
    }

    private void finishReorder() {
        reordering = false;
        activeReorder = -1;
        pendingReorder = -1;
        if (callback != null) callback.onInteractionChanged(false);
        invalidate();
    }

    private int hitRow(float x, float y) { return hitRect(rowRects, x, y); }

    private int hitRowForDrag(float y) {
        if (rowRects.isEmpty()) return -1;
        for (int i = 0; i < rowRects.size(); i++) {
            RectF rect = rowRects.get(i);
            if (y >= rect.top && y <= rect.bottom) return i;
        }
        if (y < rowRects.get(0).top) return 0;
        return rowRects.size() - 1;
    }

    private int hitRect(ArrayList<RectF> rects, float x, float y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelReorderArm();
        if (reordering) finishReorder();
        super.onDetachedFromWindow();
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private String ellipsize(String value, Paint p, float maxWidth) {
        if (p.measureText(value) <= maxWidth) return value;
        int end = value.length();
        while (end > 0 && p.measureText(value.substring(0, end) + "…") > maxWidth) end--;
        return value.substring(0, Math.max(0, end)) + "…";
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
