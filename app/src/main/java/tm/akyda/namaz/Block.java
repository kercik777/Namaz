package tm.akyda.namaz;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Один смысловой блок страницы книги.
 * t — тип блока, x — текст, items — пункты списка, src — источник/иллюстрация,
 * cap — подпись, n — номер оригинальной страницы книги.
 */
public class Block {

    public static final String PART = "part";     // крупный раздел
    public static final String H1 = "h1";         // заголовок главы
    public static final String H2 = "h2";         // подзаголовок
    public static final String P = "p";           // обычный абзац
    public static final String Q = "q";           // вопрос (S:)
    public static final String A = "a";           // ответ (J:)
    public static final String AR = "ar";         // арабский текст
    public static final String QUOTE = "quote";   // цитата/хадис
    public static final String LIST = "list";     // нумерованный список
    public static final String NOTE = "note";     // сноска/мелкий текст
    public static final String IMG = "img";       // иллюстрация
    public static final String PAGE = "page";     // маркер страницы оригинала
    public static final String CENTER = "center"; // центрированная строка
    public static final String TITLE = "title";   // титульная строка
    public static final String TABLE = "table";   // таблица
    public static final String GAP = "gap";       // вертикальный отступ

    public String type = P;
    public String text = "";
    public String source = "";
    public String caption = "";
    public int pageNumber = -1;
    public List<String> items = new ArrayList<>();
    public List<String> head = new ArrayList<>();
    public List<List<String>> rows = new ArrayList<>();

    public static Block of(String type, String text) {
        Block b = new Block();
        b.type = type;
        b.text = text;
        return b;
    }

    public static Block from(JSONObject o) {
        Block b = new Block();
        b.type = o.optString("t", P);
        b.text = o.optString("x", "");
        b.source = o.optString("src", "");
        b.caption = o.optString("cap", "");
        b.pageNumber = o.optInt("n", -1);
        JSONArray it = o.optJSONArray("items");
        if (it != null) {
            for (int i = 0; i < it.length(); i++) b.items.add(it.optString(i, ""));
        }
        JSONArray hd = o.optJSONArray("head");
        if (hd != null) {
            for (int i = 0; i < hd.length(); i++) b.head.add(hd.optString(i, ""));
        }
        JSONArray rw = o.optJSONArray("rows");
        if (rw != null) {
            for (int i = 0; i < rw.length(); i++) {
                JSONArray r = rw.optJSONArray(i);
                List<String> row = new ArrayList<>();
                if (r != null) for (int j = 0; j < r.length(); j++) row.add(r.optString(j, ""));
                b.rows.add(row);
            }
        }
        return b;
    }

    /** Есть ли у блока текстовое содержимое для поиска. */
    public boolean hasText() {
        return !U.empty(text) || !items.isEmpty();
    }

    /** Полный текст блока — для поиска, цитат и закладок. */
    public String plain() {
        StringBuilder b = new StringBuilder();
        if (!U.empty(text)) b.append(text);
        for (String s : items) {
            if (b.length() > 0) b.append(' ');
            b.append(s);
        }
        if (!U.empty(caption)) {
            if (b.length() > 0) b.append(' ');
            b.append(caption);
        }
        return b.toString();
    }

    public boolean isHeading() {
        return H1.equals(type) || H2.equals(type) || PART.equals(type) || TITLE.equals(type);
    }
}
