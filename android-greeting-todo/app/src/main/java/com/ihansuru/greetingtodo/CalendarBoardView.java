package com.ihansuru.greetingtodo;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

final class CalendarBoardView extends FrameLayout {
    interface Callback {
        void onGear();
        void onInteractionChanged(boolean active);
        void onReminderPermissionNeeded();
    }

    private Callback callback;
    private final java.util.HashSet<String> interactions = new java.util.HashSet<>();

    private LinearLayout main;
    private TextView monthLabel;
    private Button weekButton;
    private Button monthButton;
    private Button filterButton;
    private EditText searchEdit;
    private CalendarGridView grid;

    private LinearLayout editor;
    private EditText titleEdit;
    private EditText noteEdit;
    private EditText linkEdit;
    private CheckBox allDayCheck;
    private Button startDateButton;
    private Button endDateButton;
    private Button startTimeButton;
    private Button endTimeButton;
    private Spinner repeatSpinner;
    private Spinner reminderSpinner;
    private Button deleteButton;
    private LinearLayout colorRow;

    private long editingId = -1L;
    private int editStartDay;
    private int editEndDay;
    private int editStartMinute = 9 * 60;
    private int editEndMinute = 10 * 60;
    private int editColor = Color.rgb(127, 112, 232);
    private String filter = "전체";

    CalendarBoardView(Context context) {
        super(context);
        init();
    }

    void setCallback(Callback value) { callback = value; }

    void prepareForCollapse() {
        closeEditor();
        clearFocus();
        interactions.clear();
        if (callback != null) callback.onInteractionChanged(false);
    }

    void refresh() {
        if (grid != null) {
            grid.setViewMode("week".equals(Prefs.calendarView(getContext()))
                    ? CalendarGridView.VIEW_WEEK : CalendarGridView.VIEW_MONTH);
            grid.invalidate();
        }
        updateHeader();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);

