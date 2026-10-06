package tm.akyda.namaz.ui.tabs;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.ui.MainActivity;

/** Избранное: закладки и сохранённые цитаты. */
public class SavedTab implements TabPage {

    private MainActivity host;
    private LinearLayout list;
    private int mode = 0;   // 0 закладки, 1 цитаты

    @Override
    public View build(MainActivity h) {
        host = h;
        LinearLayout root = Ui.col(h);
        root.setBackgroundColor(Skin.bg(h));
        Ui.pad(root, h, 18, 12, 18, 0);

        root.addView(Ui.title(host, host.getString(R.string.saved), 25f));
        root.addView(Ui.space(host, 12));

        LinearLayout seg = Ui.row(host);
        final TextView b1 = Ui.chip(host, host.getString(R.string.tab_bookmarks), mode == 0);
        final TextView b2 = Ui.chip(host, host.getString(R.string.tab_quotes), mode == 1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = U.dp(host, 8);
        b1.setLayoutParams(lp);
        seg.addView(b1);
        seg.addView(b2);
        root.addView(seg);
        Ui.click(b1, new Runnable() {
            @Override
            public void run() {
                mode = 0;
                b1.setBackground(U.round(Skin.accent(host), 20f, host));
                b1.setTextColor(0xFF10231B);
                b2.setBackground(U.strokeRound(Skin.surface2(host), Skin.line(host), 20f, 1f, host));
                b2.setTextColor(Skin.ink(host));
                refresh();
            }
        });
        Ui.click(b2, new Runnable() {
            @Override
            public void run() {
                mode = 1;
                b2.setBackground(U.round(Skin.accent(host), 20f, host));
                b2.setTextColor(0xFF10231B);
                b1.setBackground(U.strokeRound(Skin.surface2(host), Skin.line(host), 20f, 1f, host));
                b1.setTextColor(Skin.ink(host));
                refresh();
            }
        });
        root.addView(Ui.space(host, 6));

        ScrollView sc = new ScrollView(host);
        sc.setClipToPadding(false);
        list = Ui.col(host);
        Ui.pad(list, host, 0, 6, 0, 26);
        sc.addView(list);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    @Override
    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        if (mode == 0) buildMarks();
        else buildQuotes();
    }

    private void buildMarks() {
        List<Lib.Mark> marks = Lib.get().marks();
        if (marks.isEmpty()) {
            list.addView(empty(Ico.MARK, host.getString(R.string.no_bookmarks),
                    host.getString(R.string.no_bookmarks_hint)));
            return;
        }
        for (final Lib.Mark m : marks) {
            LinearLayout card = Ui.card(host);
            Ui.pad(card, host, 15, 13, 15, 13);
            LinearLayout.LayoutParams clp = Ui.llpMatch();
            clp.bottomMargin = U.dp(host, 9);
            card.setLayoutParams(clp);

            LinearLayout head = Ui.row(host);
            head.addView(new IconView(host, Ico.MARK, Skin.accent(host), 16f));
            TextView book = Ui.tv(host, "  " + m.bookTitle, 12f, Skin.accent(host), U.uiMed(host));
            head.addView(book, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView time = Ui.tv(host, U.timeAgo(host, m.time), 11f, Skin.withAlpha(Skin.sub(host), 0.9f), U.ui(host));
            head.addView(time);
            card.addView(head);

            if (!U.empty(m.chapterTitle)) {
                TextView ch = Ui.tv(host, m.chapterTitle, 15f, Skin.ink(host), U.tf("serif", Typeface.BOLD));
                LinearLayout.LayoutParams chlp = Ui.llpMatch();
                chlp.topMargin = U.dp(host, 9);
                card.addView(ch, chlp);
            }
            if (!U.empty(m.snippet)) {
                TextView sn = Ui.tv(host, m.snippet, 13.5f, Skin.sub(host), U.ui(host));
                sn.setMaxLines(3);
                sn.setLineSpacing(U.dpf(host, 5f), 1.15f);
                LinearLayout.LayoutParams slp = Ui.llpMatch();
                slp.topMargin = U.dp(host, 6);
                card.addView(sn, slp);
            }
            TextView page = Ui.tv(host, host.getString(R.string.page) + " " + (m.page + 1), 11.5f,
                    Skin.withAlpha(Skin.sub(host), 0.85f), U.ui(host));
            LinearLayout.LayoutParams plp = Ui.llpMatch();
            plp.topMargin = U.dp(host, 6);
            card.addView(page, plp);

            LinearLayout acts = Ui.row(host);
            acts.setGravity(Gravity.END);
            LinearLayout.LayoutParams alp = Ui.llpMatch();
            alp.topMargin = U.dp(host, 10);
            acts.setLayoutParams(alp);
            acts.addView(action(Ico.PLAY, host.getString(R.string.open_book), new Runnable() {
                @Override
                public void run() {
                    Nav.openReader(host, m.bookId, m.chapter, m.block);
                }
            }));
            acts.addView(action(Ico.TRASH, host.getString(R.string.delete), new Runnable() {
                @Override
                public void run() {
                    Lib.get().removeMark(m.id);
                    host.toast(host.getString(R.string.bookmark_removed));
                    refresh();
                }
            }));
            card.addView(acts);
            Ui.click(card, new Runnable() {
                @Override
                public void run() {
                    Nav.openReader(host, m.bookId, m.chapter, m.block);
                }
            });
            list.addView(card);
        }
    }

    private void buildQuotes() {
        List<Lib.Quote> quotes = Lib.get().quotes();
        if (quotes.isEmpty()) {
            list.addView(empty(Ico.QUOTE, host.getString(R.string.no_quotes),
                    host.getString(R.string.no_quotes_hint)));
            return;
        }
        for (final Lib.Quote q : quotes) {
            LinearLayout card = Ui.card(host);
            card.setBackground(U.strokeRound(Skin.surface(host), Skin.withAlpha(Skin.accent(host), 0.35f), 20f, 1.1f, host));
            Ui.pad(card, host, 16, 14, 16, 12);
            LinearLayout.LayoutParams clp = Ui.llpMatch();
            clp.bottomMargin = U.dp(host, 9);
            card.setLayoutParams(clp);

            LinearLayout head = Ui.row(host);
            head.addView(new IconView(host, Ico.QUOTE, Skin.accent(host), 16f));
            TextView book = Ui.tv(host, "  " + q.bookTitle + (U.empty(q.chapterTitle) ? "" : " · " + q.chapterTitle),
                    11.5f, Skin.accent(host), U.uiMed(host));
            head.addView(book, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(head);

            TextView t = Ui.tv(host, q.text, 15f, Skin.ink(host), U.tf("serif", Typeface.NORMAL));
            t.setLineSpacing(U.dpf(host, 7f), 1.2f);
            LinearLayout.LayoutParams tlp = Ui.llpMatch();
            tlp.topMargin = U.dp(host, 10);
            card.addView(t, tlp);

            LinearLayout acts = Ui.row(host);
            acts.setGravity(Gravity.END);
            LinearLayout.LayoutParams alp = Ui.llpMatch();
            alp.topMargin = U.dp(host, 10);
            acts.setLayoutParams(alp);
            acts.addView(action(Ico.COPY, host.getString(R.string.copy), new Runnable() {
                @Override
                public void run() {
                    U.copy(host, q.text);
                    host.toast(host.getString(R.string.copied));
                }
            }));
            acts.addView(action(Ico.SHARE, host.getString(R.string.share), new Runnable() {
                @Override
                public void run() {
                    U.share(host, q.text + "\n— " + q.bookTitle);
                }
            }));
            acts.addView(action(Ico.TRASH, host.getString(R.string.delete), new Runnable() {
                @Override
                public void run() {
                    Lib.get().removeQuote(q.id);
                    host.toast(host.getString(R.string.quote_removed));
                    refresh();
                }
            }));
            card.addView(acts);
            list.addView(card);
        }
    }

    private View action(int icon, String label, final Runnable r) {
        LinearLayout w = Ui.row(host);
        w.setBackground(U.round(Skin.withAlpha(Skin.accent(host), 0.10f), 16f, host));
        Ui.pad(w, host, 11, 6, 13, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = U.dp(host, 7);
        w.setLayoutParams(lp);
        w.addView(new IconView(host, icon, Skin.accent(host), 14f));
        TextView t = Ui.tv(host, label, 12f, Skin.accent(host), U.uiMed(host));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = U.dp(host, 6);
        w.addView(t, tlp);
        Ui.click(w, r);
        return w;
    }

    private View empty(int icon, String title, String text) {
        LinearLayout c = Ui.col(host);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(c, host, 24, 54, 24, 24);
        IconView iv = new IconView(host, icon, Skin.withAlpha(Skin.accent(host), 0.8f), 46f);
        iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(host, 46), U.dp(host, 46)));
        c.addView(iv);
        TextView t = Ui.title(host, title, 18f);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 16);
        c.addView(t, tlp);
        TextView m = Ui.tv(host, text, 13.5f, Skin.sub(host), U.ui(host));
        m.setGravity(Gravity.CENTER);
        m.setLineSpacing(U.dpf(host, 5f), 1.15f);
        LinearLayout.LayoutParams mlp = Ui.llpMatch();
        mlp.topMargin = U.dp(host, 10);
        c.addView(m, mlp);
        return c;
    }
}
