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

    static final class Memo {
        long id;
        String title = "";
        String body = "";
        ArrayList<String> links = new ArrayList<>();
        String doodle = "[]";
        boolean pinned;

        Memo copy() {
            Memo m = new Memo();
            m.id = id;
            m.title = title;
            m.body = body;
            m.links = new ArrayList<>(links);
            m.doodle = doodle;
            m.pinned = pinned;
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
                    m.doodle = o.optString("doodle", "[]");
                    m.pinned = o.optBoolean("pinned", false);
                    JSONArray links = o.optJSONArray("links");
                    if (links != null) {
                        for (int j = 0; j < links.length(); j++) {
                            String value = links.optString(j, "").trim();
                            if (!value.isEmpty()) m.links.add(value);
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
                o.put("doodle", m.doodle == null ? "[]" : m.doodle);
                o.put("pinned", m.pinned);
                JSONArray links = new JSONArray();
                for (String link : m.links) links.put(safe(link));
                o.put("links", links);
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
        while (insert < list.size() && list.get(insert).pinned) insert++;
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
        setActiveIndex(c, Math.min(index, list.size() - 1));
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
        Collections.sort(list, (a, b) -> Boolean.compare(b.pinned, a.pinned));
        save(c, list);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == activeId) {
                setActiveIndex(c, i);
                break;
            }
        }
        return list;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
