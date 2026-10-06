package tm.akyda.namaz.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.P;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/** Первый запуск: приветствие, выбор языка и оформления. */
public class OnboardActivity extends BaseActivity {

    private FrameLayout pager;
    private LinearLayout dotsRow;
    private View[] pages;
    private int index = 0;
    private TextView nextBtn;
    private float downX;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Skin.bg(this));

        // Верхняя строка: «Пропустить»
        LinearLayout top = Ui.row(this);
        Ui.pad(top, this, 20, 14, 20, 0);
        TextView skip = Ui.tv(this, getString(R.string.onb_skip), 14f, Skin.sub(this), U.uiMed(this));
        top.addView(skip, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Ui.click(skip, new Runnable() {
            @Override
            public void run() {
                finishOnboarding();
            }
        });
        root.addView(top, Ui.llpMatch());

        pager = Ui.frame(this);
        pages = new View[]{page1(), page2(), page3()};
        pager.addView(pages[0], Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        pager.addView(pages[1], Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        pager.addView(pages[2], Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        pages[1].setVisibility(View.GONE);
        pages[2].setVisibility(View.GONE);
        root.addView(pager, Ui.llpW(0, 0, 1f));

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                if (e1.getX() - e2.getX() > 60) {
                    show(index + 1);
                    return true;
                }
                if (e2.getX() - e1.getX() > 60) {
                    show(index - 1);
                    return true;
                }
                return false;
            }
        });
        pager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                gd.onTouchEvent(e);
                return true;
            }
        });

        dotsRow = Ui.row(this);
        dotsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = Ui.llpMatch();
        dlp.bottomMargin = U.dp(this, 18);
        root.addView(dotsRow, dlp);
        buildDots();

        nextBtn = Ui.buttonPrimary(this, getString(R.string.onb_next));
        nextBtn.setTextSize(16f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, U.dp(this, 54));
        nlp.setMargins(U.dp(this, 20), 0, U.dp(this, 20), U.dp(this, 26));
        nextBtn.setLayoutParams(nlp);
        Ui.click(nextBtn, new Runnable() {
            @Override
            public void run() {
                if (index == pages.length - 1) finishOnboarding();
                else show(index + 1);
            }
        });
        root.addView(nextBtn);

        setContentView(root);
    }

    private void buildDots() {
        dotsRow.removeAllViews();
        for (int i = 0; i < pages.length; i++) {
            View d = new View(this);
            int w = i == index ? U.dp(this, 22) : U.dp(this, 7);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, U.dp(this, 7));
            lp.setMargins(U.dp(this, 4), 0, U.dp(this, 4), 0);
            d.setLayoutParams(lp);
            d.setBackground(U.round(i == index ? Skin.accent(this) : Skin.withAlpha(Skin.sub(this), 0.35f), 4f, this));
            dotsRow.addView(d);
        }
    }

    private void show(int i) {
        if (i < 0 || i >= pages.length || i == index) return;
        final View old = pages[index];
        index = i;
        final View now = pages[index];
        boolean forward = true;
        now.setVisibility(View.VISIBLE);
        now.setAlpha(0f);
        now.setTranslationX(U.dpf(this, forward ? 60 : -60));
        now.animate().alpha(1f).translationX(0f).setDuration(240).start();
        old.animate().alpha(0f).translationX(U.dpf(this, -60)).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        old.setVisibility(View.GONE);
                    }
                }).start();
        buildDots();
        nextBtn.setText(index == pages.length - 1 ? getString(R.string.onb_start) : getString(R.string.onb_next));
    }

    private void finishOnboarding() {
        P.b(P.onboarded, true);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    /* =============== Страницы =============== */

    private View pageWrap(View... content) {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(c, this, 28, 12, 28, 12);
        for (View v : content) c.addView(v);
        return c;
    }

    private TextView headline(int res) {
        TextView t = Ui.title(this, getString(res), 26f);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(U.dpf(this, 4f), 1.05f);
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.topMargin = U.dp(this, 18);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView body(int res) {
        TextView t = Ui.tv(this, getString(res), 15f, Skin.sub(this), U.ui(this));
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(U.dpf(this, 6f), 1.15f);
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.topMargin = U.dp(this, 14);
        t.setLayoutParams(lp);
        return t;
    }

    private View page1() {
        Widgets.Logo logo = new Widgets.Logo(this);
        int s = U.dp(this, 168);
        logo.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        logo.setColors(0xFFF6F2E8, 0xFFE0BE6C);
        logo.play(1200);
        return pageWrap(logo, headline(R.string.onb1_title), body(R.string.onb1_text));
    }

    private View page2() {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(c, this, 28, 12, 28, 12);

        IconView ico = new IconView(this, Ico.PALETTE, Skin.accent(this), 54f);
        ico.setLayoutParams(new LinearLayout.LayoutParams(U.dp(this, 54), U.dp(this, 54)));
        c.addView(ico);
        c.addView(headline(R.string.onb2_title));
        c.addView(body(R.string.onb2_text));

        c.addView(Ui.space(this, 26));
        c.addView(sectionLabel(getString(R.string.interface_language)));
        LinearLayout langRow = Ui.row(this);
        langRow.setGravity(Gravity.CENTER);
        final TextView ru = Ui.chip(this, getString(R.string.lang_ru), Loc.RU.equals(Loc.lang()));
        final TextView tk = Ui.chip(this, getString(R.string.lang_tk), Loc.TK.equals(Loc.lang()));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(U.dp(this, 6), 0, U.dp(this, 6), 0);
        ru.setLayoutParams(clp);
        tk.setLayoutParams(clp);
        langRow.addView(ru);
        langRow.addView(tk);
        c.addView(langRow);

        Ui.click(ru, new Runnable() {
            @Override
            public void run() {
                Loc.set(OnboardActivity.this, Loc.RU);
                restart();
            }
        });
        Ui.click(tk, new Runnable() {
            @Override
            public void run() {
                Loc.set(OnboardActivity.this, Loc.TK);
                restart();
            }
        });

        c.addView(Ui.space(this, 24));
        c.addView(sectionLabel(getString(R.string.app_theme)));
        LinearLayout themeRow = Ui.row(this);
        themeRow.setGravity(Gravity.CENTER);
        int mode = P.i(P.appTheme, Skin.MODE_SYSTEM);
        final TextView[] chips = {
                Ui.chip(this, getString(R.string.theme_light), mode == Skin.MODE_LIGHT),
                Ui.chip(this, getString(R.string.theme_dark), mode == Skin.MODE_DARK),
                Ui.chip(this, getString(R.string.theme_system), mode == Skin.MODE_SYSTEM)
        };
        for (int i = 0; i < chips.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(U.dp(this, 5), 0, U.dp(this, 5), 0);
            chips[i].setLayoutParams(lp);
            final int m = i;
            Ui.click(chips[i], new Runnable() {
                @Override
                public void run() {
                    P.i(P.appTheme, m);
                    restart();
                }
            });
            themeRow.addView(chips[i]);
        }
        c.addView(themeRow);
        return c;
    }

    private View page3() {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(c, this, 28, 12, 28, 12);

        IconView ico = new IconView(this, Ico.BOOK, Skin.accent(this), 54f);
        ico.setLayoutParams(new LinearLayout.LayoutParams(U.dp(this, 54), U.dp(this, 54)));
        c.addView(ico);
        c.addView(headline(R.string.onb3_title));
        c.addView(body(R.string.onb3_text));

        c.addView(Ui.space(this, 22));
        c.addView(featureRow(Ico.SEARCH, getString(R.string.search_placeholder_title)));
        c.addView(featureRow(Ico.MARK, getString(R.string.tab_bookmarks)));
        c.addView(featureRow(Ico.QUOTE, getString(R.string.tab_quotes)));
        c.addView(featureRow(Ico.SHIELD, getString(R.string.privacy)));
        return c;
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.label(this, s.toUpperCase());
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.bottomMargin = U.dp(this, 10);
        t.setLayoutParams(lp);
        return t;
    }

    private View featureRow(int icon, String text) {
        LinearLayout r = Ui.row(this);
        r.setBackground(U.round(Skin.surface(this), 16f, this));
        Ui.pad(r, this, 14, 12, 14, 12);
        r.setLayoutParams(Ui.llpM(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 5, 0, 5, this));
        IconView iv = new IconView(this, icon, Skin.accent(this), 22f);
        r.addView(iv);
        TextView t = Ui.tv(this, text, 14.5f, Skin.ink(this), U.uiMed(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(this, 12);
        r.addView(t, lp);
        return r;
    }
}
