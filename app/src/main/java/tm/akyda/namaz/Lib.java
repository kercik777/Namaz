package tm.akyda.namaz;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Моя библиотека»: прогресс чтения, закладки, цитаты, статистика.
 * Всё хранится локально, без интернета и без разрешений.
 */
public class Lib {

    private static final Lib I = new Lib();

    public static Lib get() {
        return I;
    }

    private static final String K_PROG = "lib_progress";
    private static final String K_MARKS = "lib_marks";
    private static final String K_QUOTES = "lib_quotes";
    private static final String K_STATS = "lib_stats";
    private static final String K_HIST = "lib_search_hist";

    private final Map<String, Prog> progCache = new HashMap<>();
    private List<Mark> marksCache;
    private List<Quote> quotesCache;

    /* ================= ПРОГРЕСС ================= */

    public static class Prog {
        public String bookId = "";
        public int chapter = 0;
        public int block = 0;
        public int page = 0;
        public int percent = 0;
        public long time = 0;
    }

    public Prog prog(String bookId) {
        Prog p = progCache.get(bookId);
        if (p != null) return p;
        p = new Prog();
        p.bookId = bookId;
        try {
            JSONObject all = new JSONObject(P.s(K_PROG, "{}"));
            JSONObject o = all.optJSONObject(bookId);
            if (o != null) {
                p.chapter = o.optInt("c", 0);
                p.block = o.optInt("b", 0);
                p.page = o.optInt("p", 0);
                p.percent = o.optInt("pr", 0);
                p.time = o.optLong("t", 0);
            }
        } catch (Exception ignored) {
        }
        progCache.put(bookId, p);
        return p;
    }

    public void saveProg(String bookId, int chapter, int block, int page, int percent) {
        Prog p = prog(bookId);
        p.chapter = chapter;
        p.block = block;
        p.page = page;
        p.percent = percent;
        p.time = System.currentTimeMillis();
        try {
            JSONObject all = new JSONObject(P.s(K_PROG, "{}"));
            JSONObject o = new JSONObject();
            o.put("c", p.chapter);
            o.put("b", p.block);
            o.put("p", p.page);
            o.put("pr", p.percent);
            o.put("t", p.time);
            all.put(bookId, o);
            P.ss(K_PROG, all.toString());
        } catch (Exception ignored) {
        }
    }

    /** Книга, которую пользователь читал последней. */
    public String lastBookId() {
        String best = null;
        long bestTime = -1;
        for (Prog p : allProgs()) {
            if (p.time > bestTime) {
                bestTime = p.time;
                best = p.bookId;
            }
        }
        return best;
    }

