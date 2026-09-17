package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends Activity {
    private static final int TODO = 1;
    private static final int IMAGE = 2;

    private FrameLayout canvas;
    private FrameLayout todoBox;
    private FrameLayout imageBox;
    private TodoCardView todoCard;
    private ImageView imageView;
    private Button todoTab;
    private Button imageTab;
    private SeekBar sizeSeek;
    private TextView sizeLabel;
    private LinearLayout colorCard;
    private LinearLayout rows;
    private final ArrayList<TaskEditor> taskEditors = new ArrayList<>();
    private Bitmap bitmap;
    private int selected = TODO;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        bitmap = ImageStore.load(this, 1400);
        setContentView(buildUi());
        rebuildRows();
        canvas.post(this::restoreCanvas);
        select(TODO);
    }

    @Override
    protected void onDestroy() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(247, 249, 253));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(9), dp(12), dp(9));
        Button back = soft("‹");
        TextView title = text("화면 편집", 20, true, dark());
        Button reset = soft("초기화");
        Button save = primary("저장");
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        top.addView(reset, new LinearLayout.LayoutParams(dp(82), dp(42)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(72), dp(42)); saveLp.setMargins(dp(7), 0, 0, 0);
        top.addView(save, saveLp);
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), 0, dp(14), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = text("미리보기에서 이미지와 투두를 직접 끌어서 배치해요", 12.5f, false, Color.rgb(105, 117, 137));
        LinearLayout.LayoutParams hintLp = wrap(); hintLp.setMargins(dp(2), dp(4), 0, dp(9));
        content.addView(hint, hintLp);

        canvas = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(212, 229, 250), Color.rgb(243, 235, 247), Color.rgb(224, 239, 248)});
        bg.setCornerRadius(dp(28));
        canvas.setBackground(bg);
        canvas.setClipChildren(false);
        content.addView(canvas, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(455)));

        todoBox = new FrameLayout(this);
        todoCard = new TodoCardView(this);
        todoCard.setEditorMode(true);
        todoBox.addView(todoCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        canvas.addView(todoBox);

        imageBox = new FrameLayout(this);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(rounded(Color.argb(110, 255, 255, 255), dp(20), Color.TRANSPARENT, 0));
        if (bitmap != null) imageView.setImageBitmap(bitmap);
        else imageView.setImageDrawable(null);
        imageBox.addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        canvas.addView(imageBox);
        installDrag(todoBox, TODO);
        installDrag(imageBox, IMAGE);

        LinearLayout tabs = card();
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        todoTab = soft("투두 카드");
        imageTab = soft("이미지");
        tabs.addView(todoTab, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams imageTabLp = new LinearLayout.LayoutParams(0, dp(46), 1); imageTabLp.setMargins(dp(8), 0, 0, 0);
        tabs.addView(imageTab, imageTabLp);
        content.addView(tabs, cardLp(dp(12)));

        LinearLayout sizeCard = card();
        LinearLayout sizeHeader = new LinearLayout(this);
        sizeHeader.setGravity(Gravity.CENTER_VERTICAL);
        sizeHeader.addView(text("크기", 15, true, dark()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sizeLabel = text("", 12.5f, true, Color.rgb(73, 113, 206));
        sizeHeader.addView(sizeLabel);
        sizeCard.addView(sizeHeader);
        sizeSeek = new SeekBar(this);
        sizeCard.addView(sizeSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        content.addView(sizeCard, cardLp(dp(10)));

        colorCard = card();
        colorCard.addView(text("투두 색상", 15, true, dark()));
        colorCard.addView(caption("색상환으로 원하는 색을 고를 수 있어요"));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setHue(Prefs.hue(this));
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(dp(176), dp(176));
        wheelLp.gravity = Gravity.CENTER_HORIZONTAL; wheelLp.setMargins(0, dp(8), 0, dp(4));
        colorCard.addView(wheel, wheelLp);
        TextView satLabel = caption("진하기  " + Prefs.saturation(this) + "%");
        colorCard.addView(satLabel);
        SeekBar sat = new SeekBar(this);
        sat.setMax(64); sat.setProgress(Prefs.saturation(this) - 12);
        colorCard.addView(sat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        content.addView(colorCard, cardLp(dp(10)));

        LinearLayout taskCard = card();
        taskCard.addView(text("오늘의 투두", 15, true, dark()));
        taskCard.addView(caption("날짜는 실제 표시되는 날 기준으로 자동으로 바뀌어요"));
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        taskCard.addView(rows, matchWrap(dp(8)));
        Button add = soft("+ 할 일 추가");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)); addLp.setMargins(0, dp(8), 0, 0);
        taskCard.addView(add, addLp);
        content.addView(taskCard, cardLp(dp(10)));

        Button preview = primary("실제 표시 미리보기");
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)); previewLp.setMargins(0, dp(14), 0, 0);
        content.addView(preview, previewLp);

        back.setOnClickListener(v -> finish());
        save.setOnClickListener(v -> { saveAll(); toast("저장했어요"); });
        reset.setOnClickListener(v -> {
            Prefs.resetLayout(this);
            wheel.setHue(Prefs.hue(this)); sat.setProgress(Prefs.saturation(this) - 12);
            todoCard.refresh(); restoreCanvas(); select(selected);
        });
        todoTab.setOnClickListener(v -> select(TODO));
        imageTab.setOnClickListener(v -> select(IMAGE));
        add.setOnClickListener(v -> { if (taskEditors.size() < 8) addTaskRow("", "업무"); });
        preview.setOnClickListener(v -> {
            saveAll();
            if (!Settings.canDrawOverlays(this)) { toast("다른 앱 위에 표시 권한이 필요해요"); return; }
            if (Prefs.showImage(this) && !ImageStore.has(this)) { toast("이미지를 먼저 선택해 주세요"); return; }
            if (!OverlayManager.show(this)) toast("미리보기를 표시하지 못했어요");
        });
        wheel.setListener(h -> { Prefs.setHue(this, h); todoCard.refresh(); });
        sat.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                int v = 12 + p; satLabel.setText("진하기  " + v + "%");
                if (fromUser) { Prefs.setSaturation(EditorActivity.this, v); todoCard.refresh(); }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                if (selected == TODO) {
                    int v = 40 + p; sizeLabel.setText("투두 너비  " + v + "%");
                    if (fromUser) Prefs.setTodoWidth(EditorActivity.this, v);
                } else {
                    int v = 30 + p; sizeLabel.setText("이미지  " + v + "%");
                    if (fromUser) Prefs.setImageSize(EditorActivity.this, v);
                }
                if (fromUser) resizeKeepingCenters();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) { savePositions(); }
        });
        return page;
    }

    private void select(int which) {
        selected = which;
        boolean todo = which == TODO;
        todoBox.setBackground(selection(todo));
        imageBox.setBackground(selection(!todo));
        todoTab.setAlpha(todo ? 1f : .58f);
        imageTab.setAlpha(todo ? .58f : 1f);
        colorCard.setVisibility(todo ? View.VISIBLE : View.GONE);
        if (todo) {
            sizeSeek.setMax(52); sizeSeek.setProgress(Prefs.todoWidth(this) - 40);
            sizeLabel.setText("투두 너비  " + Prefs.todoWidth(this) + "%");
        } else {
            sizeSeek.setMax(170); sizeSeek.setProgress(Prefs.imageSize(this) - 30);
            sizeLabel.setText("이미지  " + Prefs.imageSize(this) + "%");
        }
    }

    private void restoreCanvas() {
        if (canvas.getWidth() <= 0) return;
        applySizes();
        place(todoBox, Prefs.todoX(this), Prefs.todoY(this));
        place(imageBox, Prefs.imageX(this), Prefs.imageY(this));
        todoBox.setVisibility(Prefs.showTodo(this) ? View.VISIBLE : View.GONE);
        imageBox.setVisibility(Prefs.showImage(this) ? View.VISIBLE : View.GONE);
    }

    private void applySizes() {
        int cw = canvas.getWidth(), ch = canvas.getHeight();
        if (cw <= 0 || ch <= 0) return;
        int tw = Math.round(cw * Prefs.todoWidth(this) / 100f);
        todoCard.measure(View.MeasureSpec.makeMeasureSpec(tw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.AT_MOST));
        int th = todoCard.getMeasuredHeight();
        todoBox.setLayoutParams(new FrameLayout.LayoutParams(tw, th));
        todoCard.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        float base = Math.min(cw * .44f, ch * .38f) * Prefs.imageSize(this) / 100f;
        float ratio = bitmap == null ? 1f : bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
        int iw, ih;
        if (ratio >= 1) { iw = Math.round(base); ih = Math.round(base / ratio); }
        else { ih = Math.round(base); iw = Math.round(base * ratio); }
        imageBox.setLayoutParams(new FrameLayout.LayoutParams(Math.max(dp(64), iw), Math.max(dp(64), ih)));
    }

    private void resizeKeepingCenters() {
        if (canvas.getWidth() <= 0) return;
        float tx = centerX(todoBox) / canvas.getWidth(), ty = centerY(todoBox) / canvas.getHeight();
        float ix = centerX(imageBox) / canvas.getWidth(), iy = centerY(imageBox) / canvas.getHeight();
        applySizes(); place(todoBox, tx, ty); place(imageBox, ix, iy);
    }

    private void installDrag(View view, int kind) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startX, startY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    select(kind); downX = e.getRawX(); downY = e.getRawY(); startX = v.getX(); startY = v.getY(); v.bringToFront(); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float x = startX + e.getRawX() - downX, y = startY + e.getRawY() - downY;
                    v.setX(Math.max(0, Math.min(x, canvas.getWidth() - v.getWidth())));
                    v.setY(Math.max(0, Math.min(y, canvas.getHeight() - v.getHeight())));
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { savePositions(); v.performClick(); return true; }
                return true;
            }
        });
    }

    private void place(View v, float nx, float ny) {
        int w = v.getLayoutParams().width, h = v.getLayoutParams().height;
        float x = nx * canvas.getWidth() - w / 2f, y = ny * canvas.getHeight() - h / 2f;
        v.setX(Math.max(0, Math.min(x, canvas.getWidth() - w)));
        v.setY(Math.max(0, Math.min(y, canvas.getHeight() - h)));
    }
    private void savePositions() {
        if (canvas.getWidth() <= 0) return;
        Prefs.setTodoPosition(this, centerX(todoBox) / canvas.getWidth(), centerY(todoBox) / canvas.getHeight());
        Prefs.setImagePosition(this, centerX(imageBox) / canvas.getWidth(), centerY(imageBox) / canvas.getHeight());
    }
    private float centerX(View v) { return v.getX() + v.getWidth() / 2f; }
    private float centerY(View v) { return v.getY() + v.getHeight() / 2f; }

    private void rebuildRows() {
        taskEditors.clear(); rows.removeAllViews();
        List<String> items = Prefs.items(this), cats = Prefs.categories(this);
        for (int i = 0; i < items.size(); i++) addTaskRow(items.get(i), i < cats.size() ? cats.get(i) : "업무");
    }

    private void addTaskRow(String value, String category) {
        if (taskEditors.size() >= 8) return;
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setText(value); edit.setHint("할 일 입력"); edit.setTextSize(14); edit.setPadding(dp(11), 0, dp(8), 0);
        edit.setBackground(rounded(Color.rgb(247, 249, 252), dp(13), Color.rgb(227, 232, 241), 1));
        row.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"업무", "개인", "기타"});
        spinner.setAdapter(adapter); spinner.setSelection("개인".equals(category) ? 1 : "기타".equals(category) ? 2 : 0);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(82), dp(44)); spinLp.setMargins(dp(5), 0, 0, 0); row.addView(spinner, spinLp);
        Button remove = soft("×"); LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(43), dp(44)); removeLp.setMargins(dp(4), 0, 0, 0); row.addView(remove, removeLp);
        TaskEditor editor = new TaskEditor(row, edit, spinner); taskEditors.add(editor);
        rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        remove.setOnClickListener(v -> { taskEditors.remove(editor); rows.removeView(row); });
    }

    private void saveAll() {
        savePositions();
        ArrayList<String> items = new ArrayList<>(), cats = new ArrayList<>();
        for (TaskEditor e : taskEditors) {
            String s = e.edit.getText().toString().trim(); if (s.isEmpty()) continue;
            items.add(s); cats.add((String) e.spinner.getSelectedItem());
        }
        Prefs.setItems(this, items, cats); todoCard.refresh();
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(15), dp(14), dp(15), dp(14)); v.setBackground(rounded(Color.WHITE, dp(21), Color.rgb(232, 236, 244), 1)); return v; }
    private LinearLayout.LayoutParams cardLp(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, top, 0, 0); return p; }
    private LinearLayout.LayoutParams matchWrap(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, top, 0, 0); return p; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private Button primary(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(Color.WHITE); GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(55, 153, 255), Color.rgb(125, 112, 255)}); g.setCornerRadius(dp(15)); b.setBackground(g); return b; }
    private Button soft(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(dark()); b.setPadding(dp(5), 0, dp(5), 0); b.setBackground(rounded(Color.rgb(247, 249, 252), dp(14), Color.rgb(226, 232, 241), 1)); return b; }
    private TextView text(String s, float size, boolean bold, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private TextView caption(String s) { return text(s, 12.3f, false, Color.rgb(111, 122, 141)); }
    private GradientDrawable selection(boolean on) { return rounded(Color.argb(on ? 20 : 0, 65, 139, 255), dp(19), on ? Color.rgb(65, 139, 255) : Color.TRANSPARENT, on ? dp(2) : 0); }
    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (strokeWidth > 0) g.setStroke(strokeWidth, stroke); return g; }
    private int dark() { return Color.rgb(42, 54, 75); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static final class TaskEditor {
        final LinearLayout row; final EditText edit; final Spinner spinner;
        TaskEditor(LinearLayout row, EditText edit, Spinner spinner) { this.row = row; this.edit = edit; this.spinner = spinner; }
    }
}
