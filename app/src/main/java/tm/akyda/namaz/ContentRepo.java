package tm.akyda.namaz;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Хранилище книг: читает JSON из assets, строит поисковый индекс
 * и отдаёт данные экранам. Работает полностью офлайн.
 */
public class ContentRepo {

    private static final ContentRepo I = new ContentRepo();

    public static ContentRepo get() {
        return I;
    }

    private final List<Book> books = new ArrayList<>();
    private final List<Quote> quotes = new ArrayList<>();
    private final List<IndexEntry> index = new ArrayList<>();
    private boolean loaded;
    private boolean loading;
    private Thread thread;

    /* ---------------- Загрузка ---------------- */

    public synchronized void preload(final Context c) {
        if (loaded || loading) return;
        loading = true;
        final Context app = c.getApplicationContext();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loadSync(app);
            }
        }, "content-load");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public void awaitLoaded(Context c) {
        preload(c);
        Thread t = thread;
        if (t != null) {
            try {
                t.join(6000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    private synchronized void loadSync(Context c) {
        if (loaded) return;
        AssetManager am = c.getAssets();
        String[] order = {"akyda", "namaz_kitaby", "namaz"};
        for (String id : order) {
            try {
                String json = readAsset(am, "content/" + id + ".json");
                if (json == null) continue;
                Book b = Book.from(new JSONObject(json));
                if (b.blocks.isEmpty()) continue;
                if (b.toc.isEmpty()) b.buildToc();
                books.add(b);
            } catch (Exception e) {
                // Повреждённый файл не должен ломать приложение.
            }
        }
        String q = readAsset(am, "content/quotes.json");
        if (q != null) {
            try {
                JSONArray arr = new JSONArray(q);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    Quote qt = new Quote();
                    qt.tk = o.optString("tk", "");
                    qt.ru = o.optString("ru", "");
                    qt.src = o.optString("src", "");
                    quotes.add(qt);
                }
            } catch (Exception ignored) {
            }
        }
        buildIndex();
        loaded = true;
        loading = false;
    }

    private static String readAsset(AssetManager am, String path) {
        InputStream is = null;
        try {
            is = am.open(path);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(1 << 16);
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void buildIndex() {
        index.clear();
        for (Book b : books) {
            for (int i = 0; i < b.blocks.size(); i++) {
                Block bl = b.blocks.get(i);
                if (!bl.hasText() || bl.type.equals(Block.PAGE) || bl.type.equals(Block.GAP)) continue;
                IndexEntry e = new IndexEntry();
                e.bookId = b.id;
                e.block = i;
                e.chapter = b.chapterOf(i);
                e.norm = U.norm(bl.plain());
                e.text = bl.plain();
                index.add(e);
            }
        }
    }

    /* ---------------- Доступ ---------------- */

    public List<Book> books() {
        return books;
    }

    public Book byId(String id) {
        for (Book b : books) if (b.id.equals(id)) return b;
        return books.isEmpty() ? null : books.get(0);
    }

    public boolean has(String id) {
        for (Book b : books) if (b.id.equals(id)) return true;
        return false;
    }

    /* ---------------- Поиск ---------------- */

    public static class Hit {
        public String bookId;
        public int block;
        public int chapter;
        public String snippet;
        public String chapterTitle;
    }

    private static class IndexEntry {
        String bookId;
        int block;
        int chapter;
        String norm;
        String text;
    }

    public List<Hit> search(String query, int limit) {
        List<Hit> out = new ArrayList<>();
        String q = U.norm(query);
        if (q.length() < 2) return out;
        for (IndexEntry e : index) {
            int at = e.norm.indexOf(q);
            if (at < 0) continue;
            Hit h = new Hit();
            h.bookId = e.bookId;
            h.block = e.block;
            h.chapter = e.chapter;
            h.snippet = snippet(e.text, at, q.length());
            Book b = byId(e.bookId);
            Book.Toc t = b == null ? null : b.tocAt(e.chapter);
            h.chapterTitle = t == null ? "" : t.text;
            out.add(h);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static String snippet(String text, int at, int len) {
        int start = Math.max(0, at - 42);
        int end = Math.min(text.length(), at + len + 60);
        String s = text.substring(start, end).replace('\n', ' ').trim();
        if (start > 0) s = "… " + s;
        if (end < text.length()) s = s + " …";
        return s;
    }

    /* ---------------- Цитаты дня ---------------- */

    public static class Quote {
        public String tk;
        public String ru;
        public String src;
    }

    public List<Quote> quotes() {
        return quotes;
    }

    public Quote quoteOfDay() {
        if (quotes.isEmpty()) return null;
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_YEAR) + c.get(Calendar.YEAR) * 366;
        return quotes.get(day % quotes.size());
    }

    /** Книги, отсортированные по времени последнего открытия. */
    public List<Book> recent() {
        final Lib lib = Lib.get();
        List<Book> list = new ArrayList<>(books);
        Collections.sort(list, new Comparator<Book>() {
            @Override
            public int compare(Book a, Book b) {
                long ta = lib.prog(a.id).time;
                long tb = lib.prog(b.id).time;
                return Long.compare(tb, ta);
            }
        });
        return list;
    }
}
