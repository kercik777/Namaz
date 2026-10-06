package tm.akyda.namaz.ui.tabs;

import android.graphics.Typeface;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import tm.akyda.namaz.Book;
import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.ui.MainActivity;

/** Поиск по всем книгам с подсветкой найденного. */
public class SearchTab implements TabPage {

    private MainActivity host;
    private LinearLayout list;
    private EditText input;
    private final Handler handler = new Handler();
    private Runnable pending;
    private Runnable onQuery;

    @Override
    public View build(MainActivity h) {
        host = h;
        LinearLayout root = Ui.col(h);
        root.setBackgroundColor(Skin.bg(h));
        Ui.pad(root, h, 18, 12, 18, 0);

        TextView title = Ui.title(host, host.getString(R.string.search), 25f);
        root.addView(title);
        root.addView(Ui.space(host, 12));

        // Поле поиска
        FrameLayout field = Ui.frame(host);
        field.setBackground(U.round(Skin.surface(host), 18f, host));
        U.shadow(field, 2f);
        LinearLayout.LayoutParams flp = Ui.llpMatchH(U.dp(host, 52));
        field.setLayoutParams(flp);

        IconView searchIcon = new IconView(host, Ico.SEARCH, Skin.sub(host), 21f);
        FrameLayout.LayoutParams silp = Ui.flp(U.dp(host, 21), U.dp(host, 21), Gravity.START | Gravity.CENTER_VERTICAL);
        silp.leftMargin = U.dp(host, 16);
        field.addView(searchIcon, silp);

        input = new EditText(host);
        input.setHint(host.getString(R.string.search_hint));
        input.setHintTextColor(Skin.withAlpha(Skin.sub(host), 0.7f));
        input.setTextColor(Skin.ink(host));
        input.setTextSize(16f);
        input.setTypeface(U.ui(host));
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        ilp.leftMargin = U.dp(host, 46);
        ilp.rightMargin = U.dp(host, 44);
        field.addView(input, ilp);

        final IconView clear = new IconView(host, Ico.CLOSE, Skin.withAlpha(Skin.sub(host), 0.8f), 18f);
        FrameLayout.LayoutParams clp = Ui.flp(U.dp(host, 18), U.dp(host, 18), Gravity.END | Gravity.CENTER_VERTICAL);
        clp.rightMargin = U.dp(host, 16);
        field.addView(clear, clp);
        Ui.click(clear, new Runnable() {
            @Override
            public void run() {
                input.setText("");
                hideKeyboard();
                refresh();
            }
        });
        root.addView(field);
        root.addView(Ui.space(host, 6));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (pending != null) handler.removeCallbacks(pending);
                pending = new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                };
                handler.postDelayed(pending, 180);
            }
        });

        ScrollView sc = new ScrollView(host);
        sc.setClipToPadding(false);
        list = Ui.col(host);
        Ui.pad(list, host, 0, 6, 0, 26);
        sc.addView(list);
        root.addView(sc, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (im != null && input != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override
    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        String q = input == null ? "" : input.getText().toString().trim();
        if (q.length() < 2) {
            showPlaceholder();
            return;
        }
        ContentRepo.get().awaitLoaded(host);
        List<ContentRepo.Hit> hits = ContentRepo.get().search(q, 120);
        Lib.get().addSearch(q);

        TextView count = Ui.tv(host, host.getString(R.string.search_found, hits.size()), 12.5f,
                Skin.sub(host), U.uiMed(host));
        LinearLayout.LayoutParams clp = Ui.llpMatch();
        clp.bottomMargin = U.dp(host, 8);
        list.addView(count, clp);

        if (hits.isEmpty()) {
            list.addView(emptyState(Ico.SEARCH, host.getString(R.string.search_nothing),
                    host.getString(R.string.search_nothing_hint)));
            return;
        }
        for (final ContentRepo.Hit hit : hits) {
            list.addView(hitCard(hit, q));
            list.addView(Ui.space(host, 8));
        }
    }

    private void showPlaceholder() {
        List<String> hist = Lib.get().searchHistory();
        if (hist.isEmpty()) {
            list.addView(emptyState(Ico.SEARCH_BOOK, host.getString(R.string.search_placeholder_title),
                    host.getString(R.string.search_placeholder_text)));
            return;
        }
        LinearLayout head = Ui.row(host);
        TextView t = Ui.label(host, host.getString(R.string.search_recent).toUpperCase());
        head.addView(t, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView clearAll = Ui.tv(host, host.getString(R.string.search_clear), 12.5f, Skin.accent(host), U.uiMed(host));
        Ui.click(clearAll, new Runnable() {
            @Override
            public void run() {
                Lib.get().clearSearchHistory();
                refresh();
            }
        });
        head.addView(clearAll);
        list.addView(head);
        list.addView(Ui.space(host, 10));

        LinearLayout wrap = Ui.col(host);
        for (final String s : hist) {
            LinearLayout r = Ui.row(host);
            r.setBackground(U.round(Skin.surface(host), 14f, host));
            Ui.pad(r, host, 14, 12, 14, 12);
            LinearLayout.LayoutParams lp = Ui.llpM(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0, 4, 0, 4, host);
            r.setLayoutParams(lp);
            r.addView(new IconView(host, Ico.CLOCK, Skin.sub(host), 17f));
            TextView tv = Ui.tv(host, "  " + s, 14.5f, Skin.ink(host), U.ui(host));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = U.dp(host, 12);
            r.addView(tv, tlp);
            IconView chev = new IconView(host, Ico.RIGHT, Skin.withAlpha(Skin.sub(host), 0.6f), 14f);
            r.addView(chev);
            Ui.click(r, new Runnable() {
                @Override
                public void run() {
                    input.setText(s);
                    input.setSelection(s.length());
                    refresh();
                }
            });
            wrap.addView(r);
        }
        list.addView(wrap);
    }

    private View hitCard(final ContentRepo.Hit hit, String query) {
        Book b = ContentRepo.get().byId(hit.bookId);
        LinearLayout card = Ui.card(host);
        Ui.pad(card, host, 15, 14, 15, 14);
        LinearLayout.LayoutParams clp = Ui.llpMatch();
        clp.bottomMargin = U.dp(host, 8);
        card.setLayoutParams(clp);

        LinearLayout head = Ui.row(host);
        TextView book = Ui.tv(host, b == null ? "" : b.t(Loc.lang()), 12f, Skin.accent(host), U.uiMed(host));
        head.addView(book, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!U.empty(hit.chapterTitle)) {
            TextView ch = Ui.tv(host, U.trimTo(hit.chapterTitle, 26), 11.5f, Skin.sub(host), U.ui(host));
            head.addView(ch);
        }
        card.addView(head);

        TextView snippet = Ui.tv(host, highlight(hit.snippet, query), 14.5f, Skin.ink(host), U.tf("serif", Typeface.NORMAL));
        snippet.setLineSpacing(U.dpf(host, 6f), 1.18f);
        LinearLayout.LayoutParams slp = Ui.llpMatch();
        slp.topMargin = U.dp(host, 8);
        card.addView(snippet, slp);

        Ui.click(card, new Runnable() {
            @Override
            public void run() {
                hideKeyboard();
                Nav.openReader(host, hit.bookId, hit.chapter, hit.block);
            }
        });
        return card;
    }

    private CharSequence highlight(String text, String query) {
        SpannableString s = new SpannableString(text);
        String low = U.norm(text);
        String q = U.norm(query);
        int from = 0;
        int guard = 0;
        while (guard++ < 12) {
            int at = low.indexOf(q, from);
            if (at < 0) break;
            int end = Math.min(text.length(), at + q.length());
            s.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(Skin.accent(host)), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new BackgroundColorSpan(Skin.withAlpha(Skin.accent(host), 0.16f)), at, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = end;
        }
        return s;
    }

    private View emptyState(int icon, String title, String text) {
        LinearLayout c = Ui.col(host);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(c, host, 24, 46, 24, 24);
        IconView iv = new IconView(host, icon, Skin.withAlpha(Skin.accent(host), 0.8f), 44f);
        iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(host, 44), U.dp(host, 44)));
        c.addView(iv);
        TextView t = Ui.title(host, title, 18f);
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(host, 16);
        t.setGravity(Gravity.CENTER);
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
