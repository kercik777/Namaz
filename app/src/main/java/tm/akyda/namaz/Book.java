package tm.akyda.namaz;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Книга: метаданные, содержание и оглавление. */
public class Book {

    public String id = "";
    public Map<String, String> title = new HashMap<>();
    public Map<String, String> subtitle = new HashMap<>();
    public Map<String, String> author = new HashMap<>();
    public Map<String, String> edition = new HashMap<>();
    public String script = "latin";     // latin | cyrillic | arabic
    public List<Block> blocks = new ArrayList<>();
    public List<Toc> toc = new ArrayList<>();
    public int accent = 0;              // индивидуальный акцент обложки

    /** Пункт оглавления. */
    public static class Toc {
        public int level = 1;           // 0 — раздел, 1 — глава, 2 — подглава
        public String text = "";
        public int blockIndex = 0;
    }

    public String t(String key) {
        String v = title.get(key);
        if (v == null || v.isEmpty()) v = title.get("ru");
        return v == null ? id : v;
    }

    public String sub(String key) {
        String v = subtitle.get(key);
        if (v == null || v.isEmpty()) v = subtitle.get("ru");
        return v == null ? "" : v;
    }

    public String auth(String key) {
        String v = author.get(key);
        if (v == null || v.isEmpty()) v = author.get("ru");
        return v == null ? "" : v;
    }

    public String edit(String key) {
        String v = edition.get(key);
        if (v == null || v.isEmpty()) v = edition.get("ru");
        return v == null ? "" : v;
    }

    public int chapterOf(int blockIndex) {
        int ch = 0;
        for (int i = 0; i < toc.size(); i++) {
            Toc t = toc.get(i);
            if (t.level <= 1 && t.blockIndex <= blockIndex) ch = i;
        }
        return ch;
    }

    public Toc tocAt(int index) {
        if (index < 0 || index >= toc.size()) return null;
        return toc.get(index);
    }

    /** Границы главы: [начало, конец) в индексах блоков. */
    public int[] chapterRange(int chapterIndex) {
        Toc t = tocAt(chapterIndex);
        int start = t == null ? 0 : t.blockIndex;
        int end = blocks.size();
        for (int i = chapterIndex + 1; i < toc.size(); i++) {
            if (toc.get(i).level <= 1) {
                end = toc.get(i).blockIndex;
                break;
            }
        }
        return new int[]{start, end};
    }

    public static Book from(JSONObject o) {
        Book b = new Book();
        b.id = o.optString("id", "");
        b.script = o.optString("script", "latin");
        b.accent = o.optInt("accent", 0);
        b.title = readMap(o.optJSONObject("title"));
        b.subtitle = readMap(o.optJSONObject("subtitle"));
        b.author = readMap(o.optJSONObject("author"));
        b.edition = readMap(o.optJSONObject("edition"));
        JSONArray arr = o.optJSONArray("blocks");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject bo = arr.optJSONObject(i);
                if (bo != null) b.blocks.add(Block.from(bo));
            }
        }
        JSONArray tc = o.optJSONArray("toc");
        if (tc != null) {
            for (int i = 0; i < tc.length(); i++) {
                JSONObject to = tc.optJSONObject(i);
                if (to == null) continue;
                Toc t = new Toc();
                t.level = to.optInt("level", 1);
                t.text = to.optString("x", "");
                t.blockIndex = to.optInt("b", 0);
                b.toc.add(t);
            }
        }
        if (b.toc.isEmpty()) b.buildToc();
        return b;
    }

    private static Map<String, String> readMap(JSONObject o) {
        Map<String, String> m = new HashMap<>();
        if (o == null) return m;
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            m.put(k, o.optString(k, ""));
        }
        return m;
    }

    /** Автоматическое оглавление, если его нет в файле. */
    public void buildToc() {
        toc.clear();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (Block.PART.equals(b.type)) {
                addToc(0, b.text, i);
            } else if (Block.H1.equals(b.type)) {
                addToc(1, b.text, i);
            } else if (Block.H2.equals(b.type)) {
                addToc(2, b.text, i);
            } else if (Block.TITLE.equals(b.type)) {
                addToc(0, b.text, i);
            }
        }
    }

    private void addToc(int level, String text, int index) {
        Toc t = new Toc();
        t.level = level;
        t.text = text;
        t.blockIndex = index;
        toc.add(t);
    }
}