        main = new LinearLayout(getContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(16), dp(14), dp(16), dp(16));
        main.setBackground(rounded(DesignTokens.SURFACE, dp(18), DesignTokens.BORDER, dp(1)));
        addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        monthLabel = text("", 17f, true, DesignTokens.INK);
        header.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button add = soft("일정 추가");
        header.addView(add, new LinearLayout.LayoutParams(dp(86), dp(38)));
        Button gear = soft("설정");
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(58), dp(38));
        glp.setMargins(dp(5), 0, 0, 0);
        header.addView(gear, glp);
        main.addView(header);

        LinearLayout nav = new LinearLayout(getContext());
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = soft("〈");
        Button today = soft("오늘");
        Button next = soft("〉");
        weekButton = soft("주간");
        monthButton = soft("월간");
        nav.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(36)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(60), dp(36));
        tlp.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(today, tlp);
        nav.addView(next, new LinearLayout.LayoutParams(dp(44), dp(36)));
        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(0, dp(1), 1);
        TextView empty = new TextView(getContext());
        nav.addView(empty, spacer);
        nav.addView(weekButton, new LinearLayout.LayoutParams(dp(62), dp(36)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(62), dp(36));
        mlp.setMargins(dp(4), 0, 0, 0);
        nav.addView(monthButton, mlp);
        main.addView(nav);

        LinearLayout searchRow = new LinearLayout(getContext());
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchEdit = new EditText(getContext());
        searchEdit.setSingleLine(true);
        searchEdit.setHint("일정 검색");
        searchEdit.setTextSize(12.5f);
        searchEdit.setTextColor(DesignTokens.INK);
        searchEdit.setHintTextColor(DesignTokens.MUTED);
        searchEdit.setPadding(dp(11), 0, dp(8), 0);
        searchEdit.setBackground(rounded(DesignTokens.SURFACE_SOFT, dp(12), DesignTokens.BORDER, dp(1)));
        searchRow.addView(searchEdit, new LinearLayout.LayoutParams(0, dp(38), 1));
        filterButton = soft("전체");
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(74), dp(38));
        flp.setMargins(dp(5), 0, 0, 0);
        searchRow.addView(filterButton, flp);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        srp.setMargins(0, dp(5), 0, dp(4));
        main.addView(searchRow, srp);

        grid = new CalendarGridView(getContext());
        grid.setViewMode("week".equals(Prefs.calendarView(getContext()))
                ? CalendarGridView.VIEW_WEEK : CalendarGridView.VIEW_MONTH);
        grid.setAnchorDay(CalendarStore.today());
        main.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        buildEditor();

        prev.setOnClickListener(v -> movePeriod(-1));
        next.setOnClickListener(v -> movePeriod(1));
        today.setOnClickListener(v -> {
            grid.setAnchorDay(CalendarStore.today());
            updateHeader();
        });
        weekButton.setOnClickListener(v -> setViewMode(CalendarGridView.VIEW_WEEK));
        monthButton.setOnClickListener(v -> setViewMode(CalendarGridView.VIEW_MONTH));
        add.setOnClickListener(v -> showEditor(-1L, CalendarStore.today(), CalendarStore.today()));
        gear.setOnClickListener(v -> {
            if (callback != null) callback.onGear();
        });

        searchEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                grid.setQuery(s.toString());
            }
        });
        searchEdit.setOnFocusChangeListener((v, focused) -> setInteraction("search", focused));
        filterButton.setOnClickListener(v -> {
            if ("전체".equals(filter)) filter = "진행중";
            else if ("진행중".equals(filter)) filter = "이번주";
            else filter = "전체";
            filterButton.setText(filter);
            grid.setFilter(filter);
        });

        grid.setListener(new CalendarGridView.Listener() {
            @Override public void onRangeSelected(int startDay, int endDay) {
                showEditor(-1L, startDay, endDay);
            }

            @Override public void onEventClicked(long eventId) {
                CalendarStore.Event e = CalendarStore.get(getContext(), eventId);
                if (e != null) showEditor(eventId, e.startDay, e.endDay);
            }

            @Override public void onInteraction(boolean active) {
                setInteraction("calendar_drag", active);
            }
        });

        updateHeader();
    }

    private void buildEditor() {
        editor = new LinearLayout(getContext());
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(16), dp(14), dp(16), dp(16));
        editor.setBackground(rounded(DesignTokens.PAPER, dp(18), DesignTokens.BORDER, dp(1)));
        editor.setElevation(dp(8));
        editor.setVisibility(GONE);

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("일정 편집", 17, true, DesignTokens.INK),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        Button close = soft("닫기");
        top.addView(close, new LinearLayout.LayoutParams(dp(62), dp(38)));
        editor.addView(top);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editor.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        titleEdit = field("일정 제목", true);
        form.addView(titleEdit, fieldLp());

        LinearLayout dates = new LinearLayout(getContext());
        startDateButton = soft("");
        endDateButton = soft("");
        dates.addView(startDateButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams edlp = new LinearLayout.LayoutParams(0, dp(42), 1);
        edlp.setMargins(dp(5), 0, 0, 0);
        dates.addView(endDateButton, edlp);
        form.addView(dates, rowLp());

        allDayCheck = new CheckBox(getContext());
        allDayCheck.setText("종일 일정");
        allDayCheck.setTextSize(13);
        form.addView(allDayCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        LinearLayout times = new LinearLayout(getContext());
        startTimeButton = soft("");
        endTimeButton = soft("");
        times.addView(startTimeButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams etlp = new LinearLayout.LayoutParams(0, dp(42), 1);
        etlp.setMargins(dp(5), 0, 0, 0);
        times.addView(endTimeButton, etlp);
        form.addView(times, rowLp());

        form.addView(text("색", 12, true, DesignTokens.SECONDARY));
        colorRow = new LinearLayout(getContext());
        int[] colors = {
                Color.rgb(127,112,232), Color.rgb(81,137,229), Color.rgb(74,171,143),
                Color.rgb(240,174,69), Color.rgb(232,103,116), Color.rgb(171,105,205)
        };
        for (int color : colors) {
            Button chip = new Button(getContext());
            chip.setText("");
            chip.setPadding(0,0,0,0);
            chip.setBackground(rounded(color, dp(13), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
            lp.setMargins(dp(2), dp(3), dp(2), dp(3));
            colorRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                editColor = color;
                refreshColorSelection();
            });
            chip.setTag(color);
        }
        form.addView(colorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        repeatSpinner = spinner(new String[]{"반복 안 함", "매일", "매주", "매월", "매년"});
        form.addView(repeatSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        reminderSpinner = spinner(new String[]{
                "알림 없음", "정시", "10분 전", "30분 전", "1시간 전", "하루 전"});
        form.addView(reminderSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        noteEdit = field("메모", false);
        noteEdit.setMinLines(2);
        noteEdit.setGravity(Gravity.TOP | Gravity.START);
        form.addView(noteEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));

        LinearLayout linkRow = new LinearLayout(getContext());
        linkEdit = field("링크", true);
        linkRow.addView(linkEdit, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button openLink = soft("열기");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(62), dp(42));
        olp.setMargins(dp(5),0,0,0);
        linkRow.addView(openLink, olp);
        form.addView(linkRow, rowLp());

        LinearLayout actions = new LinearLayout(getContext());
        deleteButton = soft("삭제");
        Button save = primary("저장");
        actions.addView(deleteButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(0, dp(46), 2);
        svlp.setMargins(dp(6),0,0,0);
        actions.addView(save, svlp);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        alp.setMargins(0, dp(8), 0, 0);
        form.addView(actions, alp);

        addView(editor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        close.setOnClickListener(v -> closeEditor());
        save.setOnClickListener(v -> saveEditor());
        deleteButton.setOnClickListener(v -> {
            if (editingId > 0) {
                CalendarStore.delete(getContext(), editingId);
                closeEditor();
                grid.invalidate();
            }
        });
        startDateButton.setOnClickListener(v -> pickDate(true));
        endDateButton.setOnClickListener(v -> pickDate(false));
        startTimeButton.setOnClickListener(v -> pickTime(true));
        endTimeButton.setOnClickListener(v -> pickTime(false));
        allDayCheck.setOnCheckedChangeListener((b, checked) -> {
            startTimeButton.setEnabled(!checked);
            endTimeButton.setEnabled(!checked);
            startTimeButton.setAlpha(checked ? .45f : 1f);
            endTimeButton.setAlpha(checked ? .45f : 1f);
        });
        openLink.setOnClickListener(v -> openLink());

        for (EditText e : new EditText[]{titleEdit, noteEdit, linkEdit}) {
            e.setOnFocusChangeListener((v, focused) -> setInteraction("editor_focus", focused || editor.getVisibility() == VISIBLE));
        }
    }

    private void showEditor(long id, int startDay, int endDay) {
        editingId = id;
        CalendarStore.Event e = id > 0 ? CalendarStore.get(getContext(), id) : null;
        if (e == null) {
            e = new CalendarStore.Event();
            e.startDay = startDay;
            e.endDay = endDay;
        }
        editStartDay = e.startDay;
        editEndDay = e.endDay;
        editStartMinute = e.startMinute;
        editEndMinute = e.endMinute;
        editColor = e.color;

        titleEdit.setText(e.title);
        noteEdit.setText(e.note);
        linkEdit.setText(e.link);
        allDayCheck.setChecked(e.allDay);
        repeatSpinner.setSelection(repeatPosition(e.repeat));
        reminderSpinner.setSelection(reminderPosition(e.reminderMinutes));
        deleteButton.setVisibility(id > 0 ? VISIBLE : GONE);
        updateEditorLabels();
        refreshColorSelection();

        editor.setVisibility(VISIBLE);
        editor.bringToFront();
        setInteraction("calendar_editor", true);
        titleEdit.requestFocus();
        showKeyboard(titleEdit);
    }

    private void closeEditor() {
        if (editor == null || editor.getVisibility() != VISIBLE) return;
        editor.setVisibility(GONE);
        titleEdit.clearFocus();
        noteEdit.clearFocus();
        linkEdit.clearFocus();
        hideKeyboard();
        setInteraction("editor_focus", false);
        setInteraction("calendar_editor", false);
    }

    private void saveEditor() {
        String title = titleEdit.getText().toString().trim();
        if (title.isEmpty()) {
            toast("일정 제목을 입력해 주세요");
            return;
        }
        CalendarStore.Event e = editingId > 0 ? CalendarStore.get(getContext(), editingId) : null;
        if (e == null) e = new CalendarStore.Event();
        e.id = editingId;
        e.title = title;
        e.startDay = editStartDay;
        e.endDay = editEndDay;
        e.startMinute = editStartMinute;
        e.endMinute = editEndMinute;
        e.allDay = allDayCheck.isChecked();
        e.color = editColor;
        e.note = noteEdit.getText().toString();
        e.link = normalizeUrl(linkEdit.getText().toString());
        e.repeat = repeatValue(repeatSpinner.getSelectedItemPosition());
        e.reminderMinutes = reminderValue(reminderSpinner.getSelectedItemPosition());

        e = CalendarStore.upsert(getContext(), e);
        editingId = e.id;
        CalendarReminderManager.schedule(getContext(), e);
        if (e.reminderMinutes >= 0 && callback != null) callback.onReminderPermissionNeeded();

        closeEditor();
        grid.invalidate();
        updateHeader();
    }

    private void movePeriod(int direction) {
        int day = grid.getAnchorDay();
        if (grid.getViewMode() == CalendarGridView.VIEW_WEEK) {
            day = CalendarStore.addDays(day, direction * 7);
        } else {
            day = CalendarStore.addMonths(day, direction);
        }
        grid.setAnchorDay(day);
        updateHeader();
    }

    private void setViewMode(int mode) {
        grid.setViewMode(mode);
        Prefs.setCalendarView(getContext(), mode == CalendarGridView.VIEW_WEEK ? "week" : "month");
        updateHeader();
    }

    private void updateHeader() {
        if (grid == null) return;
        if (grid.getViewMode() == CalendarGridView.VIEW_MONTH) {
            monthLabel.setText(CalendarStore.formatMonth(grid.getAnchorDay()));
        } else {
            int s = CalendarStore.startOfWeek(grid.getAnchorDay());
            int e = CalendarStore.addDays(s, 6);
            monthLabel.setText(shortDay(s) + " ~ " + shortDay(e));
        }
        boolean week = grid.getViewMode() == CalendarGridView.VIEW_WEEK;
        weekButton.setAlpha(week ? 1f : .72f);
        monthButton.setAlpha(week ? .72f : 1f);
        weekButton.setBackground(rounded(
                week ? DesignTokens.CALENDAR_SOFT : DesignTokens.SURFACE_SOFT,
                dp(11), week ? DesignTokens.CALENDAR : DesignTokens.BORDER, dp(1)));
        monthButton.setBackground(rounded(
                week ? DesignTokens.SURFACE_SOFT : DesignTokens.CALENDAR_SOFT,
                dp(11), week ? DesignTokens.BORDER : DesignTokens.CALENDAR, dp(1)));
    }

    private void pickDate(boolean start) {
        int day = start ? editStartDay : editEndDay;
        Calendar c = CalendarStore.toCalendar(day);
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (v, y, m, d) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(y, m, d, 12, 0, 0);
            int selected = CalendarStore.fromCalendar(picked);
            if (start) {
                editStartDay = selected;
                if (CalendarStore.compare(editEndDay, selected) < 0) editEndDay = selected;
            } else {
                editEndDay = selected;
                if (CalendarStore.compare(selected, editStartDay) < 0) editStartDay = selected;
            }
            updateEditorLabels();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void pickTime(boolean start) {
        int minute = start ? editStartMinute : editEndMinute;
        TimePickerDialog dialog = new TimePickerDialog(getContext(), (v, hour, min) -> {
            int selected = hour * 60 + min;
            if (start) editStartMinute = selected;
            else editEndMinute = selected;
            updateEditorLabels();
        }, minute / 60, minute % 60, true);
        dialog.show();
    }

    private void updateEditorLabels() {
        startDateButton.setText("시작 " + CalendarStore.formatDay(editStartDay));
        endDateButton.setText("종료 " + CalendarStore.formatDay(editEndDay));
        startTimeButton.setText("시작 " + CalendarStore.minuteLabel(editStartMinute));
        endTimeButton.setText("종료 " + CalendarStore.minuteLabel(editEndMinute));
    }

    private void refreshColorSelection() {
        if (colorRow == null) return;
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            View v = colorRow.getChildAt(i);
            Object tag = v.getTag();
            if (!(tag instanceof Integer)) continue;
            int color = (Integer) tag;
            v.setScaleX(color == editColor ? 1.12f : .92f);
            v.setScaleY(color == editColor ? 1.12f : .92f);
            v.setAlpha(color == editColor ? 1f : .72f);
        }
    }

    private void openLink() {
        String value = normalizeUrl(linkEdit.getText().toString());
        if (value.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (RuntimeException e) {
            toast("링크를 열 수 없어요");
        }
    }

    private String normalizeUrl(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return "";
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "https://" + v;
        return v;
    }

    private int repeatPosition(String value) {
        if (CalendarStore.REPEAT_DAILY.equals(value)) return 1;
        if (CalendarStore.REPEAT_WEEKLY.equals(value)) return 2;
        if (CalendarStore.REPEAT_MONTHLY.equals(value)) return 3;
        if (CalendarStore.REPEAT_YEARLY.equals(value)) return 4;
        return 0;
    }

    private String repeatValue(int p) {
        if (p == 1) return CalendarStore.REPEAT_DAILY;
        if (p == 2) return CalendarStore.REPEAT_WEEKLY;
        if (p == 3) return CalendarStore.REPEAT_MONTHLY;
        if (p == 4) return CalendarStore.REPEAT_YEARLY;
        return CalendarStore.REPEAT_NONE;
    }

    private int reminderPosition(int value) {
        if (value == 0) return 1;
        if (value == 10) return 2;
        if (value == 30) return 3;
        if (value == 60) return 4;
        if (value == 1440) return 5;
        return 0;
    }

    private int reminderValue(int p) {
        if (p == 1) return 0;
        if (p == 2) return 10;
        if (p == 3) return 30;
        if (p == 4) return 60;
        if (p == 5) return 1440;
        return -1;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(getContext());
        s.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, values));
        return s;
    }

    private EditText field(String hint, boolean single) {
        EditText e = new EditText(getContext());
        e.setHint(hint);
        e.setSingleLine(single);
        e.setTextSize(13.5f);
        e.setTextColor(DesignTokens.INK);
        e.setHintTextColor(DesignTokens.MUTED);
        e.setPadding(dp(11), dp(5), dp(11), dp(5));
        e.setBackground(rounded(DesignTokens.SURFACE, dp(12), DesignTokens.BORDER, dp(1)));
        return e;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.setMargins(0, dp(3), 0, dp(3));
        return lp;
    }

    private Button soft(String value) {
        Button b = new Button(getContext());
        b.setText(value);
        b.setTextSize(11.5f);
        b.setTextColor(DesignTokens.INK);
        b.setAllCaps(false);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(rounded(DesignTokens.SURFACE_SOFT, dp(11), DesignTokens.BORDER, dp(1)));
        return b;
    }

    private Button primary(String value) {
        Button b = soft(value);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(DesignTokens.CALENDAR, dp(11), Color.TRANSPARENT, 0));
        return b;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private void setInteraction(String key, boolean active) {
        boolean wasEmpty = interactions.isEmpty();
        if (active) interactions.add(key); else interactions.remove(key);
        boolean isEmpty = interactions.isEmpty();
        if (callback != null && wasEmpty != isEmpty) callback.onInteractionChanged(!isEmpty);
    }

    private void showKeyboard(EditText edit) {
        edit.postDelayed(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        }, 100L);
    }

    private void hideKeyboard() {
        View f = findFocus();
        if (f == null) return;
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    private String shortDay(int day) {
        int y = day / 10000;
        int m = day / 100 % 100;
        int d = day % 100;
        return String.format(Locale.KOREAN, "%d.%d.%d", y, m, d);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(getContext(), value, Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
