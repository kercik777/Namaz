package tm.akyda.namaz;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки и пользовательские данные приложения.
 * Всё хранится локально в SharedPreferences — ни одного сетевого запроса,
 * ни одного разрешения.
 */
public final class P {

    private static SharedPreferences sp;

    /* ---------- Ключи ---------- */
    public static final String appTheme = "app_theme";          // 0 светлая, 1 тёмная, 2 системная
    public static final String lang = "lang";                   // "ru" | "tk"
    public static final String onboarded = "onboarded";         // пройден ли первый запуск
    public static final String readerTheme = "reader_theme";    // 0..4
    public static final String readerFont = "reader_font";      // 0 serif, 1 sans, 2 condensed
    public static final String readerSize = "reader_size";      // sp в тексте
    public static final String readerLine = "reader_line";      // множитель интервала
    public static final String readerMargin = "reader_margin";  // отступ страницы, dp
    public static final String readerSound = "reader_sound";    // звук перелистывания
    public static final String readerKeepOn = "reader_keep_on";  // не гасить экран
    public static final String readerBrightness = "reader_dim";   // затемнение поверх текста, %
    public static final String readerFullscreen = "reader_fs";    // прятать панели

    public static void init(Context c) {
        if (sp == null) {
            sp = c.getApplicationContext().getSharedPreferences("akyda_we_namaz", Context.MODE_PRIVATE);
        }
    }

    public static SharedPreferences s() {
        return sp;
    }

    public static int i(String k, int def) {
        return sp.getInt(k, def);
    }

    public static void si(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    public static String s(String k, String def) {
        return sp.getString(k, def);
    }

    public static void ss(String k, String v) {
        sp.edit().putString(k, v).apply();
    }

    public static boolean b(String k, boolean def) {
        return sp.getBoolean(k, def);
    }

    public static void sb(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
    }

    public static long l(String k, long def) {
        return sp.getLong(k, def);
    }

    public static void sl(String k, long v) {
        sp.edit().putLong(k, v).apply();
    }

    public static void del(String k) {
        sp.edit().remove(k).apply();
    }
}
