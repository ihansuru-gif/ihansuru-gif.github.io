package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private BookishSwitch enabled;
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
        scroll.setBackgroundColor(DesignTokens.PAPER);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(44));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildHero(root);

        LinearLayout tabs = section("띠지 관리", "책의 인덱스처럼 필요한 기능만 곁에 두세요.");
        tabRows = new LinearLayout(this);
        tabRows.setOrientation(LinearLayout.VERTICAL);
        tabs.addView(tabRows, matchWrap(dp(10), 0));

        LinearLayout tabSizeHeader = compactHeader("띠지 크기");
        tabSizeValue = smallValue("100%");
        tabSizeHeader.addView(tabSizeValue);
        tabs.addView(tabSizeHeader, matchWrap(dp(14), 0));

        tabSizeSeek = new SeekBar(this);
        tabSizeSeek.setMax(60);
        styleSeek(tabSizeSeek, DesignTokens.TODO);
        tabs.addView(tabSizeSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView directEdit = actionButton("실제 화면에서 위치·크기 편집",
                BookishIconView.EDIT, DesignTokens.INK, DesignTokens.SURFACE_SOFT, false);
        tabs.addView(directEdit, actionLp(dp(10)));
        TextView hint = caption("위쪽 이동선으로 옮기고, 오른쪽 아래 // 손잡이로 크기를 조절해요.");
        tabs.addView(hint, matchWrap(dp(8), 0));
        root.addView(tabs, sectionLp(0));

        LinearLayout lock = section("잠금화면", "원래 배경은 그대로, 필요한 정보만 가볍게 올립니다.");

        LinearLayout enableRow = settingRow(BookishIconView.LOCK, "화면을 켤 때 인사앱 표시",
                "잠금화면과 일반 화면 위에 투명하게 표시");
        enabled = new BookishSwitch(this);
        enabled.setAccent(DesignTokens.TODO);
        ((LinearLayout) enableRow).addView(enabled,
                new LinearLayout.LayoutParams(dp(46), dp(44)));
        lock.addView(enableRow);

        lock.addView(divider(), dividerLp());

        LinearLayout durationHeader = compactHeader("표시 시간");
        durationValue = smallValue("4.0초");
        durationHeader.addView(durationValue);
        lock.addView(durationHeader, matchWrap(dp(12), 0));

        durationSeek = new SeekBar(this);
        durationSeek.setMax(95);
        styleSeek(durationSeek, DesignTokens.CALENDAR);
        lock.addView(durationSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView timerCaption = caption(
                "입력·낙서·녹음·일정 선택·이동·크기 조절 중에는 종료가 멈추고, 조작이 끝나면 처음부터 다시 셉니다.");
        lock.addView(timerCaption, matchWrap(dp(3), 0));
        root.addView(lock, sectionLp(dp(14)));

        LinearLayout features = section("기능", "각 카드의 내용을 확인하고 바로 열 수 있어요.");
        features.addView(featureRow("todo", "투두", "할 일과 카테고리를 빠르게 확인"));
        features.addView(divider(), dividerLp());
        View calendarRow = featureRow("calendar", "일정", "주간·월간·기간 일정과 알림");
        features.addView(calendarRow);
        features.addView(divider(), dividerLp());
        View memoRow = featureRow("memo", "메모", "텍스트·체크·낙서·링크·음성");
        features.addView(memoRow);
        root.addView(features, sectionLp(dp(14)));

        LinearLayout image = section("이미지", "잠금화면에 두고 싶은 이미지를 선택합니다.");
        LinearLayout imageHeader = new LinearLayout(this);
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        imageHeader.addView(iconLabel("image", "이미지 카드"),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView choose = smallAction("이미지 선택", DesignTokens.IMAGE);
        imageHeader.addView(choose, new LinearLayout.LayoutParams(dp(104), dp(38)));
        image.addView(imageHeader);

        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePreview.setBackground(rounded(
                DesignTokens.SURFACE_SOFT, dp(16), DesignTokens.BORDER, dp(1)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        previewLp.setMargins(0, dp(10), 0, 0);
        image.addView(imagePreview, previewLp);

        imageState = caption("");
        image.addView(imageState, matchWrap(dp(8), 0));
        root.addView(image, sectionLp(dp(14)));

        LinearLayout permission = section("권한", "필요한 권한만 직접 확인할 수 있어요.");
        LinearLayout permissionRow = settingRow(BookishIconView.SETTINGS,
                "다른 앱 위에 표시", "잠금화면 위 카드 표시에 필요");
        overlayState = smallValue("");
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        stateLp.setMargins(dp(8), 0, dp(8), 0);
        ((LinearLayout) permissionRow).addView(overlayState, stateLp);
        TextView permissionOpen = smallAction("권한 열기", DesignTokens.INK);
        ((LinearLayout) permissionRow).addView(permissionOpen,
                new LinearLayout.LayoutParams(dp(88), dp(38)));
        permission.addView(permissionRow);
        root.addView(permission, sectionLp(dp(14)));

        TextView preview = actionButton("실제 표시 미리보기",
                BookishIconView.EDIT, Color.WHITE, DesignTokens.INK, true);
        LinearLayout.LayoutParams previewActionLp = actionLp(dp(20));
        previewActionLp.height = dp(54);
        root.addView(preview, previewActionLp);

        TextView footer = text("오늘의 작은 정리가, 더 나은 내일을 만듭니다.",
                12.5f, false, DesignTokens.SECONDARY);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        footerLp.setMargins(0, dp(14), 0, 0);
        root.addView(footer, footerLp);

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

        directEdit.setOnClickListener(v -> showDirectEdit());
        calendarRow.setOnClickListener(v -> {
            Prefs.setCalendarEnabled(this, true);
            Prefs.setCalendarExpanded(this, true);
            rebuildTabRows();
            showPreview();
        });
        memoRow.setOnClickListener(v -> {
            Prefs.setMemoEnabled(this, true);
            Prefs.setMemoExpanded(this, true);
            rebuildTabRows();
            showPreview();
        });
        choose.setOnClickListener(v -> pickImage());
        permissionOpen.setOnClickListener(v -> openOverlayPermission());
        preview.setOnClickListener(v -> showPreview());

        enabled.setListener(checked -> {
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

    private void buildHero(LinearLayout root) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.TOP);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("잠금화면, 나만의 작은 책상", 12.5f, false, DesignTokens.SECONDARY);
        titles.addView(eyebrow);

        TextView title = text("인사앱", 30f, true, DesignTokens.INK);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, dp(3), 0, 0);
        titles.addView(title, titleLp);

        TextView tagline = text("오늘도, 좋은 하루를 정리해요.", 15f, true, DesignTokens.INK);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.setMargins(0, dp(8), 0, 0);
        titles.addView(tagline, tagLp);

        top.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView version = text("v1.6.0", 11.5f, true, DesignTokens.SECONDARY);
        version.setGravity(Gravity.CENTER);
        version.setBackground(rounded(
                DesignTokens.SURFACE_SOFT, dp(12), DesignTokens.BORDER, dp(1)));
        top.addView(version, new LinearLayout.LayoutParams(dp(64), dp(30)));

        root.addView(top);

        TextView intro = caption(
                "투두, 일정, 메모, 이미지를 한눈에. 책을 펼치듯 필요한 것만 조용히 꺼내 쓰는 잠금화면 도구입니다.");
        intro.setLineSpacing(0f, 1.18f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introLp.setMargins(0, dp(12), 0, dp(22));
        root.addView(intro, introLp);
    }

    private LinearLayout section(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(16));
        box.setBackground(rounded(
                DesignTokens.SURFACE, dp(18), DesignTokens.BORDER, dp(1)));
        box.setElevation(dp(.5f));

        box.addView(text(title, 16.5f, true, DesignTokens.INK));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = caption(subtitle);
            sub.setLineSpacing(0f, 1.12f);
            box.addView(sub, matchWrap(dp(4), 0));
        }
        return box;
    }

    private View featureRow(String key, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        BookishIconView icon = new BookishIconView(this, iconType(key));
        icon.setIconColor(DesignTokens.accent(key));
        FrameLayout iconSurface = iconSurface(icon, key);
        row.addView(iconSurface, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(title, 14.5f, true, DesignTokens.INK));
        copy.addView(text(subtitle, 12f, false, DesignTokens.SECONDARY));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));

        BookishIconView arrow = new BookishIconView(this, BookishIconView.DOWN);
        arrow.setRotation(-90f);
        arrow.setIconColor(DesignTokens.MUTED);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(30)));
        row.setBackground(pressableSurface(Color.TRANSPARENT,
                DesignTokens.blend(DesignTokens.soft(key), DesignTokens.PAPER, .45f), dp(12)));
        row.setClickable(true);
        return row;
    }

    private LinearLayout settingRow(int iconType, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        BookishIconView icon = new BookishIconView(this, iconType);
        icon.setIconColor(DesignTokens.SECONDARY);
        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(rounded(
                DesignTokens.SURFACE_SOFT, dp(11), DesignTokens.BORDER, dp(1)));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
        iconSurface.addView(icon, ilp);
        row.addView(iconSurface, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, dp(8), 0);
        copy.addView(text(title, 14f, true, DesignTokens.INK));
        copy.addView(text(subtitle, 11.7f, false, DesignTokens.SECONDARY));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        return row;
    }

    private void rebuildTabRows() {
        if (tabRows == null) return;
        tabRows.removeAllViews();

        for (String key : Prefs.tabOrder(this)) {
            boolean checked = "todo".equals(key) ? Prefs.todoTabEnabled(this)
                    : "calendar".equals(key) ? Prefs.calendarEnabled(this)
                    : "memo".equals(key) ? Prefs.memoEnabled(this)
                    : Prefs.imageTabEnabled(this);
            tabRows.addView(tabRow(key, checked));
        }
    }

    private View tabRow(String key, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(3), dp(6), dp(3));
        row.setBackground(rounded(
                DesignTokens.soft(key), dp(14),
                DesignTokens.alpha(DesignTokens.accent(key), 66), dp(1)));

        BookishIconView icon = new BookishIconView(this, iconType(key));
        icon.setIconColor(DesignTokens.accent(key));
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView label = text(tabLabel(key), 14f, true, DesignTokens.INK);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, dp(46), 1);
        labelLp.setMargins(dp(10), 0, 0, 0);
        row.addView(label, labelLp);

        BookishSwitch toggle = new BookishSwitch(this);
        toggle.setAccent(DesignTokens.accent(key));
        toggle.setChecked(checked);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(46), dp(42)));

        View up = iconButton(BookishIconView.UP, DesignTokens.accent(key));
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        upLp.setMargins(dp(7), 0, 0, 0);
        row.addView(up, upLp);

        View down = iconButton(BookishIconView.DOWN, DesignTokens.accent(key));
        LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        downLp.setMargins(dp(4), 0, 0, 0);
        row.addView(down, downLp);

        up.setOnClickListener(v -> {
            Prefs.moveTab(this, key, -1);
            rebuildTabRows();
        });
        down.setOnClickListener(v -> {
            Prefs.moveTab(this, key, 1);
            rebuildTabRows();
        });
        toggle.setListener(value -> {
            if ("todo".equals(key)) Prefs.setTodoTabEnabled(this, value);
            else if ("calendar".equals(key)) Prefs.setCalendarEnabled(this, value);
            else if ("memo".equals(key)) Prefs.setMemoEnabled(this, value);
            else Prefs.setImageTabEnabled(this, value);
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        rowLp.setMargins(0, dp(4), 0, 0);
        row.setLayoutParams(rowLp);
        return row;
    }

    private View iconButton(int type, int accent) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(pressableSurface(
                Color.argb(224,255,255,255),
                DesignTokens.blend(Color.WHITE, accent, .12f),
                dp(11)));
        frame.setClickable(true);
        BookishIconView icon = new BookishIconView(this, type);
        icon.setIconColor(DesignTokens.blend(accent, DesignTokens.INK, .22f));
        frame.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        return frame;
    }

    private FrameLayout iconSurface(BookishIconView icon, String key) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(
                DesignTokens.soft(key), dp(12),
                DesignTokens.alpha(DesignTokens.accent(key), 58), dp(1)));
        frame.addView(icon, new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
        return frame;
    }

    private TextView iconLabel(String key, String title) {
        TextView label = text(title, 14.5f, true, DesignTokens.INK);
        label.setCompoundDrawablePadding(dp(8));
        return label;
    }

    private LinearLayout compactHeader(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 13.5f, true, DesignTokens.INK),
                new LinearLayout.LayoutParams(0, dp(30), 1));
        return row;
    }

    private TextView actionButton(String label, int iconType, int textColor,
                                  int backgroundColor, boolean dark) {
        TextView button = text(label, 14f, true, textColor);
        button.setGravity(Gravity.CENTER);
        button.setBackground(pressableSurface(
                backgroundColor,
                dark ? Color.rgb(59,61,67) : DesignTokens.PAPER,
                dp(14)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView smallAction(String label, int accent) {
        TextView button = text(label, 12.5f, true,
                DesignTokens.blend(accent, DesignTokens.INK, .35f));
        button.setGravity(Gravity.CENTER);
        button.setBackground(pressableSurface(
                DesignTokens.soft(accent == DesignTokens.IMAGE ? "image"
                        : accent == DesignTokens.CALENDAR ? "calendar"
                        : accent == DesignTokens.MEMO ? "memo" : "todo"),
                Color.WHITE, dp(12)));
        button.setClickable(true);
        return button;
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
                ? Color.rgb(73, 132, 103)
                : Color.rgb(175, 109, 71));
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
            imageState.setText("선택한 이미지가 없어요");
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
        toast("카드 위쪽 이동선과 오른쪽 아래 손잡이로 바로 편집할 수 있어요");
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

    private void styleSeek(SeekBar seek, int accent) {
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setThumbTintList(ColorStateList.valueOf(accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(222, 220, 216)));
        seek.setPadding(0, 0, 0, 0);
    }

    private GradientDrawable pressableSurface(int normal, int pressed, float radius) {
        GradientDrawable g = rounded(normal, radius, DesignTokens.BORDER, dp(1));
        return g;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(236, 233, 228));
        return v;
    }

    private LinearLayout.LayoutParams dividerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(4), dp(7), dp(4), dp(7));
        return lp;
    }

    private LinearLayout.LayoutParams sectionLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams actionLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private TextView caption(String value) {
        return text(value, 12.2f, false, DesignTokens.SECONDARY);
    }

    private TextView smallValue(String value) {
        TextView t = text(value, 12.5f, true, DesignTokens.SECONDARY);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        return t;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    private int iconType(String key) {
        if ("todo".equals(key)) return BookishIconView.TODO;
        if ("calendar".equals(key)) return BookishIconView.CALENDAR;
        if ("memo".equals(key)) return BookishIconView.MEMO;
        return BookishIconView.IMAGE;
    }

    private String tabLabel(String key) {
        if ("todo".equals(key)) return "투두";
        if ("calendar".equals(key)) return "일정";
        if ("memo".equals(key)) return "메모";
        return "이미지";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
