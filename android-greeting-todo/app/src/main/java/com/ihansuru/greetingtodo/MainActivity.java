package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 2001;

    private LinearLayout tabRows;
    private ImageView imagePreview;
    private TextView imageState;
    private TextView durationValue;
    private TextView tabSizeValue;
    private TextView overlayState;
    private SeekBar durationSeek;
    private SeekBar tabSizeSeek;
    private Switch enabled;
    private boolean syncing;
    private Bitmap previewBitmap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncUi();
        if (Prefs.enabled(this)) startWakeService();
    }

    @Override
    protected void onDestroy() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = null;
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 248, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("인사앱", 29, true, Color.rgb(28, 38, 58)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView version = text("v1.4.1", 12.5f, true, Color.rgb(112, 94, 202));
        version.setPadding(dp(10), dp(5), dp(10), dp(5));
        version.setBackground(rounded(Color.rgb(242, 239, 255), dp(14), Color.rgb(222, 215, 247), 1));
        titleRow.addView(version);
        root.addView(titleRow);

        TextView subtitle = text(
                "화면을 켜면 필요한 기능만 띠지에서 바로 열 수 있어요",
                13, false, Color.rgb(104, 113, 132));
        LinearLayout.LayoutParams slp = wrap();
        slp.setMargins(0, dp(4), 0, dp(18));
        root.addView(subtitle, slp);

        LinearLayout tabsCard = card();
        tabsCard.addView(text("띠지 관리", 17, true, dark()));
        tabsCard.addView(caption("보일 기능을 직접 고르고 순서를 정해요 · 투두는 항상 맨 위"));
        tabRows = new LinearLayout(this);
        tabRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trlp.setMargins(0, dp(8), 0, 0);
        tabsCard.addView(tabRows, trlp);

        LinearLayout tabSizeRow = new LinearLayout(this);
        tabSizeRow.setGravity(Gravity.CENTER_VERTICAL);
        tabSizeRow.addView(text("띠지 크기", 13.5f, true, dark()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabSizeValue = text("100%", 12.5f, true, Color.rgb(112, 94, 202));
        tabSizeRow.addView(tabSizeValue);
        tabsCard.addView(tabSizeRow, matchWrap(dp(9), 0));

        tabSizeSeek = new SeekBar(this);
        tabSizeSeek.setMax(60);
        tabsCard.addView(tabSizeSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        Button edit = primary("실제 화면에서 위치·크기 편집");
        tabsCard.addView(edit, buttonLp(dp(8)));
        tabsCard.addView(caption(
                "각 카드 오른쪽 아래 ↘ 손잡이를 바로 끌어 크기를 조절하고 › 버튼으로 자기 띠지에 접어요"),
                matchWrap(dp(6), 0));
        root.addView(tabsCard, cardLp(0));

        LinearLayout calendarCard = card();
        calendarCard.addView(text("일정표", 17, true, dark()));
        calendarCard.addView(caption("주간·월간 버튼 전환 · 날짜 드래그로 기간 지정"));
        calendarCard.addView(caption("기간 색선 · 겹치는 일정 레인 · 반복 · 알림 · 검색"));
        Button openCalendar = softButton("일정 바로 열기");
        calendarCard.addView(openCalendar, buttonLp(dp(10)));
        root.addView(calendarCard, cardLp(dp(12)));

        LinearLayout memoCard = card();
        memoCard.addView(text("메모", 17, true, dark()));
        memoCard.addView(caption("서식 · 체크리스트 · 검색 · 태그 · 보관함 · 이미지 · 링크"));
        memoCard.addView(caption("펜/연필/형광펜 · Undo/Redo · 무지/줄/격자 · 음성메모"));
        Button openMemo = softButton("메모 바로 열기");
        memoCard.addView(openMemo, buttonLp(dp(10)));
        root.addView(memoCard, cardLp(dp(12)));

        LinearLayout imageCard = card();
        LinearLayout imageHeader = new LinearLayout(this);
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        imageHeader.addView(text("이미지", 17, true, dark()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button choose = softButton("이미지 선택");
        imageHeader.addView(choose, new LinearLayout.LayoutParams(dp(108), dp(42)));
        imageCard.addView(imageHeader);

        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePreview.setBackground(rounded(
                Color.rgb(244, 246, 250), dp(18), Color.rgb(229, 233, 240), 1));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(175));
        previewLp.setMargins(0, dp(10), 0, 0);
        imageCard.addView(imagePreview, previewLp);
        imageState = caption("");
        imageCard.addView(imageState, matchWrap(dp(7), 0));
        root.addView(imageCard, cardLp(dp(12)));

        LinearLayout settingsCard = card();
        settingsCard.addView(text("표시 설정", 17, true, dark()));

        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        durationRow.addView(text("표시 시간", 13.5f, true, dark()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        durationValue = text("4.0초", 12.5f, true, Color.rgb(112, 94, 202));
        durationRow.addView(durationValue);
        settingsCard.addView(durationRow, matchWrap(dp(10), 0));

        durationSeek = new SeekBar(this);
        durationSeek.setMax(95);
        settingsCard.addView(durationSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        settingsCard.addView(caption(
                "입력·낙서·녹음·일정 기간 선택·편집·이동·크기 조절 중에는 자동 종료가 멈춰요"),
                matchWrap(dp(5), dp(4)));
        settingsCard.addView(caption(
                "조작이 끝나면 설정한 표시 시간이 처음부터 다시 시작돼요"),
                matchWrap(dp(2), dp(4)));

        enabled = new Switch(this);
        enabled.setText("화면을 켤 때 인사앱 표시");
        enabled.setTextSize(14);
        enabled.setTextColor(dark());
        enabled.setGravity(Gravity.CENTER_VERTICAL);
        settingsCard.addView(enabled, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(settingsCard, cardLp(dp(12)));

        LinearLayout permissionCard = card();
        LinearLayout permissionRow = new LinearLayout(this);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout permissionText = new LinearLayout(this);
        permissionText.setOrientation(LinearLayout.VERTICAL);
        permissionText.addView(text("다른 앱 위에 표시", 15, true, dark()));
        overlayState = caption("");
        permissionText.addView(overlayState);
        permissionRow.addView(permissionText,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button permission = softButton("권한 열기");
        permissionRow.addView(permission, new LinearLayout.LayoutParams(dp(100), dp(42)));
        permissionCard.addView(permissionRow);
        root.addView(permissionCard, cardLp(dp(12)));

        Button preview = primary("실제 표시 미리보기");
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        pLp.setMargins(0, dp(16), 0, 0);
        root.addView(preview, pLp);

        tabSizeSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 75 + progress;
                tabSizeValue.setText(value + "%");
                if (fromUser) Prefs.setTabSize(MainActivity.this, value);
            }
        });
        durationSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long ms = 500L + progress * 100L;
                durationValue.setText(String.format(Locale.KOREAN, "%.1f초", ms / 1000f));
                if (fromUser) Prefs.setDuration(MainActivity.this, ms);
            }
        });

        edit.setOnClickListener(v -> showDirectEdit());
        openCalendar.setOnClickListener(v -> {
            Prefs.setCalendarEnabled(this, true);
            Prefs.setCalendarExpanded(this, true);
            rebuildTabRows();
            showPreview();
        });
        openMemo.setOnClickListener(v -> {
            Prefs.setMemoEnabled(this, true);
            Prefs.setMemoExpanded(this, true);
            rebuildTabRows();
            showPreview();
        });
        choose.setOnClickListener(v -> pickImage());
        permission.setOnClickListener(v -> openOverlayPermission());
        preview.setOnClickListener(v -> showPreview());

        enabled.setOnCheckedChangeListener((b, checked) -> {
            if (syncing) return;
            if (checked && !readyToEnable()) {
                syncing = true;
                enabled.setChecked(false);
                syncing = false;
                return;
            }
            Prefs.setEnabled(this, checked);
            if (checked) startWakeService(); else stopWakeService();
        });

        rebuildTabRows();
        return scroll;
    }

    private void rebuildTabRows() {
        if (tabRows == null) return;
        tabRows.removeAllViews();
        tabRows.addView(tabRow("todo", "✓  투두", Prefs.todoTabEnabled(this), true));

        List<String> order = Prefs.tabOrder(this);
        for (String key : order) {
            if ("calendar".equals(key)) {
                tabRows.addView(tabRow(key, "▣  일정", Prefs.calendarEnabled(this), false));
            } else if ("memo".equals(key)) {
                tabRows.addView(tabRow(key, "✎  메모", Prefs.memoEnabled(this), false));
            } else if ("image".equals(key)) {
                tabRows.addView(tabRow(key, "▧  이미지", Prefs.imageTabEnabled(this), false));
            }
        }
    }

    private View tabRow(String key, String label, boolean checked, boolean fixed) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(3), dp(2), dp(3), dp(2));

        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(14);
        toggle.setTextColor(dark());
        toggle.setChecked(checked);
        row.addView(toggle, new LinearLayout.LayoutParams(0, dp(48), 1));

        if (fixed) {
            TextView pin = text("맨 위 고정", 11.5f, true, Color.rgb(129, 119, 168));
            row.addView(pin, new LinearLayout.LayoutParams(dp(78), dp(42)));
        } else {
            Button up = softButton("↑");
            Button down = softButton("↓");
            row.addView(up, new LinearLayout.LayoutParams(dp(42), dp(40)));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(42), dp(40));
            dlp.setMargins(dp(3), 0, 0, 0);
            row.addView(down, dlp);
            up.setOnClickListener(v -> {
                Prefs.moveTab(this, key, -1);
                rebuildTabRows();
            });
            down.setOnClickListener(v -> {
                Prefs.moveTab(this, key, 1);
                rebuildTabRows();
            });
        }

        toggle.setOnCheckedChangeListener((button, value) -> {
            if ("todo".equals(key)) Prefs.setTodoTabEnabled(this, value);
            else if ("calendar".equals(key)) Prefs.setCalendarEnabled(this, value);
            else if ("memo".equals(key)) Prefs.setMemoEnabled(this, value);
            else if ("image".equals(key)) Prefs.setImageTabEnabled(this, value);
        });

        return row;
    }

    private void syncUi() {
        syncing = true;
        durationSeek.setProgress((int) ((Prefs.duration(this) - 500L) / 100L));
        durationValue.setText(String.format(Locale.KOREAN, "%.1f초", Prefs.duration(this) / 1000f));
        tabSizeSeek.setProgress(Prefs.tabSize(this) - 75);
        tabSizeValue.setText(Prefs.tabSize(this) + "%");
        enabled.setChecked(Prefs.enabled(this));
        syncing = false;

        rebuildTabRows();

        boolean overlayAllowed = Settings.canDrawOverlays(this);
        overlayState.setText(overlayAllowed ? "허용됨" : "허용 필요");
        overlayState.setTextColor(overlayAllowed
                ? Color.rgb(41, 151, 101)
                : Color.rgb(207, 115, 49));
        refreshImagePreview();
    }

    private void refreshImagePreview() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = ImageStore.load(this, 1200);
        if (previewBitmap != null) {
            imagePreview.setImageBitmap(previewBitmap);
            imageState.setText("이미지 준비 완료");
        } else {
            imagePreview.setImageDrawable(null);
            imageState.setText("이미지를 선택해 주세요");
        }
    }

    private boolean readyToEnable() {
        if (!Settings.canDrawOverlays(this)) {
            toast("다른 앱 위에 표시 권한을 먼저 허용해 주세요");
            openOverlayPermission();
            return false;
        }
        return true;
    }

    private void showDirectEdit() {
        if (!readyToEnable()) return;
        if (!OverlayManager.show(this)) {
            toast("실제 화면을 열지 못했어요");
            return;
        }
        toast("띠지를 길게 누르거나 카드의 ⚙️를 눌러 위치·크기를 편집해요");
    }

    private void showPreview() {
        if (!readyToEnable()) return;
        if (!OverlayManager.show(this)) toast("미리보기를 표시하지 못했어요");
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (ImageStore.importUri(this, uri)) {
                Prefs.setImageTabEnabled(this, true);
                refreshImagePreview();
                rebuildTabRows();
                toast("이미지를 저장했어요");
            } else {
                toast("이 이미지는 사용할 수 없어요");
            }
        }
    }

    private void openOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void startWakeService() {
        Intent intent = new Intent(this, WakeService.class).setAction(WakeService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException e) {
            Prefs.setEnabled(this, false);
        }
    }

    private void stopWakeService() {
        Prefs.setEnabled(this, false);
        try { stopService(new Intent(this, WakeService.class)); } catch (RuntimeException ignored) {}
        OverlayManager.hide(this);
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        v.setBackground(rounded(Color.WHITE, dp(22), Color.rgb(230, 233, 240), 1));
        v.setElevation(dp(1));
        return v;
    }

    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private TextView caption(String s) {
        return text(s, 12.3f, false, Color.rgb(111, 119, 136));
    }

    private TextView text(String s, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14.5f);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(122, 112, 233), Color.rgb(174, 126, 220)});
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        return b;
    }

    private Button softButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(12.5f);
        b.setTextColor(dark());
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(rounded(Color.rgb(248, 247, 251), dp(14), Color.rgb(226, 223, 233), 1));
        return b;
    }

    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    private int dark() { return Color.rgb(43, 49, 65); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
