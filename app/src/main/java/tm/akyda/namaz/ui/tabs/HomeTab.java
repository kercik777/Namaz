package tm.akyda.namaz.ui.tabs;

import android.graphics.Typeface;
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
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;
import tm.akyda.namaz.ui.MainActivity;
import tm.akyda.namaz.ui.SettingsActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Главная: приветствие, продолжение чтения, цитата дня, полка и статистика. */
public class HomeTab implements TabPage {

    private MainActivity host;
    private LinearLayout list;

    @Override
    public View build(MainActivity h) {
        host = h;
        ScrollView sc = new ScrollView(h);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        list = Ui.col(h);
        Ui.pad(list, h, 18, 8, 18, 26);
        sc.addView(list);
        refresh();
        return sc;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        ContentRepo repo = ContentRepo.get();
        repo.awaitLoaded(host);
        Lib lib = Lib.get();
        String lastId = lib.lastBookId();
        Book lastBook = repo.byId(lastId);
        Lib.Prog last = lastBook != null ? lib.prog(lastBook.id) : null;

        list.addView(header());
        list.addView(Ui.space(host, 16));

        if (lastBook != null && last != null) list.addView(heroCard(lastBook, last));
        else if (!repo.books().isEmpty()) list.addView(heroCard(repo.books().get(0), lib.prog(repo.books().get(0).id)));
        list.addView(Ui.space(host, 16));

        ContentRepo.Quote q = repo.quoteOfDay();
        if (q != null) {
            list.addView(quoteCard(q));
            list.addView(Ui.space(host, 16));
        }

        list.addView(sectionTitle(getString(R.string.your_library), null));
        LinearLayout shelf = Ui.row(host);
        shelf.setGravity(Gravity.TOP);
        for (Book b : repo.books()) {
            shelf.addView(shelfItem(b, lib));
        }
        list.addView(shelf);
        list.addView(Ui.space(host, 18));

        Lib.Stats st = lib.stats();
        list.addView(sectionTitle(getString(R.string.statistics), getString(R.string.more)));
        LinearLayout stats = Ui.row(host);
        stats.setWeightSum(3f);
        stats.addView(statTile(Ico.PAGE, String.valueOf(st.pages),
                host.getResources().getQuantityString(R.plurals.pages_read, st.pages, st.pages)));
        stats.addView(statTile(Ico.CLOCK, String.valueOf(st.minutes),
                host.getResources().getQuantityString(R.plurals.minutes_read, (int) st.minutes, st.minutes)));
        stats.addView(statTile(Ico.STAR, String.valueOf(st.streak),
                host.getResources().getQuantityString(R.plurals.days_streak, st.streak, st.streak)));
        list.addView(stats);
        list.addView(Ui.space(host, 18));
        list.addView(footerNote());
    }

    private String getString(int id) {
        return host.getString(id);
    }

    /* ============ Приветствие ============ */

