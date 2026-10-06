package tm.akyda.namaz;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Мини-конструктор интерфейса: аккуратные отступы и типографика по единой сетке
 * (8 dp), чтобы всё выглядело дорого и ровно.
 */
public final class Ui {

    /* ---------- Контейнеры ---------- */

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c, float padDp) {
        LinearLayout l = col(c);
        int p = U.dp(c, padDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout colPad(Context c, float l, float t, float r, float b) {
        LinearLayout v = col(c);
        v.setPadding(U.dp(c, l), U.dp(c, t), U.dp(c, r), U.dp(c, b));
        return v;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static FrameLayout frame(Context c) {
        return new FrameLayout(c);
    }

    public static View space(Context c, float hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, U.dp(c, hDp)));
        return v;
    }

    public static View spaceW(Context c, float wDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, wDp), 1));
        return v;
    }

    public static View hline(Context c, int color, float hDp) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, U.dp(c, hDp)));
        v.setLayoutParams(lp);
        v.setBackgroundColor(color);
        return v;
    }

    /* ---------- Текст ---------- */

    public static TextView tv(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setLineSpacing(U.dpf(c, 3f), 1f);
        return t;
    }

    public static TextView title(Context c, CharSequence s, float sp) {
        TextView t = tv(c, s, sp, Skin.ink(c), U.tf("serif", Typeface.BOLD));
        t.setLetterSpacing(0.01f);
        return t;
    }

    public static TextView semi(Context c, CharSequence s, float sp) {
        return tv(c, s, sp, Skin.ink(c), U.uiMed(c));
    }

    public static TextView body(Context c, CharSequence s, float sp) {
        return tv(c, s, sp, Skin.ink(c), U.ui(c));
    }

    public static TextView sub(Context c, CharSequence s, float sp) {
        return tv(c, s, sp, Skin.sub(c), U.ui(c));
    }

    public static TextView label(Context c, CharSequence s) {
        TextView t = tv(c, s, 11.5f, Skin.sub(c), U.uiMed(c));
        t.setAllCaps(false);
        t.setLetterSpacing(0.14f);
        return t;
    }

    public static TextView gold(Context c, CharSequence s, float sp) {
        return tv(c, s, sp, Skin.accent(c), U.uiMed(c));
    }

    /* ---------- Параметры ---------- */

    public static LinearLayout.LayoutParams llp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams llp(Context c, float wDp, float hDp) {
        return new LinearLayout.LayoutParams(U.dp(c, wDp), U.dp(c, hDp));
    }

    public static LinearLayout.LayoutParams llpW(int w, int h, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.weight = weight;
        return lp;
    }

    public static LinearLayout.LayoutParams llpM(int w, int h, float l, float t, float r, float b, Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(U.dp(c, l), U.dp(c, t), U.dp(c, r), U.dp(c, b));
        return lp;
    }

    public static LinearLayout.LayoutParams llpMatch() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams llpMatchH(int h) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
    }

    public static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = gravity;
        return lp;
    }

    /** Делает элемент нажимаемым (лёгкая вибрация + ripple нет — фон задаётся вызывающим). */
    public static void click(View v, final Runnable action) {
        U.click(v, action);
    }

    public static void pad(View v, Context c, float l, float t, float r, float b) {
        v.setPadding(U.dp(c, l), U.dp(c, t), U.dp(c, r), U.dp(c, b));
    }

    public static void padH(View v, Context c, float h) {
        pad(v, c, h, 0, h, 0);
    }

    public static void margin(View v, Context c, float l, float t, float r, float b) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(U.dp(c, l), U.dp(c, t), U.dp(c, r), U.dp(c, b));
            v.setLayoutParams(lp);
        }
    }

    /* ---------- Карточки и кнопки ---------- */

    /** Карточка: скруглённая поверхность с мягкой тенью. */
    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(U.round(Skin.surface(c), 20f, c));
        U.shadow(l, 3f);
        return l;
    }

    public static TextView button(Context c, CharSequence text, int bg, int fg) {
        TextView t = tv(c, text, 15f, fg, U.uiMed(c));
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.round(bg, 26f, c));
        t.setPadding(U.dp(c, 22), U.dp(c, 13), U.dp(c, 22), U.dp(c, 13));
        return t;
    }

    public static TextView buttonGold(Context c, CharSequence text) {
        TextView t = button(c, text, Color.TRANSPARENT, Skin.primary(c));
        t.setBackground(U.strokeRound(Skin.withAlpha(Skin.accent(c), 0.14f), Skin.withAlpha(Skin.accent(c), 0.55f), 26f, 1f, c));
        return t;
    }

    public static TextView buttonPrimary(Context c, CharSequence text) {
        return button(c, text, Skin.primary(c), 0xFFF6F2E8);
    }

    /** Чип-переключатель. */
    public static TextView chip(Context c, CharSequence text, boolean selected) {
        TextView t = tv(c, text, 13.5f, selected ? 0xFF10231B : Skin.ink(c),
                selected ? U.uiMed(c) : U.ui(c));
        t.setGravity(Gravity.CENTER);
        if (selected) {
            t.setBackground(U.round(Skin.accent(c), 20f, c));
        } else {
            t.setBackground(U.strokeRound(Skin.surface2(c), Skin.line(c), 20f, 1f, c));
        }
        t.setPadding(U.dp(c, 16), U.dp(c, 9), U.dp(c, 16), U.dp(c, 9));
        return t;
    }

    /* ---------- Иконки (векторные, только системные drawable) ---------- */

    public static ImageView icon(Context c, int resId, int color, float sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(resId);
        iv.setColorFilter(color);
        float s = U.dpf(c, sizeDp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Math.round(s), Math.round(s)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    public static ImageView iconFrame(Context c, int resId, int color, float sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(resId);
        iv.setColorFilter(color);
        float s = U.dpf(c, sizeDp);
        iv.setLayoutParams(new FrameLayout.LayoutParams(Math.round(s), Math.round(s), Gravity.CENTER));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    /** Круглая «кнопка-иконка» с подложкой. iconCode — код из класса {@link Ico}. */
    public static FrameLayout iconButton(Context c, int iconCode, int tint, int bg, float sizeDp) {
        FrameLayout f = new FrameLayout(c);
        int s = U.dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        f.setBackground(U.round(bg, sizeDp / 2f, c));
        tm.akyda.namaz.IconView iv = new tm.akyda.namaz.IconView(c, iconCode, tint, sizeDp * 0.46f);
        iv.setLayoutParams(new FrameLayout.LayoutParams(U.dp(c, sizeDp * 0.46f),
                U.dp(c, sizeDp * 0.46f), Gravity.CENTER));
        f.addView(iv);
        return f;
    }

    /** Кнопка с настоящей drawable-иконкой (например, логотипом приложения). */
    public static FrameLayout drawableButton(Context c, int resId, int tint, int bg, float sizeDp) {
        FrameLayout f = new FrameLayout(c);
        int s = U.dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        f.setBackground(U.round(bg, sizeDp / 2f, c));
        f.addView(iconFrame(c, resId, tint, sizeDp * 0.44f));
        return f;
    }

    public static TextView pill(Context c, CharSequence text, int bg, int fg) {
        TextView t = tv(c, text, 12f, fg, U.uiMed(c));
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.round(bg, 12f, c));
        t.setPadding(U.dp(c, 10), U.dp(c, 5), U.dp(c, 10), U.dp(c, 5));
        return t;
    }

    public static int tvSize(Context c, float sp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                c.getResources().getDisplayMetrics()));
    }
}
