package tm.akyda.namaz.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import tm.akyda.namaz.Loc;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;

/** Общая основа экранов: тема, язык, системные панели и переходы. */
public abstract class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Loc.wrap(base));
    }

    /** Тема экрана: при необходимости переопределяется наследниками. */
    protected int themeRes() {
        return Skin.dark(this) ? R.style.AppTheme_Dark : R.style.AppTheme;
    }

    @Override
    protected void onCreate(Bundle b) {
        setTheme(themeRes());
        super.onCreate(b);
        styleSystemBars();
    }

    protected void styleSystemBars() {
        Window w = getWindow();
        int bg = Skin.bg(this);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        View d = w.getDecorView();
        int flags = d.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Skin.dark(this)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (!Skin.dark(this)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        d.setSystemUiVisibility(flags);
    }

    protected FrameLayout rootView() {
        return (FrameLayout) findViewById(android.R.id.content);
    }

    protected void go(Class<?> cls) {
        go(new Intent(this, cls));
    }

    protected void go(Intent i) {
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    protected void goBack() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.fade_out);
    }

    /** Перезапуск экрана после смены темы или языка. */
    protected void restart() {
        recreate();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    protected void toast(String s) {
        U.pill(this, s);
    }
}
