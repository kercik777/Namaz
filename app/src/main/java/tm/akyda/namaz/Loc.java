package tm.akyda.namaz;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Язык приложения: туркменский (tk). Тексты книг — на туркменском языке. */
public final class Loc {

    public static final String TK = "tk";
    public static final String RU = "tk";   // совместимость: русский больше не используется

    public static String lang() {
        return TK;
    }

    public static Locale locale() {
        return new Locale("tk", "TM");
    }

    /** Обёртка контекста с туркменской локалью (работает на всех версиях, начиная с API 17). */
    public static Context wrap(Context base) {
        Locale l = locale();
        Locale.setDefault(l);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(l);
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            cfg.setLayoutDirection(l);
        }
        return base.createConfigurationContext(cfg);
    }

    /** Оставлено для совместимости: язык всегда туркменский. */
    public static void set(Context c, String code) {
        P.ss(P.lang, TK);
        Locale.setDefault(locale());
    }
}
