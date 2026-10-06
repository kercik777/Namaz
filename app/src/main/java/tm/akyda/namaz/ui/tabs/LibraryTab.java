package tm.akyda.namaz.ui.tabs;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.Book;
import tm.akyda.namaz.Chrome;
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
import tm.akyda.namaz.ui.MainActivity;

/** Полка книг с прогрессом и действиями. */
public class LibraryTab implements TabPage {

    private MainActivity host;
    private LinearLayout list;

    @Override
    public View build(MainActivity h) {
        host = h;
        ScrollView sc = new ScrollView(h);
        sc.setClipToPadding(false);
        list = Ui.col(h);
        Ui.pad(list, h, 18, 10, 18, 26);
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

        TextView title = Ui.title(host, host.getString(R.string.library), 25f);
        list.addView(title);
        TextView sub = Ui.tv(host, host.getString(R.string.library_subtitle), 13f, Skin.sub(host), U.ui(host));
        LinearLayout.LayoutParams slp = Ui.llpMatch();
        slp.topMargin = U.dp(host, 4);
        slp.bottomMargin = U.dp(host, 16);
        list.addView(sub, slp);

        for (Book b : repo.books()) {
            list.addView(bookCard(b));
            list.addView(Ui.space(host, 14));
        }
    }

    private View bookCard(final Book b) {
        Lib lib = Lib.get();
        final Lib.Prog p = lib.prog(b.id);

        LinearLayout card = Ui.card(host);
        Ui.pad(card, host, 14, 14, 14, 14);

        LinearLayout row = Ui.row(host);
        row.setGravity(Gravity.TOP);

        Widgets.Cover cover = new Widgets.Cover(host);
        cover.setBook(b);
        cover.setProgress(p.percent / 100f);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(U.dp(host, 96), U.dp(host, 144));
        cover.setLayoutParams(clp);
        row.addView(cover);

        LinearLayout col = Ui.col(host);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(host, 14);
        col.setLayoutParams(lp);

        TextView t = Ui.tv(host, b.t(Loc.lang()), 19f, Skin.ink(host), U.tf("serif", Typeface.BOLD));
        col.addView(t);

        if (!U.empty(b.auth(Loc.lang()))) {
            TextView a = Ui.tv(host, b.auth(Loc.lang()), 12f, Skin.sub(host), U.ui(host));
            LinearLayout.LayoutParams alp = Ui.llpMatch();
            alp.topMargin = U.dp(host, 3);
            col.addView(a, alp);
        }
        if (!U.empty(b.edit(Loc.lang()))) {
            TextView e = Ui.tv(host, b.edit(Loc.lang()), 11f, Skin.withAlpha(Skin.sub(host), 0.85f), U.ui(host));
            LinearLayout.LayoutParams elp = Ui.llpMatch();
            elp.topMargin = U.dp(host, 2);
            col.addView(e, elp);
        }

        // Прогресс
        FrameLayout track = Ui.frame(host);
        LinearLayout.LayoutParams tlp = Ui.llpMatchH(U.dp(host, 4));
        tlp.topMargin = U.dp(host, 12);
        track.setLayoutParams(tlp);
        View bg = new View(host);
        bg.setBackground(U.round(Skin.surface2(host), 2f, host));
        track.addView(bg, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, U.dp(host, 4), Gravity.CENTER));
        View fg = new View(host);
        fg.setBackground(U.round(Skin.accent(host), 2f, host));
        int w = U.dp(host, 220);
        track.addView(fg, Ui.flp(Math.max(4, (int) (w * Math.max(0.02f, p.percent / 100f))), U.dp(host, 4), Gravity.START));
        col.addView(track);

        TextView pct = Ui.tv(host, p.percent > 0
                        ? host.getString(R.string.book_progress, p.percent)
                        : host.getString(R.string.book_not_started),
                11.5f, Skin.sub(host), U.ui(host));
        LinearLayout.LayoutParams plp = Ui.llpMatch();
        plp.topMargin = U.dp(host, 6);
        col.addView(pct, plp);

        LinearLayout buttons = Ui.row(host);
        LinearLayout.LayoutParams blp = Ui.llpMatch();
        blp.topMargin = U.dp(host, 12);
        buttons.setLayoutParams(blp);

