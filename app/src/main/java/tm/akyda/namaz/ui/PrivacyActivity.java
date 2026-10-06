package tm.akyda.namaz.ui;

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

/** Политика конфиденциальности (нужна для публикации в Google Play). */
public class PrivacyActivity extends BaseActivity {

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
        TextView title = Ui.title(this, getString(R.string.privacy_title), 20f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 8);
        top.addView(title, tlp);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        LinearLayout c = Ui.col(this);
        Ui.pad(c, this, 18, 8, 18, 30);

        LinearLayout card = Ui.card(this);
        Ui.pad(card, this, 18, 18, 18, 18);
        TextView t = Ui.tv(this, getString(R.string.privacy_text), 14.5f, Skin.ink(this), U.ui(this));
        t.setLineSpacing(U.dpf(this, 7f), 1.2f);
        card.addView(t);
        c.addView(card);
        sc.addView(c);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
