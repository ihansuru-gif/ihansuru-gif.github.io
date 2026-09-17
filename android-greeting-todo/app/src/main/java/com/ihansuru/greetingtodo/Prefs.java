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
    private static final String KEY_TODO_INITIALIZED = "todo_initialized";

    private Prefs() {}

    private static SharedPreferences normal(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

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

    static long duration(Context c) {
        return Math.max(500L, Math.min(10000L, normal(c).getLong("duration", 4000L)));
    }

    static void setDuration(Context c, long v) {
        normal(c).edit().putLong("duration", Math.max(500L, Math.min(10000L, v))).apply();
    }

    static boolean tapDismiss(Context c) { return false; }
    static void setTapDismiss(Context c, boolean ignored) {
        normal(c).edit().putBoolean("tap_dismiss", false).apply();
    }

    static String mode(Context c) {
        String v = normal(c).getString("mode", MODE_IMAGE);
        return MODE_TODO.equals(v) || MODE_BOTH.equals(v) ? v : MODE_IMAGE;
    }

    static void setMode(Context c, String v) {
        if (!MODE_IMAGE.equals(v) && !MODE_TODO.equals(v) && !MODE_BOTH.equals(v)) v = MODE_IMAGE;
        normal(c).edit().putString("mode", v).apply();
    }

    static boolean showImage(Context c) {
        String mode = mode(c);
        return MODE_IMAGE.equals(mode) || MODE_BOTH.equals(mode);
    }

    static boolean showTodo(Context c) {
        String mode = mode(c);
        return MODE_TODO.equals(mode) || MODE_BOTH.equals(mode);
    }

    static int imageSize(Context c) { return clamp(normal(c).getInt("image_size", 100), 30, 200); }
    static void setImageSize(Context c, int v) { normal(c).edit().putInt("image_size", clamp(v, 30, 200)).apply(); }
    static float imageX(Context c) { return unit(normal(c).getFloat("image_x", .68f)); }
    static float imageY(Context c) { return unit(normal(c).getFloat("image_y", .70f)); }
    static void setImagePosition(Context c, float x, float y) {
        normal(c).edit().putFloat("image_x", unit(x)).putFloat("image_y", unit(y)).apply();
    }

    static int todoWidth(Context c) { return clamp(normal(c).getInt("todo_width", 82), 46, 96); }
    static void setTodoWidth(Context c, int v) { normal(c).edit().putInt("todo_width", clamp(v, 46, 96)).apply(); }
    static float todoScale(Context c) { return clampFloat(normal(c).getFloat("todo_scale", 1f), .72f, 1.55f); }
    static void setTodoScale(Context c, float v) { normal(c).edit().putFloat("todo_scale", clampFloat(v, .72f, 1.55f)).apply(); }
    static float todoX(Context c) { return unit(normal(c).getFloat("todo_x", .50f)); }
    static float todoY(Context c) { return unit(normal(c).getFloat("todo_y", .34f)); }
    static void setTodoPosition(Context c, float x, float y) {
        normal(c).edit().putFloat("todo_x", unit(x)).putFloat("todo_y", unit(y)).apply();
    }

    static float textScale(Context c) { return clampFloat(normal(c).getFloat("text_scale", 1f), .82f, 1.65f); }
    static void setTextScale(Context c, float v) { normal(c).edit().putFloat("text_scale", clampFloat(v, .82f, 1.65f)).apply(); }

    static float hue(Context c) {
        float h = normal(c).getFloat("todo_hue", 232f) % 360f;
        return h < 0 ? h + 360f : h;
    }

    static void setHue(Context c, float h) {
        normal(c).edit().putFloat("todo_hue", ((h % 360f) + 360f) % 360f).apply();
    }

    static int saturation(Context c) { return clamp(normal(c).getInt("todo_sat", 34), 12, 76); }
    static void setSaturation(Context c, int v) { normal(c).edit().putInt("todo_sat", clamp(v, 12, 76)).apply(); }
    static int todoColor(Context c) {
        return Color.HSVToColor(new float[]{hue(c), saturation(c) / 100f, .98f});
    }

    static String weatherSummary(Context c) {
        String value = normal(c).getString("weather_summary", "");
        return value == null ? "" : value.trim();
    }

    static void setWeatherSummary(Context c, String value) {
        normal(c).edit().putString("weather_summary", value == null ? "" : value.trim()).apply();
    }

    static List<String> items(Context c) {
        SharedPreferences p = normal(c);
        String raw = p.getString("todo_items", null);
        boolean initialized = p.getBoolean(KEY_TODO_INITIALIZED, false);
        if (!initialized && (raw == null || raw.isEmpty())) {
            return new ArrayList<>(Arrays.asList("자료 조사하기", "메일 답장하기", "운동하기", "저녁 약속 준비"));
        }
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return split(raw);
    }

    static List<String> categories(Context c) {
        SharedPreferences p = normal(c);
        String raw = p.getString("todo_categories", null);
        boolean initialized = p.getBoolean(KEY_TODO_INITIALIZED, false);
        List<String> out;
        if (!initialized && (raw == null || raw.isEmpty())) {
            out = new ArrayList<>(Arrays.asList("업무", "업무", "개인", "개인"));
        } else {
            out = raw == null || raw.isEmpty() ? new ArrayList<>() : split(raw);
        }
        int size = items(c).size();
        while (out.size() < size) out.add("업무");
        while (out.size() > size) out.remove(out.size() - 1);
        return out;
    }

    static void setItems(Context c, List<String> items, List<String> categories) {
        ArrayList<String> cleanItems = new ArrayList<>();
        ArrayList<String> cleanCategories = new ArrayList<>();
        for (int i = 0; i < items.size() && cleanItems.size() < 12; i++) {
            String text = items.get(i) == null ? "" : items.get(i).trim().replace(SEP, " ");
            if (text.isEmpty()) continue;
            cleanItems.add(text);
            String category = i < categories.size() ? categories.get(i) : "업무";
            cleanCategories.add(normalizeCategory(category));
        }
        normal(c).edit()
                .putBoolean(KEY_TODO_INITIALIZED, true)
                .putString("todo_items", join(cleanItems))
                .putString("todo_categories", join(cleanCategories))
                .putInt("checked", 0)
                .apply();
    }

    static void addItem(Context c, String text, String category) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (currentItems.size() >= 12) return;
        currentItems.add(value);
        currentCategories.add(normalizeCategory(category));
        setItems(c, currentItems, currentCategories);
    }

    static void updateItem(Context c, int index, String text, String category) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (index < 0 || index >= currentItems.size()) return;
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            completeItem(c, index);
            return;
        }
        currentItems.set(index, value);
        while (currentCategories.size() < currentItems.size()) currentCategories.add("업무");
        currentCategories.set(index, normalizeCategory(category));
        setItems(c, currentItems, currentCategories);
    }

    static void completeItem(Context c, int index) {
        ArrayList<String> currentItems = new ArrayList<>(items(c));
        ArrayList<String> currentCategories = new ArrayList<>(categories(c));
        if (index < 0 || index >= currentItems.size()) return;
        currentItems.remove(index);
        if (index < currentCategories.size()) currentCategories.remove(index);
        setItems(c, currentItems, currentCategories);
    }

    static int checkedMask(Context c) { return 0; }
    static boolean checked(Context c, int i) { return false; }
    static void toggleChecked(Context c, int i) { completeItem(c, i); }

    static void resetLayout(Context c) {
        normal(c).edit()
                .putInt("image_size", 100)
                .putFloat("image_x", .68f)
                .putFloat("image_y", .70f)
                .putInt("todo_width", 82)
                .putFloat("todo_scale", 1f)
                .putFloat("todo_x", .50f)
                .putFloat("todo_y", .34f)
                .putFloat("text_scale", 1f)
                .putFloat("todo_hue", 232f)
                .putInt("todo_sat", 34)
                .putBoolean("tap_dismiss", false)
                .apply();
    }

    private static String normalizeCategory(String category) {
        if ("개인".equals(category) || "기타".equals(category)) return category;
        return "업무";
    }

    private static List<String> split(String raw) {
        ArrayList<String> out = new ArrayList<>();
        for (String s : raw.split(SEP, -1)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (String s : values) {
            if (b.length() > 0) b.append(SEP);
            b.append(s == null ? "" : s.replace(SEP, " "));
        }
        return b.toString();
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clampFloat(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float unit(float v) { return Math.max(.04f, Math.min(.96f, v)); }
}