        TextView read = Ui.buttonPrimary(host, host.getString(p.percent > 0 ? R.string.read_continue : R.string.read_book));
        read.setTextSize(14f);
        Ui.click(read, new Runnable() {
            @Override
            public void run() {
                host.openBook(b);
            }
        });
        buttons.addView(read);

        FrameLayout more = Ui.iconButton(host, Ico.MORE, Skin.ink(host), Skin.surface2(host), 42f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(U.dp(host, 42), U.dp(host, 42));
        mlp.leftMargin = U.dp(host, 8);
        more.setLayoutParams(mlp);
        Ui.click(more, new Runnable() {
            @Override
            public void run() {
                actionsSheet(b);
            }
        });
        buttons.addView(more);
        col.addView(buttons);

        row.addView(col);
        card.addView(row);
        return card;
    }

    /* ============ Меню книги ============ */

    private void actionsSheet(final Book b) {
        LinearLayout c = Ui.col(host);
        c.addView(row(Ico.LIST, host.getString(R.string.contents), new Runnable() {
            @Override
            public void run() {
                tocSheet(b);
            }
        }));
        c.addView(row(Ico.SEARCH_BOOK, host.getString(R.string.search_in_book), new Runnable() {
            @Override
            public void run() {
                host.show(2, true);
            }
        }));
        c.addView(row(Ico.PAGE, host.getString(R.string.book_actions) + ": " + b.script, null));
        c.addView(row(Ico.REFRESH, host.getString(R.string.reset_progress), new Runnable() {
            @Override
            public void run() {
                Lib.get().saveProg(b.id, 0, 0, 0, 0);
                host.toast(host.getString(R.string.reset_done));
                refresh();
            }
        }));
        Chrome.Sheet sheet = new Chrome.Sheet(host, b.t(Loc.lang()), c);
        host.overlay().addView(sheet.root());
        sheet.show();
    }

    private void tocSheet(final Book b) {
        LinearLayout c = Ui.col(host);
        ScrollView sc = new ScrollView(host);
        LinearLayout in = Ui.col(host);
        for (int i = 0; i < b.toc.size(); i++) {
            final Book.Toc t = b.toc.get(i);
            if (t.text == null || t.text.trim().isEmpty()) continue;
            final int chapter = i;
            LinearLayout rowView = Ui.row(host);
            Ui.pad(rowView, host, t.level == 0 ? 6 : (t.level == 1 ? 12 : 22), 11, 6, 11);
            TextView tv = Ui.tv(host, t.text,
                    t.level == 0 ? 16f : (t.level == 1 ? 15f : 13.5f),
                    t.level == 0 ? Skin.accent(host) : Skin.ink(host),
                    t.level == 0 ? U.tf("serif", Typeface.BOLD) : U.uiMed(host));
            rowView.addView(tv, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            IconView chev = new IconView(host, Ico.RIGHT, Skin.withAlpha(Skin.sub(host), 0.7f), 14f);
            rowView.addView(chev);
            Ui.click(rowView, new Runnable() {
                @Override
                public void run() {
                    tm.akyda.namaz.Nav.openReader(host, b.id, chapter, t.blockIndex);
                }
            });
            in.addView(rowView);
        }
        sc.addView(in);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(host) * 0.5f), U.dp(host, 420))));
        c.addView(sc);
        Chrome.Sheet sheet = new Chrome.Sheet(host, host.getString(R.string.contents), c);
        host.overlay().addView(sheet.root());
        sheet.show();
    }

    private View row(int icon, String text, final Runnable action) {
        LinearLayout r = Ui.row(host);
        Ui.pad(r, host, 4, 13, 4, 13);
        r.addView(new IconView(host, icon, Skin.accent(host), 20f));
        TextView t = Ui.tv(host, text, 15f, Skin.ink(host), U.ui(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(host, 14);
        r.addView(t, lp);
        if (action != null) {
            r.addView(new IconView(host, Ico.RIGHT, Skin.withAlpha(Skin.sub(host), 0.7f), 15f));
        }
        return r;
    }
}
