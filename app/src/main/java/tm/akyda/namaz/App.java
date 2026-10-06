package tm.akyda.namaz;

import android.app.Application;
import android.content.res.Configuration;

import java.util.Locale;

/** Точка входа. Инициализирует настройки, язык и кэш контента. */
public class App extends Application {

    private static App self;

    public static App get() {
        return self;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        P.init(this);
        Locale.setDefault(new Locale(Loc.lang()));
        // Контент читается из assets — фоновая загрузка не требуется,
        // но запускаем её заранее, чтобы экраны открывались мгновенно.
        ContentRepo.get().preload(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
