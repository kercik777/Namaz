package tm.akyda.namaz.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.P;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;

/** Настройки чтения. */
public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Skin.bg(this));

        LinearLayout top = Ui.row(this);
        Ui.pad(top, this, 10, 8, 18, 8);
        top.addView(toolbarBack());
        TextView title = Ui.title(this, getString(R.string.reading_settings), 20f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 8);
        top.addView(title, tlp);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        sc.setClipToPadding(false);
        LinearLayout content = Ui.col(this);
        Ui.pad(content, this, 18, 8, 18, 28);

        content.addView(SettingsPanel.build(this, new SettingsPanel.Changed() {
            @Override
            public void onChanged() {
                restart();
            }
        }, false));

        content.addView(Ui.space(this, 18));
        LinearLayout data = Ui.card(this);
        Ui.pad(data, this, 6, 4, 6, 4);
        LinearLayout reset = Ui.row(this);
        Ui.pad(reset, this, 12, 14, 12, 14);
        reset.addView(new IconView(this, Ico.REFRESH, Skin.accent(this), 20f));
        TextView rt = Ui.tv(this, getString(R.string.reset_all), 15f, Skin.ink(this), U.ui(this));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = U.dp(this, 14);
        reset.addView(rt, rlp);
        Ui.click(reset, new Runnable() {
            @Override
            public void run() {
                Nav.confirm(SettingsActivity.this, getString(R.string.reset_all),
                        getString(R.string.reset_all_question), new Runnable() {
                            @Override
                            public void run() {
                                Lib.get().resetAll();
                                toast(getString(R.string.reset_done));
                            }
                        });
            }
        });
        data.addView(reset);
        content.addView(data);

        TextView foot = Ui.tv(this, getString(R.string.settings_note), 11.5f, Skin.sub(this), U.ui(this));
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = Ui.llpMatch();
        flp.topMargin = U.dp(this, 18);
        content.addView(foot, flp);

        sc.addView(content);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    View toolbarBack() {
        android.widget.FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 40);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        f.addView(new IconView(this, Ico.BACK, Skin.ink(this), 21f), Ui.flp(U.dp(this, 21), U.dp(this, 21), Gravity.CENTER));
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        });
        return f;
    }
}
