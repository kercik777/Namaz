package tm.akyda.namaz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Иллюстрации, нарисованные кодом (полностью векторные, без картинок из PDF).
 *
 * Каждая иллюстрация рисуется в квадратной сетке 100×100 и масштабируется
 * под любой размер. Стиль — «книжный»: мягкая подложка, изумрудные силуэты,
 * золотые линии. Позы намаза повторяют смысл рисунков печатного издания,
 * но нарисованы заново.
 */
public final class Ill {

    public static final int MOSQUE = 0;      // мечеть
    public static final int KAABA = 1;       // Кааба
    public static final int QURAN = 2;       // Коран на подставке
    public static final int RUG = 3;         // намазлык
    public static final int CRESCENT = 4;    // полумесяц со звёздами
    public static final int ABLUTION = 5;    // омовение (кувшин и вода)
    public static final int TASBIH = 6;      // чётки
    public static final int STAND = 7;       // стоя (кыям)
    public static final int RUKU = 8;        // поясной поклон
    public static final int SAJDA = 9;       // земной поклон
    public static final int SIT = 10;        // сидя (ташаhhуд)
    public static final int TAKBIR = 11;     // вступительный такбир
    public static final int DUA = 12;        // дуа (руки подняты)
    public static final int MINARET = 13;    // минарет
    public static final int SUNSET = 14;     // закат (время намаза)
    public static final int NIGHT = 15;      // ночное небо
    public static final int WATER = 16;      // капли воды
    public static final int STAR = 17;       // звезда-орнамент
    public static final int COUNT = 18;

    public static final String[] IDS = {
            "mosque", "kaaba", "quran", "rug", "crescent", "ablution", "tasbih", "stand",
            "ruku", "sajda", "sit", "takbir", "dua", "minaret", "sunset", "night",
            "water", "star"
    };

    /** Названия на туркменском — для подписей под иллюстрациями. */
    public static final String[] CAPS = {
            "Metjit", "Käbe", "Kurhan", "Namazlyk", "Aý we ýyldyzlar", "Taharat",
            "Tesbih", "Kyýam — dik duruş", "Rukug — bil baglamak", "Säjdä — ýere baş urmak",
            "Oturyş — täşehhüt", "Täkbir", "Doga", "Minara", "Gün ýaşýar", "Gije",
            "Suw", "Ýyldyz"
    };

    /* ==================== Рисунки из книг (vector drawable) ==================== */

    /**
     * Имена рисунков, нарисованных кодом в tools/ill/figures.py и лежащих
     * в res/drawable/ill_<имя>.xml. Это фигурки людей: омовение, позы намаза.
     */
    public static final String[] FIGS = {
            "pose_niet_m", "pose_niet_w", "pose_takbir_m", "pose_takbir_w",
            "pose_stand_m", "pose_stand_w", "pose_ruku_m", "pose_ruku_w",
            "pose_sajda_m", "pose_sajda_w", "pose_sit_m", "pose_sit_w",
            "pose_dua_m", "pose_dua_w", "pose_salam_r", "pose_salam_l",
            "pose_salam_r_w", "pose_salam_l_w",
            "abl_intent", "abl_intent_w", "abl_hands", "abl_mouth", "abl_nose",
            "abl_face", "abl_arm", "abl_head", "abl_ear", "abl_foot", "abl_dua",
            "tay_intent", "tay_hands", "tay_face", "tay_arm",
    };