    private View header() {
        LinearLayout r = Ui.row(host);
        LinearLayout col = Ui.col(host);
        TextView greet = Ui.title(host, U.greeting(host), 23f);
        col.addView(greet);
        TextView sub = Ui.tv(host, U.todayTk() + " · " + getString(R.string.home_subtitle),
                12.5f, Skin.sub(host), U.ui(host));
        LinearLayout.LayoutParams slp = Ui.llpMatch();
        slp.topMargin = U.dp(host, 4);
        col.addView(sub, slp);
        r.addView(col, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout gear = Ui.iconButton(host, Ico.GEAR, Skin.ink(host), Skin.surface(host), 44f);
        U.shadow(gear, 2f);
        Ui.click(gear, new Runnable() {
            @Override
            public void run() {
                host.startActivity(new android.content.Intent(host, SettingsActivity.class));
                host.overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
            }
        });
        r.addView(gear);
        return r;
    }

    /* ============ Продолжить чтение ============ */

    private View heroCard(final Book b, Lib.Prog p) {
        LinearLayout card = Ui.col(host);
        card.setBackground(U.gradientRect(0xFF11543D, 0xFF06231A, 22f, host, true));
        U.shadow(card, 6f);
        Ui.pad(card, host, 16, 16, 16, 16);

        LinearLayout row = Ui.row(host);
        Widgets.Cover cover = new Widgets.Cover(host);
        cover.setBook(b);
        int cw = U.dp(host, 58), ch = U.dp(host, 86);
        cover.setLayoutParams(new LinearLayout.LayoutParams(cw, ch));
        cover.setProgress(p.percent / 100f);
        row.addView(cover);

        LinearLayout col = Ui.col(host);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = U.dp(host, 14);
        col.setLayoutParams(clp);

        TextView lab = Ui.tv(host, getString(p.percent > 0 ? R.string.continue_reading : R.string.start_reading)
                .toUpperCase(), 10.5f, 0xFFDCC07A, U.uiMed(host));
        lab.setLetterSpacing(0.16f);
        col.addView(lab);

        TextView title = Ui.tv(host, b.t(tm.akyda.namaz.Loc.lang()), 19f, 0xFFF7F1E2, U.tf("serif", Typeface.BOLD));
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 5);
        col.addView(title, tlp);

        Book.Toc toc = b.tocAt(p.chapter);
        if (toc != null) {
            TextView chTv = Ui.tv(host, toc.text, 12.5f, 0xCCF7F1E2, U.ui(host));
            LinearLayout.LayoutParams hlp = Ui.llpMatch();
            hlp.topMargin = U.dp(host, 3);
            col.addView(chTv, hlp);
        }

        // Прогресс
        View track = new View(host);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, U.dp(host, 3f));
        trlp.topMargin = U.dp(host, 12);
        track.setLayoutParams(trlp);
        track.setBackground(U.round(0x33FFFFFF, 2f, host));
        FrameLayout trackWrap = Ui.frame(host);
        trackWrap.addView(track);
        final View fill = new View(host);
        fill.setBackground(U.round(0xFFE3C36F, 2f, host));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(0, U.dp(host, 3f));
        flp.topMargin = U.dp(host, 12);
        fill.setLayoutParams(flp);
        trackWrap.addView(fill);
        col.addView(trackWrap);
        trackWrap.post(new Runnable() {
            @Override
            public void run() {
                View f = trackWrap.getChildAt(1);
                f.getLayoutParams().width = (int) (trackWrap.getWidth() * Math.max(0.02f, p.percent / 100f));
                f.requestLayout();
            }
        });

        TextView pct = Ui.tv(host, host.getString(R.string.book_progress, p.percent), 11.5f,
                Skin.withAlpha(0xFFF7F1E2, 0.75f), U.ui(host));
        LinearLayout.LayoutParams plp = Ui.llpMatch();
        plp.topMargin = U.dp(host, 6);
        col.addView(pct, plp);

