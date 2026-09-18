package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class MemoBoardView extends FrameLayout {
    interface Callback {
        void onGear();
        void onInteractionChanged(boolean active);
    }

    private static final int MODE_TEXT = 0;
    private static final int MODE_DRAW = 1;
    private static final int MODE_LINK = 2;

    private final Set<String> interactions = new HashSet<>();
    private Callback callback;

    private LinearLayout main;
    private TextView pageLabel;
    private EditText titleEdit;
    private EditText bodyEdit;
    private FrameLayout contentFrame;
    private LinearLayout drawPane;
    private LinearLayout linkPane;
    private DoodleView doodle;
    private LinearLayout linkList;
    private EditText linkInput;
    private TextView listPanel;
    private LinearLayout listRows;
    private LinearLayout colorPanel;
    private ColorWheelView paperWheel;
    private ColorWheelView brushWheel;
    private SeekBar paperSatSeek;
    private SeekBar paperValueSeek;
    private SeekBar brushSatSeek;
    private SeekBar brushValueSeek;
    private SeekBar brushWidthSeek;
    private TextView paperSatLabel;
    private TextView paperValueLabel;
    private TextView brushSatLabel;
    private TextView brushValueLabel;
    private TextView brushWidthLabel;

    private Button textTab;
    private Button drawTab;
    private Button linkTab;

    private ArrayList<MemoStore.Memo> memos = new ArrayList<>();
    private int currentIndex;
    private int mode = MODE_TEXT;
    private boolean loading;

    MemoBoardView(Context context) {
        super(context);
        init();
    }

    void setCallback(Callback value) { callback = value; }

    void refreshScale() {
        float s = Prefs.memoTextScale(getContext());
        pageLabel.setTextSize(13f * s);
        titleEdit.setTextSize(17f * s);
        bodyEdit.setTextSize(14f * s);
        linkInput.setTextSize(13f * s);
        rebuildLinks();
        rebuildList();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);

        main = new LinearLayout(getContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(12), dp(14), dp(14));
        main.setBackground(rounded(Color.rgb(255, 253, 248), dp(24), Color.rgb(229, 224, 214), dp(1)));
        addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);

        pageLabel = text("메모 1/1", 13, true, Color.rgb(78, 67, 57));
        top.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button list = soft("☰");
        top.addView(list, new LinearLayout.LayoutParams(dp(44), dp(38)));

        Button add = soft("+");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(44), dp(38));
        addLp.setMargins(dp(5), 0, 0, 0);
        top.addView(add, addLp);

        Button color = soft("색");
        LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(dp(44), dp(38));
        colorLp.setMargins(dp(5), 0, 0, 0);
        top.addView(color, colorLp);

        Button gear = soft("⚙");
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(44), dp(38));
        gearLp.setMargins(dp(5), 0, 0, 0);
        top.addView(gear, gearLp);

        main.addView(top);

        titleEdit = new EditText(getContext());
        titleEdit.setSingleLine(true);
        titleEdit.setHint("메모 제목");
        titleEdit.setTextSize(17);
        titleEdit.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleEdit.setTextColor(Color.rgb(54, 49, 45));
        titleEdit.setHintTextColor(Color.rgb(160, 151, 141));
        titleEdit.setPadding(dp(2), 0, dp(2), 0);
        titleEdit.setBackgroundColor(Color.TRANSPARENT);
        main.addView(titleEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        textTab = miniTab("T 텍스트");
        drawTab = miniTab("✎ 낙서");
        linkTab = miniTab("🔗 링크");
        tabs.addView(textTab, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(38), 1);
        dlp.setMargins(dp(5), 0, 0, 0);
        tabs.addView(drawTab, dlp);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, dp(38), 1);
        llp.setMargins(dp(5), 0, 0, 0);
        tabs.addView(linkTab, llp);
        main.addView(tabs);

        contentFrame = new FrameLayout(getContext());
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        contentLp.setMargins(0, dp(7), 0, 0);
        main.addView(contentFrame, contentLp);

        buildTextPane();
        buildDrawPane();
        buildLinkPane();
        buildListPanel();
        buildColorPanel();

        titleEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!loading) saveCurrentFromUi();
            }
        });
        bodyEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!loading) saveCurrentFromUi();
            }
        });

        setFocusTracking(titleEdit, "title");
        setFocusTracking(bodyEdit, "body");
        setFocusTracking(linkInput, "link");

        list.setOnClickListener(v -> showMemoList());
        add.setOnClickListener(v -> addMemo());
        color.setOnClickListener(v -> showColorPanel());
        gear.setOnClickListener(v -> {
            if (callback != null) callback.onGear();
        });
        textTab.setOnClickListener(v -> setMode(MODE_TEXT));
        drawTab.setOnClickListener(v -> setMode(MODE_DRAW));
        linkTab.setOnClickListener(v -> setMode(MODE_LINK));

        memos = MemoStore.load(getContext());
        currentIndex = MemoStore.activeIndex(getContext(), memos.size());
        loadCurrent();
        setMode(MODE_TEXT);
        refreshScale();
    }

    private void buildTextPane() {
        bodyEdit = new EditText(getContext());
        bodyEdit.setGravity(Gravity.TOP | Gravity.START);
        bodyEdit.setHint("여기에 자유롭게 메모하세요");
        bodyEdit.setTextSize(14);
        bodyEdit.setTextColor(Color.rgb(58, 54, 50));
        bodyEdit.setHintTextColor(Color.rgb(165, 157, 148));
        bodyEdit.setPadding(dp(9), dp(8), dp(9), dp(8));
        bodyEdit.setBackground(rounded(Color.WHITE, dp(14), Color.rgb(232, 227, 219), dp(1)));
        contentFrame.addView(bodyEdit, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildDrawPane() {
        drawPane = new LinearLayout(getContext());
        drawPane.setOrientation(LinearLayout.VERTICAL);
        drawPane.setVisibility(GONE);

        doodle = new DoodleView(getContext());
        doodle.setBackground(rounded(Color.WHITE, dp(14), Color.rgb(232, 227, 219), dp(1)));
        drawPane.addView(doodle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout tools = new LinearLayout(getContext());
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(5), 0, 0);
        Button black = colorButton("검", Color.rgb(48, 54, 67));
        Button blue = colorButton("파", Color.rgb(70, 113, 224));
        Button red = colorButton("빨", Color.rgb(220, 86, 94));
        Button green = colorButton("초", Color.rgb(66, 153, 104));
        Button custom = soft("색");
        Button eraser = soft("지우개");
        Button undo = soft("↶");
        Button clear = soft("지움");
        tools.addView(black, toolLp());
        tools.addView(blue, toolLp());
        tools.addView(red, toolLp());
        tools.addView(green, toolLp());
        tools.addView(custom, toolLp());
        tools.addView(eraser, toolLpWide());
        tools.addView(undo, toolLp());
        tools.addView(clear, toolLpWide());
        drawPane.addView(tools, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        black.setOnClickListener(v -> setPresetBrush(Color.rgb(48, 54, 67)));
        blue.setOnClickListener(v -> setPresetBrush(Color.rgb(70, 113, 224)));
        red.setOnClickListener(v -> setPresetBrush(Color.rgb(220, 86, 94)));
        green.setOnClickListener(v -> setPresetBrush(Color.rgb(66, 153, 104)));
        custom.setOnClickListener(v -> showColorPanel());
        eraser.setOnClickListener(v -> doodle.setEraser(true));
        undo.setOnClickListener(v -> doodle.undo());
        clear.setOnClickListener(v -> doodle.clearAll());

        applyBrushPrefs();

        doodle.setChangeListener(serialized -> {
            if (loading || currentIndex < 0 || currentIndex >= memos.size()) return;
            memos.get(currentIndex).doodle = serialized;
            MemoStore.save(getContext(), memos);
        });
        doodle.setInteractionListener(active -> setInteraction("draw", active));

        contentFrame.addView(drawPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildLinkPane() {
        linkPane = new LinearLayout(getContext());
        linkPane.setOrientation(LinearLayout.VERTICAL);
        linkPane.setVisibility(GONE);

        LinearLayout inputRow = new LinearLayout(getContext());
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        linkInput = new EditText(getContext());
        linkInput.setSingleLine(true);
        linkInput.setHint("https:// 링크 붙여넣기");
        linkInput.setTextSize(13);
        linkInput.setTextColor(Color.rgb(50, 57, 70));
        linkInput.setHintTextColor(Color.rgb(157, 164, 176));
        linkInput.setPadding(dp(10), 0, dp(8), 0);
        linkInput.setBackground(rounded(Color.WHITE, dp(13), Color.rgb(228, 232, 240), dp(1)));
        inputRow.addView(linkInput, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button addLink = soft("+");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(42));
        alp.setMargins(dp(6), 0, 0, 0);
        inputRow.addView(addLink, alp);
        linkPane.addView(inputRow);

        ScrollView scroll = new ScrollView(getContext());
        linkList = new LinearLayout(getContext());
        linkList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(linkList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        slp.setMargins(0, dp(7), 0, 0);
        linkPane.addView(scroll, slp);

        addLink.setOnClickListener(v -> addLink());
        linkInput.setOnEditorActionListener((v, actionId, event) -> {
            addLink();
            return true;
        });

        contentFrame.addView(linkPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildColorPanel() {
        colorPanel = new LinearLayout(getContext());
        colorPanel.setOrientation(LinearLayout.VERTICAL);
        colorPanel.setPadding(dp(14), dp(12), dp(14), dp(14));
        colorPanel.setBackground(rounded(
                Color.rgb(255, 255, 255), dp(24), Color.rgb(220, 224, 234), dp(1)));
        colorPanel.setVisibility(GONE);

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("색 조절", 17, true, Color.rgb(55, 58, 68)),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        Button reset = soft("기본");
        top.addView(reset, new LinearLayout.LayoutParams(dp(62), dp(38)));
        Button close = soft("닫기");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(62), dp(38));
        closeLp.setMargins(dp(5), 0, 0, 0);
        top.addView(close, closeLp);
        colorPanel.addView(top);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(controls, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        colorPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        controls.addView(text("메모지 색", 14, true, Color.rgb(66, 61, 57)));
        paperWheel = new ColorWheelView(getContext());
        LinearLayout.LayoutParams paperWheelLp = new LinearLayout.LayoutParams(dp(136), dp(136));
        paperWheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(paperWheel, paperWheelLp);
        paperSatLabel = text("", 12, false, Color.rgb(105, 105, 112));
        controls.addView(paperSatLabel);
        paperSatSeek = new SeekBar(getContext());
        paperSatSeek.setMax(45);
        controls.addView(paperSatSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        paperValueLabel = text("", 12, false, Color.rgb(105, 105, 112));
        controls.addView(paperValueLabel);
        paperValueSeek = new SeekBar(getContext());
        paperValueSeek.setMax(20);
        controls.addView(paperValueSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        TextView brushTitle = text("브러시 색 · 굵기", 14, true, Color.rgb(66, 61, 57));
        LinearLayout.LayoutParams brushTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brushTitleLp.setMargins(0, dp(10), 0, 0);
        controls.addView(brushTitle, brushTitleLp);
        brushWheel = new ColorWheelView(getContext());
        LinearLayout.LayoutParams brushWheelLp = new LinearLayout.LayoutParams(dp(136), dp(136));
        brushWheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(brushWheel, brushWheelLp);
        brushSatLabel = text("", 12, false, Color.rgb(105, 105, 112));
        controls.addView(brushSatLabel);
        brushSatSeek = new SeekBar(getContext());
        brushSatSeek.setMax(100);
        controls.addView(brushSatSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        brushValueLabel = text("", 12, false, Color.rgb(105, 105, 112));
        controls.addView(brushValueLabel);
        brushValueSeek = new SeekBar(getContext());
        brushValueSeek.setMax(92);
        controls.addView(brushValueSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        brushWidthLabel = text("", 12, false, Color.rgb(105, 105, 112));
        controls.addView(brushWidthLabel);
        brushWidthSeek = new SeekBar(getContext());
        brushWidthSeek.setMax(16);
        controls.addView(brushWidthSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        addView(colorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        paperWheel.setListener(hue -> {
            if (memos.isEmpty()) return;
            memos.get(currentIndex).paperHue = hue;
            saveColorState();
        });
        paperSatSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                paperSatLabel.setText("채도  " + progress + "%");
                if (fromUser && !memos.isEmpty()) {
                    memos.get(currentIndex).paperSat = progress;
                    saveColorState();
                }
            }
        });
        paperValueSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 80 + progress;
                paperValueLabel.setText("밝기  " + value + "%");
                if (fromUser && !memos.isEmpty()) {
                    memos.get(currentIndex).paperValue = value;
                    saveColorState();
                }
            }
        });
        brushWheel.setListener(hue -> {
            Prefs.setMemoBrushHue(getContext(), hue);
            applyBrushPrefs();
            syncColorPanelValues();
        });
        brushSatSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brushSatLabel.setText("채도  " + progress + "%");
                if (fromUser) {
                    Prefs.setMemoBrushSat(getContext(), progress);
                    applyBrushPrefs();
                }
            }
        });
        brushValueSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 8 + progress;
                brushValueLabel.setText("밝기  " + value + "%");
                if (fromUser) {
                    Prefs.setMemoBrushValue(getContext(), value);
                    applyBrushPrefs();
                }
            }
        });
        brushWidthSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float width = 2f + progress;
                brushWidthLabel.setText(String.format(Locale.KOREAN, "굵기  %.0f", width));
                if (fromUser) {
                    Prefs.setMemoBrushWidth(getContext(), width);
                    applyBrushPrefs();
                }
            }
        });

        reset.setOnClickListener(v -> {
            if (!memos.isEmpty()) {
                MemoStore.Memo m = memos.get(currentIndex);
                m.paperHue = 42f;
                m.paperSat = 6;
                m.paperValue = 100;
                MemoStore.save(getContext(), memos);
            }
            Prefs.setMemoBrushHue(getContext(), 220f);
            Prefs.setMemoBrushSat(getContext(), 82);
            Prefs.setMemoBrushValue(getContext(), 92);
            Prefs.setMemoBrushWidth(getContext(), 5f);
            applyMemoAppearance();
            applyBrushPrefs();
            syncColorPanelValues();
        });
        close.setOnClickListener(v -> hideColorPanel());
    }

    private void showColorPanel() {
        if (colorPanel == null) return;
        syncColorPanelValues();
        colorPanel.setVisibility(VISIBLE);
        colorPanel.bringToFront();
        setInteraction("color_panel", true);
    }

    private void hideColorPanel() {
        if (colorPanel == null) return;
        colorPanel.setVisibility(GONE);
        setInteraction("color_panel", false);
    }

    private void syncColorPanelValues() {
        if (colorPanel == null || memos.isEmpty()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        paperWheel.setHue(m.paperHue);
        paperSatSeek.setProgress(m.paperSat);
        paperValueSeek.setProgress(m.paperValue - 80);
        paperSatLabel.setText("채도  " + m.paperSat + "%");
        paperValueLabel.setText("밝기  " + m.paperValue + "%");

        brushWheel.setHue(Prefs.memoBrushHue(getContext()));
        brushSatSeek.setProgress(Prefs.memoBrushSat(getContext()));
        brushValueSeek.setProgress(Prefs.memoBrushValue(getContext()) - 8);
        brushWidthSeek.setProgress(Math.round(Prefs.memoBrushWidth(getContext()) - 2f));
        brushSatLabel.setText("채도  " + Prefs.memoBrushSat(getContext()) + "%");
        brushValueLabel.setText("밝기  " + Prefs.memoBrushValue(getContext()) + "%");
        brushWidthLabel.setText(String.format(
                Locale.KOREAN, "굵기  %.0f", Prefs.memoBrushWidth(getContext())));
    }

    private void saveColorState() {
        MemoStore.save(getContext(), memos);
        applyMemoAppearance();
        rebuildList();
        syncColorPanelValues();
    }

    private void applyBrushPrefs() {
        if (doodle == null) return;
        doodle.setPenColor(Prefs.memoBrushColor(getContext()));
        doodle.setPenWidth(Prefs.memoBrushWidth(getContext()));
    }

    private void setPresetBrush(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        Prefs.setMemoBrushHue(getContext(), hsv[0]);
        Prefs.setMemoBrushSat(getContext(), Math.round(hsv[1] * 100f));
        Prefs.setMemoBrushValue(getContext(), Math.max(8, Math.round(hsv[2] * 100f)));
        applyBrushPrefs();
        if (colorPanel != null) syncColorPanelValues();
    }

    private int memoPaperColor(MemoStore.Memo memo) {
        return Color.HSVToColor(new float[]{
                memo.paperHue,
                memo.paperSat / 100f,
                memo.paperValue / 100f});
    }

    private void applyMemoAppearance() {
        if (memos.isEmpty() || main == null) return;
        int paper = memoPaperColor(memos.get(currentIndex));
        int surface = blend(paper, Color.WHITE, .32f);
        int border = blend(paper, Color.rgb(185, 181, 176), .45f);
        main.setBackground(rounded(paper, dp(24), border, dp(1)));
        bodyEdit.setBackground(rounded(surface, dp(14), border, dp(1)));
        if (doodle != null) doodle.setCanvasColor(surface);
        if (linkInput != null) linkInput.setBackground(rounded(surface, dp(13), border, dp(1)));
    }

    private int blend(int base, int overlay, float overlayAmount) {
        float a = Math.max(0f, Math.min(1f, overlayAmount));
        int r = Math.round(Color.red(base) * (1f - a) + Color.red(overlay) * a);
        int g = Math.round(Color.green(base) * (1f - a) + Color.green(overlay) * a);
        int b = Math.round(Color.blue(base) * (1f - a) + Color.blue(overlay) * a);
        return Color.rgb(r, g, b);
    }

    private void buildListPanel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));
        panel.setBackground(rounded(Color.rgb(255, 253, 248), dp(24), Color.rgb(220, 214, 204), dp(1)));

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("메모 목록", 17, true, Color.rgb(55, 50, 46)),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        Button add = soft("+ 새 메모");
        top.addView(add, new LinearLayout.LayoutParams(dp(92), dp(38)));
        Button close = soft("닫기");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(64), dp(38));
        clp.setMargins(dp(5), 0, 0, 0);
        top.addView(close, clp);
        panel.addView(top);

        TextView hint = text("누르면 열기 · ↑↓ 순서 · 📌 고정", 11.5f, false, Color.rgb(120, 111, 103));
        panel.addView(hint);

        ScrollView scroll = new ScrollView(getContext());
        listRows = new LinearLayout(getContext());
        listRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        slp.setMargins(0, dp(6), 0, 0);
        panel.addView(scroll, slp);

        listPanel = new TextView(getContext());
        addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.setTag("memo_list_panel");
        panel.setVisibility(GONE);

        add.setOnClickListener(v -> {
            hideMemoList();
            addMemo();
        });
        close.setOnClickListener(v -> hideMemoList());
    }

    private View findListPanel() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if ("memo_list_panel".equals(v.getTag())) return v;
        }
        return null;
    }

    private void showMemoList() {
        saveCurrentFromUi();
        rebuildList();
        View panel = findListPanel();
        if (panel != null) {
            panel.setVisibility(VISIBLE);
            panel.bringToFront();
            setInteraction("list", true);
        }
    }

    private void hideMemoList() {
        View panel = findListPanel();
        if (panel != null) panel.setVisibility(GONE);
        setInteraction("list", false);
    }

    private void rebuildList() {
        if (listRows == null) return;
        listRows.removeAllViews();
        for (int i = 0; i < memos.size(); i++) {
            final int index = i;
            MemoStore.Memo memo = memos.get(i);

            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(4), dp(4), dp(4));
            int rowPaper = memoPaperColor(memo);
            if (index == currentIndex) rowPaper = blend(rowPaper, Color.rgb(231, 225, 255), .24f);
            row.setBackground(rounded(
                    rowPaper, dp(13), Color.rgb(220, 216, 210), dp(1)));

            String name = displayName(memo, index);
            TextView title = text(name, 13, true, Color.rgb(58, 53, 49));
            title.setSingleLine(true);
            row.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

            Button pin = soft(memo.pinned ? "📌" : "핀");
            row.addView(pin, new LinearLayout.LayoutParams(dp(44), dp(38)));

            Button up = soft("↑");
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(38), dp(38));
            ulp.setMargins(dp(3), 0, 0, 0);
            row.addView(up, ulp);

            Button down = soft("↓");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(38), dp(38));
            dlp.setMargins(dp(3), 0, 0, 0);
            row.addView(down, dlp);

            Button del = soft("×");
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(dp(38), dp(38));
            xlp.setMargins(dp(3), 0, 0, 0);
            row.addView(del, xlp);

            title.setOnClickListener(v -> selectMemo(index));
            pin.setOnClickListener(v -> {
                long id = memos.get(index).id;
                memos = MemoStore.togglePin(getContext(), index);
                currentIndex = indexOfId(id);
                MemoStore.setActiveIndex(getContext(), currentIndex);
                loadCurrent();
                rebuildList();
            });
            up.setOnClickListener(v -> moveMemo(index, Math.max(0, index - 1)));
            down.setOnClickListener(v -> moveMemo(index, Math.min(memos.size() - 1, index + 1)));
            del.setOnClickListener(v -> deleteMemo(index));

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            rlp.setMargins(0, dp(4), 0, 0);
            listRows.addView(row, rlp);
        }
    }

    private void addMemo() {
        saveCurrentFromUi();
        currentIndex = MemoStore.add(getContext());
        memos = MemoStore.load(getContext());
        currentIndex = MemoStore.activeIndex(getContext(), memos.size());
        loadCurrent();
        setMode(MODE_TEXT);
        titleEdit.requestFocus();
        setInteraction("title", true);
        titleEdit.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void selectMemo(int index) {
        saveCurrentFromUi();
        currentIndex = Math.max(0, Math.min(memos.size() - 1, index));
        MemoStore.setActiveIndex(getContext(), currentIndex);
        loadCurrent();
        hideMemoList();
    }

    private void moveMemo(int from, int to) {
        if (from == to) return;
        long activeId = memos.get(currentIndex).id;
        memos = MemoStore.move(getContext(), from, to);
        currentIndex = indexOfId(activeId);
        MemoStore.setActiveIndex(getContext(), currentIndex);
        loadCurrent();
        rebuildList();
    }

    private void deleteMemo(int index) {
        long activeId = memos.get(currentIndex).id;
        boolean deletingActive = index == currentIndex;
        memos = MemoStore.delete(getContext(), index);
        currentIndex = deletingActive ? MemoStore.activeIndex(getContext(), memos.size()) : indexOfId(activeId);
        currentIndex = Math.max(0, Math.min(memos.size() - 1, currentIndex));
        MemoStore.setActiveIndex(getContext(), currentIndex);
        loadCurrent();
        rebuildList();
    }

    private int indexOfId(long id) {
        for (int i = 0; i < memos.size(); i++) if (memos.get(i).id == id) return i;
        return 0;
    }

    private void loadCurrent() {
        if (memos.isEmpty()) memos = MemoStore.load(getContext());
        currentIndex = Math.max(0, Math.min(memos.size() - 1, currentIndex));
        MemoStore.Memo m = memos.get(currentIndex);

        loading = true;
        titleEdit.setText(m.title);
        bodyEdit.setText(m.body);
        doodle.setSerialized(m.doodle);
        loading = false;

        pageLabel.setText(String.format(Locale.KOREAN, "메모 %d/%d", currentIndex + 1, memos.size()));
        applyMemoAppearance();
        applyBrushPrefs();
        rebuildLinks();
        rebuildList();
        if (colorPanel != null && colorPanel.getVisibility() == VISIBLE) syncColorPanelValues();
    }

    private void saveCurrentFromUi() {
        if (loading || memos.isEmpty() || currentIndex < 0 || currentIndex >= memos.size()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        m.title = titleEdit.getText().toString();
        m.body = bodyEdit.getText().toString();
        m.doodle = doodle.serialize();
        MemoStore.save(getContext(), memos);
        pageLabel.setText(String.format(Locale.KOREAN, "메모 %d/%d", currentIndex + 1, memos.size()));
    }

    private void addLink() {
        if (memos.isEmpty()) return;
        String value = normalizeUrl(linkInput.getText().toString());
        if (value.isEmpty()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        if (m.links.size() < 20) m.links.add(value);
        linkInput.setText("");
        MemoStore.save(getContext(), memos);
        rebuildLinks();
    }

    private void rebuildLinks() {
        if (linkList == null || memos.isEmpty()) return;
        linkList.removeAllViews();
        MemoStore.Memo m = memos.get(currentIndex);
        if (m.links.isEmpty()) {
            TextView empty = text("저장된 링크가 없어요", 12, false, Color.rgb(146, 150, 160));
            linkList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            return;
        }

        for (int i = 0; i < m.links.size(); i++) {
            final int index = i;
            String url = m.links.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView link = text(shortUrl(url), 12.5f, true, Color.rgb(67, 105, 198));
            link.setSingleLine(true);
            link.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(226, 231, 240), dp(1)));
            link.setPadding(dp(10), 0, dp(8), 0);
            row.addView(link, new LinearLayout.LayoutParams(0, dp(42), 1));

            Button remove = soft("×");
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(42), dp(42));
            rlp.setMargins(dp(5), 0, 0, 0);
            row.addView(remove, rlp);

            link.setOnClickListener(v -> openUrl(url));
            remove.setOnClickListener(v -> {
                if (index >= 0 && index < m.links.size()) {
                    m.links.remove(index);
                    MemoStore.save(getContext(), memos);
                    rebuildLinks();
                }
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            rowLp.setMargins(0, dp(4), 0, 0);
            linkList.addView(row, rowLp);
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), "링크를 열 수 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void setMode(int value) {
        mode = value;
        bodyEdit.setVisibility(mode == MODE_TEXT ? VISIBLE : GONE);
        drawPane.setVisibility(mode == MODE_DRAW ? VISIBLE : GONE);
        linkPane.setVisibility(mode == MODE_LINK ? VISIBLE : GONE);
        textTab.setAlpha(mode == MODE_TEXT ? 1f : .55f);
        drawTab.setAlpha(mode == MODE_DRAW ? 1f : .55f);
        linkTab.setAlpha(mode == MODE_LINK ? 1f : .55f);
        if (mode == MODE_DRAW) doodle.bringToFront();
    }

    private void setFocusTracking(EditText edit, String key) {
        edit.setOnFocusChangeListener((v, focused) -> setInteraction(key, focused));
    }

    private void setInteraction(String key, boolean active) {
        boolean wasEmpty = interactions.isEmpty();
        if (active) interactions.add(key);
        else interactions.remove(key);
        boolean isEmpty = interactions.isEmpty();
        if (callback != null && wasEmpty != isEmpty) callback.onInteractionChanged(!isEmpty);
    }

    private String displayName(MemoStore.Memo memo, int index) {
        String title = memo.title == null ? "" : memo.title.trim();
        if (!title.isEmpty()) return (memo.pinned ? "📌 " : "") + title;
        String body = memo.body == null ? "" : memo.body.trim();
        if (!body.isEmpty()) {
            int end = body.indexOf('\n');
            String first = end >= 0 ? body.substring(0, end) : body;
            if (first.length() > 20) first = first.substring(0, 20) + "…";
            return (memo.pinned ? "📌 " : "") + first;
        }
        return (memo.pinned ? "📌 " : "") + "메모 " + (index + 1);
    }

    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
        return value;
    }

    private String shortUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || host.isEmpty()) return url;
            if (path == null || path.equals("/")) return "🔗 " + host;
            String text = host + path;
            if (text.length() > 34) text = text.substring(0, 34) + "…";
            return "🔗 " + text;
        } catch (RuntimeException e) {
            return "🔗 " + url;
        }
    }

    private Button miniTab(String label) {
        Button b = soft(label);
        b.setTextSize(11.5f);
        return b;
    }

    private Button colorButton(String label, int color) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setTextSize(10.5f);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(rounded(color, dp(11), Color.TRANSPARENT, 0));
        return b;
    }

    private LinearLayout.LayoutParams toolLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(34));
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams toolLpWide() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(34));
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private Button soft(String value) {
        Button button = new Button(getContext());
        button.setText(value);
        button.setTextSize(11.5f);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(67, 61, 56));
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setBackground(rounded(Color.rgb(250, 247, 241), dp(12), Color.rgb(226, 220, 211), dp(1)));
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
