package tm.akyda.namaz;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import tm.akyda.namaz.util.Prefs;

/**
 * Точка входа приложения. Здесь применяются выбранные пользователем язык и тема,
 * а также создаются долгоживущие объекты (настройки, каталог книг).
 */
public class App extends Application {

    private static App self;

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        Prefs.get(this).migrateIfNeeded();
    }

    public static App get() {
        return self;
    }

    /**
     * Возвращает контекст с подставленными языком и режимом оформления.
     * Используется в attachBaseContext каждой активности — так приложение
     * переключает язык и тему мгновенно, без перезапуска процесса.
     */
    public static Context wrap(Context base) {
        if (base == null) {
            return null;
        }
        Prefs prefs = Prefs.get(base);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        java.util.Locale locale = new java.util.Locale(prefs.getLanguage());
        cfg.setLocale(locale);
        cfg.setLayoutDirection(locale);

        String themeMode = prefs.getThemeMode();
        if (Prefs.THEME_DARK.equals(themeMode)) {
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
        } else if (Prefs.THEME_LIGHT.equals(themeMode)) {
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
        }
        return base.createConfigurationContext(cfg);
    }
}
