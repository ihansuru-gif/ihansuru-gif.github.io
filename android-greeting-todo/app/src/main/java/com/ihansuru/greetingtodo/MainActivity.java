package com.ihansuru.greetingtodo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 2001;
    private static final int NOTIFICATION_PERMISSION = 2002;

    private RadioButton imageMode;
    private RadioButton todoMode;
    private RadioButton bothMode;
    private ImageView imagePreview;
    private TextView imageState;
    private TextView durationValue;
    private TextView overlayState;
    private SeekBar durationSeek;
    private Switch tapDismiss;
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
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 253));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("인사앱", 29, true, Color.rgb(25, 38, 61));
        root.addView(title);
        TextView subtitle = text("화면을 켜면 내가 정한 이미지와 투두가 잠깐 나타나요", 13, false, Color.rgb(104, 116, 136));
        LinearLayout.LayoutParams subLp = wrap();
        subLp.setMargins(0, dp(4), 0, dp(18));
        root.addView(subtitle, subLp);

        LinearLayout modeCard = card();
        modeCard.addView(text("표시 요소", 16, true, dark()));
        modeCard.addView(caption("이미지와 투두 중 원하는 구성을 골라요"));
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.VERTICAL);
        imageMode = radio("이미지");
        todoMode = radio("투두");
        bothMode = radio("이미지 + 투두 둘 다");
        modes.addView(imageMode);
        modes.addView(todoMode);
        modes.addView(bothMode);
        modeCard.addView(modes, matchWrap(dp(8), 0));

        Button todoEdit = softButton("투두 내용 수정");
        modeCard.addView(todoEdit, buttonLp(dp(10)));
        Button edit = primary("화면 편집");
        modeCard.addView(edit, buttonLp(dp(8)));
        root.addView(modeCard, cardLp(0));

        LinearLayout imageCard = card();
        LinearLayout imageHeader = new LinearLayout(this);
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        imageHeader.addView(text("이미지", 16, true, dark()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button choose = softButton("이미지 선택");
        imageHeader.addView(choose, new LinearLayout.LayoutParams(dp(108), dp(42)));
        imageCard.addView(imageHeader);
        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePreview.setBackground(rounded(Color.rgb(244, 247, 251), dp(18), Color.rgb(230, 235, 243), 1));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        previewLp.setMargins(0, dp(12), 0, 0);
        imageCard.addView(imagePreview, previewLp);
        imageState = caption("");
        imageCard.addView(imageState, matchWrap(dp(8), 0));
        root.addView(imageCard, cardLp(dp(12)));

        LinearLayout settingsCard = card();
        settingsCard.addView(text("표시 설정", 16, true, dark()));
        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        durationRow.addView(text("표시 시간", 14, true, dark()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        durationValue = text("4.0초", 13, true, Color.rgb(66, 117, 235));
        durationRow.addView(durationValue);
        settingsCard.addView(durationRow, matchWrap(dp(12), 0));
        durationSeek = new SeekBar(this);
        durationSeek.setMax(95);
        settingsCard.addView(durationSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        settingsCard.addView(divider(), matchHeight(dp(10), 1));
        tapDismiss = new Switch(this);
        tapDismiss.setText("터치 시 바로 닫기");
        tapDismiss.setTextSize(14);
        tapDismiss.setTextColor(dark());
        tapDismiss.setGravity(Gravity.CENTER_VERTICAL);
        settingsCard.addView(tapDismiss, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        enabled = new Switch(this);
        enabled.setText("기능 사용");
        enabled.setTextSize(14);
        enabled.setTextColor(dark());
        enabled.setGravity(Gravity.CENTER_VERTICAL);
        settingsCard.addView(enabled, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(settingsCard, cardLp(dp(12)));

        LinearLayout permissionCard = card();
        LinearLayout permissionRow = new LinearLayout(this);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout permissionText = new LinearLayout(this);
        permissionText.setOrientation(LinearLayout.VERTICAL);
        permissionText.addView(text("다른 앱 위에 표시", 15, true, dark()));
        overlayState = caption("");
        permissionText.addView(overlayState);
        permissionRow.addView(permissionText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button permission = softButton("권한 열기");
        permissionRow.addView(permission, new LinearLayout.LayoutParams(dp(100), dp(42)));
        permissionCard.addView(permissionRow);
        root.addView(permissionCard, cardLp(dp(12)));

        Button preview = primary("실제 표시 미리보기");
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        pLp.setMargins(0, dp(16), 0, 0);
        root.addView(preview, pLp);

        modes.setOnCheckedChangeListener((g, id) -> {
            if (syncing) return;
            if (imageMode.isChecked()) Prefs.setMode(this, Prefs.MODE_IMAGE);
            else if (todoMode.isChecked()) Prefs.setMode(this, Prefs.MODE_TODO);
            else Prefs.setMode(this, Prefs.MODE_BOTH);
            updateImageCardVisibility(imageCard);
        });
        todoEdit.setOnClickListener(v -> startActivity(new Intent(this, TodoQuickEditActivity.class)));
        edit.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        choose.setOnClickListener(v -> pickImage());
        permission.setOnClickListener(v -> openOverlayPermission());
        preview.setOnClickListener(v -> showPreview());
        tapDismiss.setOnCheckedChangeListener((b, checked) -> {
            if (!syncing) Prefs.setTapDismiss(this, checked);
        });
        enabled.setOnCheckedChangeListener((b, checked) -> {
            if (syncing) return;
            if (checked && !readyToEnable()) {
                syncing = true;
                enabled.setChecked(false);
                syncing = false;
                return;
            }
            Prefs.setEnabled(this, checked);
            if (checked) {
                startWakeService();
                requestNotificationPermission();
            } else {
                stopWakeService();
            }
        });
        durationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                long ms = 500L + progress * 100L;
                durationValue.setText(String.format(java.util.Locale.KOREAN, "%.1f초", ms / 1000f));
                if (fromUser) Prefs.setDuration(MainActivity.this, ms);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        return scroll;
    }

    private void syncUi() {
        syncing = true;
        String mode = Prefs.mode(this);
        imageMode.setChecked(Prefs.MODE_IMAGE.equals(mode));
        todoMode.setChecked(Prefs.MODE_TODO.equals(mode));
        bothMode.setChecked(Prefs.MODE_BOTH.equals(mode));
        durationSeek.setProgress((int) ((Prefs.duration(this) - 500) / 100));
        durationValue.setText(String.format(java.util.Locale.KOREAN, "%.1f초", Prefs.duration(this) / 1000f));
        tapDismiss.setChecked(Prefs.tapDismiss(this));
        enabled.setChecked(Prefs.enabled(this));
        syncing = false;
        overlayState.setText(Settings.canDrawOverlays(this) ? "허용됨" : "허용 필요");
        overlayState.setTextColor(Settings.canDrawOverlays(this) ? Color.rgb(41, 151, 101) : Color.rgb(207, 115, 49));
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

    private void updateImageCardVisibility(LinearLayout imageCard) {
        imageCard.setAlpha(Prefs.showImage(this) ? 1f : .45f);
    }

    private boolean readyToEnable() {
        if (!Settings.canDrawOverlays(this)) {
            toast("다른 앱 위에 표시 권한을 먼저 허용해 주세요");
            openOverlayPermission();
            return false;
        }
        if (Prefs.showImage(this) && !ImageStore.has(this)) {
            toast("이미지를 먼저 선택해 주세요");
            return false;
        }
        return true;
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
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (ImageStore.importUri(this, uri)) {
                refreshImagePreview();
                toast("이미지를 저장했어요");
            } else {
                toast("이 이미지는 사용할 수 없어요");
            }
        }
    }

    private void openOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private void startWakeService() {
        Intent i = new Intent(this, WakeService.class).setAction(WakeService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (RuntimeException e) {
            Prefs.setEnabled(this, false);
        }
    }

    private void stopWakeService() {
        Prefs.setEnabled(this, false);
        try {
            stopService(new Intent(this, WakeService.class));
        } catch (RuntimeException ignored) {}
        OverlayManager.hide(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        }
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        v.setBackground(rounded(Color.WHITE, dp(22), Color.rgb(232, 236, 244), 1));
        v.setElevation(dp(1));
        return v;
    }

    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private LinearLayout.LayoutParams matchHeight(int top, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    private TextView caption(String s) {
        return text(s, 12.5f, false, Color.rgb(112, 123, 142));
    }

    private TextView text(String s, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private RadioButton radio(String s) {
        RadioButton b = new RadioButton(this);
        b.setText(s);
        b.setTextSize(15);
        b.setTextColor(dark());
        b.setMinHeight(dp(46));
        return b;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(53, 155, 255), Color.rgb(122, 111, 255)});
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        return b;
    }

    private Button softButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setTextColor(dark());
        b.setAllCaps(false);
        b.setBackground(rounded(Color.rgb(246, 248, 252), dp(14), Color.rgb(226, 232, 241), 1));
        return b;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(235, 238, 244));
        return v;
    }

    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    private int dark() { return Color.rgb(42, 54, 75); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
