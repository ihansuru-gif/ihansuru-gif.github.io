package com.ihansuru.greetingtodo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class MemoBoardView extends FrameLayout {
    interface Callback {
        void onGear();
        void onInteractionChanged(boolean active);
        void onPickImage();
        void onRequestAudioPermission();
    }

    private static final int MODE_TEXT = 0;
    private static final int MODE_CHECK = 1;
    private static final int MODE_DRAW = 2;
    private static final int MODE_LINK = 3;
    private static final int MODE_IMAGE = 4;
    private static final int MODE_VOICE = 5;

    private final Set<String> interactions = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback callback;

    private LinearLayout main;
    private TextView pageLabel;
    private EditText titleEdit;
    private EditText tagsEdit;

    private FrameLayout contentFrame;
    private LinearLayout textPane;
    private LinearLayout checkPane;
    private LinearLayout drawPane;
    private LinearLayout linkPane;
    private LinearLayout imagePane;
    private LinearLayout voicePane;

    private EditText bodyEdit;
    private LinearLayout checkRows;
    private DoodleView doodle;
    private LinearLayout linkList;
    private EditText linkInput;
    private LinearLayout imageList;
    private LinearLayout voiceList;

    private Button textTab;
    private Button checkTab;
    private Button drawTab;
    private Button linkTab;
    private Button imageTab;
    private Button voiceTab;

    private LinearLayout listPanel;
    private LinearLayout listRows;
    private EditText searchInput;
    private Button archiveFilter;
    private boolean showingArchive;

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

    private Button recordButton;
    private Button pauseRecordButton;
    private Button stopRecordButton;
    private TextView recordingState;
    private MediaRecorder recorder;
    private String recordingPath = "";
    private boolean recording;
    private boolean recordingPaused;

    private MediaPlayer player;
    private String playingPath = "";
    private SeekBar activePlaybackSeek;
    private TextView activePlaybackLabel;
    private final Runnable playbackTicker = new Runnable() {
        @Override public void run() {
            if (player == null || activePlaybackSeek == null) return;
            try {
                int duration = Math.max(1, player.getDuration());
                int current = Math.max(0, player.getCurrentPosition());
                activePlaybackSeek.setMax(duration);
                activePlaybackSeek.setProgress(current);
                if (activePlaybackLabel != null) {
                    activePlaybackLabel.setText(timeLabel(current) + " / " + timeLabel(duration));
                }
                if (player.isPlaying()) handler.postDelayed(this, 250L);
            } catch (RuntimeException ignored) {}
        }
    };

    private ArrayList<MemoStore.Memo> memos = new ArrayList<>();
    private final ArrayList<CheckRow> checkEditors = new ArrayList<>();
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
        tagsEdit.setTextSize(12.5f * s);
        bodyEdit.setTextSize(14f * s);
        linkInput.setTextSize(13f * s);
        rebuildChecklist();
        rebuildLinks();
        rebuildImages();
        rebuildVoice();
        rebuildList();
    }

    void attachImage(Uri uri) {
        if (uri == null || memos.isEmpty()) return;
        setInteraction("image_import", true);
        String path = MemoMediaStore.importImage(getContext(), uri);
        if (!path.isEmpty()) {
            MemoStore.Memo m = memos.get(currentIndex);
            if (m.imagePaths.size() < 12) {
                m.imagePaths.add(path);
                MemoStore.save(getContext(), memos);
                rebuildImages();
                setMode(MODE_IMAGE);
            } else {
                MemoMediaStore.deletePath(path);
                toast("이미지는 메모당 12개까지 넣을 수 있어요");
            }
        } else {
            toast("이미지를 불러오지 못했어요");
        }
        setInteraction("image_import", false);
    }

    void onAudioPermissionResult(boolean granted) {
        if (granted) startRecording();
        else toast("음성메모를 쓰려면 마이크 권한이 필요해요");
    }

    void prepareForCollapse() {
        saveCurrentFromUi();
        if (recording) finishRecording(true);
        stopPlayback();
        hideMemoList();
        hideColorPanel();
        interactions.clear();
        if (callback != null) callback.onInteractionChanged(false);
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);

        main = new LinearLayout(getContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(12), dp(14), dp(14));
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        tagsEdit = new EditText(getContext());
        tagsEdit.setSingleLine(true);
        tagsEdit.setHint("#태그, #태그2");
        tagsEdit.setTextSize(12.5f);
        tagsEdit.setTextColor(Color.rgb(87, 83, 78));
        tagsEdit.setHintTextColor(Color.rgb(166, 159, 151));
        tagsEdit.setPadding(dp(2), 0, dp(2), 0);
        tagsEdit.setBackgroundColor(Color.TRANSPARENT);
        main.addView(tagsEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        HorizontalScrollView tabsScroll = new HorizontalScrollView(getContext());
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        textTab = miniTab("T 텍스트");
        checkTab = miniTab("☑ 체크");
        drawTab = miniTab("✎ 낙서");
        linkTab = miniTab("🔗 링크");
        imageTab = miniTab("▧ 이미지");
        voiceTab = miniTab("🎙 음성");
        Button[] tabButtons = {textTab, checkTab, drawTab, linkTab, imageTab, voiceTab};
        for (int i = 0; i < tabButtons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(38));
            if (i > 0) lp.setMargins(dp(5), 0, 0, 0);
            tabs.addView(tabButtons[i], lp);
        }
        tabsScroll.addView(tabs, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        main.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        contentFrame = new FrameLayout(getContext());
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        contentLp.setMargins(0, dp(6), 0, 0);
        main.addView(contentFrame, contentLp);

        buildTextPane();
        buildChecklistPane();
        buildDrawPane();
        buildLinkPane();
        buildImagePane();
        buildVoicePane();
        buildListPanel();
        buildColorPanel();

        titleEdit.addTextChangedListener(new SaveWatcher());
        tagsEdit.addTextChangedListener(new SaveWatcher());
        bodyEdit.addTextChangedListener(new SaveWatcher());

        setFocusTracking(titleEdit, "title");
        setFocusTracking(tagsEdit, "tags");
        setFocusTracking(bodyEdit, "body");
        setFocusTracking(linkInput, "link");

        list.setOnClickListener(v -> showMemoList());
        add.setOnClickListener(v -> addMemo());
        color.setOnClickListener(v -> showColorPanel());
        gear.setOnClickListener(v -> {
            if (callback != null) callback.onGear();
        });

        textTab.setOnClickListener(v -> setMode(MODE_TEXT));
        checkTab.setOnClickListener(v -> setMode(MODE_CHECK));
        drawTab.setOnClickListener(v -> setMode(MODE_DRAW));
        linkTab.setOnClickListener(v -> setMode(MODE_LINK));
        imageTab.setOnClickListener(v -> setMode(MODE_IMAGE));
        voiceTab.setOnClickListener(v -> setMode(MODE_VOICE));

        memos = MemoStore.load(getContext());
        currentIndex = MemoStore.activeIndex(getContext(), memos.size());
        loadCurrent();
        setMode(MODE_TEXT);
        refreshScale();
    }

    private void buildTextPane() {
        textPane = new LinearLayout(getContext());
        textPane.setOrientation(LinearLayout.VERTICAL);

        HorizontalScrollView formatScroll = new HorizontalScrollView(getContext());
        formatScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout format = new LinearLayout(getContext());
        format.setGravity(Gravity.CENTER_VERTICAL);
        Button bold = soft("B");
        bold.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Button italic = soft("I");
        italic.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        Button underline = soft("U̲");
        Button bullet = soft("• 목록");
        format.addView(bold, new LinearLayout.LayoutParams(dp(46), dp(36)));
        LinearLayout.LayoutParams f1 = new LinearLayout.LayoutParams(dp(46), dp(36)); f1.setMargins(dp(4),0,0,0);
        format.addView(italic, f1);
        LinearLayout.LayoutParams f2 = new LinearLayout.LayoutParams(dp(46), dp(36)); f2.setMargins(dp(4),0,0,0);
        format.addView(underline, f2);
        LinearLayout.LayoutParams f3 = new LinearLayout.LayoutParams(dp(70), dp(36)); f3.setMargins(dp(4),0,0,0);
        format.addView(bullet, f3);
        formatScroll.addView(format);
        textPane.addView(formatScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        bodyEdit = new EditText(getContext());
        bodyEdit.setGravity(Gravity.TOP | Gravity.START);
        bodyEdit.setHint("여기에 자유롭게 메모하세요");
        bodyEdit.setTextSize(14);
        bodyEdit.setTextColor(Color.rgb(58, 54, 50));
        bodyEdit.setHintTextColor(Color.rgb(165, 157, 148));
        bodyEdit.setPadding(dp(10), dp(9), dp(10), dp(9));
        textPane.addView(bodyEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bold.setOnClickListener(v -> applyTextStyle(Typeface.BOLD));
        italic.setOnClickListener(v -> applyTextStyle(Typeface.ITALIC));
        underline.setOnClickListener(v -> applyUnderline());
        bullet.setOnClickListener(v -> applyBullets());

        contentFrame.addView(textPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildChecklistPane() {
        checkPane = new LinearLayout(getContext());
        checkPane.setOrientation(LinearLayout.VERTICAL);
        checkPane.setVisibility(GONE);

        Button addCheck = soft("+ 체크 항목");
        checkPane.addView(addCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        ScrollView scroll = new ScrollView(getContext());
        checkRows = new LinearLayout(getContext());
        checkRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(checkRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        slp.setMargins(0, dp(5), 0, 0);
        checkPane.addView(scroll, slp);

        addCheck.setOnClickListener(v -> {
            saveChecklist();
            MemoStore.Memo m = memos.get(currentIndex);
            m.checklist.add(new MemoStore.CheckItem("", false));
            rebuildChecklist();
            if (!checkEditors.isEmpty()) {
                EditText e = checkEditors.get(checkEditors.size() - 1).edit;
                e.requestFocus();
                showKeyboard(e);
            }
        });

        contentFrame.addView(checkPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildDrawPane() {
        drawPane = new LinearLayout(getContext());
        drawPane.setOrientation(LinearLayout.VERTICAL);
        drawPane.setVisibility(GONE);

        LinearLayout tools1 = new LinearLayout(getContext());
        tools1.setGravity(Gravity.CENTER_VERTICAL);
        Button pen = soft("펜");
        Button pencil = soft("연필");
        Button highlighter = soft("형광");
        Button eraser = soft("지우개");
        tools1.addView(pen, toolWeight());
        tools1.addView(pencil, toolWeight());
        tools1.addView(highlighter, toolWeight());
        tools1.addView(eraser, toolWeight());
        drawPane.addView(tools1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        doodle = new DoodleView(getContext());
        drawPane.addView(doodle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        HorizontalScrollView toolScroll = new HorizontalScrollView(getContext());
        toolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools2 = new LinearLayout(getContext());
        tools2.setGravity(Gravity.CENTER_VERTICAL);
        Button black = colorButton("검", Color.rgb(48, 54, 67));
        Button blue = colorButton("파", Color.rgb(70, 113, 224));
        Button red = colorButton("빨", Color.rgb(220, 86, 94));
        Button green = colorButton("초", Color.rgb(66, 153, 104));
        Button custom = soft("색");
        Button undo = soft("↶");
        Button redo = soft("↷");
        Button plain = soft("무지");
        Button line = soft("줄");
        Button grid = soft("격자");
        for (Button b : new Button[]{black, blue, red, green, custom, undo, redo, plain, line, grid}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(34));
            lp.setMargins(dp(2), 0, dp(2), 0);
            tools2.addView(b, lp);
        }
        toolScroll.addView(tools2);
        drawPane.addView(toolScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        pen.setOnClickListener(v -> doodle.setTool(DoodleView.TOOL_PEN));
        pencil.setOnClickListener(v -> doodle.setTool(DoodleView.TOOL_PENCIL));
        highlighter.setOnClickListener(v -> doodle.setTool(DoodleView.TOOL_HIGHLIGHTER));
        eraser.setOnClickListener(v -> doodle.setTool(DoodleView.TOOL_ERASER));
        black.setOnClickListener(v -> setPresetBrush(Color.rgb(48, 54, 67)));
        blue.setOnClickListener(v -> setPresetBrush(Color.rgb(70, 113, 224)));
        red.setOnClickListener(v -> setPresetBrush(Color.rgb(220, 86, 94)));
        green.setOnClickListener(v -> setPresetBrush(Color.rgb(66, 153, 104)));
        custom.setOnClickListener(v -> showColorPanel());
        undo.setOnClickListener(v -> doodle.undo());
        redo.setOnClickListener(v -> doodle.redo());
        plain.setOnClickListener(v -> setPaperPattern(0));
        line.setOnClickListener(v -> setPaperPattern(1));
        grid.setOnClickListener(v -> setPaperPattern(2));

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

    private void buildImagePane() {
        imagePane = new LinearLayout(getContext());
        imagePane.setOrientation(LinearLayout.VERTICAL);
        imagePane.setVisibility(GONE);

        Button add = soft("+ 이미지 첨부");
        imagePane.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        ScrollView scroll = new ScrollView(getContext());
        imageList = new LinearLayout(getContext());
        imageList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(imageList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        slp.setMargins(0, dp(5), 0, 0);
        imagePane.addView(scroll, slp);

        add.setOnClickListener(v -> {
            if (callback != null) callback.onPickImage();
        });

        contentFrame.addView(imagePane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildVoicePane() {
        voicePane = new LinearLayout(getContext());
        voicePane.setOrientation(LinearLayout.VERTICAL);
        voicePane.setVisibility(GONE);

        LinearLayout controls = new LinearLayout(getContext());
        controls.setGravity(Gravity.CENTER_VERTICAL);
        recordButton = soft("● 녹음");
        pauseRecordButton = soft("일시정지");
        stopRecordButton = soft("■ 저장");
        controls.addView(recordButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(40), 1);
        plp.setMargins(dp(5), 0, 0, 0);
        controls.addView(pauseRecordButton, plp);
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0, dp(40), 1);
        slp2.setMargins(dp(5), 0, 0, 0);
        controls.addView(stopRecordButton, slp2);
        voicePane.addView(controls);

        recordingState = text("녹음 대기", 12, false, Color.rgb(112, 118, 130));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        rlp.setMargins(dp(4), dp(2), 0, 0);
        voicePane.addView(recordingState, rlp);

        ScrollView scroll = new ScrollView(getContext());
        voiceList = new LinearLayout(getContext());
        voiceList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(voiceList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        voicePane.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        recordButton.setOnClickListener(v -> requestOrStartRecording());
        pauseRecordButton.setOnClickListener(v -> pauseResumeRecording());
        stopRecordButton.setOnClickListener(v -> finishRecording(true));
        updateRecorderButtons();

        contentFrame.addView(voicePane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildListPanel() {
        listPanel = new LinearLayout(getContext());
        listPanel.setOrientation(LinearLayout.VERTICAL);
        listPanel.setPadding(dp(14), dp(12), dp(14), dp(14));
        listPanel.setBackground(rounded(Color.rgb(255, 253, 248), dp(24), Color.rgb(220, 214, 204), dp(1)));

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("메모 목록", 17, true, Color.rgb(55, 50, 46)),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        archiveFilter = soft("보관함");
        top.addView(archiveFilter, new LinearLayout.LayoutParams(dp(78), dp(38)));
        Button add = soft("+");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(38));
        alp.setMargins(dp(5), 0, 0, 0);
        top.addView(add, alp);
        Button close = soft("닫기");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(62), dp(38));
        clp.setMargins(dp(5), 0, 0, 0);
        top.addView(close, clp);
        listPanel.addView(top);

        searchInput = new EditText(getContext());
        searchInput.setSingleLine(true);
        searchInput.setHint("제목·내용·태그·체크리스트 검색");
        searchInput.setTextSize(13);
        searchInput.setPadding(dp(10), 0, dp(10), 0);
        searchInput.setBackground(rounded(Color.WHITE, dp(13), Color.rgb(226, 222, 215), dp(1)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        searchLp.setMargins(0, dp(3), 0, dp(4));
        listPanel.addView(searchInput, searchLp);

        ScrollView scroll = new ScrollView(getContext());
        listRows = new LinearLayout(getContext());
        listRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        listPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        addView(listPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listPanel.setVisibility(GONE);

        searchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { rebuildList(); }
        });
        archiveFilter.setOnClickListener(v -> {
            showingArchive = !showingArchive;
            archiveFilter.setText(showingArchive ? "일반 메모" : "보관함");
            rebuildList();
        });
        add.setOnClickListener(v -> {
            hideMemoList();
            addMemo();
        });
        close.setOnClickListener(v -> hideMemoList());
        setFocusTracking(searchInput, "search");
    }

    private void buildColorPanel() {
        colorPanel = new LinearLayout(getContext());
        colorPanel.setOrientation(LinearLayout.VERTICAL);
        colorPanel.setPadding(dp(14), dp(12), dp(14), dp(14));
        colorPanel.setBackground(rounded(Color.WHITE, dp(24), Color.rgb(220, 224, 234), dp(1)));
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
        scroll.addView(controls);
        colorPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        controls.addView(text("메모지 색", 14, true, Color.rgb(66, 61, 57)));
        paperWheel = new ColorWheelView(getContext());
        LinearLayout.LayoutParams pwp = new LinearLayout.LayoutParams(dp(132), dp(132));
        pwp.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(paperWheel, pwp);
        paperSatLabel = small("");
        controls.addView(paperSatLabel);
        paperSatSeek = new SeekBar(getContext());
        paperSatSeek.setMax(45);
        controls.addView(paperSatSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        paperValueLabel = small("");
        controls.addView(paperValueLabel);
        paperValueSeek = new SeekBar(getContext());
        paperValueSeek.setMax(20);
        controls.addView(paperValueSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        controls.addView(text("브러시 색·굵기", 14, true, Color.rgb(66, 61, 57)));
        brushWheel = new ColorWheelView(getContext());
        LinearLayout.LayoutParams bwp = new LinearLayout.LayoutParams(dp(132), dp(132));
        bwp.gravity = Gravity.CENTER_HORIZONTAL;
        controls.addView(brushWheel, bwp);
        brushSatLabel = small("");
        controls.addView(brushSatLabel);
        brushSatSeek = new SeekBar(getContext());
        brushSatSeek.setMax(100);
        controls.addView(brushSatSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        brushValueLabel = small("");
        controls.addView(brushValueLabel);
        brushValueSeek = new SeekBar(getContext());
        brushValueSeek.setMax(92);
        controls.addView(brushValueSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        brushWidthLabel = small("");
        controls.addView(brushWidthLabel);
        brushWidthSeek = new SeekBar(getContext());
        brushWidthSeek.setMax(16);
        controls.addView(brushWidthSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        addView(colorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        paperWheel.setListener(h -> {
            if (!memos.isEmpty()) {
                memos.get(currentIndex).paperHue = h;
                saveColorState();
            }
        });
        paperSatSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean user) {
                paperSatLabel.setText("채도  " + p + "%");
                if (user && !memos.isEmpty()) {
                    memos.get(currentIndex).paperSat = p;
                    saveColorState();
                }
            }
        });
        paperValueSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean user) {
                int value = 80 + p;
                paperValueLabel.setText("밝기  " + value + "%");
                if (user && !memos.isEmpty()) {
                    memos.get(currentIndex).paperValue = value;
                    saveColorState();
                }
            }
        });
        brushWheel.setListener(h -> {
            Prefs.setMemoBrushHue(getContext(), h);
            applyBrushPrefs();
            syncColorPanelValues();
        });
        brushSatSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                brushSatLabel.setText("채도  " + p + "%");
                if (user) {
                    Prefs.setMemoBrushSat(getContext(), p);
                    applyBrushPrefs();
                }
            }
        });
        brushValueSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int value = 8 + p;
                brushValueLabel.setText("밝기  " + value + "%");
                if (user) {
                    Prefs.setMemoBrushValue(getContext(), value);
                    applyBrushPrefs();
                }
            }
        });
        brushWidthSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                float width = 2f + p;
                brushWidthLabel.setText(String.format(Locale.KOREAN, "굵기  %.0f", width));
                if (user) {
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

    private void rebuildChecklist() {
        if (checkRows == null || memos.isEmpty()) return;
        saveChecklist();
        checkEditors.clear();
        checkRows.removeAllViews();
        MemoStore.Memo m = memos.get(currentIndex);

        for (int i = 0; i < m.checklist.size(); i++) {
            final int index = i;
            MemoStore.CheckItem item = m.checklist.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox check = new CheckBox(getContext());
            check.setChecked(item.checked);
            row.addView(check, new LinearLayout.LayoutParams(dp(42), dp(44)));

            EditText edit = new EditText(getContext());
            edit.setSingleLine(true);
            edit.setText(item.text);
            edit.setHint("체크 항목");
            edit.setTextSize(13.5f * Prefs.memoTextScale(getContext()));
            edit.setPadding(dp(6), 0, dp(6), 0);
            edit.setBackgroundColor(Color.TRANSPARENT);
            row.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));

            Button up = soft("↑");
            row.addView(up, new LinearLayout.LayoutParams(dp(38), dp(38)));
            Button down = soft("↓");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(38), dp(38)); dlp.setMargins(dp(3),0,0,0);
            row.addView(down, dlp);
            Button del = soft("×");
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(dp(38), dp(38)); xlp.setMargins(dp(3),0,0,0);
            row.addView(del, xlp);

            CheckRow cr = new CheckRow(check, edit);
            checkEditors.add(cr);
            checkRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

            check.setOnCheckedChangeListener((b, checked) -> saveChecklist());
            edit.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void afterTextChanged(Editable s) { if (!loading) saveChecklist(); }
            });
            setFocusTracking(edit, "check_" + index);
            up.setOnClickListener(v -> moveCheck(index, Math.max(0, index - 1)));
            down.setOnClickListener(v -> moveCheck(index, Math.min(m.checklist.size() - 1, index + 1)));
            del.setOnClickListener(v -> {
                saveChecklist();
                if (index < m.checklist.size()) m.checklist.remove(index);
                MemoStore.save(getContext(), memos);
                rebuildChecklist();
            });
        }
        applyChecklistBackground();
    }

    private void saveChecklist() {
        if (loading || memos.isEmpty() || checkEditors.isEmpty()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        ArrayList<MemoStore.CheckItem> next = new ArrayList<>();
        for (CheckRow row : checkEditors) {
            String value = row.edit.getText().toString();
            next.add(new MemoStore.CheckItem(value, row.check.isChecked()));
        }
        m.checklist = next;
        MemoStore.save(getContext(), memos);
    }

    private void moveCheck(int from, int to) {
        saveChecklist();
        MemoStore.Memo m = memos.get(currentIndex);
        if (from < 0 || from >= m.checklist.size() || to < 0 || to >= m.checklist.size() || from == to) return;
        MemoStore.CheckItem item = m.checklist.remove(from);
        m.checklist.add(to, item);
        MemoStore.save(getContext(), memos);
        rebuildChecklist();
    }

    private void rebuildLinks() {
        if (linkList == null || memos.isEmpty()) return;
        linkList.removeAllViews();
        MemoStore.Memo m = memos.get(currentIndex);
        if (m.links.isEmpty()) {
            linkList.addView(text("저장된 링크가 없어요", 12, false, Color.rgb(146, 150, 160)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            return;
        }
        for (int i = 0; i < m.links.size(); i++) {
            final int index = i;
            String url = m.links.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView link = text(shortUrl(url), 12.5f, true, Color.rgb(67, 105, 198));
            link.setSingleLine(true);
            link.setPadding(dp(10), 0, dp(8), 0);
            link.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(226, 231, 240), dp(1)));
            row.addView(link, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button remove = soft("×");
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(42), dp(42)); rlp.setMargins(dp(5),0,0,0);
            row.addView(remove, rlp);
            link.setOnClickListener(v -> openUrl(url));
            remove.setOnClickListener(v -> {
                if (index < m.links.size()) {
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

    private void rebuildImages() {
        if (imageList == null || memos.isEmpty()) return;
        imageList.removeAllViews();
        MemoStore.Memo m = memos.get(currentIndex);
        if (m.imagePaths.isEmpty()) {
            imageList.addView(text("첨부된 이미지가 없어요", 12, false, Color.rgb(146, 150, 160)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            return;
        }
        for (int i = 0; i < m.imagePaths.size(); i++) {
            final int index = i;
            String path = m.imagePaths.get(i);
            Bitmap bmp = MemoMediaStore.loadBitmap(path, 900);
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(4), dp(4), dp(4), dp(4));
            box.setBackground(rounded(Color.WHITE, dp(14), Color.rgb(225, 228, 235), dp(1)));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (bmp != null) image.setImageBitmap(bmp);
            box.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));

            Button remove = soft("이미지 삭제");
            box.addView(remove, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
            remove.setOnClickListener(v -> {
                if (index < m.imagePaths.size()) {
                    String removed = m.imagePaths.remove(index);
                    MemoMediaStore.deletePath(removed);
                    MemoStore.save(getContext(), memos);
                    rebuildImages();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(218));
            lp.setMargins(0, dp(5), 0, 0);
            imageList.addView(box, lp);
        }
    }

    private void rebuildVoice() {
        if (voiceList == null || memos.isEmpty()) return;
        voiceList.removeAllViews();
        MemoStore.Memo m = memos.get(currentIndex);
        if (m.voicePaths.isEmpty()) {
            voiceList.addView(text("저장된 음성메모가 없어요", 12, false, Color.rgb(146, 150, 160)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            return;
        }

        for (int i = 0; i < m.voicePaths.size(); i++) {
            final int index = i;
            String path = m.voicePaths.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button play = soft("▶");
            row.addView(play, new LinearLayout.LayoutParams(dp(44), dp(40)));

            LinearLayout middle = new LinearLayout(getContext());
            middle.setOrientation(LinearLayout.VERTICAL);
            TextView label = text("음성 " + (i + 1) + " · " + MemoMediaStore.durationLabel(path),
                    12.5f, true, Color.rgb(64, 68, 78));
            SeekBar seek = new SeekBar(getContext());
            middle.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
            middle.addView(seek, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(50), 1);
            mlp.setMargins(dp(5), 0, dp(5), 0);
            row.addView(middle, mlp);

            Button del = soft("×");
            row.addView(del, new LinearLayout.LayoutParams(dp(42), dp(40)));

            play.setOnClickListener(v -> togglePlayback(path, seek, label, play));
            seek.setOnSeekBarChangeListener(new SimpleSeek() {
                @Override public void onProgressChanged(SeekBar seekBar, int p, boolean user) {
                    if (user && player != null && path.equals(playingPath)) {
                        try { player.seekTo(p); } catch (RuntimeException ignored) {}
                    }
                }
            });
            del.setOnClickListener(v -> {
                if (path.equals(playingPath)) stopPlayback();
                if (index < m.voicePaths.size()) {
                    String removed = m.voicePaths.remove(index);
                    MemoMediaStore.deletePath(removed);
                    MemoStore.save(getContext(), memos);
                    rebuildVoice();
                }
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            rowLp.setMargins(0, dp(4), 0, 0);
            voiceList.addView(row, rowLp);
        }
    }

    private void showMemoList() {
        saveCurrentFromUi();
        rebuildList();
        listPanel.setVisibility(VISIBLE);
        listPanel.bringToFront();
        setInteraction("list", true);
    }

    private void hideMemoList() {
        if (listPanel != null) listPanel.setVisibility(GONE);
        setInteraction("list", false);
    }

    private void rebuildList() {
        if (listRows == null) return;
        listRows.removeAllViews();
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.KOREAN);
        int shown = 0;
        for (int i = 0; i < memos.size(); i++) {
            final int index = i;
            MemoStore.Memo memo = memos.get(i);
            if (memo.archived != showingArchive) continue;
            if (!query.isEmpty() && !matches(memo, query)) continue;
            shown++;

            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(4), dp(4), dp(4));
            int rowPaper = memoPaperColor(memo);
            if (index == currentIndex) rowPaper = blend(rowPaper, Color.rgb(231, 225, 255), .24f);
            row.setBackground(rounded(rowPaper, dp(13), Color.rgb(220, 216, 210), dp(1)));

            LinearLayout nameBox = new LinearLayout(getContext());
            nameBox.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(displayName(memo, index), 13, true, Color.rgb(58, 53, 49));
            title.setSingleLine(true);
            TextView tags = text(tagsText(memo), 10.5f, false, Color.rgb(115, 108, 101));
            tags.setSingleLine(true);
            nameBox.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
            nameBox.addView(tags, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
            row.addView(nameBox, new LinearLayout.LayoutParams(0, dp(44), 1));

            Button pin = soft(memo.pinned ? "📌" : "핀");
            row.addView(pin, new LinearLayout.LayoutParams(dp(44), dp(38)));
            Button archive = soft(memo.archived ? "복원" : "보관");
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(54), dp(38)); alp.setMargins(dp(3),0,0,0);
            row.addView(archive, alp);
            Button up = soft("↑");
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(38), dp(38)); ulp.setMargins(dp(3),0,0,0);
            row.addView(up, ulp);
            Button down = soft("↓");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(38), dp(38)); dlp.setMargins(dp(3),0,0,0);
            row.addView(down, dlp);
            Button del = soft("×");
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(dp(38), dp(38)); xlp.setMargins(dp(3),0,0,0);
            row.addView(del, xlp);

            title.setOnClickListener(v -> selectMemo(index));
            pin.setOnClickListener(v -> {
                long id = memos.get(index).id;
                memos = MemoStore.togglePin(getContext(), index);
                currentIndex = MemoStore.indexOfId(memos, id);
                MemoStore.setActiveIndex(getContext(), currentIndex);
                loadCurrent();
                rebuildList();
            });
            archive.setOnClickListener(v -> {
                long id = memos.get(index).id;
                memos = MemoStore.toggleArchive(getContext(), index);
                currentIndex = MemoStore.indexOfId(memos, id);
                MemoStore.setActiveIndex(getContext(), currentIndex);
                loadCurrent();
                rebuildList();
            });
            up.setOnClickListener(v -> moveMemo(index, Math.max(0, index - 1)));
            down.setOnClickListener(v -> moveMemo(index, Math.min(memos.size() - 1, index + 1)));
            del.setOnClickListener(v -> deleteMemo(index));

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            rowLp.setMargins(0, dp(4), 0, 0);
            listRows.addView(row, rowLp);
        }

        if (shown == 0) {
            listRows.addView(text(showingArchive ? "보관된 메모가 없어요" : "검색 결과가 없어요",
                    12.5f, false, Color.rgb(135, 132, 129)),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }
    }

    private boolean matches(MemoStore.Memo m, String q) {
        StringBuilder s = new StringBuilder();
        s.append(m.title).append(' ').append(m.body).append(' ');
        for (String tag : m.tags) s.append(tag).append(' ');
        for (MemoStore.CheckItem item : m.checklist) s.append(item.text).append(' ');
        for (String link : m.links) s.append(link).append(' ');
        return s.toString().toLowerCase(Locale.KOREAN).contains(q);
    }

    private void addMemo() {
        saveCurrentFromUi();
        currentIndex = MemoStore.add(getContext());
        memos = MemoStore.load(getContext());
        currentIndex = MemoStore.activeIndex(getContext(), memos.size());
        loadCurrent();
        setMode(MODE_TEXT);
        titleEdit.requestFocus();
        showKeyboard(titleEdit);
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
        saveCurrentFromUi();
        long activeId = memos.get(currentIndex).id;
        memos = MemoStore.move(getContext(), from, to);
        currentIndex = MemoStore.indexOfId(memos, activeId);
        MemoStore.setActiveIndex(getContext(), currentIndex);
        loadCurrent();
        rebuildList();
    }

    private void deleteMemo(int index) {
        if (index < 0 || index >= memos.size()) return;
        MemoStore.Memo removedMemo = memos.get(index);
        for (String p : removedMemo.imagePaths) MemoMediaStore.deletePath(p);
        for (String p : removedMemo.voicePaths) MemoMediaStore.deletePath(p);
        boolean deletingActive = index == currentIndex;
        long activeId = memos.get(currentIndex).id;
        memos = MemoStore.delete(getContext(), index);
        currentIndex = deletingActive ? MemoStore.activeIndex(getContext(), memos.size())
                : MemoStore.indexOfId(memos, activeId);
        currentIndex = Math.max(0, Math.min(memos.size() - 1, currentIndex));
        MemoStore.setActiveIndex(getContext(), currentIndex);
        loadCurrent();
        rebuildList();
    }

    private void loadCurrent() {
        if (memos.isEmpty()) memos = MemoStore.load(getContext());
        currentIndex = Math.max(0, Math.min(memos.size() - 1, currentIndex));
        MemoStore.Memo m = memos.get(currentIndex);

        loading = true;
        titleEdit.setText(m.title);
        tagsEdit.setText(tagsText(m));
        setRichBody(m);
        doodle.setSerialized(m.doodle);
        loading = false;

        pageLabel.setText(String.format(Locale.KOREAN, "메모 %d/%d%s",
                currentIndex + 1, memos.size(), m.archived ? " · 보관됨" : ""));
        applyMemoAppearance();
        applyBrushPrefs();
        doodle.setPaperPattern(m.paperPattern);
        rebuildChecklist();
        rebuildLinks();
        rebuildImages();
        rebuildVoice();
        rebuildList();
        if (colorPanel != null && colorPanel.getVisibility() == VISIBLE) syncColorPanelValues();
    }

    private void saveCurrentFromUi() {
        if (loading || memos.isEmpty() || currentIndex < 0 || currentIndex >= memos.size()) return;
        saveChecklist();
        MemoStore.Memo m = memos.get(currentIndex);
        m.title = titleEdit.getText().toString();
        m.body = bodyEdit.getText().toString();
        m.bodyHtml = richBodyHtml();
        m.tags = parseTags(tagsEdit.getText().toString());
        m.doodle = doodle.serialize();
        MemoStore.save(getContext(), memos);
        pageLabel.setText(String.format(Locale.KOREAN, "메모 %d/%d%s",
                currentIndex + 1, memos.size(), m.archived ? " · 보관됨" : ""));
    }

    private void setRichBody(MemoStore.Memo memo) {
        if (memo.bodyHtml == null || memo.bodyHtml.isEmpty()) {
            bodyEdit.setText(memo.body == null ? "" : memo.body);
            return;
        }
        try {
            Spanned spanned;
            if (Build.VERSION.SDK_INT >= 24) {
                spanned = Html.fromHtml(memo.bodyHtml, Html.FROM_HTML_MODE_LEGACY);
            } else {
                spanned = Html.fromHtml(memo.bodyHtml);
            }
            bodyEdit.setText(spanned);
        } catch (RuntimeException e) {
            bodyEdit.setText(memo.body == null ? "" : memo.body);
        }
    }

    private String richBodyHtml() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                return Html.toHtml(bodyEdit.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
            }
            return Html.toHtml(bodyEdit.getText());
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void applyTextStyle(int style) {
        int start = bodyEdit.getSelectionStart();
        int end = bodyEdit.getSelectionEnd();
        if (start < 0 || end <= start) {
            toast("서식을 적용할 글자를 먼저 선택해 주세요");
            return;
        }
        bodyEdit.getText().setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        saveCurrentFromUi();
    }

    private void applyUnderline() {
        int start = bodyEdit.getSelectionStart();
        int end = bodyEdit.getSelectionEnd();
        if (start < 0 || end <= start) {
            toast("밑줄을 적용할 글자를 먼저 선택해 주세요");
            return;
        }
        bodyEdit.getText().setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        saveCurrentFromUi();
    }

    private void applyBullets() {
        Editable e = bodyEdit.getText();
        int start = Math.max(0, bodyEdit.getSelectionStart());
        int end = Math.max(start, bodyEdit.getSelectionEnd());
        int lineStart = start;
        while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = end;
        while (lineEnd < e.length() && e.charAt(lineEnd) != '\n') lineEnd++;
        String segment = e.subSequence(lineStart, lineEnd).toString();
        String[] lines = segment.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            out.append(line.startsWith("• ") ? line : "• " + line);
            if (i < lines.length - 1) out.append('\n');
        }
        e.replace(lineStart, lineEnd, out.toString());
        saveCurrentFromUi();
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

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (RuntimeException e) {
            toast("링크를 열 수 없어요");
        }
    }

    private void requestOrStartRecording() {
        if (recording) return;
        if (Build.VERSION.SDK_INT >= 23
                && getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (callback != null) callback.onRequestAudioPermission();
            return;
        }
        startRecording();
    }

    @SuppressWarnings("deprecation")
    private void startRecording() {
        if (recording || memos.isEmpty()) return;
        stopPlayback();
        File file = MemoMediaStore.newVoiceFile(getContext());
        try {
            MediaRecorder r = new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(96000);
            r.setAudioSamplingRate(44100);
            r.setOutputFile(file.getAbsolutePath());
            r.prepare();
            r.start();
            recorder = r;
            recordingPath = file.getAbsolutePath();
            recording = true;
            recordingPaused = false;
            recordingState.setText("● 녹음 중");
            setInteraction("recording", true);
            updateRecorderButtons();
        } catch (Exception e) {
            try { if (recorder != null) recorder.release(); } catch (RuntimeException ignored) {}
            recorder = null;
            recording = false;
            recordingPath = "";
            file.delete();
            toast("녹음을 시작하지 못했어요");
            setInteraction("recording", false);
            updateRecorderButtons();
        }
    }

    private void pauseResumeRecording() {
        if (!recording || recorder == null) return;
        if (Build.VERSION.SDK_INT < 24) {
            toast("이 기기에서는 녹음 일시정지를 지원하지 않아요");
            return;
        }
        try {
            if (recordingPaused) {
                recorder.resume();
                recordingPaused = false;
                recordingState.setText("● 녹음 중");
            } else {
                recorder.pause();
                recordingPaused = true;
                recordingState.setText("Ⅱ 일시정지");
            }
            updateRecorderButtons();
        } catch (RuntimeException e) {
            finishRecording(true);
        }
    }

    private void finishRecording(boolean save) {
        if (!recording) return;
        MediaRecorder r = recorder;
        recorder = null;
        recording = false;
        recordingPaused = false;
        String path = recordingPath;
        recordingPath = "";
        try {
            if (r != null) {
                r.stop();
                r.release();
            }
            if (save && path != null && !path.isEmpty()) {
                MemoStore.Memo m = memos.get(currentIndex);
                m.voicePaths.add(path);
                MemoStore.save(getContext(), memos);
            } else {
                MemoMediaStore.deletePath(path);
            }
        } catch (RuntimeException e) {
            try { if (r != null) r.release(); } catch (RuntimeException ignored) {}
            MemoMediaStore.deletePath(path);
        }
        recordingState.setText("녹음 대기");
        setInteraction("recording", false);
        updateRecorderButtons();
        rebuildVoice();
    }

    private void updateRecorderButtons() {
        if (recordButton == null) return;
        recordButton.setEnabled(!recording);
        pauseRecordButton.setEnabled(recording);
        stopRecordButton.setEnabled(recording);
        pauseRecordButton.setText(recordingPaused ? "이어 녹음" : "일시정지");
    }

    private void togglePlayback(String path, SeekBar seek, TextView label, Button playButton) {
        if (path.equals(playingPath) && player != null) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    playButton.setText("▶");
                    setInteraction("playback", false);
                } else {
                    player.start();
                    playButton.setText("⏸");
                    setInteraction("playback", true);
                    handler.post(playbackTicker);
                }
            } catch (RuntimeException ignored) {}
            return;
        }
        stopPlayback();
        try {
            MediaPlayer p = new MediaPlayer();
            p.setDataSource(path);
            p.prepare();
            p.start();
            player = p;
            playingPath = path;
            activePlaybackSeek = seek;
            activePlaybackLabel = label;
            seek.setMax(Math.max(1, p.getDuration()));
            playButton.setText("⏸");
            p.setOnCompletionListener(mp -> {
                playButton.setText("▶");
                stopPlayback();
            });
            setInteraction("playback", true);
            handler.post(playbackTicker);
        } catch (Exception e) {
            stopPlayback();
            toast("음성을 재생하지 못했어요");
        }
    }

    private void stopPlayback() {
        handler.removeCallbacks(playbackTicker);
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) {}
            try { player.release(); } catch (RuntimeException ignored) {}
        }
        player = null;
        playingPath = "";
        activePlaybackSeek = null;
        activePlaybackLabel = null;
        setInteraction("playback", false);
    }

    private void setMode(int value) {
        mode = value;
        textPane.setVisibility(mode == MODE_TEXT ? VISIBLE : GONE);
        checkPane.setVisibility(mode == MODE_CHECK ? VISIBLE : GONE);
        drawPane.setVisibility(mode == MODE_DRAW ? VISIBLE : GONE);
        linkPane.setVisibility(mode == MODE_LINK ? VISIBLE : GONE);
        imagePane.setVisibility(mode == MODE_IMAGE ? VISIBLE : GONE);
        voicePane.setVisibility(mode == MODE_VOICE ? VISIBLE : GONE);
        Button[] tabs = {textTab, checkTab, drawTab, linkTab, imageTab, voiceTab};
        for (int i = 0; i < tabs.length; i++) tabs[i].setAlpha(mode == i ? 1f : .55f);
        if (mode == MODE_DRAW) doodle.bringToFront();
    }

    private void showColorPanel() {
        syncColorPanelValues();
        colorPanel.setVisibility(VISIBLE);
        colorPanel.bringToFront();
        setInteraction("color_panel", true);
    }

    private void hideColorPanel() {
        if (colorPanel != null) colorPanel.setVisibility(GONE);
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
        brushWidthLabel.setText(String.format(Locale.KOREAN, "굵기  %.0f", Prefs.memoBrushWidth(getContext())));
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
        if (colorPanel != null && colorPanel.getVisibility() == VISIBLE) syncColorPanelValues();
    }

    private void setPaperPattern(int pattern) {
        if (memos.isEmpty()) return;
        memos.get(currentIndex).paperPattern = pattern;
        MemoStore.save(getContext(), memos);
        doodle.setPaperPattern(pattern);
        applyMemoAppearance();
    }

    private int memoPaperColor(MemoStore.Memo memo) {
        return Color.HSVToColor(new float[]{
                memo.paperHue, memo.paperSat / 100f, memo.paperValue / 100f});
    }

    private void applyMemoAppearance() {
        if (memos.isEmpty()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        int paper = memoPaperColor(m);
        int surface = blend(paper, Color.WHITE, .32f);
        int border = blend(paper, Color.rgb(185, 181, 176), .45f);
        main.setBackground(rounded(paper, dp(24), border, dp(1)));
        bodyEdit.setBackground(new NotePaperDrawable(getContext(), surface, border, m.paperPattern, 14f));
        linkInput.setBackground(rounded(surface, dp(13), border, dp(1)));
        doodle.setCanvasColor(surface);
        doodle.setPaperPattern(m.paperPattern);
        applyChecklistBackground();
    }

    private void applyChecklistBackground() {
        if (checkRows == null || memos.isEmpty()) return;
        MemoStore.Memo m = memos.get(currentIndex);
        int paper = memoPaperColor(m);
        int surface = blend(paper, Color.WHITE, .32f);
        int border = blend(paper, Color.rgb(185, 181, 176), .45f);
        checkRows.setBackground(new NotePaperDrawable(getContext(), surface, border, m.paperPattern, 14f));
        checkRows.setPadding(dp(6), dp(6), dp(6), dp(6));
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

    private ArrayList<String> parseTags(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split("[,\\s]+");
        for (String part : parts) {
            String v = part.trim();
            while (v.startsWith("#")) v = v.substring(1);
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
        return out;
    }

    private String tagsText(MemoStore.Memo memo) {
        if (memo == null || memo.tags.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String tag : memo.tags) {
            if (b.length() > 0) b.append("  ");
            b.append('#').append(tag);
        }
        return b.toString();
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
            String value = host + path;
            if (value.length() > 34) value = value.substring(0, 34) + "…";
            return "🔗 " + value;
        } catch (RuntimeException e) {
            return "🔗 " + url;
        }
    }

    private Button miniTab(String label) {
        Button b = soft(label);
        b.setTextSize(11f);
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

    private LinearLayout.LayoutParams toolWeight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
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

    private TextView small(String value) {
        return text(value, 12f, false, Color.rgb(105, 105, 112));
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int blend(int base, int overlay, float overlayAmount) {
        float a = Math.max(0f, Math.min(1f, overlayAmount));
        int r = Math.round(Color.red(base) * (1f - a) + Color.red(overlay) * a);
        int g = Math.round(Color.green(base) * (1f - a) + Color.green(overlay) * a);
        int b = Math.round(Color.blue(base) * (1f - a) + Color.blue(overlay) * a);
        return Color.rgb(r, g, b);
    }

    private void showKeyboard(EditText edit) {
        edit.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private String timeLabel(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.KOREAN, "%d:%02d", sec / 60, sec % 60);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(getContext(), value, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDetachedFromWindow() {
        prepareForCollapse();
        super.onDetachedFromWindow();
    }

    private final class SaveWatcher extends SimpleTextWatcher {
        @Override public void afterTextChanged(Editable s) {
            if (!loading) saveCurrentFromUi();
        }
    }

    private static final class CheckRow {
        final CheckBox check;
        final EditText edit;
        CheckRow(CheckBox check, EditText edit) {
            this.check = check;
            this.edit = edit;
        }
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
