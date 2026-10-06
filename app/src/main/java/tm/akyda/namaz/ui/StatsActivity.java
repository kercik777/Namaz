package tm.akyda.namaz.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Book;
import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/** Статистика чтения. */
public class StatsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Skin.bg(this));

        LinearLayout top = Ui.row(this);
        Ui.pad(top, this, 10, 8, 18, 8);
        FrameLayout back = Ui.frame(this);
        int s = U.dp(this, 40);
        back.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        back.addView(new IconView(this, Ico.BACK, Skin.ink(this), 21f), Ui.flp(U.dp(this, 21), U.dp(this, 21), Gravity.CENTER));
        Ui.click(back, new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        });
        top.addView(back);
        TextView title = Ui.title(this, getString(R.string.statistics), 20f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 8);
        top.addView(title, tlp);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        LinearLayout content = Ui.col(this);
        Ui.pad(content, this, 18, 8, 18, 28);

        Lib.Stats st = Lib.get().stats();

        // Большие показатели
        LinearLayout row1 = Ui.row(this);
        row1.setWeightSum(2f);
        row1.addView(bigTile(Ico.PAGE, String.valueOf(st.pages),
                getResources().getQuantityString(R.plurals.pages_read, st.pages, st.pages), 0.5f));
        row1.addView(bigTile(Ico.CLOCK, String.valueOf(st.minutes),
                getResources().getQuantityString(R.plurals.minutes_read, (int) st.minutes, st.minutes), 0.5f));
        content.addView(row1);
        content.addView(Ui.space(this, 12));
        LinearLayout row2 = Ui.row(this);
        row2.setWeightSum(2f);
        row2.addView(bigTile(Ico.STAR, String.valueOf(st.streak),
                getResources().getQuantityString(R.plurals.days_streak, st.streak, st.streak), 0.5f));
        row2.addView(bigTile(Ico.CHECK, String.valueOf(st.days), getString(R.string.stat_streak), 0.5f));
        content.addView(row2);

        if (st.lastRead > 0) {
            TextView last = Ui.tv(this, getString(R.string.stat_last) + ": " + U.timeAgo(this, st.lastRead),
                    12.5f, Skin.sub(this), U.ui(this));
            last.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = Ui.llpMatch();
            llp.topMargin = U.dp(this, 14);
            content.addView(last, llp);
        }

        content.addView(Ui.space(this, 22));
        content.addView(Ui.title(this, getString(R.string.your_library), 17f));
        content.addView(Ui.space(this, 12));

        for (Book bk : ContentRepo.get().books()) {
            Lib.Prog p = Lib.get().prog(bk.id);
            LinearLayout card = Ui.card(this);
            Ui.pad(card, this, 15, 14, 15, 14);
            LinearLayout.LayoutParams clp = Ui.llpMatch();
            clp.bottomMargin = U.dp(this, 10);
            card.setLayoutParams(clp);

            LinearLayout head = Ui.row(this);
            TextView name = Ui.tv(this, bk.t(Loc.lang()), 15f, Skin.ink(this), U.uiMed(this));
            head.addView(name, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.tv(this, p.percent + "%", 13f, Skin.accent(this), U.uiMed(this)));
            card.addView(head);

            FrameLayout track = Ui.frame(this);
            LinearLayout.LayoutParams trackLp = Ui.llpMatchH(U.dp(this, 5));
            trackLp.topMargin = U.dp(this, 10);
            track.setLayoutParams(trackLp);
            View bg = new View(this);
            bg.setBackground(U.round(Skin.surface2(this), 3f, this));
            track.addView(bg, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, U.dp(this, 5), Gravity.CENTER));
            View fg = new View(this);
            fg.setBackground(U.round(Skin.accent(this), 3f, this));
            track.addView(fg, Ui.flp(Math.max(6, (int) (U.dp(this, 260) * Math.max(0.02f, p.percent / 100f))), U.dp(this, 5), Gravity.START));
            card.addView(track);

            if (p.time > 0) {
                TextView when = Ui.tv(this, U.timeAgo(this, p.time), 11.5f, Skin.sub(this), U.ui(this));
                LinearLayout.LayoutParams wlp = Ui.llpMatch();
                wlp.topMargin = U.dp(this, 8);
                card.addView(when, wlp);
            }
            content.addView(card);
        }

        sc.addView(content);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private View bigTile(int icon, String value, String label, float weight) {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(U.dp(this, 6), 0, U.dp(this, 6), 0);
        c.setLayoutParams(lp);
        c.setBackground(U.round(Skin.surface(this), 20f, this));
        Ui.pad(c, this, 12, 20, 12, 20);

        c.addView(new IconView(this, icon, Skin.accent(this), 22f));
        TextView v = Ui.title(this, value, 30f);
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = Ui.llpMatch();
        vlp.topMargin = U.dp(this, 8);
        c.addView(v, vlp);
        TextView l = Ui.tv(this, label, 12f, Skin.sub(this), U.ui(this));
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = Ui.llpMatch();
        llp.topMargin = U.dp(this, 4);
        c.addView(l, llp);
        return c;
    }
}
