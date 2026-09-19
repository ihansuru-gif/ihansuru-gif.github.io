package com.ihansuru.greetingtodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

final class OverlayCardFrame extends FrameLayout {
    static final int KIND_TODO = 1;
    static final int KIND_IMAGE = 2;
    static final int KIND_MEMO = 3;
    static final int KIND_CALENDAR = 4;

    interface GestureListener {
        void onGestureStart(int kind);
        void onGestureEnd(int kind, int startW, int startH);
    }

    interface CollapseListener {
        void onCollapse();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private final android.view.ScaleGestureDetector scaleDetector;

    private GestureListener listener;
    private CollapseListener collapseListener;
    private TextView collapseButton;
    private TextView moveHandle;
    private TextView resizeHandle;

    private boolean editing;
    private boolean controlTouch;
    private boolean scaling;
    private boolean bodyGestureActive;

    private float downRawX;
    private float downRawY;
    private float startX;
    private float startY;
    private int startW;
    private int startH;

    OverlayCardFrame(Context context, int kind) {
        super(context);
        this.kind = kind;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        scaleDetector = new android.view.ScaleGestureDetector(context,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(android.view.ScaleGestureDetector detector) {
                        if (!editing) return false;
                        scaling = true;
                        if (!bodyGestureActive) beginBodyGesture();
                        return true;
                    }

                    @Override public boolean onScale(android.view.ScaleGestureDetector detector) {
                        if (!editing) return false;
                        float factor = detector.getScaleFactor();
                        if (Float.isNaN(factor) || Float.isInfinite(factor)) return false;
                        scaleFrame(factor);
                        return true;
                    }

                    @Override public void onScaleEnd(android.view.ScaleGestureDetector detector) {
                        scaling = false;
                    }
                });
    }

    void setGestureListener(GestureListener value) { listener = value; }

    void enableCollapse(CollapseListener value) {
        collapseListener = value;
        if (collapseButton != null) return;
        installCollapseButton();
        installMoveHandle();
        installResizeHandle();
    }

    boolean isEditing() { return editing; }

    void setEditing(boolean value) {
        editing = value;
        if (!editing) {
            scaling = false;
            bodyGestureActive = false;
        }
        bringControlsToFront();
        invalidate();
    }

