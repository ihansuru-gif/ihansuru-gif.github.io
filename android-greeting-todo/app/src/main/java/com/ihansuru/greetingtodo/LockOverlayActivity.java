package com.ihansuru.greetingtodo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LockOverlayActivity extends Activity {
    private static final int REQ_MEMO_IMAGE = 3201;
    private static final int REQ_MEMO_AUDIO = 3202;
    private static final int REQ_CALENDAR_NOTIFY = 3203;
    private static WeakReference<LockOverlayActivity> current = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> pauseReasons = new HashSet<>();
    private final ArrayList<EditRow> editRows = new ArrayList<>();

    private FrameLayout root;
    private OverlayCardFrame todoFrame;
    private OverlayCardFrame imageFrame;
    private OverlayCardFrame memoFrame;
    private OverlayCardFrame calendarFrame;
    private LockTodoWidget todoWidget;
    private MemoBoardView memoBoard;
    private CalendarBoardView calendarBoard;
    private ImageView imageView;
    private Bitmap imageBitmap;

    private LinearLayout tabRail;
    private TextView todoTab;
    private TextView memoTab;
    private TextView calendarTab;
    private TextView imageTab;

    private LinearLayout editToolbar;
    private LinearLayout detailPanel;
    private LinearLayout editRowsContainer;
    private boolean directEditing;

    private Runnable finishTask;
    private long remainingMs;
    private boolean timerScheduled;
    private boolean closing;

    static boolean launch(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, LockOverlayActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            app.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    static boolean isVisibleNow() {
        LockOverlayActivity activity = current.get();
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    static void closeActive() {
        LockOverlayActivity activity = current.get();
        if (activity != null) activity.runOnUiThread(activity::finishOverlay);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = new WeakReference<>(this);
        configureWindow();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        setContentView(root);

        buildObjects();
        remainingMs = Prefs.duration(this);
        root.post(() -> {
            layoutObjects();
            scheduleTimer();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        remainingMs = Prefs.duration(this);
        pauseReasons.clear();
        scheduleTimer();
    }

    @Override
    protected void onDestroy() {
        if (finishTask != null) handler.removeCallbacks(finishTask);
        finishTask = null;
        timerScheduled = false;
        if (memoBoard != null) memoBoard.prepareForCollapse();
        if (calendarBoard != null) calendarBoard.prepareForCollapse();
        if (imageBitmap != null && !imageBitmap.isRecycled()) imageBitmap.recycle();
        imageBitmap = null;
        LockOverlayActivity existing = current.get();
        if (existing == this) current = new WeakReference<>(null);
        super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (closing) return;
        remainingMs = Prefs.duration(this);
        if (pauseReasons.isEmpty()) scheduleTimer();
    }

    @Override
    public void onBackPressed() {
        if (detailPanel != null) {
            closeDetailEditor();
            return;
        }
        if (directEditing) exitDirectEdit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEMO_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && memoBoard != null) {
                memoBoard.attachImage(data.getData());
            }
            resume("memo_picker");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEMO_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (memoBoard != null) memoBoard.onAudioPermissionResult(granted);
            resume("memo_permission");
        } else if (requestCode == REQ_CALENDAR_NOTIFY) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CalendarReminderManager.scheduleAll(this);
            }
            resume("calendar_permission");
        }
    }

    private void pickMemoImage() {
        pause("memo_picker");
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_MEMO_IMAGE);
        } catch (RuntimeException e) {
            resume("memo_picker");
            Toast.makeText(this, "이미지 선택창을 열지 못했어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestMemoAudioPermission() {
        if (Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            if (memoBoard != null) memoBoard.onAudioPermissionResult(true);
            return;
        }
        pause("memo_permission");
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MEMO_AUDIO);
    }


    private void requestCalendarNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            CalendarReminderManager.scheduleAll(this);
            return;
        }
        pause("calendar_permission");
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_CALENDAR_NOTIFY);
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setFormat(PixelFormat.TRANSLUCENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void buildObjects() {
        if (Prefs.todoTabEnabled(this) && Prefs.todoExpanded(this)) buildTodoObject();
        if (Prefs.imageTabEnabled(this) && Prefs.imageExpanded(this) && ImageStore.has(this)) buildImageObject();
        if (Prefs.memoEnabled(this) && Prefs.memoExpanded(this)) buildMemoObject();
        if (Prefs.calendarEnabled(this) && Prefs.calendarExpanded(this)) buildCalendarObject();
        buildTabRail();
    }

    private void buildImageObject() {
        if (imageFrame != null) return;
        imageBitmap = ImageStore.load(this, 2400);
        if (imageBitmap == null) return;

        imageFrame = new OverlayCardFrame(this, OverlayCardFrame.KIND_IMAGE);
        imageFrame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int kind) { pause("gesture"); }
            @Override public void onGestureEnd(int kind, int startW, int startH) {
                persistImageGesture(startW, startH);
                resume("gesture");
            }
        });

        imageView = new ImageView(this);
        imageView.setImageBitmap(imageBitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.TRANSPARENT);
        imageFrame.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        imageFrame.enableCollapse(() -> collapseImage());
        root.addView(imageFrame);
    }

    private void buildTodoObject() {
        if (todoFrame != null) return;
        todoFrame = new OverlayCardFrame(this, OverlayCardFrame.KIND_TODO);
        todoFrame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int kind) { pause("gesture"); }
            @Override public void onGestureEnd(int kind, int startW, int startH) {
                persistTodoGesture(startW, startH);
                resume("gesture");
            }
        });

        todoWidget = new LockTodoWidget(this);
        todoWidget.setCallback(new LockTodoWidget.Callback() {
            @Override public void onComplete(int index) {
                Prefs.completeItem(LockOverlayActivity.this, index);
                refreshTodoKeepingCenter();
            }

            @Override public void onGear() {
                enterDirectEdit();
            }

            @Override public void onAdd(String text) {
                Prefs.addItem(LockOverlayActivity.this, text, "업무");
                refreshTodoKeepingCenter();
            }

            @Override public void onInteractionChanged(boolean active) {
                if (active) pause("todo_interaction");
                else resume("todo_interaction");
            }
        });

        todoFrame.addView(todoWidget, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        todoFrame.enableCollapse(() -> collapseTodo());
        root.addView(todoFrame);
    }

    private void buildMemoObject() {
        if (memoFrame != null || !Prefs.memoEnabled(this)) return;

        memoFrame = new OverlayCardFrame(this, OverlayCardFrame.KIND_MEMO);
        memoFrame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int kind) { pause("gesture"); }
            @Override public void onGestureEnd(int kind, int startW, int startH) {
                persistMemoGesture();
                resume("gesture");
            }
        });

        memoBoard = new MemoBoardView(this);
        memoBoard.setCallback(new MemoBoardView.Callback() {
            @Override public void onGear() { enterDirectEdit(); }
            @Override public void onInteractionChanged(boolean active) {
                if (active) pause("memo_interaction");
                else resume("memo_interaction");
            }
            @Override public void onPickImage() { pickMemoImage(); }
            @Override public void onRequestAudioPermission() { requestMemoAudioPermission(); }
        });

        memoFrame.addView(memoBoard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        memoFrame.enableCollapse(() -> collapseMemo());
        root.addView(memoFrame);
    }

    private void buildCalendarObject() {
        if (calendarFrame != null || !Prefs.calendarEnabled(this)) return;

        calendarFrame = new OverlayCardFrame(this, OverlayCardFrame.KIND_CALENDAR);
        calendarFrame.setGestureListener(new OverlayCardFrame.GestureListener() {
            @Override public void onGestureStart(int kind) { pause("gesture"); }
            @Override public void onGestureEnd(int kind, int startW, int startH) {
                persistCalendarGesture();
                resume("gesture");
            }
        });

        calendarBoard = new CalendarBoardView(this);
        calendarBoard.setCallback(new CalendarBoardView.Callback() {
            @Override public void onGear() { enterDirectEdit(); }
            @Override public void onInteractionChanged(boolean active) {
                if (active) pause("calendar_interaction");
                else resume("calendar_interaction");
            }
            @Override public void onReminderPermissionNeeded() {
                requestCalendarNotificationPermission();
            }
        });

        calendarFrame.addView(calendarBoard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        calendarFrame.enableCollapse(() -> collapseCalendar());
        root.addView(calendarFrame);
    }

    private void buildTabRail() {
        if (tabRail != null) return;

        tabRail = new LinearLayout(this);
        tabRail.setOrientation(LinearLayout.VERTICAL);
        tabRail.setGravity(Gravity.CENTER_HORIZONTAL);
        tabRail.setPadding(dp(4), dp(6), dp(4), dp(6));
        tabRail.setBackground(rounded(
                Color.argb(214, 255, 255, 255),
                dp(18),
                Color.argb(58, 90, 102, 130),
                dp(1)));
        tabRail.setElevation(dp(14));

        todoTab = sideTab("✓\n투두", "todo");
        calendarTab = sideTab("▣\n일정", "calendar");
        memoTab = sideTab("✎\n메모", "memo");
        imageTab = sideTab("▧\n이미지", "image");

        int scale = Prefs.tabSize(this);
        int tabW = Math.max(dp(42), Math.round(dp(52) * scale / 100f));
        int tabH = Math.max(dp(50), Math.round(dp(62) * scale / 100f));

        for (String key : Prefs.tabOrder(this)) {
            if ("todo".equals(key) && Prefs.todoTabEnabled(this)) {
                addRailTab(todoTab, tabW, tabH);
            } else if ("calendar".equals(key) && Prefs.calendarEnabled(this)) {
                addRailTab(calendarTab, tabW, tabH);
            } else if ("memo".equals(key) && Prefs.memoEnabled(this)) {
                addRailTab(memoTab, tabW, tabH);
            } else if ("image".equals(key) && Prefs.imageTabEnabled(this)) {
                addRailTab(imageTab, tabW, tabH);
            }
        }

        if (tabRail.getChildCount() == 0) {
            tabRail = null;
            return;
        }

        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(
                tabW + dp(8), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        railLp.setMargins(0, 0, dp(2), 0);
        root.addView(tabRail, railLp);
        tabRail.bringToFront();

        todoTab.setOnClickListener(v -> toggleTodo());
        calendarTab.setOnClickListener(v -> toggleCalendar());
        memoTab.setOnClickListener(v -> toggleMemo());
        imageTab.setOnClickListener(v -> toggleImage());

        todoTab.setOnLongClickListener(v -> { ensureTodoVisible(); enterDirectEdit(); return true; });
        calendarTab.setOnLongClickListener(v -> { ensureCalendarVisible(); enterDirectEdit(); return true; });
        memoTab.setOnLongClickListener(v -> { ensureMemoVisible(); enterDirectEdit(); return true; });
        imageTab.setOnLongClickListener(v -> {
            if (ImageStore.has(this)) { ensureImageVisible(); enterDirectEdit(); }
            return true;
        });

        updateTabStates();
    }

    private void addRailTab(TextView tab, int width, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        if (tabRail.getChildCount() > 0) lp.setMargins(0, dp(5), 0, 0);
        tabRail.addView(tab, lp);
    }

    private TextView sideTab(String label, String key) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(11.5f);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);

        int fill;
        int stroke;
        int text;
        if ("todo".equals(key)) {
            fill = Color.rgb(235, 243, 255);
            stroke = Color.rgb(170, 195, 240);
            text = Color.rgb(72, 107, 184);
        } else if ("calendar".equals(key)) {
            fill = Color.rgb(241, 237, 255);
            stroke = Color.rgb(197, 184, 241);
            text = Color.rgb(111, 91, 187);
        } else if ("memo".equals(key)) {
            fill = Color.rgb(255, 241, 231);
            stroke = Color.rgb(235, 199, 173);
            text = Color.rgb(167, 103, 65);
        } else {
            fill = Color.rgb(232, 246, 239);
            stroke = Color.rgb(177, 215, 199);
            text = Color.rgb(61, 126, 101);
        }
        v.setTextColor(text);
        v.setBackground(rounded(fill, dp(14), stroke, dp(1)));
        v.setElevation(dp(2));
        return v;
    }

    private void toggleTodo() {
        if (todoFrame != null && todoFrame.getVisibility() == View.VISIBLE) {
            collapseTodo();
            return;
        }
        ensureTodoVisible();
        animateExpand(todoFrame, todoTab);
    }

    private void toggleCalendar() {
        if (!Prefs.calendarEnabled(this)) return;
        if (calendarFrame != null && calendarFrame.getVisibility() == View.VISIBLE) {
            collapseCalendar();
            return;
        }
        ensureCalendarVisible();
        animateExpand(calendarFrame, calendarTab);
    }

    private void toggleMemo() {
        if (!Prefs.memoEnabled(this)) return;
        if (memoFrame != null && memoFrame.getVisibility() == View.VISIBLE) {
            collapseMemo();
            return;
        }
        ensureMemoVisible();
        animateExpand(memoFrame, memoTab);
    }

    private void toggleImage() {
        if (!ImageStore.has(this)) {
            Toast.makeText(this, "앱에서 이미지를 먼저 선택해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (imageFrame != null && imageFrame.getVisibility() == View.VISIBLE) {
            collapseImage();
            return;
        }
        ensureImageVisible();
        animateExpand(imageFrame, imageTab);
    }

    private void collapseTodo() {
        if (todoFrame == null || todoFrame.getVisibility() != View.VISIBLE) return;
        if (directEditing) exitDirectEdit();
        Prefs.setTodoExpanded(this, false);
        animateCollapse(todoFrame, todoTab);
    }

    private void collapseCalendar() {
        if (calendarFrame == null || calendarFrame.getVisibility() != View.VISIBLE) return;
        if (directEditing) exitDirectEdit();
        if (calendarBoard != null) calendarBoard.prepareForCollapse();
        Prefs.setCalendarExpanded(this, false);
        animateCollapse(calendarFrame, calendarTab);
    }

    private void collapseMemo() {
        if (memoFrame == null || memoFrame.getVisibility() != View.VISIBLE) return;
        if (directEditing) exitDirectEdit();
        if (memoBoard != null) memoBoard.prepareForCollapse();
        Prefs.setMemoExpanded(this, false);
        animateCollapse(memoFrame, memoTab);
    }

    private void collapseImage() {
        if (imageFrame == null || imageFrame.getVisibility() != View.VISIBLE) return;
        if (directEditing) exitDirectEdit();
        Prefs.setImageExpanded(this, false);
        animateCollapse(imageFrame, imageTab);
    }

    private void animateCollapse(View frame, View tab) {
        if (frame == null) return;
        pause("collapse_animation");
        float dx = collapseTargetX(frame, tab);
        float dy = collapseTargetY(frame, tab);
        frame.animate().cancel();
        frame.animate()
                .translationX(dx)
                .translationY(dy)
                .scaleX(.18f)
                .scaleY(.18f)
                .alpha(.06f)
                .setDuration(220L)
                .withEndAction(() -> {
                    frame.setVisibility(View.GONE);
                    frame.setTranslationX(0f);
                    frame.setTranslationY(0f);
                    frame.setScaleX(1f);
                    frame.setScaleY(1f);
                    frame.setAlpha(1f);
                    updateTabStates();
                    if (tabRail != null) tabRail.bringToFront();
                    resume("collapse_animation");
                })
                .start();
    }

    private void animateExpand(View frame, View tab) {
        if (frame == null) return;
        pause("expand_animation");
        frame.animate().cancel();
        frame.setVisibility(View.VISIBLE);
        frame.setScaleX(.18f);
        frame.setScaleY(.18f);
        frame.setAlpha(.06f);
        frame.setTranslationX(collapseTargetX(frame, tab));
        frame.setTranslationY(collapseTargetY(frame, tab));
        frame.bringToFront();
        if (tabRail != null) tabRail.bringToFront();
        frame.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(220L)
                .withEndAction(() -> {
                    updateTabStates();
                    if (tabRail != null) tabRail.bringToFront();
                    resume("expand_animation");
                })
                .start();
    }

    private float collapseTargetX(View frame, View tab) {
        if (frame == null || tab == null || tabRail == null) return dp(48);
        float frameCx = frame.getX() + frame.getWidth() / 2f;
        float tabCx = tabRail.getX() + tab.getX() + tab.getWidth() / 2f;
        return tabCx - frameCx;
    }

    private float collapseTargetY(View frame, View tab) {
        if (frame == null || tab == null || tabRail == null) return 0f;
        float frameCy = frame.getY() + frame.getHeight() / 2f;
        float tabCy = tabRail.getY() + tab.getY() + tab.getHeight() / 2f;
        return tabCy - frameCy;
    }

    private void ensureTodoVisible() {
        if (todoFrame == null) buildTodoObject();
        if (root.getWidth() > 0) layoutTodoFrame(true);
        if (todoFrame != null) todoFrame.setVisibility(View.VISIBLE);
        Prefs.setTodoExpanded(this, true);
        updateTabStates();
    }

    private void ensureCalendarVisible() {
        if (calendarFrame == null) buildCalendarObject();
        if (root.getWidth() > 0) layoutCalendarFrame(true);
        if (calendarFrame != null) calendarFrame.setVisibility(View.VISIBLE);
        Prefs.setCalendarExpanded(this, true);
        updateTabStates();
    }

    private void ensureMemoVisible() {
        if (memoFrame == null) buildMemoObject();
        if (root.getWidth() > 0) layoutMemoFrame(true);
        if (memoFrame != null) memoFrame.setVisibility(View.VISIBLE);
        Prefs.setMemoExpanded(this, true);
        updateTabStates();
    }

    private void ensureImageVisible() {
        if (imageFrame == null) buildImageObject();
        if (root.getWidth() > 0) layoutImageFrame(true);
        if (imageFrame != null) imageFrame.setVisibility(View.VISIBLE);
        Prefs.setImageExpanded(this, true);
        updateTabStates();
    }

    private void updateTabStates() {
        if (todoTab != null) todoTab.setAlpha(todoFrame != null && todoFrame.getVisibility() == View.VISIBLE ? 1f : .55f);
        if (calendarTab != null) calendarTab.setAlpha(calendarFrame != null && calendarFrame.getVisibility() == View.VISIBLE ? 1f : .55f);
        if (memoTab != null) memoTab.setAlpha(memoFrame != null && memoFrame.getVisibility() == View.VISIBLE ? 1f : .55f);
        if (imageTab != null) {
            imageTab.setAlpha(imageFrame != null && imageFrame.getVisibility() == View.VISIBLE ? 1f : .45f);
            imageTab.setEnabled(ImageStore.has(this));
        }
    }

    private void layoutObjects() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        if (imageFrame != null) layoutImageFrame(true);
        if (todoFrame != null) layoutTodoFrame(true);
        if (memoFrame != null) layoutMemoFrame(true);
        if (calendarFrame != null) layoutCalendarFrame(true);
        if (editToolbar != null) editToolbar.bringToFront();
        if (detailPanel != null) detailPanel.bringToFront();
        if (tabRail != null) tabRail.bringToFront();
    }

    private void layoutImageFrame(boolean fromPrefs) {
        int sw = root.getWidth();
        int sh = root.getHeight();
        if (sw <= 0 || sh <= 0 || imageBitmap == null || imageFrame == null) return;

        float base = Math.min(sw * .46f, sh * .36f) * (Prefs.imageSize(this) / 100f);
        base = Math.max(dp(72), Math.min(base, Math.min(sw * .90f, sh * .65f)));
        float ratio = imageBitmap.getWidth() / (float) Math.max(1, imageBitmap.getHeight());

        int width;
        int height;
        if (ratio >= 1f) {
            width = Math.round(base);
            height = Math.max(dp(48), Math.round(base / ratio));
        } else {
            height = Math.round(base);
            width = Math.max(dp(48), Math.round(base * ratio));
        }

        imageFrame.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (fromPrefs) placeByCenter(imageFrame, Prefs.imageX(this), Prefs.imageY(this));
        else clampPosition(imageFrame);
    }

    private void layoutTodoFrame(boolean fromPrefs) {
        int sw = root.getWidth();
        int sh = root.getHeight();
        if (sw <= 0 || sh <= 0 || todoWidget == null || todoFrame == null) return;

        int width = clamp(
                Math.round(sw * Prefs.todoWidth(this) / 100f),
                Math.min(dp(280), Math.round(sw * .96f)),
                Math.round(sw * .96f));

        todoWidget.refresh();
        int storedHeight = Prefs.todoHeight(this);
        int height;
        if (storedHeight > 0) {
            height = clamp(Math.round(sh * storedHeight / 100f), Math.min(dp(280), Math.round(sh * .90f)), Math.round(sh * .90f));
            todoWidget.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        } else {
            todoWidget.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Math.round(sh * .86f), View.MeasureSpec.AT_MOST));
            height = Math.min(todoWidget.getMeasuredHeight(), Math.round(sh * .86f));
        }

        todoFrame.setLayoutParams(new FrameLayout.LayoutParams(width, Math.max(Math.min(dp(280), Math.round(sh * .90f)), height)));
        todoWidget.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (fromPrefs) placeByCenter(todoFrame, Prefs.todoX(this), Prefs.todoY(this));
        else clampPosition(todoFrame);
    }

    private void layoutCalendarFrame(boolean fromPrefs) {
        int sw = root.getWidth();
        int sh = root.getHeight();
        if (sw <= 0 || sh <= 0 || calendarFrame == null || calendarBoard == null) return;

        int width = clamp(Math.round(sw * Prefs.calendarWidth(this) / 100f),
                Math.min(dp(330), Math.round(sw * .96f)), Math.round(sw * .96f));
        int height = clamp(Math.round(sh * Prefs.calendarHeight(this) / 100f),
                Math.min(dp(440), Math.round(sh * .90f)), Math.round(sh * .90f));

        calendarFrame.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        calendarBoard.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        calendarBoard.refresh();

        if (fromPrefs) placeByCenter(calendarFrame, Prefs.calendarX(this), Prefs.calendarY(this));
        else clampPosition(calendarFrame);
    }

    private void layoutMemoFrame(boolean fromPrefs) {
        int sw = root.getWidth();
        int sh = root.getHeight();
        if (sw <= 0 || sh <= 0 || memoFrame == null || memoBoard == null) return;

        int width = clamp(Math.round(sw * Prefs.memoWidth(this) / 100f),
                Math.min(dp(310), Math.round(sw * .96f)), Math.round(sw * .96f));
        int height = clamp(Math.round(sh * Prefs.memoHeight(this) / 100f),
                Math.min(dp(340), Math.round(sh * .88f)), Math.round(sh * .88f));

        memoFrame.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        memoBoard.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        memoBoard.refreshScale();

        if (fromPrefs) placeByCenter(memoFrame, Prefs.memoX(this), Prefs.memoY(this));
        else clampPosition(memoFrame);
    }

    private void refreshMemoKeepingCenter() {
        if (memoFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        float cx = centerX(memoFrame) / root.getWidth();
        float cy = centerY(memoFrame) / root.getHeight();
        boolean editing = memoFrame.isEditing();
        layoutMemoFrame(false);
        placeByCenter(memoFrame, cx, cy);
        memoFrame.setEditing(editing);
        if (tabRail != null) tabRail.bringToFront();
    }

    private void refreshTodoKeepingCenter() {
        if (todoFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        float cx = centerX(todoFrame) / root.getWidth();
        float cy = centerY(todoFrame) / root.getHeight();
        boolean wasEditing = todoFrame.isEditing();

        layoutTodoFrame(false);
        placeByCenter(todoFrame, cx, cy);
        todoFrame.setEditing(wasEditing);
        if (editToolbar != null) editToolbar.bringToFront();
        if (detailPanel != null) detailPanel.bringToFront();
        if (tabRail != null) tabRail.bringToFront();
    }

    private void placeByCenter(View view, float nx, float ny) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        int width = raw.width;
        int height = raw.height;
        float x = nx * root.getWidth() - width / 2f;
        float y = ny * root.getHeight() - height / 2f;
        view.setX(clampFloat(x, 0, Math.max(0, root.getWidth() - width)));
        view.setY(clampFloat(y, 0, Math.max(0, root.getHeight() - height)));
    }

    private void clampPosition(View view) {
        view.setX(clampFloat(view.getX(), 0, Math.max(0, root.getWidth() - view.getWidth())));
        view.setY(clampFloat(view.getY(), 0, Math.max(0, root.getHeight() - view.getHeight())));
    }

    private void persistTodoGesture(int startW, int startH) {
        if (todoFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        float cx = centerX(todoFrame) / root.getWidth();
        float cy = centerY(todoFrame) / root.getHeight();

        int widthPct = Math.round(todoFrame.getWidth() * 100f / root.getWidth());
        int heightPct = Math.round(todoFrame.getHeight() * 100f / root.getHeight());
        Prefs.setTodoSize(this, widthPct, heightPct);
        Prefs.setTodoPosition(this, cx, cy);

        boolean editing = todoFrame.isEditing();
        layoutTodoFrame(false);
        placeByCenter(todoFrame, cx, cy);
        todoFrame.setEditing(editing);

        Prefs.setTodoPosition(this,
                centerX(todoFrame) / root.getWidth(),
                centerY(todoFrame) / root.getHeight());
    }

    private void persistImageGesture(int startW, int startH) {
        if (imageFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        float cx = centerX(imageFrame) / root.getWidth();
        float cy = centerY(imageFrame) / root.getHeight();

        if (startW > 0 && imageFrame.getWidth() > 0 && imageFrame.getWidth() != startW) {
            float ratio = imageFrame.getWidth() / (float) startW;
            Prefs.setImageSize(this, Math.round(Prefs.imageSize(this) * ratio));
        }

        Prefs.setImagePosition(this, cx, cy);

        boolean editing = imageFrame.isEditing();
        layoutImageFrame(false);
        placeByCenter(imageFrame, cx, cy);
        imageFrame.setEditing(editing);

        Prefs.setImagePosition(this,
                centerX(imageFrame) / root.getWidth(),
                centerY(imageFrame) / root.getHeight());
    }

    private void persistCalendarGesture() {
        if (calendarFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        float cx = centerX(calendarFrame) / root.getWidth();
        float cy = centerY(calendarFrame) / root.getHeight();
        int widthPct = Math.round(calendarFrame.getWidth() * 100f / root.getWidth());
        int heightPct = Math.round(calendarFrame.getHeight() * 100f / root.getHeight());

        Prefs.setCalendarSize(this, widthPct, heightPct);
        Prefs.setCalendarPosition(this, cx, cy);

        boolean editing = calendarFrame.isEditing();
        layoutCalendarFrame(false);
        placeByCenter(calendarFrame, cx, cy);
        calendarFrame.setEditing(editing);
        Prefs.setCalendarPosition(this,
                centerX(calendarFrame) / root.getWidth(),
                centerY(calendarFrame) / root.getHeight());
    }

    private void persistMemoGesture() {
        if (memoFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        float cx = centerX(memoFrame) / root.getWidth();
        float cy = centerY(memoFrame) / root.getHeight();
        int widthPct = Math.round(memoFrame.getWidth() * 100f / root.getWidth());
        int heightPct = Math.round(memoFrame.getHeight() * 100f / root.getHeight());

        Prefs.setMemoSize(this, widthPct, heightPct);
        Prefs.setMemoPosition(this, cx, cy);

        boolean editing = memoFrame.isEditing();
        layoutMemoFrame(false);
        placeByCenter(memoFrame, cx, cy);
        memoFrame.setEditing(editing);
        Prefs.setMemoPosition(this,
                centerX(memoFrame) / root.getWidth(),
                centerY(memoFrame) / root.getHeight());
    }

    private void enterDirectEdit() {
        if (directEditing) return;
        directEditing = true;
        pause("direct_edit");

        if (todoWidget != null) todoWidget.clearInputFocus();
        if (todoFrame != null && todoFrame.getVisibility() == View.VISIBLE) todoFrame.setEditing(true);
        if (imageFrame != null && imageFrame.getVisibility() == View.VISIBLE) imageFrame.setEditing(true);
        if (memoFrame != null && memoFrame.getVisibility() == View.VISIBLE) memoFrame.setEditing(true);
        if (calendarFrame != null && calendarFrame.getVisibility() == View.VISIBLE) calendarFrame.setEditing(true);

        buildEditToolbar();
    }

    private void buildEditToolbar() {
        if (editToolbar != null) return;

        editToolbar = new LinearLayout(this);
        editToolbar.setOrientation(LinearLayout.HORIZONTAL);
        editToolbar.setGravity(Gravity.CENTER_VERTICAL);
        editToolbar.setPadding(dp(10), dp(7), dp(7), dp(7));
        editToolbar.setBackground(rounded(
                Color.argb(248, 255, 255, 255),
                dp(18),
                Color.rgb(220, 226, 238),
                dp(1)));
        editToolbar.setElevation(dp(16));

        TextView hint = label(
                "위쪽 이동선으로 이동 · 오른쪽 아래 // 로 크기 조절 · 편집모드에서는 두 손가락 확대/축소",
                12.5f,
                true,
                Color.rgb(54, 66, 88));
        editToolbar.addView(hint, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button detail = softButton("내용·색");
        editToolbar.addView(detail, new LinearLayout.LayoutParams(dp(86), dp(40)));

        Button done = primaryButton("완료");
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(66), dp(40));
        doneLp.setMargins(dp(6), 0, 0, 0);
        editToolbar.addView(done, doneLp);

        FrameLayout.LayoutParams toolbarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.TOP);
        toolbarLp.setMargins(dp(10), dp(10), dp(10), 0);
        root.addView(editToolbar, toolbarLp);
        editToolbar.bringToFront();

        detail.setOnClickListener(v -> openDetailEditor());
        done.setOnClickListener(v -> exitDirectEdit());
    }

    private void exitDirectEdit() {
        if (!directEditing) return;
        closeDetailEditor();

        if (editToolbar != null) {
            root.removeView(editToolbar);
            editToolbar = null;
        }

        if (todoFrame != null) todoFrame.setEditing(false);
        if (imageFrame != null) imageFrame.setEditing(false);
        if (memoFrame != null) memoFrame.setEditing(false);
        if (calendarFrame != null) calendarFrame.setEditing(false);

        directEditing = false;
        hideKeyboard();
        resume("direct_edit");
    }

    private void openDetailEditor() {
        if (!directEditing || detailPanel != null || todoWidget == null) return;
        pause("detail_editor");

        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setPadding(dp(16), dp(13), dp(16), dp(14));
        detailPanel.setBackground(rounded(
                Color.argb(252, 255, 255, 255),
                dp(24),
                Color.rgb(224, 229, 239),
                dp(1)));
        detailPanel.setElevation(dp(18));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label("투두 세부 편집", 17, true, Color.rgb(38, 48, 67)),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        Button close = softButton("닫기");
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(68), dp(40)));
        detailPanel.addView(titleRow);

        ScrollView scroll = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(4), 0, dp(8));
        scroll.addView(controls, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        detailPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        controls.addView(sectionTitle("카드 색상"));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setHue(Prefs.hue(this));
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(dp(138), dp(138));
        wheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        wheelLp.setMargins(0, dp(3), 0, dp(2));
        controls.addView(wheel, wheelLp);
        wheel.setListener(hue -> {
            Prefs.setHue(this, hue);
            todoWidget.refresh();
        });

        TextView satLabel = smallValue("진하기  " + Prefs.saturation(this) + "%");
        controls.addView(satLabel);
        SeekBar sat = new SeekBar(this);
        sat.setMax(64);
        sat.setProgress(Prefs.saturation(this) - 12);
        controls.addView(sat, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        sat.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 12 + progress;
                satLabel.setText("진하기  " + value + "%");
                if (fromUser) {
                    Prefs.setSaturation(LockOverlayActivity.this, value);
                    todoWidget.refresh();
                }
            }
        });

        controls.addView(sectionTitle("글자 크기"));
        TextView textLabel = smallValue(String.format(
                Locale.KOREAN, "%.0f%%", Prefs.textScale(this) * 100f));
        controls.addView(textLabel);
        SeekBar textSize = new SeekBar(this);
        textSize.setMax(83);
        textSize.setProgress(Math.round((Prefs.textScale(this) - .82f) * 100f));
        controls.addView(textSize, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        textSize.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = .82f + progress / 100f;
                textLabel.setText(String.format(Locale.KOREAN, "%.0f%%", value * 100f));
                if (fromUser) {
                    Prefs.setTextScale(LockOverlayActivity.this, value);
                    refreshTodoKeepingCenter();
                }
            }
        });

        controls.addView(sectionTitle("일정 내용"));
        controls.addView(label(
                "업무·개인·기타 태그는 평소 화면에서도 바로 눌러 바꿀 수 있어요.",
                11.5f, false, Color.rgb(105, 116, 136)));

        editRowsContainer = new LinearLayout(this);
        editRowsContainer.setOrientation(LinearLayout.VERTICAL);
        controls.addView(editRowsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildEditorRows();

        Button add = softButton("+ 일정 추가");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45));
        addLp.setMargins(0, dp(7), 0, 0);
        controls.addView(add, addLp);
        add.setOnClickListener(v -> addEditorRow("", "업무", true));

        close.setOnClickListener(v -> closeDetailEditor());

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(300), Math.round(getResources().getDisplayMetrics().heightPixels * .48f)),
                Gravity.BOTTOM);
        panelLp.setMargins(dp(10), dp(78), dp(10), dp(10));
        root.addView(detailPanel, panelLp);
        detailPanel.bringToFront();
        if (editToolbar != null) editToolbar.bringToFront();
    }

    private void closeDetailEditor() {
        if (detailPanel == null) return;

        saveEditorRows();

        View panel = detailPanel;
        detailPanel = null;
        editRowsContainer = null;
        editRows.clear();
        root.removeView(panel);
        hideKeyboard();

        if (editToolbar != null) editToolbar.bringToFront();
        resume("detail_editor");
    }

    private void rebuildEditorRows() {
        if (editRowsContainer == null) return;
        editRows.clear();
        editRowsContainer.removeAllViews();

        List<String> items = Prefs.items(this);
        List<String> categories = Prefs.categories(this);
        for (int i = 0; i < items.size(); i++) {
            addEditorRow(
                    items.get(i),
                    i < categories.size() ? categories.get(i) : "업무",
                    false);
        }
    }

    private void addEditorRow(String value, String category, boolean focus) {
        if (editRowsContainer == null || editRows.size() >= 12) return;

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setHint("할 일 입력");
        edit.setTextSize(14);
        edit.setTextColor(Color.rgb(38, 48, 67));
        edit.setHintTextColor(Color.rgb(155, 164, 179));
        edit.setPadding(dp(11), 0, dp(8), 0);
        edit.setBackground(rounded(
                Color.rgb(248, 249, 253),
                dp(13),
                Color.rgb(226, 231, 240),
                dp(1)));
        row.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"업무", "개인", "기타"});
        spinner.setAdapter(adapter);
        spinner.setSelection("개인".equals(category) ? 1 : "기타".equals(category) ? 2 : 0);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(80), dp(44));
        spinLp.setMargins(dp(5), 0, 0, 0);
        row.addView(spinner, spinLp);

        Button remove = softButton("×");
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(42), dp(44));
        removeLp.setMargins(dp(4), 0, 0, 0);
        row.addView(remove, removeLp);

        EditRow entry = new EditRow(row, edit, spinner);
        editRows.add(entry);
        editRowsContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveEditorRows(); }
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveEditorRows();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        remove.setOnClickListener(v -> {
            editRows.remove(entry);
            editRowsContainer.removeView(row);
            saveEditorRows();
        });

        edit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pause("detail_input");
            else resume("detail_input");
        });

        if (focus) {
            edit.requestFocus();
            edit.postDelayed(() -> {
                edit.setSelection(edit.getText().length());
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }
    }

    private void saveEditorRows() {
        if (todoWidget == null || editRowsContainer == null) return;

        ArrayList<String> items = new ArrayList<>();
        ArrayList<String> categories = new ArrayList<>();
        for (EditRow row : editRows) {
            String value = row.edit.getText().toString().trim();
            if (value.isEmpty()) continue;
            items.add(value);
            Object selected = row.category.getSelectedItem();
            categories.add(selected == null ? "업무" : selected.toString());
        }

        Prefs.setItems(this, items, categories);
        refreshTodoKeepingCenter();
    }

    private void pause(String reason) {
        if (pauseReasons.add(reason) && pauseReasons.size() == 1) {
            if (finishTask != null) handler.removeCallbacks(finishTask);
            timerScheduled = false;
        }
        // Any active edit/typing/drawing session earns a fresh full display window
        // after the final interaction finishes.
        remainingMs = Prefs.duration(this);
    }

    private void resume(String reason) {
        pauseReasons.remove(reason);
        if (pauseReasons.isEmpty()) {
            remainingMs = Prefs.duration(this);
            scheduleTimer();
        }
    }

    private void scheduleTimer() {
        if (closing || !pauseReasons.isEmpty()) return;

        if (finishTask != null) handler.removeCallbacks(finishTask);
        remainingMs = Math.max(500L, Prefs.duration(this));
        finishTask = this::finishOverlay;
        handler.postDelayed(finishTask, remainingMs);
        timerScheduled = true;
    }

    private void finishOverlay() {
        if (closing) return;
        closing = true;

        if (finishTask != null) handler.removeCallbacks(finishTask);
        finishTask = null;
        timerScheduled = false;

        finish();
        overridePendingTransition(0, 0);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;

        focused.clearFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private TextView sectionTitle(String value) {
        TextView view = label(value, 14, true, Color.rgb(48, 58, 78));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(9), 0, dp(4));
        view.setLayoutParams(lp);
        return view;
    }

    private TextView smallValue(String value) {
        return label(value, 12, false, Color.rgb(104, 115, 134));
    }

    private TextView label(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button softButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12.5f);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(47, 57, 76));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(rounded(
                Color.rgb(248, 249, 252),
                dp(13),
                Color.rgb(225, 230, 239),
                dp(1)));
        return button;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12.5f);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(rounded(
                Color.rgb(83, 112, 244),
                dp(13),
                Color.TRANSPARENT,
                0));
        return button;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private float centerX(View view) { return view.getX() + view.getWidth() / 2f; }
    private float centerY(View view) { return view.getY() + view.getHeight() / 2f; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clampFloat(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private static final class EditRow {
        final LinearLayout row;
        final EditText edit;
        final Spinner category;

        EditRow(LinearLayout row, EditText edit, Spinner category) {
            this.row = row;
            this.edit = edit;
            this.category = category;
        }
    }


}