    /** Ресурсы рисунков в том же порядке, что {@link #FIGS}. */
    public static final int[] FIG_RES = {
            R.drawable.ill_pose_niet_m, R.drawable.ill_pose_niet_w,
            R.drawable.ill_pose_takbir_m, R.drawable.ill_pose_takbir_w,
            R.drawable.ill_pose_stand_m, R.drawable.ill_pose_stand_w,
            R.drawable.ill_pose_ruku_m, R.drawable.ill_pose_ruku_w,
            R.drawable.ill_pose_sajda_m, R.drawable.ill_pose_sajda_w,
            R.drawable.ill_pose_sit_m, R.drawable.ill_pose_sit_w,
            R.drawable.ill_pose_dua_m, R.drawable.ill_pose_dua_w,
            R.drawable.ill_pose_salam_r, R.drawable.ill_pose_salam_l,
            R.drawable.ill_pose_salam_r_w, R.drawable.ill_pose_salam_l_w,
            R.drawable.ill_abl_intent, R.drawable.ill_abl_intent_w,
            R.drawable.ill_abl_hands, R.drawable.ill_abl_mouth, R.drawable.ill_abl_nose,
            R.drawable.ill_abl_face, R.drawable.ill_abl_arm, R.drawable.ill_abl_head,
            R.drawable.ill_abl_ear, R.drawable.ill_abl_foot, R.drawable.ill_abl_dua,
            R.drawable.ill_tay_intent, R.drawable.ill_tay_hands,
            R.drawable.ill_tay_face, R.drawable.ill_tay_arm,
    };

    /** Номер ресурса рисунка или 0. */
    public static int figRes(String name) {
        if (U.empty(name)) return 0;
        String n = name.trim();
        for (int i = 0; i < FIGS.length; i++) {
            if (FIGS[i].equals(n) && i < FIG_RES.length) return FIG_RES[i];
        }
        return 0;
    }

    public static boolean isFigure(String name) {
        return figRes(name) != 0;
    }

    /** Рисунок из drawable, растянутый в заданный прямоугольник. */
    public static Bitmap renderFigure(Context c, String name, int w, int h) {
        int res = figRes(name);
        if (res == 0 || w <= 0 || h <= 0) return null;
        try {
            android.graphics.drawable.Drawable d = c.getResources().getDrawable(res);
            if (d == null) return null;
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    private Ill() {
    }

    public static int idOf(String name) {
        if (U.empty(name)) return -1;
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equalsIgnoreCase(name.trim())) return i;
        }
        return -1;
    }

    public static String caption(int id) {
        return (id >= 0 && id < CAPS.length) ? CAPS[id] : "";
    }

    /* ==================== Палитра ==================== */

    private static int ink(int theme) {
        return Skin.paperInk(theme);
    }

    private static int gold(int theme) {
        return Skin.paperAccent(theme);
    }

    private static int paper(int theme) {
        return Skin.paper(theme);
    }

    private static int sub(int theme) {
        return Skin.paperSub(theme);
    }

    /** Основной цвет заливки силуэтов. */
    private static int body(int theme) {
        return Skin.paperIsDark(theme) ? U.mix(gold(theme), paper(theme), 0.30f) : 0xFF11543D;
    }

    /** Мягкая подложка-«карточка» под иллюстрацией. */
    private static int soft(int theme) {
        return Skin.paperIsDark(theme)
                ? U.mix(paper(theme), gold(theme), 0.10f)
                : U.mix(paper(theme), gold(theme), 0.085f);
    }

    private static int hair(int theme) {
        return Skin.withAlpha(gold(theme), 0.45f);
    }

    /* ==================== Готовые битмапы (для страниц книги) ==================== */

