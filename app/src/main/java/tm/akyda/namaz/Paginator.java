package tm.akyda.namaz;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Движок книги: превращает блоки главы в «поток» элементов и раскладывает его
 * по страницам с учётом размера экрана, шрифта и интерлиньяжа.
 * Работает на системных StaticLayout — текст получается по-настоящему книжным.
 */
public class Paginator {

    /* ---------------- Настройки вёрстки ---------------- */

    public static class Opt {
        public int width;          // ширина текстовой области, px
        public int height;         // высота текстовой области, px
        public int marginLeft;     // отступ текста от края страницы
        public int marginTop;
        public float textSize;     // px
        public float lineSpacing;  // множитель интерлиньяжа
        public String family = "serif";
        public int theme = Skin.T_PAPER;
        public boolean justify = true;
        public boolean dropCap = true;

        public float marginLeft() {
            return marginLeft;
        }

        public float marginTop() {
            return marginTop;
        }

        public String key(String bookId, int chapter) {
            return bookId + "|" + chapter + "|" + width + "x" + height + "|" + Math.round(textSize)
                    + "|" + Math.round(lineSpacing * 100) + "|" + family + "|" + theme;
        }
    }

    /* ---------------- Элемент потока ---------------- */

    public static class Item {
        public String type = Block.P;
        public int blockIndex = -1;
        public String text = "";
        public StaticLayout layout;
        public int lineStart = 0;
        public int lineEnd = 0;      // исключая
        public float height = 0;     // высота всего элемента (полного)
        public float spaceBefore = 0;
        public float spaceAfter = 0;
        public boolean indentLeft = false;
        public Bitmap image;
        public int imageHeight = 0;
        public String caption = "";
        public float captionHeight = 0;
        public StaticLayout captionLayout;
        public boolean ornament = false;
        public StaticLayout rightLayout;   // для «строка слева … справа»
        public boolean firstPiece = true;
        public boolean lastPiece = true;
        public int pageMarker = -1;
    }

    public static class Page {
        public List<Item> items = new ArrayList<>();
        public int firstBlock = 0;
        public int lastBlock = 0;
        public int pageMarker = -1;
        public int lines = 0;
    }

    public static class Result {
        public List<Page> pages = new ArrayList<>();
    }

    private final Map<String, Result> cache = new HashMap<>();
    private final Map<String, Bitmap> imageCache = new HashMap<>();

    private final Context app;

