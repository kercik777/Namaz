package tm.akyda.namaz;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Единая дизайн-система: палитры приложения и темы чтения.
 * Никаких «дешёвых» цветов — только глубокий изумруд, золото, бумага и графит.
 */
public final class Skin {

    /* ================= ТЕМА ПРИЛОЖЕНИЯ ================= */
    public static final int MODE_LIGHT = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_SYSTEM = 2;

    public static boolean dark(Context c) {
        int mode = P.i(P.appTheme, MODE_SYSTEM);
        if (mode == MODE_DARK) return true;
        if (mode == MODE_LIGHT) return false;
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    /* ---------- Цвета интерфейса ---------- */
    private static final int L_BG = 0xFFF7F3EA;
    private static final int L_SURFACE = 0xFFFFFFFF;
    private static final int L_SURFACE_2 = 0xFFF1EBDF;
    private static final int L_INK = 0xFF16211C;
    private static final int L_SUB = 0xFF6C7A73;
    private static final int L_LINE = 0x1A0F2B21;
    private static final int L_PRIMARY = 0xFF0B3B2C;
    private static final int L_ACCENT = 0xFFB8912F;

    private static final int D_BG = 0xFF0B1210;
    private static final int D_SURFACE = 0xFF14201B;
    private static final int D_SURFACE_2 = 0xFF1B2A24;
    private static final int D_INK = 0xFFEDE8DC;
    private static final int D_SUB = 0xFF98A69F;
    private static final int D_LINE = 0x1FFFFFFF;
    private static final int D_PRIMARY = 0xFF1E6B4F;
    private static final int D_ACCENT = 0xFFE0BE6C;

    public static int bg(Context c) { return dark(c) ? D_BG : L_BG; }

    public static int surface(Context c) { return dark(c) ? D_SURFACE : L_SURFACE; }

    public static int surface2(Context c) { return dark(c) ? D_SURFACE_2 : L_SURFACE_2; }

    public static int ink(Context c) { return dark(c) ? D_INK : L_INK; }

    public static int sub(Context c) { return dark(c) ? D_SUB : L_SUB; }

    public static int line(Context c) { return dark(c) ? D_LINE : L_LINE; }

    public static int primary(Context c) { return dark(c) ? D_PRIMARY : L_PRIMARY; }

    public static int accent(Context c) { return dark(c) ? D_ACCENT : L_ACCENT; }

    public static int goldGradStart() { return 0xFFF0D9A0; }

    public static int goldGradEnd() { return 0xFFB9902F; }

    public static int withAlpha(int color, float a) {
        return Color.argb((int) (255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    /* ================= ТЕМЫ ЧТЕНИЯ ================= */
    public static final int T_PAPER = 0;
    public static final int T_SEPIA = 1;
    public static final int T_EMERALD = 2;
    public static final int T_DARK = 3;
    public static final int T_NIGHT = 4;

    public static final int[] T_NAMES = {
            R.string.theme_paper, R.string.theme_sepia, R.string.theme_emerald,
            R.string.theme_dark, R.string.theme_night
    };

    /** Цвет «бумаги» страницы. */
    public static int paper(int t) {
        switch (t) {
            case T_SEPIA: return 0xFFF2E5C9;
            case T_EMERALD: return 0xFF0C3527;
            case T_DARK: return 0xFF121A17;
            case T_NIGHT: return 0xFF000000;
            default: return 0xFFFBF6EB;
        }
    }

    /** Основной цвет текста на странице. */
    public static int paperInk(int t) {
        switch (t) {
            case T_SEPIA: return 0xFF33291A;
            case T_EMERALD: return 0xFFF2EDDD;
            case T_DARK: return 0xFFD8D3C6;
            case T_NIGHT: return 0xFF9C6B3A;
            default: return 0xFF1D2A24;
        }
    }

    /** Приглушённый цвет (подписи, номера страниц, сноски). */
    public static int paperSub(int t) {
        switch (t) {
            case T_SEPIA: return 0xFF8A744C;
            case T_EMERALD: return 0xFF9DBFA9;
            case T_DARK: return 0xFF7C8A83;
            case T_NIGHT: return 0xFF7A5430;
            default: return 0xFF7C8A83;
        }
    }

    /** Акцент темы чтения (заголовки, линии, буквица). */
    public static int paperAccent(int t) {
        switch (t) {
            case T_SEPIA: return 0xFFA9762A;
            case T_EMERALD: return 0xFFDCC07A;
            case T_DARK: return 0xFFE0BE6C;
            case T_NIGHT: return 0xFFC08A44;
            default: return 0xFF0F5A41;
        }
    }

    /** Тонкая линия-разделитель на странице. */
    public static int paperLine(int t) {
        return withAlpha(paperInk(t), 0.14f);
    }

    /** Цвет фона вокруг страницы (рамка). */
    public static int readerFrame(int t) {
        switch (t) {
            case T_SEPIA: return 0xFFE6D6B4;
            case T_EMERALD: return 0xFF07231A;
            case T_DARK: return 0xFF060A09;
            case T_NIGHT: return 0xFF000000;
            default: return 0xFFEDE5D6;
        }
    }

    public static boolean paperIsDark(int t) {
        return t == T_EMERALD || t == T_DARK || t == T_NIGHT;
    }

    /* ================= ТИПОГРАФИКА (только системные шрифты) ================= */

    public static final int F_SERIF = 0;
    public static final int F_SANS = 1;
    public static final int F_CONDENSED = 2;

    public static String familyName(int f) {
        switch (f) {
            case F_SANS: return "sans-serif";
            case F_CONDENSED: return "sans-serif-condensed";
            default: return "serif";
        }
    }
}
