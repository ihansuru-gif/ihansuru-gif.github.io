package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LockOverlayActivity extends Activity {
    private static WeakReference<LockOverlayActivity> current = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> pauseReasons = new HashSet<>();
    private final ArrayList<EditRow> editRows = new ArrayList<>();

    private FrameLayout root;
    private GestureFrame todoFrame;
    private GestureFrame imageFrame;
    private LockTodoWidget todoWidget;
    private ImageView imageView;
    private Bitmap imageBitmap;
    private LinearLayout editPanel;
    private LinearLayout editRowsContainer;

    private Runnable finishTask;
    private long remainingMs;
    private long timerStartedAt;
    private boolean timerScheduled;
    private boolean closing;

    static boolean launch(Context context) {
        Context app = context.getApplicationContext();
        if (Prefs.showImage(app) && !ImageStore.has(app)) return false;
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
        if (imageBitmap != null && !imageBitmap.isRecycled()) imageBitmap.recycle();
        imageBitmap = null;
        LockOverlayActivity existing = current.get();
        if (existing == this) current = new WeakReference<>(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (editPanel != null) {
            closeEditor();
            return;
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.argb(36, 0, 0, 0));
        }
    }

    private void buildObjects() {
        if (Prefs.showImage(this)) buildImageObject();
        if (Prefs.showTodo(this)) buildTodoObject();
    }

    private void buildImageObject() {
        imageBitmap = ImageStore.load(this, 2400);
        if (imageBitmap == null) return;
        imageFrame = new GestureFrame(this, GestureFrame.KIND_IMAGE);
        imageFrame.setGestureListener(new GestureFrame.GestureListener() {
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(imageFrame);
    }

    private void buildTodoObject() {
        todoFrame = new GestureFrame(this, GestureFrame.KIND_TODO);
        todoFrame.setGestureListener(new GestureFrame.GestureListener() {
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
                openEditor();
            }

            @Override public void onAdd(String text) {
                Prefs.addItem(LockOverlayActivity.this, text, "업무");
                refreshTodoKeepingCenter();
            }

            @Override public void onInteractionChanged(boolean active) {
                if (active) pause("quick_input");
                else resume("quick_input");
            }
        });
        todoFrame.addView(todoWidget, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(todoFrame);
    }

    private void layoutObjects() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        if (imageFrame != null) layoutImageFrame(true);
        if (todoFrame != null) layoutTodoFrame(true);
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (fromPrefs) placeByCenter(imageFrame, Prefs.imageX(this), Prefs.imageY(this));
        else clampPosition(imageFrame);
    }

    private void layoutTodoFrame(boolean fromPrefs) {
        int sw = root.getWidth();
        int sh = root.getHeight();
        if (sw <= 0 || sh <= 0 || todoWidget == null || todoFrame == null) return;

        int width = clamp(Math.round(sw * Prefs.todoWidth(this) / 100f), dp(252), Math.round(sw * .96f));
        todoWidget.refresh();
        todoWidget.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(sh * .82f), View.MeasureSpec.AT_MOST));
        int height = Math.min(todoWidget.getMeasuredHeight(), Math.round(sh * .82f));
        todoFrame.setLayoutParams(new FrameLayout.LayoutParams(width, Math.max(dp(180), height)));
        todoWidget.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (fromPrefs) placeByCenter(todoFrame, Prefs.todoX(this), Prefs.todoY(this));
        else clampPosition(todoFrame);
    }

    private void refreshTodoKeepingCenter() {
        if (todoFrame == null || root.getWidth() <= 0) return;
        float cx = centerX(todoFrame) / root.getWidth();
        float cy = centerY(todoFrame) / root.getHeight();
        layoutTodoFrame(false);
        placeByCenter(todoFrame, cx, cy);
    }

    private void placeByCenter(View view, float nx, float ny) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
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
        Prefs.setTodoPosition(this,
                centerX(todoFrame) / root.getWidth(),
                centerY(todoFrame) / root.getHeight());
        int widthPct = Math.round(todoFrame.getWidth() * 100f / root.getWidth());
        Prefs.setTodoWidth(this, widthPct);
        if (startH > 0 && todoFrame.getHeight() > 0) {
            float ratio = todoFrame.getHeight() / (float) startH;
            Prefs.setTodoScale(this, Prefs.todoScale(this) * ratio);
        }
        layoutTodoFrame(false);
        clampPosition(todoFrame);
        Prefs.setTodoPosition(this,
                centerX(todoFrame) / root.getWidth(),
                centerY(todoFrame) / root.getHeight());
    }

    private void persistImageGesture(int startW, int startH) {
        if (imageFrame == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        Prefs.setImagePosition(this,
                centerX(imageFrame) / root.getWidth(),
                centerY(imageFrame) / root.getHeight());
        if (startW > 0 && imageFrame.getWidth() > 0) {
            float ratio = imageFrame.getWidth() / (float) startW;
            Prefs.setImageSize(this, Math.round(Prefs.imageSize(this) * ratio));
        }
        layoutImageFrame(false);
        clampPosition(imageFrame);
        Prefs.setImagePosition(this,
                centerX(imageFrame) / root.getWidth(),
                centerY(imageFrame) / root.getHeight());
    }

    private void openEditor() {
        if (editPanel != null || todoWidget == null) return;
        pause("editor");
        todoWidget.clearInputFocus();
        if (todoFrame != null) todoFrame.setEditing(true);
        if (imageFrame != null) imageFrame.setEditing(true);

        editPanel = new LinearLayout(this);
        editPanel.setOrientation(LinearLayout.VERTICAL);
        editPanel.setPadding(dp(16), dp(14), dp(16), dp(14));
        editPanel.setBackground(rounded(Color.argb(252, 255, 255, 255), dp(26), Color.rgb(228, 232, 241), dp(1)));
        editPanel.setElevation(dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("투두 편집", 18, true, Color.rgb(38, 48, 67));
        Button close = softButton("닫기");
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        top.addView(close, new LinearLayout.LayoutParams(dp(74), dp(42)));
        editPanel.addView(top);
        editPanel.addView(label("카드와 이미지는 테두리를 끌어 이동하고, 오른쪽 아래 모서리를 잡아 크기를 바꿔요.",
                11.5f, false, Color.rgb(107, 118, 137)), matchWrap(dp(2)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(7), 0, dp(8));
        scroll.addView(controls, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        controls.addView(sectionTitle("카드 색상"));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setHue(Prefs.hue(this));
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(dp(148), dp(148));
        wheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        wheelLp.setMargins(0, dp(4), 0, dp(3));
        controls.addView(wheel, wheelLp);
        wheel.setListener(hue -> {
            Prefs.setHue(this, hue);
            todoWidget.refresh();
        });

        TextView saturationLabel = smallValue("진하기  " + Prefs.saturation(this) + "%");
        controls.addView(saturationLabel);
        SeekBar saturation = new SeekBar(this);
        saturation.setMax(64);
        saturation.setProgress(Prefs.saturation(this) - 12);
        controls.addView(saturation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        saturation.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 12 + progress;
                saturationLabel.setText("진하기  " + value + "%");
                if (fromUser) {
                    Prefs.setSaturation(LockOverlayActivity.this, value);
                    todoWidget.refresh();
                }
            }
        });

        controls.addView(sectionTitle("글자 크기"));
        TextView textSizeLabel = smallValue(String.format(Locale.KOREAN, "%.0f%%", Prefs.textScale(this) * 100f));
        controls.addView(textSizeLabel);
        SeekBar textSize = new SeekBar(this);
        textSize.setMax(83);
        textSize.setProgress(Math.round((Prefs.textScale(this) - .82f) * 100f));
        controls.addView(textSize, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        textSize.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = .82f + progress / 100f;
                textSizeLabel.setText(String.format(Locale.KOREAN, "%.0f%%", value * 100f));
                if (fromUser) {
                    Prefs.setTextScale(LockOverlayActivity.this, value);
                    refreshTodoKeepingCenter();
                }
            }
        });

        controls.addView(sectionTitle("일정 내용"));
        editRowsContainer = new LinearLayout(this);
        editRowsContainer.setOrientation(LinearLayout.VERTICAL);
        controls.addView(editRowsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildEditorRows();

        Button add = softButton("+ 일정 추가");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        addLp.setMargins(0, dp(7), 0, 0);
        controls.addView(add, addLp);
        add.setOnClickListener(v -> addEditorRow("", "업무", true));

        close.setOnClickListener(v -> closeEditor());

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(330), Math.round(getResources().getDisplayMetrics().heightPixels * .52f)),
                Gravity.BOTTOM);
        panelLp.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(editPanel, panelLp);
        editPanel.bringToFront();
    }

    private void closeEditor() {
        if (editPanel == null) return;
        saveEditorRows();
        View panel = editPanel;
        editPanel = null;
        editRowsContainer = null;
        editRows.clear();
        root.removeView(panel);
        if (todoFrame != null) todoFrame.setEditing(false);
        if (imageFrame != null) imageFrame.setEditing(false);
        hideKeyboard();
        resume("editor");
    }

    private void rebuildEditorRows() {
        if (editRowsContainer == null) return;
        editRows.clear();
        editRowsContainer.removeAllViews();
        List<String> items = Prefs.items(this);
        List<String> categories = Prefs.categories(this);
        for (int i = 0; i < items.size(); i++) {
            addEditorRow(items.get(i), i < categories.size() ? categories.get(i) : "업무", false);
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
        edit.setBackground(rounded(Color.rgb(248, 249, 253), dp(13), Color.rgb(226, 231, 240), dp(1)));
        row.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"업무", "개인", "기타"});
        spinner.setAdapter(adapter);
        spinner.setSelection("개인".equals(category) ? 1 : "기타".equals(category) ? 2 : 0);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(82), dp(44));
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
            if (hasFocus) pause("panel_input");
            else resume("panel_input");
        });

        if (focus) {
            edit.requestFocus();
            edit.postDelayed(() -> {
                edit.setSelection(edit.getText().length());
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }
    }

    private void saveEditorRows() {
        if (todoWidget == null || editRows.isEmpty() && editRowsContainer == null) return;
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
        if (pauseReasons.add(reason) && pauseReasons.size() == 1 && timerScheduled) {
            long elapsed = Math.max(0L, SystemClock.uptimeMillis() - timerStartedAt);
            remainingMs = Math.max(0L, remainingMs - elapsed);
            if (finishTask != null) handler.removeCallbacks(finishTask);
            timerScheduled = false;
        }
    }

    private void resume(String reason) {
        pauseReasons.remove(reason);
        if (pauseReasons.isEmpty()) scheduleTimer();
    }

    private void scheduleTimer() {
        if (closing || !pauseReasons.isEmpty()) return;
        if (finishTask != null) handler.removeCallbacks(finishTask);
        if (remainingMs <= 0L) {
            finishOverlay();
            return;
        }
        timerStartedAt = SystemClock.uptimeMillis();
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
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private TextView sectionTitle(String value) {
        TextView view = label(value, 14, true, Color.rgb(48, 58, 78));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(47, 57, 76));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(rounded(Color.rgb(248, 249, 252), dp(14), Color.rgb(225, 230, 239), dp(1)));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, 0);
        return lp;
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

    private static final class GestureFrame extends FrameLayout {
        static final int KIND_TODO = 1;
        static final int KIND_IMAGE = 2;

        interface GestureListener {
            void onGestureStart(int kind);
            void onGestureEnd(int kind, int startW, int startH);
        }

        private final android.graphics.Paint borderPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint handlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private GestureListener listener;
        private boolean editing;
        private boolean resizing;
        private float downRawX;
        private float downRawY;
        private float startX;
        private float startY;
        private int startW;
        private int startH;

        GestureFrame(Context context, int kind) {
            super(context);
            this.kind = kind;
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
        }

        void setGestureListener(GestureListener value) { listener = value; }

        void setEditing(boolean value) {
            editing = value;
            invalidate();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (!editing) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                begin(event);
                return true;
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!editing) return super.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                begin(event);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (resizing) {
                    int minW = dpLocal(kind == KIND_TODO ? 240 : 64);
                    int minH = dpLocal(kind == KIND_TODO ? 150 : 64);
                    int width = Math.max(minW, Math.round(startW + dx));
                    int height = Math.max(minH, Math.round(startH + dy));
                    ViewGroup.LayoutParams lp = getLayoutParams();
                    lp.width = width;
                    lp.height = height;
                    setLayoutParams(lp);
                } else {
                    View parent = (View) getParent();
                    float maxX = Math.max(0, parent.getWidth() - getWidth());
                    float maxY = Math.max(0, parent.getHeight() - getHeight());
                    setX(clampLocal(startX + dx, 0, maxX));
                    setY(clampLocal(startY + dy, 0, maxY));
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (listener != null) listener.onGestureEnd(kind, startW, startH);
                performClick();
                return true;
            }
            return true;
        }

        private void begin(MotionEvent event) {
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            startX = getX();
            startY = getY();
            startW = getWidth();
            startH = getHeight();
            int handle = dpLocal(42);
            resizing = event.getX() >= getWidth() - handle && event.getY() >= getHeight() - handle;
            if (listener != null) listener.onGestureStart(kind);
        }

        @Override
        protected void dispatchDraw(android.graphics.Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!editing) return;
            borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dpLocal(2));
            borderPaint.setColor(Color.rgb(80, 133, 246));
            float inset = dpLocal(2);
            canvas.drawRoundRect(new android.graphics.RectF(inset, inset, getWidth() - inset, getHeight() - inset),
                    dpLocal(12), dpLocal(12), borderPaint);
            handlePaint.setStyle(android.graphics.Paint.Style.FILL);
            handlePaint.setColor(Color.WHITE);
            handlePaint.setShadowLayer(dpLocal(3), 0, dpLocal(1), Color.argb(70, 0, 0, 0));
            float r = dpLocal(8);
            canvas.drawCircle(getWidth() - dpLocal(5), getHeight() - dpLocal(5), r, handlePaint);
            handlePaint.clearShadowLayer();
            handlePaint.setStyle(android.graphics.Paint.Style.STROKE);
            handlePaint.setStrokeWidth(dpLocal(2));
            handlePaint.setColor(Color.rgb(80, 133, 246));
            canvas.drawCircle(getWidth() - dpLocal(5), getHeight() - dpLocal(5), r, handlePaint);
        }

        @Override public boolean performClick() { super.performClick(); return true; }
        private int dpLocal(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
        private float clampLocal(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    }
}
