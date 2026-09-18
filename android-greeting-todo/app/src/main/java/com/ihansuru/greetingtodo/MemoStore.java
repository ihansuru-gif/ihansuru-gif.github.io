package com.ihansuru.greetingtodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MemoStore {
    private static final String PREF = "greeting_memo_store";
    private static final String KEY_LIST = "memo_list";
    private static final String KEY_ACTIVE = "memo_active";

    static final class CheckItem {
        String text = "";
        boolean checked;

        CheckItem() {}
        CheckItem(String text, boolean checked) {
            this.text = text == null ? "" : text;
            this.checked = checked;
        }
    }

    static final class Memo {
        long id;
        String title = "";
        String body = "";
        String bodyHtml = "";
        ArrayList<String> tags = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        ArrayList<CheckItem> checklist = new ArrayList<>();
        ArrayList<String> imagePaths = new ArrayList<>();
        ArrayList<String> voicePaths = new ArrayList<>();
        String doodle = "[]";
        float paperHue = 42f;
        int paperSat = 6;
        int paperValue = 100;
        int paperPattern = 0; // 0 plain, 1 line, 2 grid
        boolean pinned;
        boolean archived;

        Memo copy() {
            Memo m = new Memo();
            m.id = id;
            m.title = title;
            m.body = body;
            m.bodyHtml = bodyHtml;
            m.tags = new ArrayList<>(tags);
            m.links = new ArrayList<>(links);
            for (CheckItem item : checklist) m.checklist.add(new CheckItem(item.text, item.checked));
            m.imagePaths = new ArrayList<>(imagePaths);
            m.voicePaths = new ArrayList<>(voicePaths);
            m.doodle = doodle;
            m.paperHue = paperHue;
            m.paperSat = paperSat;
            m.paperValue = paperValue;
            m.paperPattern = paperPattern;
            m.pinned = pinned;
            m.archived = archived;
            return m;
        }
    }

    private MemoStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static ArrayList<Memo> load(Context c) {
        ArrayList<Memo> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_LIST, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    Memo m = new Memo();
                    m.id = o.optLong("id", System.currentTimeMillis() + i);
                    m.title = o.optString("title", "");
                    m.body = o.optString("body", "");
                    m.bodyHtml = o.optString("bodyHtml", "");
                    m.doodle = o.optString("doodle", "[]");
                    m.paperHue = (float) o.optDouble("paperHue", 42.0);
                    m.paperSat = clamp(o.optInt("paperSat", 6), 0, 45);
                    m.paperValue = clamp(o.optInt("paperValue", 100), 80, 100);
                    m.paperPattern = clamp(o.optInt("paperPattern", 0), 0, 2);
                    m.pinned = o.optBoolean("pinned", false);
                    m.archived = o.optBoolean("archived", false);
                    readStrings(o.optJSONArray("tags"), m.tags);
                    readStrings(o.optJSONArray("links"), m.links);
                    readStrings(o.optJSONArray("imagePaths"), m.imagePaths);
                    readStrings(o.optJSONArray("voicePaths"), m.voicePaths);

                    JSONArray checks = o.optJSONArray("checklist");
                    if (checks != null) {
                        for (int j = 0; j < checks.length(); j++) {
                            JSONObject item = checks.optJSONObject(j);
                            if (item == null) continue;
                            String text = item.optString("text", "");
                            boolean checked = item.optBoolean("checked", false);
                            if (!text.isEmpty()) m.checklist.add(new CheckItem(text, checked));
                        }
                    }
                    out.add(m);
                }
            } catch (JSONException ignored) {}
        }
        if (out.isEmpty()) {
            Memo m = new Memo();
            m.id = System.currentTimeMillis();
            m.title = "새 메모";
            out.add(m);
            save(c, out);
            setActiveIndex(c, 0);
        }
        return out;
    }

    static void save(Context c, List<Memo> memos) {
        JSONArray arr = new JSONArray();
        for (Memo m : memos) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", m.id);
                o.put("title", safe(m.title));
                o.put("body", safe(m.body));
                o.put("bodyHtml", safe(m.bodyHtml));
                o.put("doodle", m.doodle == null ? "[]" : m.doodle);
                o.put("paperHue", m.paperHue);
                o.put("paperSat", clamp(m.paperSat, 0, 45));
                o.put("paperValue", clamp(m.paperValue, 80, 100));
                o.put("paperPattern", clamp(m.paperPattern, 0, 2));
                o.put("pinned", m.pinned);
                o.put("archived", m.archived);
                o.put("tags", strings(m.tags));
                o.put("links", strings(m.links));
                o.put("imagePaths", strings(m.imagePaths));
                o.put("voicePaths", strings(m.voicePaths));

                JSONArray checks = new JSONArray();
                for (CheckItem item : m.checklist) {
                    String value = safe(item.text).trim();
                    if (value.isEmpty()) continue;
                    JSONObject ci = new JSONObject();
                    ci.put("text", value);
                    ci.put("checked", item.checked);
                    checks.put(ci);
                }
                o.put("checklist", checks);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    static int activeIndex(Context c, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, prefs(c).getInt(KEY_ACTIVE, 0)));
    }

    static void setActiveIndex(Context c, int index) {
        prefs(c).edit().putInt(KEY_ACTIVE, Math.max(0, index)).apply();
    }

    static int add(Context c) {
        ArrayList<Memo> list = load(c);
        Memo m = new Memo();
        m.id = System.currentTimeMillis();
        m.title = "새 메모";
        int insert = 0;
        while (insert < list.size() && list.get(insert).pinned && !list.get(insert).archived) insert++;
        list.add(insert, m);
        save(c, list);
        setActiveIndex(c, insert);
        return insert;
    }

    static ArrayList<Memo> delete(Context c, int index) {
        ArrayList<Memo> list = load(c);
        if (index >= 0 && index < list.size()) list.remove(index);
        if (list.isEmpty()) {
            Memo m = new Memo();
            m.id = System.currentTimeMillis();
            m.title = "새 메모";
            list.add(m);
        }
        save(c, list);
        setActiveIndex(c, Math.max(0, Math.min(index, list.size() - 1)));
        return list;
    }

    static ArrayList<Memo> move(Context c, int from, int to) {
        ArrayList<Memo> list = load(c);
        if (from < 0 || from >= list.size() || to < 0 || to >= list.size() || from == to) return list;
        Memo m = list.remove(from);
        list.add(to, m);
        save(c, list);
        setActiveIndex(c, to);
        return list;
    }

    static ArrayList<Memo> togglePin(Context c, int index) {
        ArrayList<Memo> list = load(c);
        if (index < 0 || index >= list.size()) return list;
        Memo target = list.get(index);
        target.pinned = !target.pinned;
        long activeId = target.id;
        Collections.sort(list, (a, b) -> {
            if (a.archived != b.archived) return a.archived ? 1 : -1;
            if (a.pinned == b.pinned) return 0;
            return a.pinned ? -1 : 1;
        });
        save(c, list);
        setActiveIndex(c, indexOfId(list, activeId));
        return list;
    }

    static ArrayList<Memo> toggleArchive(Context c, int index) {
        ArrayList<Memo> list = load(c);
        if (index < 0 || index >= list.size()) return list;
        long activeId = list.get(index).id;
        list.get(index).archived = !list.get(index).archived;
        save(c, list);
        setActiveIndex(c, indexOfId(list, activeId));
        return list;
    }

    static int indexOfId(List<Memo> list, long id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) return i;
        return 0;
    }

    private static void readStrings(JSONArray arr, ArrayList<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, "").trim();
            if (!value.isEmpty()) out.add(value);
        }
    }

    private static JSONArray strings(List<String> values) {
        JSONArray arr = new JSONArray();
        for (String value : values) {
            String clean = safe(value).trim();
            if (!clean.isEmpty()) arr.put(clean);
        }
        return arr;
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
