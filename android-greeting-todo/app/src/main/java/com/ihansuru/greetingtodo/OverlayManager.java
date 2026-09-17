package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

final class OverlayManager {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager windowManager;
    private static View imageView;
    private static TodoCardView todoView;
    private static Bitmap imageBitmap;
    private static Runnable hideTask;
    private static boolean visible;

    private OverlayManager() {}

    static boolean show(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        Context app = context.getApplicationContext();
        hide(app);
        if (!Settings.canDrawOverlays(app)) return false;
        if (Prefs.showImage(app) && !ImageStore.has(app)) return false;

        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;
        windowManager = wm;
        int[] screen = screenSize(app, wm);
        boolean imageAdded = false;
        boolean todoAdded = false;

        try {
            if (Prefs.showImage(app)) {
                imageBitmap = ImageStore.load(app, 2600);
                if (imageBitmap == null) return false;
                ImageView image = new ImageView(app);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(imageBitmap);
                image.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                int[] imageSize = imageSize(screen[0], screen[1], imageBitmap, Prefs.imageSize(app));
                WindowManager.LayoutParams p = params(app, imageSize[0], imageSize[1], !Prefs.tapDismiss(app));
                p.x = clamp(Math.round(Prefs.imageX(app) * screen[0] - imageSize[0] / 2f), 0, Math.max(0, screen[0] - imageSize[0]));
                p.y = clamp(Math.round(Prefs.imageY(app) * screen[1] - imageSize[1] / 2f), 0, Math.max(0, screen[1] - imageSize[1]));
                p.setTitle("GreetingImageOverlay");
                if (Prefs.tapDismiss(app)) image.setOnClickListener(v -> hide(app));
                wm.addView(image, p);
                imageView = image;
                imageAdded = true;
            }

            if (Prefs.showTodo(app)) {
                int todoWidth = clamp(Math.round(screen[0] * Prefs.todoWidth(app) / 100f), dp(app, 220), Math.max(dp(app, 220), screen[0] - dp(app, 20)));
                TodoCardView todo = new TodoCardView(app);
                todo.setEditorMode(false);
                todo.measure(View.MeasureSpec.makeMeasureSpec(todoWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(screen[1], View.MeasureSpec.AT_MOST));
                int todoHeight = Math.min(todo.getMeasuredHeight(), screen[1] - dp(app, 20));
                WindowManager.LayoutParams p = params(app, todoWidth, todoHeight, false);
                p.x = clamp(Math.round(Prefs.todoX(app) * screen[0] - todoWidth / 2f), 0, Math.max(0, screen[0] - todoWidth));
                p.y = clamp(Math.round(Prefs.todoY(app) * screen[1] - todoHeight / 2f), 0, Math.max(0, screen[1] - todoHeight));
                p.setTitle("GreetingTodoOverlay");

                todo.setEditTapListener(() -> openTodoEditor(app));
                todo.setCompleteListener(index -> {
                    Prefs.completeItem(app, index);
                    refreshTodoOverlay(app, screen);
                });
                if (Prefs.tapDismiss(app)) todo.setHeaderTapListener(() -> hide(app));

                wm.addView(todo, p);
                todoView = todo;
                todoAdded = true;
            }
        } catch (RuntimeException error) {
            if (imageAdded || todoAdded) hide(app);
            return false;
        }

        visible = imageAdded || todoAdded;
        if (!visible) return false;
        long duration = Math.max(500, Math.min(10000, Prefs.duration(app)));
        hideTask = () -> hide(app);
        MAIN.postDelayed(hideTask, duration);
        return true;
    }

    private static void openTodoEditor(Context app) {
        hide(app);
        try {
            Intent edit = new Intent(app, TodoQuickEditActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            app.startActivity(edit);
        } catch (RuntimeException ignored) {}
    }

    private static void refreshTodoOverlay(Context app, int[] screen) {
        if (todoView == null || windowManager == null) return;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) todoView.getLayoutParams();
            int width = p.width;
            todoView.refresh();
            todoView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(screen[1], View.MeasureSpec.AT_MOST));
            p.height = Math.min(todoView.getMeasuredHeight(), screen[1] - dp(app, 20));
            p.x = clamp(p.x, 0, Math.max(0, screen[0] - width));
            p.y = clamp(p.y, 0, Math.max(0, screen[1] - p.height));
            windowManager.updateViewLayout(todoView, p);
            todoView.invalidate();
        } catch (RuntimeException ignored) {}
    }

    static void hide(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Context app = context.getApplicationContext();
            MAIN.post(() -> hide(app));
            return;
        }
        if (hideTask != null) {
            MAIN.removeCallbacks(hideTask);
            hideTask = null;
        }
        WindowManager wm = windowManager;
        if (wm != null) {
            if (todoView != null) {
                try { wm.removeViewImmediate(todoView); } catch (RuntimeException ignored) {}
            }
            if (imageView != null) {
                try { wm.removeViewImmediate(imageView); } catch (RuntimeException ignored) {}
            }
        }
        todoView = null;
        imageView = null;
        windowManager = null;
        visible = false;
        if (imageBitmap != null && !imageBitmap.isRecycled()) imageBitmap.recycle();
        imageBitmap = null;
    }

    static boolean isVisible() { return visible; }

    private static WindowManager.LayoutParams params(Context c, int width, int height, boolean notTouchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (notTouchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private static int[] imageSize(int sw, int sh, Bitmap bitmap, int percent) {
        float base = Math.min(sw * .47f, sh * .38f) * (percent / 100f);
        base = Math.max(dpStatic(sw, sh, 84), Math.min(base, Math.min(sw * .90f, sh * .70f)));
        float ratio = bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
        int w, h;
        if (ratio >= 1f) { w = Math.round(base); h = Math.round(base / ratio); }
        else { h = Math.round(base); w = Math.round(base * ratio); }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private static float dpStatic(int sw, int sh, int value) { return Math.min(sw, sh) / 360f * value; }

    private static int[] screenSize(Context c, WindowManager wm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            return new int[]{Math.max(1, b.width()), Math.max(1, b.height())};
        }
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return new int[]{Math.max(1, m.widthPixels), Math.max(1, m.heightPixels)};
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
