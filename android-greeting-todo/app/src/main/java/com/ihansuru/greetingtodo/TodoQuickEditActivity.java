package com.ihansuru.greetingtodo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class TodoQuickEditActivity extends Activity {
    private LinearLayout rows;
    private TodoCardView preview;
    private final ArrayList<RowEditor> editors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        rebuildRows();
    }

    @Override
    protected void onPause() {
        saveAll(false);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        saveAll(false);
        super.onBackPressed();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(247, 249, 253));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(9), dp(12), dp(9));
        Button back = soft("‹");
        TextView title = text("투두 수정", 20, true, dark());
        Button save = primary("저장");
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        top.addView(save, new LinearLayout.LayoutParams(dp(76), dp(42)));
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(4), dp(14), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout previewCard = card();
        previewCard.addView(text("미리보기", 15, true, dark()));
        previewCard.addView(caption("날짜는 실제 표시되는 날 기준으로 자동 변경돼요"));
        preview = new TodoCardView(this);
        preview.setEditorMode(true);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.setMargins(0, dp(10), 0, 0);
        previewCard.addView(preview, previewLp);
        content.addView(previewCard, cardLp(0));

        LinearLayout styleCard = card();
        styleCard.addView(text("색상", 16, true, dark()));
        styleCard.addView(caption("색상환에서 투두 상단 색을 바로 바꿀 수 있어요"));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setHue(Prefs.hue(this));
        LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(dp(176), dp(176));
        wheelLp.gravity = Gravity.CENTER_HORIZONTAL;
        wheelLp.setMargins(0, dp(8), 0, dp(4));
        styleCard.addView(wheel, wheelLp);
        TextView satLabel = caption("진하기  " + Prefs.saturation(this) + "%");
        styleCard.addView(satLabel);
        SeekBar saturation = new SeekBar(this);
        saturation.setMax(64);
        saturation.setProgress(Prefs.saturation(this) - 12);
        styleCard.addView(saturation, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        content.addView(styleCard, cardLp(dp(12)));

        LinearLayout editCard = card();
        editCard.addView(text("할 일", 16, true, dark()));
        editCard.addView(caption("칸을 눌러 직접 입력하고, 오른쪽에서 업무·개인·기타를 골라요"));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        editCard.addView(rows, matchWrap(dp(10)));

        Button add = soft("+ 할 일 추가");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        addLp.setMargins(0, dp(8), 0, 0);
        editCard.addView(add, addLp);
        content.addView(editCard, cardLp(dp(12)));

        TextView completeHelp = caption("배경화면에서 체크 원을 누르면 완료된 항목은 목록에서 바로 사라져요");
        LinearLayout.LayoutParams helpLp = matchWrap(dp(10));
        content.addView(completeHelp, helpLp);

        Button saveBig = primary("투두 저장");
        LinearLayout.LayoutParams saveBigLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        saveBigLp.setMargins(0, dp(14), 0, 0);
        content.addView(saveBig, saveBigLp);

        back.setOnClickListener(v -> {
            saveAll(false);
            finish();
        });
        save.setOnClickListener(v -> saveAll(true));
        saveBig.setOnClickListener(v -> saveAll(true));
        add.setOnClickListener(v -> {
            if (editors.size() >= 8) {
                toast("투두는 최대 8개까지 넣을 수 있어요");
                return;
            }
            addRow("", "업무", true);
        });
        wheel.setListener(h -> {
            Prefs.setHue(this, h);
            preview.refresh();
        });
        saturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 12 + progress;
                satLabel.setText("진하기  " + value + "%");
                if (fromUser) {
                    Prefs.setSaturation(TodoQuickEditActivity.this, value);
                    preview.refresh();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        return page;
    }

    private void rebuildRows() {
        editors.clear();
        rows.removeAllViews();
        List<String> items = Prefs.items(this);
        List<String> categories = Prefs.categories(this);
        for (int i = 0; i < items.size(); i++) {
            addRow(items.get(i), i < categories.size() ? categories.get(i) : "업무", false);
        }
    }

    private void addRow(String value, String category, boolean focus) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setHint("할 일 입력");
        edit.setTextSize(15);
        edit.setTextColor(dark());
        edit.setHintTextColor(Color.rgb(157, 166, 181));
        edit.setPadding(dp(12), 0, dp(10), 0);
        edit.setBackground(rounded(Color.rgb(247, 249, 252), dp(14), Color.rgb(222, 229, 240), 1));
        row.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"업무", "개인", "기타"});
        spinner.setAdapter(adapter);
        spinner.setSelection("개인".equals(category) ? 1 : "기타".equals(category) ? 2 : 0);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(86), dp(48));
        spinLp.setMargins(dp(6), 0, 0, 0);
        row.addView(spinner, spinLp);

        Button remove = soft("×");
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(44), dp(48));
        removeLp.setMargins(dp(5), 0, 0, 0);
        row.addView(remove, removeLp);

        RowEditor item = new RowEditor(row, edit, spinner);
        editors.add(item);
        rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        remove.setOnClickListener(v -> {
            editors.remove(item);
            rows.removeView(row);
            saveAll(false);
        });

        if (focus) {
            edit.requestFocus();
            edit.postDelayed(() -> {
                edit.setSelection(edit.getText().length());
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        }
    }

    private void saveAll(boolean notify) {
        ArrayList<String> items = new ArrayList<>();
        ArrayList<String> categories = new ArrayList<>();
        for (RowEditor editor : editors) {
            String value = editor.edit.getText().toString().trim();
            if (value.isEmpty()) continue;
            items.add(value);
            Object selected = editor.category.getSelectedItem();
            categories.add(selected == null ? "업무" : selected.toString());
        }
        Prefs.setItems(this, items, categories);
        preview.refresh();
        if (notify) toast("투두를 저장했어요");
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(15), dp(16), dp(15));
        v.setBackground(rounded(Color.WHITE, dp(22), Color.rgb(232, 236, 244), 1));
        return v;
    }

    private LinearLayout.LayoutParams cardLp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, 0);
        return p;
    }

    private Button primary(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(55, 153, 255), Color.rgb(125, 112, 255)});
        g.setCornerRadius(dp(15));
        b.setBackground(g);
        return b;
    }

    private Button soft(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(dark());
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackground(rounded(Color.rgb(247, 249, 252), dp(14), Color.rgb(226, 232, 241), 1));
        return b;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView caption(String value) { return text(value, 12.3f, false, Color.rgb(111, 122, 141)); }

    private GradientDrawable rounded(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    private int dark() { return Color.rgb(42, 54, 75); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private static final class RowEditor {
        final LinearLayout row;
        final EditText edit;
        final Spinner category;
        RowEditor(LinearLayout row, EditText edit, Spinner category) {
            this.row = row;
            this.edit = edit;
            this.category = category;
        }
    }
}