    public Paginator(Context c) {
        this.app = c.getApplicationContext();
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearImages() {
        imageCache.clear();
    }

    /* ---------------- Публичный API ---------------- */

    public Result paginate(Book book, int chapter, Opt o) {
        String key = o.key(book.id, chapter);
        Result r = cache.get(key);
        if (r != null) return r;
        List<Item> flow = flow(book, chapter, o);
        r = new Result();
        r.pages = split(flow, o);
        if (cache.size() > 8) cache.clear();
        cache.put(key, r);
        return r;
    }

    /** «Поток» главы без разбивки на страницы — для режима прокрутки. */
    public List<Item> flow(Book book, int chapter, Opt o) {
        List<Item> out = new ArrayList<>();
        int[] range = book.chapterRange(chapter);
        Book.Toc toc = book.tocAt(chapter);
        int pendingMarker = -1;   // номер страницы оригинала из маркеров Block.PAGE

        // Титульная страница главы
        if (toc != null && !U.empty(toc.text)) {
            Item t = text(toc.text, Block.TITLE, -1, o, Typeface.BOLD);
            t.spaceBefore = o.textSize * 1.6f;
            t.spaceAfter = o.textSize * 0.35f;
            out.add(t);
            Item orn = new Item();
            orn.ornament = true;
            orn.height = Math.max(28, (int) (o.textSize * 1.7f));
            out.add(orn);
        }

        for (int i = range[0]; i < range[1]; i++) {
            Block b = book.blocks.get(i);
            int before = out.size();
            switch (b.type) {
                case Block.PAGE: {
                    pendingMarker = b.pageNumber;
                    break;
                }
                case Block.GAP: {
                    Item g = new Item();
                    g.type = Block.GAP;
                    g.height = o.textSize * 0.7f;
                    out.add(g);
                    break;
                }
                case Block.IMG: {
                    Item it = image(b, o);
                    if (it != null) {
                        it.blockIndex = i;   // чтобы закладки и прогресс знали, где мы
                        out.add(it);
                    }
                    break;
                }
                case Block.TABLE: {
                    for (String row : tableRows(b)) {
                        Item it = text(row, Block.P, i, o, Typeface.NORMAL);
                        it.indentLeft = true;
                        it.spaceAfter = o.textSize * 0.1f;
                        out.add(it);
                    }
                    break;
                }
                case Block.LIST: {
                    for (int k = 0; k < b.items.size(); k++) {
                        Item it = text(b.items.get(k), Block.LIST, i, o, Typeface.NORMAL);
                        it.indentLeft = true;
                        out.add(it);
                    }
                    break;
                }
                case Block.AR: {
                    Item it = text(b.text, Block.AR, i, o, Typeface.NORMAL);
                    it.spaceBefore = o.textSize * 0.5f;
                    it.spaceAfter = o.textSize * 0.5f;
                    out.add(it);
                    break;
                }
                case Block.PART:
                case Block.H1: {
                    Item it = text(b.text, b.type, i, o, Typeface.BOLD);
                    it.spaceBefore = o.textSize * 1.15f;
                    it.spaceAfter = o.textSize * 0.25f;
                    out.add(it);
                    break;
                }
                case Block.H2: {
                    Item it = text(b.text, Block.H2, i, o, Typeface.BOLD);
                    it.spaceBefore = o.textSize * 0.9f;
                    it.spaceAfter = o.textSize * 0.15f;
                    out.add(it);
                    break;
                }
                case Block.QUOTE: {
                    Item it = text(b.text, Block.QUOTE, i, o, Typeface.ITALIC);
                    it.indentLeft = true;
                    it.spaceBefore = o.textSize * 0.5f;
                    it.spaceAfter = o.textSize * 0.4f;
                    out.add(it);
                    if (!U.empty(b.source)) {
                        Item src = text(b.source, Block.NOTE, i, o, Typeface.NORMAL);
                        src.indentLeft = true;
                        out.add(src);
                    }
                    break;
                }
                case Block.CENTER: {
                    Item it = text(b.text, Block.CENTER, i, o, Typeface.NORMAL);
                    out.add(it);
                    break;
                }
                default: { // P, Q, A, NOTE, TITLE
                    Item it = text(b.text, b.type, i, o, Typeface.NORMAL);
                    out.add(it);
                    break;
                }
            }
            for (int k = before; k < out.size(); k++) {
                out.get(k).pageMarker = pendingMarker;
            }
        }
        return out;
    }

    /* ---------------- Разбивка на страницы ---------------- */

    private List<Page> split(List<Item> flow, Opt o) {
        List<Page> pages = new ArrayList<>();
        Page cur = newPage(flow.isEmpty() ? 0 : flow.get(0).blockIndex);
        float y = 0;
        float lineH = o.textSize * o.lineSpacing;

        for (int idx = 0; idx < flow.size(); idx++) {
            Item it = flow.get(idx);

            // аккуратные переносы заголовков: не оставляем «висячий» заголовок
            if (it.layout != null && isHeading(it.type)) {
                float need = it.spaceBefore + it.layout.getHeight() + lineH * 2.2f;
                if (cur.items.size() > 0 && y + need > o.height) {
                    pages.add(cur);
                    cur = newPage(it.blockIndex);
                    y = 0;
                }
            }

            if (it.image != null) {
                float h = it.spaceBefore + it.imageHeight + (it.captionLayout != null ? it.captionHeight + o.textSize * 0.4f : 0);
                if (cur.items.size() > 0 && y + h > o.height) {
                    pages.add(cur);
                    cur = newPage(it.blockIndex);
                    y = 0;
                }
                it.spaceBefore = 0;
                cur.items.add(it);
                y += h;
                cur.lines += 3;
                cur.lastBlock = it.blockIndex;
                if (it.pageMarker > 0 && cur.pageMarker < 0) cur.pageMarker = it.pageMarker;
                continue;
            }

            if (it.layout == null) { // отступ
                if (cur.items.size() > 0) {
                    cur.items.add(it);
                    y += it.height;
                }
                continue;
            }

            int total = it.layout.getLineCount();
            int placed = 0;
            while (placed < total) {
                float avail = o.height - y;
                int fit = linesThatFit(it.layout, placed, total, avail);
                if (fit <= 1 && y > 0 && (o.height - y) < lineH * 2f) {
                    // слишком мало места — переносим на новую страницу
                    pages.add(cur);
                    cur = newPage(it.blockIndex);
                    y = 0;
                    continue;
                }
                if (fit <= 0) {
                    pages.add(cur);
                    cur = newPage(it.blockIndex);
                    y = 0;
                    continue;
                }
                Item piece = copyForPiece(it, placed, placed + fit);
                if (placed == 0) piece.spaceBefore = Math.min(it.spaceBefore, o.textSize * 0.6f);
                float h = piece.spaceBefore + layoutHeight(piece.layout, piece.lineStart, piece.lineEnd);
                boolean pageFull = (placed + fit) < total;
                if (!pageFull) h += it.spaceAfter;
                cur.items.add(piece);
                cur.lines += fit;
                cur.lastBlock = it.blockIndex;
                if (piece.pageMarker > 0 && cur.pageMarker < 0) cur.pageMarker = piece.pageMarker;
                if (cur.firstBlock < 0) cur.firstBlock = it.blockIndex;
                y += h;
                placed += fit;
                if (placed < total) {
                    pages.add(cur);
                    cur = newPage(it.blockIndex);
                    y = 0;
                }
            }
        }
        if (!cur.items.isEmpty()) pages.add(cur);
        if (pages.isEmpty()) pages.add(cur);
        return pages;
    }

    private static Page newPage(int block) {
        Page p = new Page();
        p.firstBlock = block;
        p.lastBlock = block;
        return p;
    }

    private static boolean isHeading(String type) {
        return Block.H1.equals(type) || Block.H2.equals(type) || Block.PART.equals(type) || Block.TITLE.equals(type);
    }

    private static Item copyForPiece(Item src, int startLine, int endLine) {
        Item it = new Item();
        it.type = src.type;
        it.blockIndex = src.blockIndex;
        it.text = src.text;
        it.layout = src.layout;
        it.lineStart = startLine;
        it.lineEnd = endLine;
        it.indentLeft = src.indentLeft;
        it.firstPiece = startLine == 0;
        it.lastPiece = endLine >= src.layout.getLineCount();
        it.spaceAfter = src.spaceAfter;
        it.spaceBefore = src.spaceBefore;
        it.pageMarker = src.pageMarker;
        it.height = src.height;
        return it;
    }

    private static int linesThatFit(StaticLayout l, int from, int to, float avail) {
        if (avail <= 0) return 0;
        if (l.getLineTop(from) > 0) {
            // считаем относительно начала фрагмента
        }
        float top = l.getLineTop(from);
        int last = from;
        for (int i = from; i < to; i++) {
            float bottom = l.getLineBottom(i) - top;
            if (bottom <= avail) last = i + 1;
            else break;
        }
        return last - from;
    }

    private static float layoutHeight(StaticLayout l, int from, int to) {
        float top = l.getLineTop(from);
        float bottom = to <= 0 ? l.getHeight() : l.getLineBottom(Math.min(to - 1, l.getLineCount() - 1));
        return bottom - top;
    }

    /* ---------------- Построение текстовых элементов ---------------- */

    private Item text(String value, String type, int blockIndex, Opt o, int style) {
        Item it = new Item();
        it.type = type;
        it.blockIndex = blockIndex;
        it.text = value == null ? "" : value;
        float size = o.textSize;
        int color = Skin.paperInk(o.theme);
        Typeface tf = U.tf(o.family, style);
        boolean rtl = Block.AR.equals(type);
        float pad = 0;
        String prefix = null;
        int indent = 0;

        switch (type) {
            case Block.TITLE:
                size = o.textSize * 1.5f;
                color = Skin.paperAccent(o.theme);
                tf = U.tf("serif", Typeface.BOLD);
                break;
            case Block.PART:
                size = o.textSize * 1.42f;
                color = Skin.paperAccent(o.theme);
                tf = U.tf("serif", Typeface.BOLD);
                break;
            case Block.H1:
                size = o.textSize * 1.28f;
                color = Skin.paperAccent(o.theme);
                break;
            case Block.H2:
                size = o.textSize * 1.1f;
                break;
            case Block.AR:
                size = o.textSize * 1.18f;
                color = Block.AR.equals(type) ? Skin.paperInk(o.theme) : color;
                break;
            case Block.QUOTE:
                size = o.textSize * 1.02f;
                indent = (int) (o.textSize * 1.6f);
                pad = o.textSize * 1.1f;
                break;
            case Block.NOTE:
                size = o.textSize * 0.8f;
                color = Skin.paperSub(o.theme);
                indent = (int) (o.textSize * 0.6f);
                break;
            case Block.CENTER:
                break;
            case Block.Q:
                prefix = "S:  ";
                break;
            case Block.A:
                prefix = "J:  ";
                break;
            case Block.LIST: {
                indent = (int) (o.textSize * 1.3f);
                break;
            }
            default:
                break;
        }

        SpannableString cs = new SpannableString(prefix == null ? it.text : prefix + it.text);
        if (prefix != null) {
            cs.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cs.setSpan(new ForegroundColorSpan(Skin.paperAccent(o.theme)), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (indent > 0) {
            cs.setSpan(new LeadingMarginSpan.Standard(indent, Block.LIST.equals(type) ? indent : 0),
                    0, cs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (Block.LIST.equals(type)) {
            // пункт списка с «точкой-маркером» цвета акцента
            cs = new SpannableString("•  " + it.text);
            cs.setSpan(new ForegroundColorSpan(Skin.paperAccent(o.theme)), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cs.setSpan(new LeadingMarginSpan.Standard(indent, indent), 0, cs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (Block.QUOTE.equals(type)) {
            cs.setSpan(new ForegroundColorSpan(U.mix(color, Skin.paperAccent(o.theme), 0.25f)),
                    0, cs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(tf);
        p.setSubpixelText(true);
        p.setLinearText(false);
        if (Block.TITLE.equals(type) || Block.PART.equals(type)) {
            p.setLetterSpacing(0.06f);
        }
        if (Block.NOTE.equals(type)) {
            p.setLetterSpacing(0.01f);
        }

        int width = o.width - (int) pad;
        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
        TextDirectionHeuristic dir = rtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.FIRSTSTRONG_LTR;
        if (Block.TITLE.equals(type) || Block.PART.equals(type) || Block.CENTER.equals(type) || Block.AR.equals(type)) {
            align = rtl ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_CENTER;
        }
        it.layout = makeLayout(cs, p, width, align, o.lineSpacing, 0f, o.justify && !rtl &&
                (Block.P.equals(type) || Block.QUOTE.equals(type)), dir);
        it.height = it.layout.getHeight() + it.spaceAfter + it.spaceBefore;
        if (indent > 0 && !Block.LIST.equals(type)) {
            it.height = it.layout.getHeight() + it.spaceAfter + it.spaceBefore;
        }
        return it;
    }

    /** Обёртка над StaticLayout с поддержкой выключки на новых версиях Android. */
    @SuppressWarnings("deprecation")
    public static StaticLayout makeLayout(CharSequence text, TextPaint paint, int width,
                                          Layout.Alignment align, float spacingMult, float spacingAdd,
                                          boolean justify, TextDirectionHeuristic dir) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder b = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                    .setAlignment(align)
                    .setLineSpacing(spacingAdd, spacingMult)
                    .setIncludePad(false)
                    .setTextDirection(dir);
            if (justify && android.os.Build.VERSION.SDK_INT >= 26) {
                b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
            }
            return b.build();
        }
        return new StaticLayout(text, paint, Math.max(1, width), align, spacingMult, spacingAdd, false);
    }

    /* ---------------- Иллюстрации ---------------- */

    private Item image(Block b, Opt o) {
        String src = b.source;
        if (U.empty(src)) return null;
        Bitmap bm;
        boolean isFig = false;
        if (src.startsWith("ill:fig:")) {
            // рисунок из книги: фигурка человека (омовение, позы намаза) — квадратный
            String name = src.substring(8);
            int side = Math.max(120, Math.min(o.width, (int) (o.height * 0.62f)));
            bm = Ill.renderFigure(app, name, side, side);
            isFig = bm != null;
            if (!isFig) return null;
        } else if (src.startsWith("ill:")) {
            // декоративная векторная иллюстрация приложения
            int id = Ill.idOf(src.substring(4));
            if (id < 0) return null;
            int w = Math.max(120, o.width);
            int h = Math.max(120, Math.round(w * 0.72f));
            bm = Ill.render(app, id, w, h, o.theme);
            if (U.empty(b.caption)) b.caption = Ill.caption(id);
        } else {
            String path = "images/" + src;
            bm = imageCache.get(path);
            if (bm == null) {
                bm = decode(path, o.width);
                if (bm == null) return null;
                if (imageCache.size() > 6) imageCache.clear();
                imageCache.put(path, bm);
            }
        }
        Item it = new Item();
        it.type = Block.IMG;
        it.blockIndex = -1;
        it.image = bm;
        int w = Math.min(o.width, bm.getWidth());
        it.imageHeight = Math.max(1, (int) ((float) bm.getHeight() * w / bm.getWidth()));
        int maxH = (int) (o.height * (isFig ? 0.68f : 0.62f));
        if (it.imageHeight > maxH) {
            it.imageHeight = maxH;
        }
        it.spaceBefore = o.textSize * 0.6f;
        it.spaceAfter = o.textSize * 0.6f;
        if (!U.empty(b.caption)) {
            TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            p.setTextSize(o.textSize * 0.78f);
            p.setColor(Skin.paperSub(o.theme));
            p.setTypeface(U.tf("sans-serif", Typeface.NORMAL));
            it.caption = b.caption;
            it.captionLayout = makeLayout(b.caption, p, o.width, Layout.Alignment.ALIGN_CENTER,
                    o.lineSpacing, 0f, false, TextDirectionHeuristics.FIRSTSTRONG_LTR);
            it.captionHeight = it.captionLayout.getHeight();
        }
        return it;
    }

    private Bitmap decode(String path, int targetW) {
        AssetManager am = app.getAssets();
        InputStream is = null;
        try {
            is = am.open(path);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= Math.max(320, targetW)) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            is = am.open(path);
            return BitmapFactory.decodeStream(is, null, o2);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static List<String> tableRows(Block b) {
        List<String> out = new ArrayList<>();
        if (!b.head.isEmpty()) out.add(join(b.head));
        for (List<String> r : b.rows) out.add(join(r));
        return out;
    }

    private static String join(List<String> cells) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) s.append("   ");
            s.append(cells.get(i));
        }
        return s.toString();
    }

    /** Текст страницы целиком — для закладок и цитат. */
    public static String pageText(Page p) {
        StringBuilder sb = new StringBuilder();
        for (Item it : p.items) {
            if (!U.empty(it.text)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(it.text);
            }
        }
        return sb.toString();
    }
}
