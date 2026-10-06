package tm.akyda.namaz.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/** О приложении: назначение, источники, честные предупреждения. */
public class AboutActivity extends BaseActivity {

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
        TextView title = Ui.title(this, getString(R.string.about_app), 20f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 8);
        top.addView(title, tlp);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        Ui.pad(c, this, 18, 4, 18, 30);

        Widgets.Logo logo = new Widgets.Logo(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(U.dp(this, 120), U.dp(this, 120));
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        logo.setLayoutParams(llp);
        logo.play(1200);
        c.addView(logo);

        TextView name = Ui.tv(this, getString(R.string.app_name), 23f, Skin.ink(this), U.tf("serif", Typeface.BOLD));
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = Ui.llpMatch();
        nlp.topMargin = U.dp(this, 10);
        c.addView(name, nlp);

        TextView ver = Ui.tv(this, getString(R.string.version) + " 1.0.0", 12.5f, Skin.sub(this), U.ui(this));
        ver.setGravity(Gravity.CENTER);
        c.addView(ver);
        c.addView(Ui.space(this, 20));

        c.addView(paragraph(getString(R.string.about_intro)));
        c.addView(Ui.space(this, 16));

        c.addView(block(Ico.BOOK, getString(R.string.about_features), getString(R.string.about_features_text)));
        c.addView(Ui.space(this, 10));
        c.addView(block(Ico.TYPE, getString(R.string.about_features2), getString(R.string.about_features2_text)));
        c.addView(Ui.space(this, 10));
        c.addView(block(Ico.SHIELD, getString(R.string.about_sources), getString(R.string.about_sources_text)));
        c.addView(Ui.space(this, 10));
        c.addView(block(Ico.INFO, getString(R.string.error_report_title), getString(R.string.about_disclaimer)));
        c.addView(Ui.space(this, 18));

        TextView thanks = Ui.tv(this, getString(R.string.about_thanks), 14f, Skin.accent(this), U.tf("serif", Typeface.ITALIC));
        thanks.setGravity(Gravity.CENTER);
        c.addView(thanks);
        c.addView(Ui.space(this, 20));

        TextView mail = Ui.buttonGold(this, getString(R.string.email_hint));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        mail.setLayoutParams(mlp);
        Ui.click(mail, new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Intent.ACTION_SENDTO);
                i.setData(Uri.parse("mailto:"));
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " — " + getString(R.string.feedback));
                try {
                    startActivity(Intent.createChooser(i, getString(R.string.email_hint)));
                } catch (ActivityNotFoundException e) {
                    toast(getString(R.string.no_email_app));
                }
            }
        });
        c.addView(mail);

        sc.addView(c);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private View paragraph(String text) {
        TextView t = Ui.tv(this, text, 14.5f, Skin.ink(this), U.ui(this));
        t.setLineSpacing(U.dpf(this, 6f), 1.18f);
        return t;
    }

    private View block(int icon, String title, String text) {
        LinearLayout card = Ui.card(this);
        Ui.pad(card, this, 16, 15, 16, 15);
        LinearLayout head = Ui.row(this);
        head.addView(new IconView(this, icon, Skin.accent(this), 19f));
        TextView t = Ui.tv(this, "  " + title, 15f, Skin.ink(this), U.uiMed(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(this, 12);
        head.addView(t, lp);
        card.addView(head);
        TextView m = Ui.tv(this, text, 13.5f, Skin.sub(this), U.ui(this));
        m.setLineSpacing(U.dpf(this, 5.5f), 1.16f);
        LinearLayout.LayoutParams mlp = Ui.llpMatch();
        mlp.topMargin = U.dp(this, 9);
        card.addView(m, mlp);
        return card;
    }
}
