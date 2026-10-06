package tm.akyda.namaz;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Язык интерфейса: русский и туркменский (переключается в приложении). */
public final class Loc {

    public static final String RU = "ru";
    public static final String TK = "tk";

    public static String lang() {
        return P.s(P.lang, RU);
    }

    public static Locale locale() {
        return new Locale(lang());
    }

    /** Обёртка контекста с нужной локалью (работает на всех версиях, начиная с API 17). */
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

    public static void set(Context c, String code) {
        P.s(P.lang, code);
        Locale.setDefault(new Locale(code));
    }
}
