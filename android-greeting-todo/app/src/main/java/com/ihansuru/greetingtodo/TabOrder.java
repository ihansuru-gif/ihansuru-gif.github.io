package com.ihansuru.greetingtodo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TabOrder {
    private static final List<String> DEFAULT =
            Arrays.asList("todo", "calendar", "memo", "image");

    private TabOrder() {}

    static ArrayList<String> normalize(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw != null) {
            for (String key : raw.split(",")) {
                String clean = key == null ? "" : key.trim();
                if (DEFAULT.contains(clean) && !out.contains(clean)) out.add(clean);
            }
        }
        for (String key : DEFAULT) if (!out.contains(key)) out.add(key);
        return out;
    }

    static ArrayList<String> move(List<String> source, String key, int direction) {
        ArrayList<String> out = new ArrayList<>(source == null ? DEFAULT : source);
        int from = out.indexOf(key);
        if (from < 0) return out;
        int to = Math.max(0, Math.min(out.size() - 1, from + direction));
        if (from == to) return out;
        out.remove(from);
        out.add(to, key);
        return out;
    }

    static String serialize(List<String> order) {
        StringBuilder b = new StringBuilder();
        for (String key : order) {
            if (b.length() > 0) b.append(',');
            b.append(key);
        }
        return b.toString();
    }
}
