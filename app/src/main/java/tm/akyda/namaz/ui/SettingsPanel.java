package tm.akyda.namaz.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.P;
import tm.akyda.namaz.Paginator;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/**
 * Панель настроек чтения: шрифт, размер, интервал, поля, цвет страницы,
 * режим, анимация, звук, экран и затемнение. Используется и в читалке, и в «Ещё».
 */
public final class SettingsPanel {

    public interface Changed {
        void onChanged();
    }

    public static View build(final Activity a, final Changed listener, boolean compact) {
        LinearLayout c = Ui.col(a);

        /* ---------- Шрифт ---------- */
        c.addView(label(a, a.getString(R.string.font)));
        LinearLayout fonts = Ui.row(a);
        final int font = P.i(P.readerFont, Skin.F_SERIF);
        String[] fontNames = {a.getString(R.string.font_serif), a.getString(R.string.font_sans), a.getString(R.string.font_condensed)};
        for (int i = 0; i < fontNames.length; i++) {
            final int f = i;
            TextView chip = Ui.chip(a, fontNames[i], font == i);
            chip.setTypeface(U.tf(Skin.familyName(i), Typeface.NORMAL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = U.dp(a, 8);
            chip.setLayoutParams(lp);
            Ui.click(chip, new Runnable() {
                @Override
                public void run() {
                    P.si(P.readerFont, f);
                    listener.onChanged();
                    refreshChips(chip);
                }
            });
            fonts.addView(chip);
        }
        c.addView(fonts);
        c.addView(Ui.space(a, 16));

        /* ---------- Размер текста ---------- */
        final int size = P.i(P.readerSize, 19);
        c.addView(sliderRow(a, a.getString(R.string.text_size), String.valueOf(size),
                (size - 14) / 12f, new Widgets.OnSlide() {
                    @Override
                    public void onSlide(float value, boolean fromUser) {
                        int v = 14 + Math.round(value * 12);
                        P.si(P.readerSize, v);
                        listener.onChanged();
                    }
                }));

        /* ---------- Интерлиньяж ---------- */
        final int line = P.i(P.readerLine, 145);
        c.addView(sliderRow(a, a.getString(R.string.line_spacing), String.format(java.util.Locale.US, "%.2f", line / 100f),
                (line - 120) / 80f, new Widgets.OnSlide() {
                    @Override
                    public void onSlide(float value, boolean fromUser) {
                        P.si(P.readerLine, 120 + Math.round(value * 80));
                        listener.onChanged();
                    }
                }));

        /* ---------- Поля ---------- */
        final int margin = P.i(P.readerMargin, 20);
        c.addView(sliderRow(a, a.getString(R.string.page_margins), String.valueOf(margin),
                (margin - 12) / 28f, new Widgets.OnSlide() {
                    @Override
                    public void onSlide(float value, boolean fromUser) {
                        P.si(P.readerMargin, 12 + Math.round(value * 28));
                        listener.onChanged();
                    }
                }));
        c.addView(Ui.space(a, 10));

        /* ---------- Цвет страницы ---------- */
        c.addView(label(a, a.getString(R.string.page_theme)));
        LinearLayout themes = Ui.row(a);
        final int theme = P.i(P.readerTheme, Skin.T_PAPER);
        for (int i = 0; i < Skin.T_NAMES.length; i++) {
            final int t = i;
            LinearLayout sw = Ui.col(a);
            sw.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            sw.setLayoutParams(slp);

            FrameLayout dot = Ui.frame(a);
            int d = U.dp(a, 42);
            dot.setLayoutParams(new LinearLayout.LayoutParams(d, d));
            dot.setBackground(U.round(Skin.paper(i), 21f, a));
            if (theme == i) {
                FrameLayout ring = Ui.frame(a);
                ring.setBackground(U.strokeRound(0x00000000, Skin.accent(a), 24f, 2f, a));
                int r = U.dp(a, 52);
                ring.setLayoutParams(new LinearLayout.LayoutParams(r, r));
                sw.addView(ring, 0);
                ring.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams dlp = Ui.flp(d, d, Gravity.CENTER);
                dot.setLayoutParams(dlp);
                ring.addView(dot);
                sw.setPadding(0, U.dp(a, 5), 0, U.dp(a, 5));
            } else {
                sw.addView(dot);
            }
            TextView tn = Ui.tv(a, a.getString(Skin.T_NAMES[i]), 10.5f, Skin.sub(a), U.ui(a));
            LinearLayout.LayoutParams tlp = Ui.llpMatch();
            tlp.topMargin = U.dp(a, 6);
            sw.addView(tn, tlp);
            Ui.click(sw, new Runnable() {
                @Override
                public void run() {
                    P.si(P.readerTheme, t);
                    listener.onChanged();
                }
            });
            themes.addView(sw);
        }
        c.addView(themes);
        c.addView(Ui.space(a, 16));

        /* ---------- Режим чтения ---------- */
        c.addView(label(a, a.getString(R.string.reading_mode)));
        LinearLayout modes = Ui.row(a);
        final int mode = P.i(P.readerMode, 0);
        String[] modeNames = {a.getString(R.string.mode_pages), a.getString(R.string.mode_scroll)};
        for (int i = 0; i < 2; i++) {
            final int m = i;
            TextView chip = Ui.chip(a, modeNames[i], mode == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = U.dp(a, 8);
            chip.setLayoutParams(lp);
            Ui.click(chip, new Runnable() {
                @Override
                public void run() {
                    P.si(P.readerMode, m);
                    listener.onChanged();
                    refreshChips(chip);
                }
            });
            modes.addView(chip);
        }
        c.addView(modes);
        c.addView(Ui.space(a, 16));

        /* ---------- Анимация перелистывания ---------- */
        c.addView(label(a, a.getString(R.string.page_animation)));
        LinearLayout anims = Ui.row(a);
        final int anim = P.i(P.readerAnim, 0);
        String[] animNames = {a.getString(R.string.anim_slide), a.getString(R.string.anim_fade), a.getString(R.string.anim_none)};
        for (int i = 0; i < 3; i++) {
            final int an = i;
            TextView chip = Ui.chip(a, animNames[i], anim == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = U.dp(a, 8);
            chip.setLayoutParams(lp);
            Ui.click(chip, new Runnable() {
                @Override
                public void run() {
                    P.si(P.readerAnim, an);
                    listener.onChanged();
                    refreshChips(chip);
                }
            });
            anims.addView(chip);
        }
        c.addView(anims);
        c.addView(Ui.space(a, 14));

        /* ---------- Переключатели ---------- */
        c.addView(toggle(a, Ico.HEADPHONES, a.getString(R.string.page_sound), P.b(P.readerSound, true),
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }, P.readerSound));
        c.addView(toggle(a, Ico.SUN, a.getString(R.string.keep_screen_on), P.b(P.readerKeepOn, false),
                new Runnable() {
                    @Override
                    public void run() {
                    }
                }, P.readerKeepOn));

        return c;
    }

    private static void refreshChips(View anyChip) {
        if (anyChip.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) anyChip.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    boolean sel = v == anyChip;
                    tv.setBackground(sel ? U.round(Skin.accent(tv.getContext()), 20f, tv.getContext())
                            : U.strokeRound(Skin.surface2(tv.getContext()), Skin.line(tv.getContext()), 20f, 1f, tv.getContext()));
                    tv.setTextColor(sel ? 0xFF10231B : Skin.ink(tv.getContext()));
                }
            }
        }
    }

