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
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Ill;
import tm.akyda.namaz.P;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;

/** Первый запуск: приветствие, книги, оформление и основные возможности. */
public class OnboardActivity extends BaseActivity {

    private FrameLayout pager;
    private LinearLayout dotsRow;
    private View[] pages;
    private int index = 0;
    private TextView nextBtn;
    private View[] dots;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Skin.bg(this));

        // Верхняя строка: «Geçmek»
        LinearLayout top = Ui.row(this);
        Ui.pad(top, this, 8, 10, 14, 0);
        top.addView(new View(this), Ui.llpW(0, 1, 1f));
        TextView skip = Ui.tv(this, getString(R.string.onb_skip), 14f, Skin.sub(this), U.uiMed(this));
        skip.setPadding(U.dp(this, 14), U.dp(this, 8), U.dp(this, 8), U.dp(this, 8));
        Ui.click(skip, new Runnable() {
            @Override
            public void run() {
                finishOnboarding();
            }
        });
        top.addView(skip);
        root.addView(top, Ui.llpMatch());

        pager = Ui.frame(this);
        pages = new View[]{pageGreeting(), pageFeatures()};
        for (View p : pages) {
            pager.addView(p, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            p.setVisibility(View.GONE);
        }
        pages[0].setVisibility(View.VISIBLE);
        // вес 1 по высоте: ширина должна быть MATCH_PARENT (иначе страницы схлопнутся)
        root.addView(pager, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < 70 || Math.abs(dx) < Math.abs(dy) * 1.2f) return false;
                show(dx < 0 ? index + 1 : index - 1);
                return true;
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
        dots = new View[pages.length];
        for (int i = 0; i < pages.length; i++) {
            View d = new View(this);
            int w = i == 0 ? U.dp(this, 24) : U.dp(this, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, U.dp(this, 8));
            lp.setMargins(U.dp(this, 4), 0, U.dp(this, 4), 0);
            d.setLayoutParams(lp);
            d.setBackground(U.round(i == 0 ? Skin.accent(this) : Skin.withAlpha(Skin.sub(this), 0.35f), 5f, this));
            dots[i] = d;
            dotsRow.addView(d);
        }
        LinearLayout.LayoutParams dlp = Ui.llpMatch();
        dlp.bottomMargin = U.dp(this, 16);
        root.addView(dotsRow, dlp);

        nextBtn = Ui.buttonPrimary(this, getString(R.string.onb_next));
        nextBtn.setTextSize(16f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, U.dp(this, 54));
        nlp.setMargins(U.dp(this, 20), 0, U.dp(this, 20), U.dp(this, 24));
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

    /** Переход между страницами: лёгкий сдвиг и появление. */
    private void show(int i) {
        if (i < 0 || i >= pages.length || i == index) return;
        boolean forward = i > index;
        final View old = pages[index];
        index = i;
        final View now = pages[index];
        now.setVisibility(View.VISIBLE);
        now.setAlpha(0f);
        now.setTranslationX(U.dpf(this, forward ? 46 : -46));
        now.animate().alpha(1f).translationX(0f).setDuration(260).start();
        old.animate().alpha(0f).translationX(U.dpf(this, forward ? -46 : 46)).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        old.setVisibility(View.GONE);
                    }
                }).start();
        for (int k = 0; k < dots.length; k++) {
            final boolean active = k == index;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dots[k].getLayoutParams();
            if (lp != null) {
                lp.width = active ? U.dp(this, 24) : U.dp(this, 8);
                dots[k].setLayoutParams(lp);
            }
            dots[k].setBackground(U.round(active ? Skin.accent(this) : Skin.withAlpha(Skin.sub(this), 0.35f),
                    5f, this));
        }
        nextBtn.setText(index == pages.length - 1 ? getString(R.string.onb_start) : getString(R.string.onb_next));
    }

    private void finishOnboarding() {
        P.sb(P.onboarded, true);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    /* =============== Страницы =============== */

    private LinearLayout pageColumn;   // столбец, в который пишут страницы

    /** Каркас страницы: прокрутка на случай маленького экрана. */
    private LinearLayout newPage() {
        LinearLayout wrap = Ui.col(this);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        pageColumn = Ui.col(this);
        pageColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(pageColumn, this, 24, 10, 24, 10);
        sc.addView(pageColumn, Ui.llpMatch());
        wrap.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return wrap;
    }

    private View pageGreeting() {
        LinearLayout wrap = newPage();
        addArt(Ill.MOSQUE, 148);
        addHeadline(R.string.onb1_title);
        addBody(R.string.onb1_text);
        addFeature(Ico.BOOK, R.string.library);
        addFeature(Ico.SHIELD, R.string.privacy);
        return wrap;
    }

    private View pageFeatures() {
        LinearLayout wrap = newPage();
        addArt(Ill.CRESCENT, 148);
        addHeadline(R.string.onb3_title);
        addBody(R.string.onb3_text);
        addFeature(Ico.MARK, R.string.tab_bookmarks);
        addFeature(Ico.QUOTE, R.string.tab_quotes);
        addFeature(Ico.CLOCK, R.string.statistics);
        return wrap;
    }

    private void addArt(int id, float sizeDp) {
        Ill.Art a = new Ill.Art(this, id, Skin.T_PAPER);
        int s = U.dp(this, sizeDp);
        a.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        a.setAlpha(0f);
        a.setScaleX(0.88f);
        a.setScaleY(0.88f);
        a.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).start();
        pageColumn.addView(a);
    }

    private void addHeadline(int res) {
        TextView t = Ui.title(this, getString(res), 25f);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(U.dpf(this, 4f), 1.05f);
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.topMargin = U.dp(this, 22);
        t.setLayoutParams(lp);
        pageColumn.addView(t);
    }

    private void addBody(int res) {
        TextView t = Ui.tv(this, getString(res), 15f, Skin.sub(this), U.ui(this));
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(U.dpf(this, 7f), 1.16f);
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.topMargin = U.dp(this, 12);
        t.setLayoutParams(lp);
        pageColumn.addView(t);
    }

    private void addFeature(int icon, int textRes) {
        pageColumn.addView(featureRow(icon, getString(textRes)));
    }

    private View featureRow(int icon, String text) {
        LinearLayout r = Ui.row(this);
        r.setBackground(U.round(Skin.surface(this), 16f, this));
        Ui.pad(r, this, 14, 12, 14, 12);
        r.setLayoutParams(Ui.llpM(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0, this));
        IconView iv = new IconView(this, icon, Skin.accent(this), 20f);
        r.addView(iv);
        TextView t = Ui.tv(this, text, 14.5f, Skin.ink(this), U.tf("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(this, 12);
        r.addView(t, lp);
        return r;
    }
}
