package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Prefs {
    static final String MODE_IMAGE = "image";
    static final String MODE_TODO = "todo";
    static final String MODE_BOTH = "both";
    private static final String NAME = "greeting_todo_settings";
    private static final String DIRECT_NAME = "greeting_todo_direct";
    private static final String SEP = "__GT_SEP__";

    private Prefs() {}

    private static SharedPreferences normal(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    private static SharedPreferences direct(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return c.createDeviceProtectedStorageContext().getSharedPreferences(DIRECT_NAME, Context.MODE_PRIVATE);
        }
        return normal(c);
    }

    static boolean enabled(Context c) { return direct(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) {
        direct(c).edit().putBoolean("enabled", v).apply();
        normal(c).edit().putBoolean("enabled", v).apply();
    }
    static long duration(Context c) { return Math.max(500, Math.min(10000, normal(c).getLong("duration", 4000))); }
    static void setDuration(Context c, long v) { normal(c).edit().putLong("duration", Math.max(500, Math.min(10000, v))).apply(); }
    static boolean tapDismiss(Context c) { return normal(c).getBoolean("tap_dismiss", true); }
    static void setTapDismiss(Context c, boolean v) { normal(c).edit().putBoolean("tap_dismiss", v).apply(); }

    static String mode(Context c) {
        String v = normal(c).getString("mode", MODE_IMAGE);
        return MODE_TODO.equals(v) || MODE_BOTH.equals(v) ? v : MODE_IMAGE;
    }
    static void setMode(Context c, String v) {
        if (!MODE_IMAGE.equals(v) && !MODE_TODO.equals(v) && !MODE_BOTH.equals(v)) v = MODE_IMAGE;
        normal(c).edit().putString("mode", v).apply();
    }
    static boolean showImage(Context c) { return MODE_IMAGE.equals(mode(c)) || MODE_BOTH.equals(mode(c)); }
    static boolean showTodo(Context c) { return MODE_TODO.equals(mode(c)) || MODE_BOTH.equals(mode(c)); }

    static int imageSize(Context c) { return clamp(normal(c).getInt("image_size", 100), 30, 200); }
    static void setImageSize(Context c, int v) { normal(c).edit().putInt("image_size", clamp(v, 30, 200)).apply(); }
    static float imageX(Context c) { return unit(normal(c).getFloat("image_x", .68f)); }
    static float imageY(Context c) { return unit(normal(c).getFloat("image_y", .70f)); }
    static void setImagePosition(Context c, float x, float y) { normal(c).edit().putFloat("image_x", unit(x)).putFloat("image_y", unit(y)).apply(); }

    static int todoWidth(Context c) { return clamp(normal(c).getInt("todo_width", 70), 40, 92); }
    static void setTodoWidth(Context c, int v) { normal(c).edit().putInt("todo_width", clamp(v, 40, 92)).apply(); }
    static float todoX(Context c) { return unit(normal(c).getFloat("todo_x", .35f)); }
    static float todoY(Context c) { return unit(normal(c).getFloat("todo_y", .31f)); }
    static void setTodoPosition(Context c, float x, float y) { normal(c).edit().putFloat("todo_x", unit(x)).putFloat("todo_y", unit(y)).apply(); }

    static float hue(Context c) {
        float h = normal(c).getFloat("todo_hue", 232f) % 360f;
        return h < 0 ? h + 360f : h;
    }
    static void setHue(Context c, float h) { normal(c).edit().putFloat("todo_hue", ((h % 360f) + 360f) % 360f).apply(); }
    static int saturation(Context c) { return clamp(normal(c).getInt("todo_sat", 34), 12, 76); }
    static void setSaturation(Context c, int v) { normal(c).edit().putInt("todo_sat", clamp(v, 12, 76)).apply(); }
    static int todoColor(Context c) { return Color.HSVToColor(new float[]{hue(c), saturation(c) / 100f, .98f}); }

    static List<String> items(Context c) {
        String raw = normal(c).getString("todo_items", null);
        if (raw == null || raw.isEmpty()) return new ArrayList<>(Arrays.asList("자료 조사하기", "메일 답장하기", "운동하기", "저녁 약속 준비"));
        return split(raw);
    }
    static List<String> categories(Context c) {
        String raw = normal(c).getString("todo_categories", null);
        List<String> out = raw == null ? new ArrayList<>(Arrays.asList("업무", "업무", "개인", "개인")) : split(raw);
        int size = items(c).size();
        while (out.size() < size) out.add("업무");
        while (out.size() > size) out.remove(out.size() - 1);
        return out;
    }
    static void setItems(Context c, List<String> items, List<String> categories) {
        ArrayList<String> a = new ArrayList<>();
        ArrayList<String> b = new ArrayList<>();
        for (int i = 0; i < items.size() && a.size() < 8; i++) {
            String s = items.get(i) == null ? "" : items.get(i).trim().replace(SEP, " ");
            if (s.isEmpty()) continue;
            a.add(s);
            String cat = i < categories.size() ? categories.get(i) : "업무";
            if (!"개인".equals(cat) && !"기타".equals(cat)) cat = "업무";
            b.add(cat);
        }
        if (a.isEmpty()) { a.add("오늘 할 일"); b.add("업무"); }
        int mask = checkedMask(c);
        int valid = a.size() >= 31 ? -1 : (1 << a.size()) - 1;
        normal(c).edit().putString("todo_items", join(a)).putString("todo_categories", join(b)).putInt("checked", mask & valid).apply();
    }

    static int checkedMask(Context c) { return normal(c).getInt("checked", 3); }
    static boolean checked(Context c, int i) { return i >= 0 && i < 31 && (checkedMask(c) & (1 << i)) != 0; }
    static void toggleChecked(Context c, int i) {
        if (i < 0 || i >= 31) return;
        normal(c).edit().putInt("checked", checkedMask(c) ^ (1 << i)).apply();
    }

    static void resetLayout(Context c) {
        normal(c).edit()
                .putInt("image_size", 100).putFloat("image_x", .68f).putFloat("image_y", .70f)
                .putInt("todo_width", 70).putFloat("todo_x", .35f).putFloat("todo_y", .31f)
                .putFloat("todo_hue", 232f).putInt("todo_sat", 34).apply();
    }

    private static List<String> split(String raw) {
        ArrayList<String> out = new ArrayList<>();
        for (String s : raw.split(SEP, -1)) if (!s.isEmpty()) out.add(s);
        return out;
    }
    private static String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (String s : values) { if (b.length() > 0) b.append(SEP); b.append(s); }
        return b.toString();
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float unit(float v) { return Math.max(.05f, Math.min(.95f, v)); }
}
