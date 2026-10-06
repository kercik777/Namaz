package tm.akyda.namaz.ui.tabs;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.P;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.ui.AboutActivity;
import tm.akyda.namaz.ui.MainActivity;
import tm.akyda.namaz.ui.PrivacyActivity;
import tm.akyda.namaz.ui.SettingsActivity;
import tm.akyda.namaz.ui.StatsActivity;

/** «Ещё»: оформление, язык, данные и информация. */
public class MoreTab implements TabPage {

    private MainActivity host;
    private LinearLayout list;

    @Override
    public View build(MainActivity h) {
        host = h;
        ScrollView sc = new ScrollView(h);
        sc.setClipToPadding(false);
        list = Ui.col(h);
        Ui.pad(list, h, 18, 12, 18, 26);
        sc.addView(list);
        refresh();
        return sc;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        list.removeAllViews();

        list.addView(Ui.title(host, host.getString(R.string.more), 25f));
        list.addView(Ui.space(host, 16));

        // Оформление
        list.addView(section(host.getString(R.string.appearance)));
        LinearLayout themeWrap = Ui.card(host);
        Ui.pad(themeWrap, host, 16, 14, 16, 16);
        themeWrap.addView(Ui.tv(host, host.getString(R.string.app_theme), 14.5f, Skin.ink(host), U.uiMed(host)));
        LinearLayout themes = Ui.row(host);
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 12);
        themes.setLayoutParams(tlp);
        int mode = tm.akyda.namaz.P.i(tm.akyda.namaz.P.appTheme, Skin.MODE_SYSTEM);
        final String[] themeNames = {
                host.getString(R.string.theme_light), host.getString(R.string.theme_dark),
                host.getString(R.string.theme_system)
        };
        for (int i = 0; i < themeNames.length; i++) {
            final int m = i;
            TextView chip = Ui.chip(host, themeNames[i], mode == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = U.dp(host, 8);
            chip.setLayoutParams(lp);
            Ui.click(chip, new Runnable() {
                @Override
                public void run() {
                    P.si(P.appTheme, m);
                    host.restartApp();
                }
            });
            themes.addView(chip);
        }
        themeWrap.addView(themes);

        themeWrap.addView(Ui.space(host, 16));
        themeWrap.addView(Ui.tv(host, host.getString(R.string.interface_language), 14.5f, Skin.ink(host), U.uiMed(host)));
        LinearLayout langs = Ui.row(host);
        LinearLayout.LayoutParams llp = Ui.llpMatch();
        llp.topMargin = U.dp(host, 12);
        langs.setLayoutParams(llp);
        final TextView ru = Ui.chip(host, host.getString(R.string.lang_ru), Loc.RU.equals(Loc.lang()));
        final TextView tk = Ui.chip(host, host.getString(R.string.lang_tk), Loc.TK.equals(Loc.lang()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = U.dp(host, 8);
        ru.setLayoutParams(lp);
        langs.addView(ru);
        langs.addView(tk);
        Ui.click(ru, new Runnable() {
            @Override
            public void run() {
                Loc.set(host, Loc.RU);
                host.restartApp();
            }
        });
        Ui.click(tk, new Runnable() {
            @Override
            public void run() {
                Loc.set(host, Loc.TK);
                host.restartApp();
            }
        });
        themeWrap.addView(langs);
        TextView note = Ui.tv(host, host.getString(R.string.language_note), 11.5f, Skin.sub(host), U.ui(host));
        LinearLayout.LayoutParams nlp = Ui.llpMatch();
        nlp.topMargin = U.dp(host, 10);
        themeWrap.addView(note, nlp);
        list.addView(themeWrap);
        list.addView(Ui.space(host, 18));

        // Чтение
        list.addView(section(host.getString(R.string.reading_defaults)));
        LinearLayout reading = Ui.card(host);
        Ui.pad(reading, host, 6, 4, 6, 4);
        reading.addView(row(Ico.TYPE, host.getString(R.string.reading_settings), null, new Runnable() {
            @Override
            public void run() {
                open(SettingsActivity.class);
            }
        }));
        reading.addView(row(Ico.STATS, host.getString(R.string.statistics), null, new Runnable() {
            @Override
            public void run() {
                open(StatsActivity.class);
            }
        }));
        list.addView(reading);
        list.addView(Ui.space(host, 18));

        // Данные
        list.addView(section(host.getString(R.string.my_data)));
        LinearLayout data = Ui.card(host);
        Ui.pad(data, host, 6, 4, 6, 4);
        data.addView(row(Ico.TRASH, host.getString(R.string.reset_all), null, new Runnable() {
            @Override
            public void run() {
                Nav.confirm(host, host.getString(R.string.reset_all),
                        host.getString(R.string.reset_all_question), new Runnable() {
                            @Override
                            public void run() {
                                Lib.get().resetAll();
                                host.toast(host.getString(R.string.reset_done));
                                refresh();
                            }
                        });
            }
        }));
        list.addView(data);
        list.addView(Ui.space(host, 18));

        // О приложении
        list.addView(section(host.getString(R.string.about_app)));
        LinearLayout about = Ui.card(host);
        Ui.pad(about, host, 6, 4, 6, 4);
        about.addView(row(Ico.INFO, host.getString(R.string.about_app), null, new Runnable() {
            @Override
            public void run() {
                open(AboutActivity.class);
            }
        }));
        about.addView(row(Ico.SHIELD, host.getString(R.string.privacy), null, new Runnable() {
            @Override
            public void run() {
                open(PrivacyActivity.class);
            }
        }));
        about.addView(row(Ico.MAIL, host.getString(R.string.feedback), null, new Runnable() {
            @Override
            public void run() {
                feedback();
            }
        }));
        about.addView(row(Ico.STAR, host.getString(R.string.rate), null, new Runnable() {
            @Override
            public void run() {
                rate();
            }
        }));
        about.addView(row(Ico.SHARE, host.getString(R.string.share_app), null, new Runnable() {
            @Override
            public void run() {
                U.share(host, host.getString(R.string.app_name) + " — "
                        + host.getString(R.string.app_tagline) + "\nhttps://play.google.com/store/apps/details?id="
                        + host.getPackageName());
            }
        }));
        list.addView(about);

        TextView ver = Ui.tv(host, host.getString(R.string.version) + " 1.0.0", 11.5f, Skin.sub(host), U.ui(host));
        ver.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams vlp = Ui.llpMatch();
        vlp.topMargin = U.dp(host, 22);
        list.addView(ver, vlp);
    }

    private void open(Class<?> cls) {
        host.startActivity(new Intent(host, cls));
        host.overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    private void feedback() {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:"));
        i.putExtra(Intent.EXTRA_SUBJECT, host.getString(R.string.app_name) + " — " + host.getString(R.string.feedback));
        i.putExtra(Intent.EXTRA_TEXT, host.getString(R.string.error_report_text) + "\n\n");
        try {
            host.startActivity(Intent.createChooser(i, host.getString(R.string.email_hint)));
        } catch (ActivityNotFoundException e) {
            host.toast(host.getString(R.string.no_email_app));
        }
    }

    private void rate() {
        try {
            host.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + host.getPackageName())));
        } catch (ActivityNotFoundException e) {
            try {
                host.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + host.getPackageName())));
            } catch (ActivityNotFoundException e2) {
                host.toast(host.getString(R.string.no_store_app));
            }
        }
    }

    private View section(String title) {
        TextView t = Ui.label(host, title.toUpperCase());
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.bottomMargin = U.dp(host, 8);
        lp.leftMargin = U.dp(host, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private View row(int icon, String title, String value, final Runnable action) {
        LinearLayout r = Ui.row(host);
        Ui.pad(r, host, 12, 14, 12, 14);
        r.addView(new IconView(host, icon, Skin.accent(host), 20f));
        TextView t = Ui.tv(host, title, 15f, Skin.ink(host), U.ui(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(host, 14);
        r.addView(t, lp);
        if (value != null) {
            r.addView(Ui.tv(host, value, 13f, Skin.sub(host), U.ui(host)));
        }
        r.addView(new IconView(host, Ico.RIGHT, Skin.withAlpha(Skin.sub(host), 0.65f), 15f));
        Ui.click(r, action);
        return r;
    }
}