        row.addView(col);
        card.addView(row);
        Ui.click(card, new Runnable() {
            @Override
            public void run() {
                host.openBook(b);
            }
        });
        return card;
    }

    /* ============ Цитата дня ============ */

    private View quoteCard(final ContentRepo.Quote q) {
        final String txt = tm.akyda.namaz.Loc.TK.equals(tm.akyda.namaz.Loc.lang()) && !U.empty(q.tk) ? q.tk : q.ru;
        LinearLayout card = Ui.card(host);
        if (q.located()) {
            // нажатие открывает то самое место в книге
            Ui.click(card, new Runnable() {
                @Override
                public void run() {
                    Nav.openReader(host, q.bookId, q.chapter, q.block);
                }
            });
        }
        card.setBackground(U.strokeRound(Skin.surface(host), Skin.withAlpha(Skin.accent(host), 0.4f), 22f, 1.2f, host));
        Ui.pad(card, host, 18, 16, 18, 16);

        LinearLayout head = Ui.row(host);
        IconView qi = new IconView(host, Ico.QUOTE, Skin.accent(host), 20f);
        head.addView(qi);
        TextView lab = Ui.tv(host, getString(R.string.quote_of_day).toUpperCase(), 10.5f, Skin.accent(host), U.uiMed(host));
        lab.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = U.dp(host, 10);
        head.addView(lab, llp);
        card.addView(head);

        TextView text = Ui.tv(host, txt, 15.5f, Skin.ink(host), U.tf("serif", Typeface.NORMAL));
        text.setLineSpacing(U.dpf(host, 8f), 1.2f);
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 12);
        card.addView(text, tlp);

        if (!U.empty(q.src)) {
            TextView src = Ui.tv(host, "— " + q.src, 12f, Skin.sub(host), U.ui(host));
            LinearLayout.LayoutParams slp = Ui.llpMatch();
            slp.topMargin = U.dp(host, 8);
            src.setLayoutParams(slp);
            card.addView(src);
        }
        if (q.located()) {
            LinearLayout open = Ui.row(host);
            open.setGravity(Gravity.CENTER_VERTICAL);
            open.addView(new IconView(host, Ico.BOOK, Skin.accent(host), 15f));
            TextView ol = Ui.tv(host, getString(R.string.quote_open), 12.5f, Skin.accent(host), U.uiMed(host));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            olp.leftMargin = U.dp(host, 7);
            open.addView(ol, olp);
            LinearLayout.LayoutParams olp2 = Ui.llpMatch();
            olp2.topMargin = U.dp(host, 6);
            card.addView(open, olp2);
        }

        LinearLayout actions = Ui.row(host);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams alp = Ui.llpMatch();
        alp.topMargin = U.dp(host, 12);
        View copy = smallAction(Ico.COPY, getString(R.string.copy));
        View share = smallAction(Ico.SHARE, getString(R.string.share));
        actions.addView(copy);
        actions.addView(share);
        card.addView(actions, alp);
        Ui.click(copy, new Runnable() {
            @Override
            public void run() {
                U.copy(host, txt + (U.empty(q.src) ? "" : "\n— " + q.src));
                host.toast(getString(R.string.copied));
            }
        });
        Ui.click(share, new Runnable() {
            @Override
            public void run() {
                U.share(host, txt + (U.empty(q.src) ? "" : "\n— " + q.src));
            }
        });
        return card;
    }

    private View smallAction(int icon, String text) {
        LinearLayout wrap = Ui.row(host);
        wrap.setBackground(U.round(Skin.withAlpha(Skin.accent(host), 0.10f), 18f, host));
        Ui.pad(wrap, host, 12, 7, 14, 7);
        wrap.addView(new IconView(host, icon, Skin.accent(host), 15f));
        TextView label = Ui.tv(host, text, 12.5f, Skin.accent(host), U.uiMed(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = U.dp(host, 6);
        wrap.addView(label, lp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = U.dp(host, 8);
        wrap.setLayoutParams(mlp);
        return wrap;
    }

    /* ============ Полка ============ */

    private View shelfItem(final Book b, Lib lib) {
        LinearLayout c = Ui.col(host);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(U.dp(host, 5), 0, U.dp(host, 5), 0);
        c.setLayoutParams(lp);

        Widgets.Cover cover = new Widgets.Cover(host);
        cover.setBook(b);
        cover.setProgress(lib.prog(b.id).percent / 100f);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, U.dp(host, 138));
        cover.setLayoutParams(clp);
        c.addView(cover);

        TextView t = Ui.tv(host, b.t(tm.akyda.namaz.Loc.lang()), 12.5f, Skin.ink(host), U.uiMed(host));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 8);
        c.addView(t, tlp);
        Ui.click(cover, new Runnable() {
            @Override
            public void run() {
                host.openBook(b);
            }
        });
        return c;
    }

    /* ============ Статистика ============ */

    private View statTile(int icon, String value, String label) {
        LinearLayout c = Ui.col(host);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(U.dp(host, 5), 0, U.dp(host, 5), 0);
        c.setLayoutParams(lp);
        c.setBackground(U.round(Skin.surface(host), 18f, host));
        Ui.pad(c, host, 8, 14, 8, 14);

        IconView iv = new IconView(host, icon, Skin.accent(host), 19f);
        c.addView(iv);
        TextView v = Ui.title(host, value, 20f);
        LinearLayout.LayoutParams vlp = Ui.llpMatch();
        vlp.topMargin = U.dp(host, 6);
        v.setGravity(Gravity.CENTER);
        c.addView(v, vlp);
        TextView l = Ui.tv(host, label, 10.5f, Skin.sub(host), U.ui(host));
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        LinearLayout.LayoutParams llp = Ui.llpMatch();
        llp.topMargin = U.dp(host, 3);
        c.addView(l, llp);
        return c;
    }

    private View footerNote() {
        LinearLayout r = Ui.row(host);
        r.setGravity(Gravity.CENTER);
        IconView iv = new IconView(host, Ico.SHIELD, Skin.withAlpha(Skin.sub(host), 0.8f), 15f);
        r.addView(iv);
        TextView t = Ui.tv(host, "  " + getString(R.string.privacy), 11.5f, Skin.sub(host), U.ui(host));
        r.addView(t);
        Ui.click(r, new Runnable() {
            @Override
            public void run() {
                host.startActivity(new android.content.Intent(host, tm.akyda.namaz.ui.PrivacyActivity.class));
                host.overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
            }
        });
        return r;
    }

    private View sectionTitle(String title, String action) {
        LinearLayout r = Ui.row(host);
        TextView t = Ui.title(host, title, 17f);
        r.addView(t, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView a = Ui.tv(host, action, 13f, Skin.accent(host), U.uiMed(host));
            r.addView(a);
        }
        LinearLayout.LayoutParams lp = Ui.llpMatch();
        lp.bottomMargin = U.dp(host, 10);
        r.setLayoutParams(lp);
        return r;
    }
}