    private static View label(Activity a, String text) {
        TextView t = Ui.label(a, text.toUpperCase());
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.bottomMargin = U.dp(a, 9);
        lp.leftMargin = U.dp(a, 2);
        t.setLayoutParams(lp);
        return t;
    }

    private static View sliderRow(final Activity a, String title, final String value, float initial,
                                  final Widgets.OnSlide slide) {
        LinearLayout c = Ui.col(a);
        LinearLayout.LayoutParams clp = Ui.llpMatch();
        clp.bottomMargin = U.dp(a, 12);
        c.setLayoutParams(clp);

        LinearLayout head = Ui.row(a);
        TextView t = Ui.tv(a, title, 14f, Skin.ink(a), U.uiMed(a));
        head.addView(t, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView v = Ui.tv(a, value, 13f, Skin.accent(a), U.uiMed(a));
        head.addView(v);
        c.addView(head);

        Widgets.Slider s = new Widgets.Slider(a);
        s.setColors(Skin.withAlpha(Skin.sub(a), 0.25f), Skin.accent(a), Skin.surface(a));
        s.set(Math.max(0f, Math.min(1f, initial)));
        LinearLayout.LayoutParams slp = Ui.llpMatchH(U.dp(a, 34));
        slp.topMargin = U.dp(a, 4);
        s.setLayoutParams(slp);
        s.setListener(new Widgets.OnSlide() {
            @Override
            public void onSlide(float value01, boolean fromUser) {
                slide.onSlide(value01, fromUser);
                if (!fromUser) {
                    v.setText(currentValue(a, title));
                }
            }
        });
        c.addView(s);
        return c;
    }

    private static String currentValue(Activity a, String title) {
        if (title.equals(a.getString(R.string.text_size))) return String.valueOf(P.i(P.readerSize, 19));
        if (title.equals(a.getString(R.string.line_spacing)))
            return String.format(java.util.Locale.US, "%.2f", P.i(P.readerLine, 145) / 100f);
        return String.valueOf(P.i(P.readerMargin, 20));
    }

    private static View toggle(final Activity a, int icon, final String text, boolean initial,
                               Runnable unused, final String key) {
        final boolean[] state = {initial};
        LinearLayout r = Ui.row(a);
        Ui.pad(r, a, 4, 13, 4, 13);
        r.addView(new IconView(a, icon, Skin.accent(a), 19f));
        TextView t = Ui.tv(a, text, 14.5f, Skin.ink(a), U.ui(a));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(a, 13);
        r.addView(t, lp);

        final SwitchLike sw = new SwitchLike(a, state[0]);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(U.dp(a, 46), U.dp(a, 26));
        sw.setLayoutParams(slp);
        r.addView(sw);
        Ui.click(r, new Runnable() {
            @Override
            public void run() {
                state[0] = !state[0];
                P.sb(key, state[0]);
                sw.setChecked(state[0]);
            }
        });
        return r;
    }

    /** Мини-переключатель, нарисованный кодом. */
    public static class SwitchLike extends View {
        private boolean checked;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        public SwitchLike(android.content.Context c, boolean checked) {
            super(c);
            this.checked = checked;
        }

        public void setChecked(boolean v) {
            checked = v;
            invalidate();
        }

        public boolean isChecked() {
            return checked;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(checked ? Skin.accent(getContext()) : Skin.withAlpha(Skin.sub(getContext()), 0.35f));
            U.roundRect(c, 0, 0, w, h, h / 2f, p);
            float r = h / 2f - U.dpf(getContext(), 3f);
            float cx = checked ? w - h / 2f : h / 2f;
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, h / 2f, r, p);
        }
    }

    /** Параметры вёрстки из настроек. */
    public static Paginator.Opt opt(Activity a, int width, int height) {
        Paginator.Opt o = new Paginator.Opt();
        o.theme = P.i(P.readerTheme, Skin.T_PAPER);
        o.family = Skin.familyName(P.i(P.readerFont, Skin.F_SERIF));
        o.textSize = U.sp(a, P.i(P.readerSize, 19));
        o.lineSpacing = P.i(P.readerLine, 145) / 100f;
        o.marginLeft = U.dp(a, P.i(P.readerMargin, 20));
        o.marginTop = U.dp(a, 46);
        o.width = Math.max(100, width - o.marginLeft * 2);
        o.height = Math.max(100, height - o.marginTop - U.dp(a, 44));
        o.justify = true;
        return o;
    }
}