    /** Иллюстрация-«карточка»: подложка, рисунок и тонкая золотая рамка. */
    public static Bitmap render(Context c, int id, int width, int height, int theme) {
        int w = Math.max(24, width);
        int h = Math.max(24, height);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float rad = Math.min(w, h) * 0.06f;
        p.setColor(soft(theme));
        U.roundRect(canvas, 0, 0, w, h, rad, p);

        float pad = Math.min(w, h) * 0.07f;
        float side = Math.min(w - pad * 2f, h - pad * 2f);
        draw(canvas, id, (w - side) / 2f, (h - side) / 2f, side, theme);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, Math.min(w, h) * 0.008f));
        p.setColor(hair(theme));
        U.roundRect(canvas, p.getStrokeWidth() / 2f, p.getStrokeWidth() / 2f,
                w - p.getStrokeWidth() / 2f, h - p.getStrokeWidth() / 2f, rad, p);
        p.setStyle(Paint.Style.FILL);
        return bm;
    }

    /* ==================== Рисование ==================== */

    /** Рисует иллюстрацию в квадрат (l, t, size). */
    public static void draw(Canvas c, int id, float l, float t, float size, int theme) {
        c.save();
        c.translate(l, t);
        c.scale(size / 100f, size / 100f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (id) {
            case MOSQUE: mosque(c, p, theme); break;
            case KAABA: kaaba(c, p, theme); break;
            case QURAN: quran(c, p, theme); break;
            case RUG: rug(c, p, theme); break;
            case CRESCENT: crescent(c, p, theme); break;
            case ABLUTION: ablution(c, p, theme); break;
            case TASBIH: tasbih(c, p, theme); break;
            case STAND: stand(c, p, theme); break;
            case RUKU: ruku(c, p, theme); break;
            case SAJDA: sajda(c, p, theme); break;
            case SIT: sit(c, p, theme); break;
            case TAKBIR: takbir(c, p, theme); break;
            case DUA: dua(c, p, theme); break;
            case MINARET: minaret(c, p, theme); break;
            case SUNSET: sunset(c, p, theme); break;
            case NIGHT: night(c, p, theme); break;
            case WATER: water(c, p, theme); break;
            default: star(c, p, theme); break;
        }
        c.restore();
    }

    /* ---------- примитивы ---------- */

    private static void fill(Canvas c, Paint p, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
    }

    private static void stroke(Canvas c, Paint p, int color, float w) {
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(w);
    }

    private static void rr(Canvas c, Paint p, float l, float t, float r, float b, float rad) {
        U.roundRect(c, l, t, r, b, rad, p);
    }

    private static void dot(Canvas c, Paint p, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, p);
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2, float w) {
        p.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, p);
    }

    private static void star5(Canvas c, Paint p, float cx, float cy, float r) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.42f;
            float px = cx + (float) Math.cos(a) * rr;
            float py = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    /** Полумесяц: большой круг минус смещённый круг (EVEN_ODD). */
    private static void moon(Canvas c, Paint p, float cx, float cy, float r, float off) {
        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addOval(new RectF(cx - r, cy - r, cx + r, cy + r), Path.Direction.CW);
        float r2 = r * 0.92f;
        path.addOval(new RectF(cx - r2 + off, cy - r2, cx + r2 + off, cy + r2), Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    /** Голова человечка. */
    private static void head(Canvas c, Paint p, int theme, float cx, float cy, float r) {
        fill(c, p, body(theme));
        dot(c, p, cx, cy, r);
    }

    /** Намазлык под фигурой. */
    private static void rugUnder(Canvas c, Paint p, int theme, float l, float r, float y) {
        fill(c, p, U.mix(paper(theme), gold(theme), Skin.paperIsDark(theme) ? 0.22f : 0.30f));
        rr(c, p, l, y, r, y + 6f, 2.4f);
        stroke(c, p, hair(theme), 1.4f);
        rr(c, p, l, y, r, y + 6f, 2.4f);
    }

    /* ---------- сюжеты ---------- */

    private static void mosque(Canvas c, Paint p, int theme) {
        int b = body(theme);
        int g = gold(theme);
        // купол
        fill(c, p, b);
        Path dome = new Path();
        dome.moveTo(30, 52);
        dome.cubicTo(30, 26, 70, 26, 70, 52);
        dome.close();
        c.drawPath(dome, p);
        // основание
        rr(c, p, 26, 52, 74, 82, 3f);
        // арки
        fill(c, p, paper(theme));
        rr(c, p, 44, 62, 56, 82, 6f);
        rr(c, p, 30, 60, 38, 70, 4f);
        rr(c, p, 62, 60, 70, 70, 4f);
        // минареты
        fill(c, p, b);
        rr(c, p, 15, 40, 22, 82, 3f);
        rr(c, p, 78, 40, 85, 82, 3f);
        fill(c, p, g);
        dot(c, p, 18.5f, 36, 3.2f);
        dot(c, p, 81.5f, 36, 3.2f);
        // полумесяц на куполе
        fill(c, p, g);
        moon(c, p, 50, 20, 5.4f, 2.6f);
        // земля
        stroke(c, p, hair(theme), 1.6f);
        line(c, p, 12, 84, 88, 84, 1.6f);
    }

    private static void kaaba(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        fill(c, p, 0xFF1B1B1B);
        rr(c, p, 33, 30, 67, 76, 2.5f);
        // пояс с надписью
        fill(c, p, g);
        rr(c, p, 33, 40, 67, 47, 1.2f);
        // дверь
        fill(c, p, U.mix(g, 0xFF8A6C22, 0.45f));
        rr(c, p, 46, 55, 54, 70, 1.4f);
        // архитектурная арка вокруг
        stroke(c, p, hair(theme), 2.2f);
        Path arc = new Path();
        arc.moveTo(22, 78);
        arc.lineTo(22, 58);
        arc.quadTo(50, 26, 78, 58);
        arc.lineTo(78, 78);
        c.drawPath(arc, p);
        stroke(c, p, hair(theme), 1.6f);
        line(c, p, 18, 82, 82, 82, 1.6f);
        // люди вокруг (силуэты)
        fill(c, p, body(theme));
        dot(c, p, 30, 74, 3.2f);
        rr(c, p, 27.6f, 77, 32.4f, 84, 1.6f);
        dot(c, p, 70, 74, 3.2f);
        rr(c, p, 67.6f, 77, 72.4f, 84, 1.6f);
    }

    private static void quran(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        // подставка
        stroke(c, p, g, 2.6f);
        line(c, p, 30, 78, 70, 78, 2.6f);
        line(c, p, 34, 78, 46, 62, 2.4f);
        line(c, p, 66, 78, 54, 62, 2.4f);
        // книга
        fill(c, p, paper(theme));
        Path left = new Path();
        left.moveTo(50, 36);
        left.lineTo(24, 30);
        left.lineTo(24, 58);
        left.lineTo(50, 64);
        left.close();
        c.drawPath(left, p);
        Path right = new Path();
        right.moveTo(50, 36);
        right.lineTo(76, 30);
        right.lineTo(76, 58);
        right.lineTo(50, 64);
        right.close();
        c.drawPath(right, p);
        // обрез и строки
        stroke(c, p, hair(theme), 1.5f);
        for (int i = 0; i < 4; i++) {
            float y = 38 + i * 6.4f;
            line(c, p, 28, y, 46, y + 3.2f, 1.5f);
            line(c, p, 54, y + 3.2f, 72, y, 1.5f);
        }
        line(c, p, 50, 36, 50, 64, 1.6f);
        fill(c, p, g);
        moon(c, p, 50, 22, 5.6f, 2.8f);
    }

    private static void rug(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        fill(c, p, U.mix(paper(theme), g, Skin.paperIsDark(theme) ? 0.26f : 0.34f));
        rr(c, p, 22, 22, 78, 84, 3f);
        stroke(c, p, g, 2f);
        rr(c, p, 26, 26, 74, 80, 2.4f);
        // михрабная арка
        stroke(c, p, body(theme), 2.2f);
        Path arch = new Path();
        arch.moveTo(38, 70);
        arch.lineTo(38, 50);
        arch.quadTo(50, 36, 62, 50);
        arch.lineTo(62, 70);
        c.drawPath(arch, p);
        // кисти
        stroke(c, p, g, 1.6f);
        for (int i = 0; i < 5; i++) {
            float x = 32 + i * 9;
            line(c, p, x, 84, x, 88, 1.6f);
        }
    }

    private static void crescent(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        stroke(c, p, hair(theme), 1.6f);
        c.drawCircle(50, 50, 34, p);
        fill(c, p, g);
        moon(c, p, 54, 48, 18, 8f);
        star5(c, p, 70, 30, 6f);
        star5(c, p, 32, 68, 4.4f);
    }

    private static void ablution(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        // кувшин
        fill(c, p, body(theme));
        Path jug = new Path();
        jug.moveTo(30, 40);
        jug.lineTo(30, 62);
        jug.quadTo(30, 74, 42, 74);
        jug.quadTo(54, 74, 54, 62);
        jug.lineTo(54, 40);
        jug.close();
        c.drawPath(jug, p);
        rr(c, p, 34, 30, 50, 40, 2f);
        // носик и ручка
        stroke(c, p, body(theme), 3.4f);
        line(c, p, 54, 46, 64, 42, 3.4f);
        Path h = new Path();
        h.moveTo(30, 46);
        h.quadTo(20, 50, 30, 58);
        c.drawPath(h, p);
        // вода
        fill(c, p, g);
        dot(c, p, 70, 60, 2.8f);
        dot(c, p, 76, 70, 2.2f);
        dot(c, p, 68, 74, 1.8f);
        // таз
        stroke(c, p, body(theme), 2.8f);
        Path basin = new Path();
        basin.moveTo(40, 80);
        basin.quadTo(58, 94, 82, 80);
        c.drawPath(basin, p);
        stroke(c, p, hair(theme), 1.6f);
        line(c, p, 22, 86, 88, 86, 1.6f);
    }

    private static void tasbih(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        stroke(c, p, body(theme), 2.2f);
        c.drawCircle(50, 46, 24, p);
        fill(c, p, g);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 - Math.PI / 2;
            dot(c, p, 50 + (float) Math.cos(a) * 24f, 46 + (float) Math.sin(a) * 24f, 3.1f);
        }
        // кисточка
        stroke(c, p, g, 2f);
        line(c, p, 50, 70, 50, 84, 2f);
        line(c, p, 46, 84, 54, 84, 2.4f);
        line(c, p, 48, 88, 52, 88, 1.8f);
    }

    private static void stand(Canvas c, Paint p, int theme) {
        rugUnder(c, p, theme, 22, 78, 84);
        int b = body(theme);
        stroke(c, p, b, 4.4f);
        line(c, p, 50, 44, 50, 76, 4.6f);          // туловище
        line(c, p, 50, 76, 43, 84, 4.2f);          // правая нога
        line(c, p, 50, 76, 57, 84, 4.2f);          // левая нога
        stroke(c, p, b, 3.6f);
        line(c, p, 42, 46, 54, 56, 3.6f);          // руки сложены
        line(c, p, 58, 46, 46, 56, 3.6f);
        head(c, p, theme, 50, 36, 7f);
        fill(c, p, gold(theme));
        moon(c, p, 74, 24, 5f, 2.4f);
    }

    private static void ruku(Canvas c, Paint p, int theme) {
        rugUnder(c, p, theme, 16, 84, 86);
        int b = body(theme);
        stroke(c, p, b, 4.6f);
        line(c, p, 34, 50, 62, 46, 4.6f);          // спина (наклон)
        line(c, p, 34, 50, 34, 80, 4.4f);          // ноги
        line(c, p, 34, 80, 27, 86, 4f);
        line(c, p, 34, 80, 41, 86, 4f);
        stroke(c, p, b, 3.6f);
        line(c, p, 58, 47, 62, 66, 3.6f);          // рука к колену
        line(c, p, 52, 48, 40, 66, 3.4f);
        head(c, p, theme, 66, 44, 6.6f);
    }

    private static void sajda(Canvas c, Paint p, int theme) {
        rugUnder(c, p, theme, 14, 86, 88);
        int b = body(theme);
        // корпус дугой
        stroke(c, p, b, 5f);
        Path bodyArc = new Path();
        bodyArc.moveTo(30, 80);
        bodyArc.quadTo(48, 56, 66, 76);
        c.drawPath(bodyArc, p);
        // сложенные ноги
        stroke(c, p, b, 4.2f);
        line(c, p, 30, 80, 34, 88, 4.2f);
        line(c, p, 30, 74, 24, 84, 4f);
        // голова у земли
        head(c, p, theme, 74, 78, 6.4f);
        // знак земного поклона
        fill(c, p, gold(theme));
        dot(c, p, 88, 82, 2.6f);
    }

    private static void sit(Canvas c, Paint p, int theme) {
        rugUnder(c, p, theme, 20, 80, 84);
        int b = body(theme);
        stroke(c, p, b, 4.4f);
        line(c, p, 44, 46, 44, 72, 4.6f);          // туловище
        stroke(c, p, b, 4.2f);
        line(c, p, 44, 72, 30, 82, 4.2f);          // сложенные ноги
        line(c, p, 44, 72, 62, 82, 4.2f);
        stroke(c, p, b, 3.4f);
        line(c, p, 44, 52, 38, 74, 3.4f);          // руки на коленях
        line(c, p, 44, 52, 52, 74, 3.4f);
        head(c, p, theme, 44, 38, 7f);
        fill(c, p, gold(theme));
        star5(c, p, 76, 30, 5f);
    }

    private static void takbir(Canvas c, Paint p, int theme) {
        rugUnder(c, p, theme, 22, 78, 84);
        int b = body(theme);
        stroke(c, p, b, 4.6f);
        line(c, p, 50, 46, 50, 76, 4.6f);
        line(c, p, 50, 76, 43, 84, 4.2f);
        line(c, p, 50, 76, 57, 84, 4.2f);
        stroke(c, p, b, 3.6f);
        line(c, p, 46, 48, 38, 34, 3.6f);          // руки подняты к ушам
        line(c, p, 54, 48, 62, 34, 3.6f);
        stroke(c, p, gold(theme), 2f);
        dot(c, p, 37, 32, 2.6f);
        dot(c, p, 63, 32, 2.6f);
        head(c, p, theme, 50, 38, 7f);
    }

    private static void dua(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        // свет
        stroke(c, p, Skin.withAlpha(g, 0.55f), 2f);
        line(c, p, 50, 8, 50, 22, 2f);
        line(c, p, 34, 14, 40, 26, 2f);
        line(c, p, 66, 14, 60, 26, 2f);
        // ладони
        fill(c, p, body(theme));
        Path left = new Path();
        left.moveTo(30, 46);
        left.quadTo(38, 40, 46, 62);
        left.quadTo(38, 74, 28, 66);
        left.close();
        c.drawPath(left, p);
        Path right = new Path();
        right.moveTo(70, 46);
        right.quadTo(62, 40, 54, 62);
        right.quadTo(62, 74, 72, 66);
        right.close();
        c.drawPath(right, p);
        // рукава
        fill(c, p, U.mix(body(theme), paper(theme), 0.25f));
        rr(c, p, 22, 66, 40, 84, 3f);
        rr(c, p, 60, 66, 78, 84, 3f);
    }

    private static void minaret(Canvas c, Paint p, int theme) {
        int b = body(theme);
        int g = gold(theme);
        fill(c, p, b);
        rr(c, p, 44, 26, 56, 84, 4f);
        rr(c, p, 40, 38, 60, 44, 2.4f);            // балкон
        fill(c, p, g);
        rr(c, p, 41, 38, 59, 41, 1.6f);
        // шатёр
        fill(c, p, b);
        Path top = new Path();
        top.moveTo(50, 8);
        top.lineTo(60, 26);
        top.lineTo(40, 26);
        top.close();
        c.drawPath(top, p);
        fill(c, p, g);
        moon(c, p, 50, 12, 4.2f, 2f);
        // окна
        fill(c, p, U.mix(paper(theme), g, 0.3f));
        for (int i = 0; i < 3; i++) {
            rr(c, p, 48, 50 + i * 10, 52, 58 + i * 10, 1.6f);
        }
        stroke(c, p, hair(theme), 1.6f);
        line(c, p, 30, 86, 70, 86, 1.6f);
    }

    private static void sunset(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        fill(c, p, Skin.withAlpha(g, 0.35f));
        dot(c, p, 50, 54, 16f);
        fill(c, p, g);
        dot(c, p, 50, 54, 11f);
        // горизонт
        stroke(c, p, hair(theme), 1.8f);
        line(c, p, 14, 70, 86, 70, 1.8f);
        // силуэт мечети
        fill(c, p, body(theme));
        rr(c, p, 30, 70, 70, 84, 2f);
        Path dome = new Path();
        dome.moveTo(36, 70);
        dome.cubicTo(36, 56, 64, 56, 64, 70);
        dome.close();
        c.drawPath(dome, p);
        rr(c, p, 24, 60, 28, 84, 1.6f);
        rr(c, p, 72, 60, 76, 84, 1.6f);
        fill(c, p, paper(theme));
        rr(c, p, 46, 74, 54, 84, 3f);
    }

    private static void night(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        fill(c, p, g);
        moon(c, p, 56, 44, 17f, 7.5f);
        star5(c, p, 28, 30, 5f);
        star5(c, p, 74, 68, 4f);
        star5(c, p, 30, 70, 3.2f);
        stroke(c, p, hair(theme), 1.4f);
        dot(c, p, 44, 72, 1.6f);
        dot(c, p, 68, 26, 1.6f);
    }

    private static void water(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        stroke(c, p, Skin.withAlpha(g, 0.85f), 2.4f);
        Path drop = new Path();
        drop.moveTo(50, 20);
        drop.cubicTo(66, 44, 68, 56, 50, 68);
        drop.cubicTo(32, 56, 34, 44, 50, 20);
        c.drawPath(drop, p);
        fill(c, p, Skin.withAlpha(g, 0.28f));
        c.drawPath(drop, p);
        stroke(c, p, hair(theme), 1.6f);
        Path wave = new Path();
        wave.moveTo(20, 80);
        wave.quadTo(32, 74, 44, 80);
        wave.quadTo(56, 86, 68, 80);
        wave.quadTo(74, 76, 80, 80);
        c.drawPath(wave, p);
    }

    private static void star(Canvas c, Paint p, int theme) {
        int g = gold(theme);
        fill(c, p, g);
        star5(c, p, 50, 50, 26f);
        stroke(c, p, hair(theme), 1.6f);
        c.drawCircle(50, 50, 36, p);
    }

    /* ==================== Иллюстрация для экранов (View) ==================== */

    /** Простая вью-иллюстрация: используется на экранах «Ещё», онбординге и т. п. */
    public static class Art extends android.view.View {
        private int id;
        private int theme = Skin.T_PAPER;
        private boolean card = true;

        public Art(Context c, int id, int theme) {
            super(c);
            this.id = id;
            this.theme = theme;
        }

        public void setId(int id) {
            this.id = id;
            invalidate();
        }

        public void setThemed(int theme) {
            this.theme = theme;
            invalidate();
        }

        public void setCard(boolean card) {
            this.card = card;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float side = Math.min(w, h);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (card) {
                p.setColor(soft(theme));
                U.roundRect(c, 0, 0, w, h, side * 0.16f, p);
            }
            Ill.draw(c, id, (w - side) / 2f, (h - side) / 2f, side, theme);
            if (card) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, side * 0.008f));
                p.setColor(hair(theme));
                U.roundRect(c, p.getStrokeWidth() / 2f, p.getStrokeWidth() / 2f,
                        w - p.getStrokeWidth() / 2f, h - p.getStrokeWidth() / 2f, side * 0.16f, p);
            }
        }
    }

    /** Подпись под иллюстрацией (мелкий текст с золотой линией). */
    public static void captionInto(Context c, android.widget.LinearLayout parent, String text) {
        android.widget.TextView t = Ui.tv(c, text, 12f, Skin.sub(c), U.tf("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.06f);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = U.dp(c, 10);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        t.setLayoutParams(lp);
        parent.addView(t);
    }
}
