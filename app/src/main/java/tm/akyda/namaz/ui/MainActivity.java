package tm.akyda.namaz.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import tm.akyda.namaz.Book;
import tm.akyda.namaz.Chrome;
import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.Ico;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.R;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.ui.tabs.HomeTab;
import tm.akyda.namaz.ui.tabs.LibraryTab;
import tm.akyda.namaz.ui.tabs.MoreTab;
import tm.akyda.namaz.ui.tabs.SavedTab;
import tm.akyda.namaz.ui.tabs.SearchTab;
import tm.akyda.namaz.ui.tabs.TabPage;

/** Главный экран: пять вкладок и нижняя навигация. */
public class MainActivity extends BaseActivity {

    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_CONTINUE = "continue_reading";

    private FrameLayout content;
    private Chrome.TabBar bar;
    private TabPage[] tabs;
    private View[] views;
    private int current = -1;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(tm.akyda.namaz.Skin.bg(this));

        content = Ui.frame(this);
        root.addView(content, Ui.llpW(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        String[] labels = {
                getString(R.string.nav_home), getString(R.string.nav_library),
                getString(R.string.nav_search), getString(R.string.nav_saved),
                getString(R.string.nav_more)
        };
        int[] icons = {Ico.HOME, Ico.LIBRARY, Ico.SEARCH, Ico.MARK, Ico.MORE};
        bar = new Chrome.TabBar(this, labels, icons);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, U.dp(this, 62)));
        bar.setListener(new Chrome.OnTab() {
            @Override
            public void onTab(int index) {
                show(index, true);
            }
        });
        root.addView(bar);
        setContentView(root);

        tabs = new TabPage[]{new HomeTab(), new LibraryTab(), new SearchTab(), new SavedTab(), new MoreTab()};
        views = new View[tabs.length];

        int start = 0;
        Intent i = getIntent();
        if (i != null && i.hasExtra(EXTRA_TAB)) start = Math.max(0, Math.min(4, i.getIntExtra(EXTRA_TAB, 0)));
        show(start, false);

        if (i != null && i.getBooleanExtra(EXTRA_CONTINUE, false)) {
            openLast();
        }
    }

    private void openLast() {
        ContentRepo.get().awaitLoaded(this);
        String last = Lib.get().lastBookId();
        if (last == null) last = ContentRepo.get().books().isEmpty() ? null : ContentRepo.get().books().get(0).id;
        if (last == null) return;
        Lib.Prog p = Lib.get().prog(last);
        Nav.openReader(this, last, p.chapter, p.block);
    }

    public void show(int index, boolean animate) {
        if (index < 0 || index >= tabs.length) return;
        if (current == index) return;
        View v = views[index];
        if (v == null) {
            v = tabs[index].build(this);
            views[index] = v;
            content.addView(v, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, android.view.Gravity.TOP));
        }
        if (current >= 0 && views[current] != null) {
            final View old = views[current];
            old.animate().alpha(0f).setDuration(animate ? 150 : 0).withEndAction(new Runnable() {
                @Override
                public void run() {
                    old.setVisibility(View.GONE);
                }
            }).start();
        }
        v.setVisibility(View.VISIBLE);
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationY(U.dpf(this, 14f));
            v.animate().alpha(1f).translationY(0f).setDuration(220).start();
        } else {
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
        current = index;
        bar.select(index, animate);
        tabs[index].refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        if (intent.hasExtra(EXTRA_TAB)) show(intent.getIntExtra(EXTRA_TAB, 0), true);
        if (intent.getBooleanExtra(EXTRA_CONTINUE, false)) openLast();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current >= 0 && views[current] != null) tabs[current].refresh();
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            show(0, true);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2200) {
            super.onBackPressed();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } else {
            lastBack = now;
            toast(getString(R.string.press_again_exit));
        }
    }

    public void openBook(Book b) {
        Lib.Prog p = Lib.get().prog(b.id);
        Nav.openReader(this, b.id, p.chapter, p.block);
    }

    /** Контейнер поверх содержимого — для нижних панелей. */
    public FrameLayout overlay() {
        return rootView();
    }

    /** Перезапуск после смены темы или языка. */
    public void restartApp() {
        restart();
    }

    @Override
    public void toast(String s) {
        super.toast(s);
    }
}