    private List<Prog> allProgs() {
        List<Prog> out = new ArrayList<>();
        try {
            JSONObject all = new JSONObject(P.s(K_PROG, "{}"));
            java.util.Iterator<String> it = all.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.add(prog(k));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /* ================= ЗАКЛАДКИ ================= */

    public static class Mark {
        public String id = "";
        public String bookId = "";
        public String bookTitle = "";
        public String chapterTitle = "";
        public String snippet = "";
        public int chapter = 0;
        public int block = 0;
        public int page = 0;
        public long time = 0;
    }

    public List<Mark> marks() {
        if (marksCache != null) return marksCache;
        List<Mark> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(P.s(K_MARKS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Mark m = new Mark();
                m.id = o.optString("id", "");
                m.bookId = o.optString("bk", "");
                m.bookTitle = o.optString("bt", "");
                m.chapterTitle = o.optString("ct", "");
                m.snippet = o.optString("sn", "");
                m.chapter = o.optInt("ch", 0);
                m.block = o.optInt("bl", 0);
                m.page = o.optInt("pg", 0);
                m.time = o.optLong("t", 0);
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        marksCache = out;
        return out;
    }

    private void saveMarks(List<Mark> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Mark m : list) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("bk", m.bookId);
                o.put("bt", m.bookTitle);
                o.put("ct", m.chapterTitle);
                o.put("sn", m.snippet);
                o.put("ch", m.chapter);
                o.put("bl", m.block);
                o.put("pg", m.page);
                o.put("t", m.time);
                arr.put(o);
            }
            P.ss(K_MARKS, arr.toString());
            marksCache = list;
        } catch (Exception ignored) {
        }
    }

    public Mark markAt(String bookId, int chapter, int page) {
        for (Mark m : marks()) {
            if (m.bookId.equals(bookId) && m.chapter == chapter && m.page == page) return m;
        }
        return null;
    }

    public boolean toggleMark(Book b, int chapter, int page, String chapterTitle, String snippet) {
        List<Mark> list = marks();
        Mark found = null;
        for (Mark m : list) {
            if (m.bookId.equals(b.id) && m.chapter == chapter && m.page == page) {
                found = m;
                break;
            }
        }
        if (found != null) {
            list.remove(found);
            saveMarks(list);
            return false;
        }
        Mark m = new Mark();
        m.id = b.id + "-" + chapter + "-" + page + "-" + System.currentTimeMillis();
        m.bookId = b.id;
        m.bookTitle = b.t(Loc.lang());
        m.chapterTitle = chapterTitle == null ? "" : chapterTitle;
        m.snippet = U.trimTo(snippet, 160);
        m.chapter = chapter;
        m.page = page;
        m.time = System.currentTimeMillis();
        list.add(0, m);
        saveMarks(list);
        return true;
    }

    public void removeMark(String id) {
        List<Mark> list = marks();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveMarks(list);
    }

    /* ================= ЦИТАТЫ ================= */

    public static class Quote {
        public String id = "";
        public String bookId = "";
        public String bookTitle = "";
        public String chapterTitle = "";
        public String text = "";
        public int chapter = 0;
        public int block = 0;
        public long time = 0;
    }

    public List<Quote> quotes() {
        if (quotesCache != null) return quotesCache;
        List<Quote> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(P.s(K_QUOTES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Quote q = new Quote();
                q.id = o.optString("id", "");
                q.bookId = o.optString("bk", "");
                q.bookTitle = o.optString("bt", "");
                q.chapterTitle = o.optString("ct", "");
                q.text = o.optString("tx", "");
                q.chapter = o.optInt("ch", 0);
                q.block = o.optInt("bl", 0);
                q.time = o.optLong("t", 0);
                out.add(q);
            }
        } catch (Exception ignored) {
        }
        quotesCache = out;
        return out;
    }

    private void saveQuotes(List<Quote> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Quote q : list) {
                JSONObject o = new JSONObject();
                o.put("id", q.id);
                o.put("bk", q.bookId);
                o.put("bt", q.bookTitle);
                o.put("ct", q.chapterTitle);
                o.put("tx", q.text);
                o.put("ch", q.chapter);
                o.put("bl", q.block);
                o.put("t", q.time);
                arr.put(o);
            }
            P.ss(K_QUOTES, arr.toString());
            quotesCache = list;
        } catch (Exception ignored) {
        }
    }

    public boolean hasQuote(String bookId, int block) {
        for (Quote q : quotes()) if (q.bookId.equals(bookId) && q.block == block) return true;
        return false;
    }

    public void addQuote(Book b, int chapter, int block, String text) {
        List<Quote> list = quotes();
        Quote q = new Quote();
        q.id = b.id + "-" + block + "-" + System.currentTimeMillis();
        q.bookId = b.id;
        q.bookTitle = b.t(Loc.lang());
        Book.Toc t = b.tocAt(chapter);
        q.chapterTitle = t == null ? "" : t.text;
        q.text = text;
        q.chapter = chapter;
        q.block = block;
        q.time = System.currentTimeMillis();
        list.add(0, q);
        saveQuotes(list);
    }

    public void removeQuote(String id) {
        List<Quote> list = quotes();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveQuotes(list);
    }

    /* ================= СТАТИСТИКА ================= */

    public static class Stats {
        public int pages = 0;
        public long minutes = 0;
        public int streak = 0;
        public int days = 0;
        public long lastRead = 0;
    }

    private JSONObject statsObj() {
        try {
            return new JSONObject(P.s(K_STATS, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void addPage() {
        try {
            JSONObject o = statsObj();
            o.put("pages", o.optInt("pages", 0) + 1);
            touchDay(o);
            P.ss(K_STATS, o.toString());
        } catch (Exception ignored) {
        }
    }

    public void addReadingTime(long ms) {
        if (ms <= 0) return;
        try {
            JSONObject o = statsObj();
            o.put("ms", o.optLong("ms", 0) + ms);
            touchDay(o);
            P.ss(K_STATS, o.toString());
        } catch (Exception ignored) {
        }
    }

    private void touchDay(JSONObject o) {
        try {
            String today = dayKey(0);
            JSONArray days = o.optJSONArray("d");
            if (days == null) days = new JSONArray();
            boolean has = false;
            for (int i = 0; i < days.length(); i++) {
                if (today.equals(days.optString(i))) has = true;
            }
            if (!has) days.put(today);
            o.put("d", days);
            o.put("last", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    private static String dayKey(int shift) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, shift);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(c.getTime());
    }

    public Stats stats() {
        Stats s = new Stats();
        try {
            JSONObject o = statsObj();
            s.pages = o.optInt("pages", 0);
            s.minutes = o.optLong("ms", 0) / 60000L;
            s.lastRead = o.optLong("last", 0);
            JSONArray days = o.optJSONArray("d");
            s.days = days == null ? 0 : days.length();
            // серия дней подряд
            if (days != null) {
                int streak = 0;
                for (int i = 0; i < 400; i++) {
                    String key = dayKey(-i);
                    boolean has = false;
                    for (int j = 0; j < days.length(); j++) {
                        if (key.equals(days.optString(j))) has = true;
                    }
                    if (has) {
                        streak++;
                    } else if (i > 0) {
                        break;
                    }
                }
                s.streak = streak;
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    /* ================= ИСТОРИЯ ПОИСКА ================= */

    public List<String> searchHistory() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(P.s(K_HIST, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    public void addSearch(String q) {
        if (U.empty(q)) return;
        List<String> h = searchHistory();
        String n = q.trim();
        for (int i = 0; i < h.size(); i++) {
            if (h.get(i).equalsIgnoreCase(n)) h.remove(i--);
        }
        h.add(0, n);
        while (h.size() > 12) h.remove(h.size() - 1);
        JSONArray arr = new JSONArray();
        for (String s : h) arr.put(s);
        P.ss(K_HIST, arr.toString());
    }

    public void clearSearchHistory() {
        P.ss(K_HIST, "[]");
    }

    /** Сброс всех пользовательских данных. */
    public void resetAll() {
        P.del(K_PROG);
        P.del(K_MARKS);
        P.del(K_QUOTES);
        P.del(K_STATS);
        P.del(K_HIST);
        progCache.clear();
        marksCache = null;
        quotesCache = null;
    }

    public String dateOf(long time) {
        return new SimpleDateFormat("d MMMM, HH:mm", new Locale("ru")).format(new Date(time));
    }
}
