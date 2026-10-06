package tm.akyda.namaz.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Единое хранилище настроек пользователя (SharedPreferences).
 * Никаких персональных данных наружу не уходит — всё остаётся на устройстве.
 */
public final class Prefs {

    public static final String LANG_RU = "ru";
    public static final String LANG_TK = "tk";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String MODE_TEXT = "text";
    public static final String MODE_SCAN = "scan";

    private static final String FILE = "akyda_prefs";
    private static Prefs instance;

    private final SharedPreferences sp;

    private Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context ctx) {
        if (instance == null) {
            instance = new Prefs(ctx);
        }
        return instance;
    }

    /** Первичная настройка значений по умолчанию. */
    public void migrateIfNeeded() {
        if (!sp.contains("lang")) {
            String sys = java.util.Locale.getDefault().getLanguage();
            sp.edit().putString("lang", "tk".equals(sys) ? LANG_TK : LANG_RU).apply();
        }
        if (!sp.contains("theme")) {
            sp.edit().putString("theme", THEME_SYSTEM).apply();
        }
    }

    // ---------- язык и тема ----------
    public String getLanguage() {
        return sp.getString("lang", LANG_RU);
    }

    public void setLanguage(String lang) {
        sp.edit().putString("lang", lang).apply();
    }

    public String getThemeMode() {
        return sp.getString("theme", THEME_SYSTEM);
    }

    public void setThemeMode(String mode) {
        sp.edit().putString("theme", mode).apply();
    }

    // ---------- чтение ----------
    public int getReaderFontSize() {
        return sp.getInt("font_size", 18);
    }

    public void setReaderFontSize(int sp8) {
        sp.edit().putInt("font_size", sp8).apply();
    }

    public float getLineSpacing() {
        return sp.getFloat("line_spacing", 1.5f);
    }

    public void setLineSpacing(float value) {
        sp.edit().putFloat("line_spacing", value).apply();
    }

    public String getReadingPalette() {
        return sp.getString("palette", "paper");
    }

    public void setReadingPalette(String palette) {
        sp.edit().putString("palette", palette).apply();
    }

    public String getDefaultMode() {
        return sp.getString("default_mode", MODE_TEXT);
    }

    public void setDefaultMode(String mode) {
        sp.edit().putString("default_mode", mode).apply();
    }

    public boolean isPageSound() {
        return sp.getBoolean("page_sound", true);
    }

    public void setPageSound(boolean on) {
        sp.edit().putBoolean("page_sound", on).apply();
    }

    public boolean isVibration() {
        return sp.getBoolean("vibration", true);
    }

    public void setVibration(boolean on) {
        sp.edit().putBoolean("vibration", on).apply();
    }

    public boolean isKeepScreenOn() {
        return sp.getBoolean("keep_screen", false);
    }

    public void setKeepScreenOn(boolean on) {
        sp.edit().putBoolean("keep_screen", on).apply();
    }

    /** Последняя открытая книга и позиция — для «Продолжить чтение». */
    public void setLastRead(String bookId, int chapterIndex, int page, long time) {
        sp.edit()
                .putString("last_book", bookId)
                .putInt("last_chapter", chapterIndex)
                .putInt("last_page", page)
                .putLong("last_time", time)
                .apply();
    }

    public String getLastBook() {
        return sp.getString("last_book", null);
    }

    public int getLastChapter() {
        return sp.getInt("last_chapter", 0);
    }

    public int getLastPage() {
        return sp.getInt("last_page", 0);
    }

    public long getLastTime() {
        return sp.getLong("last_time", 0L);
    }

    // ---------- инструменты ----------
    public String getCity() {
        return sp.getString("city", "ashgabat");
    }

    public void setCity(String id) {
        sp.edit().putString("city", id).apply();
    }

    public String getCalcMethod() {
        return sp.getString("calc_method", "mwl");
    }

    public void setCalcMethod(String m) {
        sp.edit().putString("calc_method", m).apply();
    }

    public boolean isAsrHanafi() {
        return sp.getBoolean("asr_hanafi", true);
    }

    public void setAsrHanafi(boolean hanafi) {
        sp.edit().putBoolean("asr_hanafi", hanafi).apply();
    }

    public int getTasbihCount() {
        return sp.getInt("tasbih_count", 0);
    }

    public void setTasbihCount(int count) {
        sp.edit().putInt("tasbih_count", count).apply();
    }

    public int getTasbihTarget() {
        return sp.getInt("tasbih_target", 33);
    }

    public void setTasbihTarget(int target) {
        sp.edit().putInt("tasbih_target", target).apply();
    }

    public int getTasbihRounds() {
        return sp.getInt("tasbih_rounds", 0);
    }

    public void setTasbihRounds(int rounds) {
        sp.edit().putInt("tasbih_rounds", rounds).apply();
    }

    public String getTasbihDhikr() {
        return sp.getString("tasbih_dhikr", "subhanallah");
    }

    public void setTasbihDhikr(String key) {
        sp.edit().putString("tasbih_dhikr", key).apply();
    }

    public boolean isOnboarded() {
        return sp.getBoolean("onboarded", false);
    }

    public void setOnboarded(boolean done) {
        sp.edit().putBoolean("onboarded", done).apply();
    }

    // ---------- статистика ----------
    public long getReadingSeconds() {
        return sp.getLong("reading_seconds", 0L);
    }

    public void addReadingSeconds(long seconds) {
        sp.edit().putLong("reading_seconds", getReadingSeconds() + seconds).apply();
    }

    public int getReadPagesTotal() {
        return sp.getInt("read_pages", 0);
    }

    public void setReadPagesTotal(int pages) {
        sp.edit().putInt("read_pages", pages).apply();
    }

    public int getStreak() {
        return sp.getInt("streak", 0);
    }

    public void setStreak(int streak) {
        sp.edit().putInt("streak", streak).apply();
    }

    public String getLastReadDay() {
        return sp.getString("last_read_day", "");
    }

    public void setLastReadDay(String day) {
        sp.edit().putString("last_read_day", day).apply();
    }

    public void clearAll() {
        sp.edit().clear().apply();
        migrateIfNeeded();
    }
}