    private void installCollapseButton() {
        collapseButton = new TextView(getContext());
        collapseButton.setText("›");
        collapseButton.setTextSize(21f);
        collapseButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        collapseButton.setTextColor(accentColor());
        collapseButton.setGravity(Gravity.CENTER);
        collapseButton.setContentDescription(kindName() + " 카드 접기");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(226, 255, 255, 255));
        bg.setCornerRadius(dpLocal(10));
        bg.setStroke(dpLocal(1), alphaColor(accentColor(), 54));
        collapseButton.setBackground(bg);
        collapseButton.setElevation(dpLocal(2));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpLocal(30), dpLocal(30),
                Gravity.END | Gravity.TOP);
        lp.setMargins(0, dpLocal(6), dpLocal(6), 0);
        addView(collapseButton, lp);
        collapseButton.setOnClickListener(v -> {
            if (collapseListener != null) collapseListener.onCollapse();
        });
    }

    private void installMoveHandle() {
        moveHandle = new TextView(getContext());
        moveHandle.setText("━━");
        moveHandle.setTextSize(9.5f);
        moveHandle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        moveHandle.setTextColor(alphaColor(accentColor(), 118));
        moveHandle.setGravity(Gravity.CENTER);
        moveHandle.setBackgroundColor(Color.TRANSPARENT);
        moveHandle.setContentDescription(kindName() + " 카드 이동 손잡이");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpLocal(52), dpLocal(24),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addView(moveHandle, lp);

        moveHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginHandleGesture(event);
                    v.setAlpha(.62f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    applyMove(event.getRawX() - downRawX, event.getRawY() - downRawY);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    finishHandleGesture();
                    v.performClick();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void installResizeHandle() {
        resizeHandle = new TextView(getContext());
        resizeHandle.setText("╱╱");
        resizeHandle.setTextSize(10.5f);
        resizeHandle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resizeHandle.setTextColor(alphaColor(accentColor(), 156));
        resizeHandle.setGravity(Gravity.END | Gravity.BOTTOM);
        resizeHandle.setPadding(0, 0, dpLocal(3), dpLocal(3));
        resizeHandle.setBackgroundColor(Color.TRANSPARENT);
        resizeHandle.setContentDescription(kindName() + " 카드 크기 조절 손잡이");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpLocal(32), dpLocal(32),
                Gravity.END | Gravity.BOTTOM);
        lp.setMargins(0, 0, dpLocal(1), dpLocal(1));
        addView(resizeHandle, lp);

        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginHandleGesture(event);
                    v.setAlpha(.62f);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    applyResize(event.getRawX() - downRawX, event.getRawY() - downRawY);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    finishHandleGesture();
                    v.performClick();
                    return true;
                default:
                    return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            controlTouch = hitControl(event.getX(), event.getY());
        }
        if (controlTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                controlTouch = false;
            }
            return false;
        }
        return editing;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editing) return false;

        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                beginBodyGesture();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaling && event.getPointerCount() == 1) {
                    applyMove(event.getRawX() - downRawX, event.getRawY() - downRawY);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishBodyGesture();
                performClick();
                return true;
            default:
                return true;
        }
    }

    private void beginHandleGesture(MotionEvent event) {
        downRawX = event.getRawX();
        downRawY = event.getRawY();
        startX = getX();
        startY = getY();
        startW = getWidth();
        startH = getHeight();
        if (listener != null) listener.onGestureStart(kind);
    }

    private void finishHandleGesture() {
        if (listener != null) listener.onGestureEnd(kind, startW, startH);
    }

    private void beginBodyGesture() {
        if (bodyGestureActive) return;
        bodyGestureActive = true;
        startX = getX();
        startY = getY();
        startW = getWidth();
        startH = getHeight();
        if (listener != null) listener.onGestureStart(kind);
    }

    private void finishBodyGesture() {
        if (!bodyGestureActive) return;
        bodyGestureActive = false;
        scaling = false;
        if (listener != null) listener.onGestureEnd(kind, startW, startH);
    }

    private void applyMove(float dx, float dy) {
        View parent = (View) getParent();
        if (parent == null) return;
        applyBox(CardGeometry.move(
                startX, startY, startW, startH,
                dx, dy, parent.getWidth(), parent.getHeight()));
    }

    private void applyResize(float dx, float dy) {
        View parent = (View) getParent();
        if (parent == null) return;
        applyBox(CardGeometry.resizeBottomRight(
                startX, startY, startW, startH,
                dx, dy, parent.getWidth(), parent.getHeight(),
                minWidth(parent.getWidth()), minHeight(parent.getHeight()),
                kind == KIND_IMAGE));
    }

    private void scaleFrame(float factor) {
        View parent = (View) getParent();
        if (parent == null) return;
        applyBox(CardGeometry.scaleAroundCenter(
                getX(), getY(), getWidth(), getHeight(),
                factor, parent.getWidth(), parent.getHeight(),
                minWidth(parent.getWidth()), minHeight(parent.getHeight()),
                kind == KIND_IMAGE));
    }

    private void applyBox(CardGeometry.Box box) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.width = box.width;
        lp.height = box.height;
        setLayoutParams(lp);
        setX(box.x);
        setY(box.y);
        bringControlsToFront();
    }

    private int minWidth(int parentWidth) {
        int requested = kind == KIND_IMAGE ? 72
                : kind == KIND_CALENDAR ? 330
                : kind == KIND_MEMO ? 310 : 280;
        return Math.min(dpLocal(requested), Math.max(1, parentWidth));
    }

    private int minHeight(int parentHeight) {
        int requested = kind == KIND_IMAGE ? 72
                : kind == KIND_CALENDAR ? 440
                : kind == KIND_MEMO ? 340 : 280;
        return Math.min(dpLocal(requested), Math.max(1, parentHeight));
    }

    private boolean hitControl(float x, float y) {
        return hit(moveHandle, x, y, dpLocal(4))
                || hit(resizeHandle, x, y, dpLocal(5))
                || hit(collapseButton, x, y, dpLocal(3));
    }

    private boolean hit(View view, float x, float y, int extra) {
        return view != null && view.getVisibility() == View.VISIBLE
                && x >= view.getLeft() - extra
                && x <= view.getRight() + extra
                && y >= view.getTop() - extra
                && y <= view.getBottom() + extra;
    }

    private void bringControlsToFront() {
        if (moveHandle != null) moveHandle.bringToFront();
        if (resizeHandle != null) resizeHandle.bringToFront();
        if (collapseButton != null) collapseButton.bringToFront();
    }

    private String kindName() {
        if (kind == KIND_TODO) return "투두";
        if (kind == KIND_CALENDAR) return "일정";
        if (kind == KIND_MEMO) return "메모";
        return "이미지";
    }

    private int accentColor() {
        if (kind == KIND_TODO) return DesignTokens.TODO;
        if (kind == KIND_CALENDAR) return DesignTokens.CALENDAR;
        if (kind == KIND_MEMO) return DesignTokens.MEMO;
        return DesignTokens.IMAGE;
    }

    private int alphaColor(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        bringControlsToFront();
        if (!editing) return;

        float inset = dpLocal(2);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpLocal(1.5f));
        borderPaint.setColor(accentColor());
        canvas.drawRoundRect(
                new RectF(inset, inset, getWidth() - inset, getHeight() - inset),
                dpLocal(13), dpLocal(13), borderPaint);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dpLocal(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
