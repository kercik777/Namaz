package tm.akyda.namaz.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;

import tm.akyda.namaz.App;
import tm.akyda.namaz.util.Prefs;

/**
 * Общая основа всех экранов: язык, оформление системных панелей,
 * аккуратная работа с вырезами экрана и жестовой навигацией.
 */
public class BaseActivity extends Activity {

    protected Prefs prefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(App.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        applySystemBars();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    protected boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    protected void applySystemBars() {
        Window window = getWindow();
        window.addFlags(Window.FEATURE_ACTIVITY_TRANSITIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(Window.FEATURE_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        setLightStatusBar(!isNight());
    }

    protected void setLightStatusBar(boolean light) {
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (light) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (light) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    /** Добавляет отступ сверху (строка состояния, вырез). */
    protected void padTop(final View target) {
        if (target == null) {
            return;
        }
        final int base = target.getPaddingTop();
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.displayCutout());
                    top = bars.top;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    top = insets.getSystemWindowInsetTop();
                }
                v.setPadding(v.getPaddingLeft(), base + top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    /** Добавляет отступ снизу (панель навигации). */
    protected void padBottom(final View target) {
        if (target == null) {
            return;
        }
        final int base = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + bottom);
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    protected void padTopAndBottom(final View target) {
        if (target == null) {
            return;
        }
        final int baseTop = target.getPaddingTop();
        final int baseBottom = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = 0;
                int bottom = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    top = insets.getInsets(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.displayCutout()).top;
                    bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(v.getPaddingLeft(), baseTop + top, v.getPaddingRight(), baseBottom + bottom);
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    // ---------- удобные вычисления ----------
    public int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    public int sp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics()));
    }

    public int color(int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(res);
        }
        return getResources().getColor(res);
    }

    protected int attrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return tv.data != 0 ? tv.data : Color.BLACK;
    }

    /** Мягкое появление содержимого при открытии экрана. */
    protected void enterAnimation(View root) {
        if (root == null) {
            return;
        }
        root.setAlpha(0f);
        root.setTranslationY(dp(12));
        root.animate().alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    protected static ViewGroup.LayoutParams matchParams() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
