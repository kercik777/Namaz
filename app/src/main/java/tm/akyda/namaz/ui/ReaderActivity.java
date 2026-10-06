package tm.akyda.namaz.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tm.akyda.namaz.Block;
import tm.akyda.namaz.Book;
import tm.akyda.namaz.Chrome;
import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Ill;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.P;
import tm.akyda.namaz.PageView;
import tm.akyda.namaz.PagesView;
import tm.akyda.namaz.Paginator;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/**
 * Читалка: одна простая панель сверху, одна снизу и книжная страница между ними.
 *
 * Слева и справа — перелистывание листа, как в бумажной книге. Всё остальное
 * (оглавление, поиск, закладки, настройки, размер текста) — в меню одной кнопкой.
 */
public class ReaderActivity extends BaseActivity {

    /** Во сколько раз книжный лист выше видимой части: остаток читается свайпом вниз. */
    private static final float LEAF = 1.55f;

    private Book book;
    private Paginator paginator;
    private Paginator.Result pages;

    private int chapter = 0;
    private int pageIndex = 0;

    private FrameLayout root, stage;
    private PagesView viewer;
    private PageView renderer;
    private View dim;
    private LinearLayout topBar, bottomBar;
    private TextView barTitle, pageLabel;
    private IconView markIcon;

    private boolean chromeShown = true;
    private boolean ready = false;
    private long sessionStart = 0;
    private int chapterStartBlock = 0;

    private final Map<Integer, Bitmap> pageCache = new LinkedHashMap<Integer, Bitmap>(6, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
            return size() > 6;
        }
    };

    private int theme() {
        return P.i(P.readerTheme, Skin.T_PAPER);
    }

    @Override
    protected int themeRes() {
        return R.style.AppTheme_Reader;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ContentRepo.get().awaitLoaded(this);
        String id = getIntent() == null ? null : getIntent().getStringExtra(Nav.EXTRA_BOOK);
        book = ContentRepo.get().byId(id);
        if (book == null) {
            book = ContentRepo.get().books().isEmpty() ? null : ContentRepo.get().books().get(0);
        }
        if (book == null) {
            finish();
            return;
        }
        paginator = new Paginator(this);
        Lib.Prog pr = Lib.get().prog(book.id);
        chapter = Math.max(0, getIntent().getIntExtra(Nav.EXTRA_CHAPTER, pr.chapter));
        if (chapter >= book.toc.size()) chapter = 0;
        final int startBlock = getIntent().getIntExtra(Nav.EXTRA_BLOCK, pr.block);

        buildUi();
        applyKeepScreenOn();
        sessionStart = System.currentTimeMillis();

        stage.post(new Runnable() {
            @Override
            public void run() {
                ready = true;
                rebuild(startBlock);
            }
        });
    }

    /* ==================== Экран ==================== */

    private void buildUi() {
        root = Ui.frame(this);
        root.setBackgroundColor(Skin.readerFrame(theme()));

        stage = Ui.frame(this);
        root.addView(stage, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        viewer = new PagesView(this);
        viewer.setTheme(theme());
        viewer.setInsets(72f, 78f);
        viewer.setListener(new PagesView.Listener() {
            @Override
            public void onNeedPages() {
                preparePages();
            }

            @Override
            public void onTurned(boolean forward) {
                afterTurn(forward);
            }

            @Override
            public void onTap(float x, float y) {
                tapAt(x);
            }
        });
        stage.addView(viewer, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        dim = new View(this);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
        dim.setClickable(false);
        root.addView(dim, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        buildTopBar();
        buildBottomBar();
        setContentView(root);
        applyReaderColors();
    }

    /** Верхняя панель: назад, название главы (нажатие — оглавление), закладка, меню. */
    private void buildTopBar() {
        topBar = Ui.row(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackground(U.round(Skin.withAlpha(Skin.paper(theme()), 0.96f), 20f, this));
        U.shadow(topBar, 12f);
        Ui.pad(topBar, this, 6, 6, 6, 6);

        topBar.addView(roundButton(Ico.BACK, 24f, new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        }));

        barTitle = Ui.tv(this, "", 15f, Skin.paperInk(theme()), U.uiMed(this));
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 6);
        tlp.rightMargin = U.dp(this, 6);
        topBar.addView(barTitle, tlp);

        markIcon = new IconView(this, Ico.MARK, Skin.paperInk(theme()), 24f);
        topBar.addView(roundButtonWrap(markIcon, new Runnable() {
            @Override
            public void run() {
                toggleBookmark();
            }
        }));

        topBar.addView(roundButton(Ico.MORE, 24f, new Runnable() {
            @Override
            public void run() {
                menuSheet();
            }
        }));
        Ui.click(barTitle, new Runnable() {
            @Override
            public void run() {
                tocSheet();
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, U.dp(this, 58));
        lp.gravity = Gravity.TOP;
        lp.setMargins(U.dp(this, 10), U.dp(this, 8), U.dp(this, 10), 0);
        root.addView(topBar, lp);
    }

    /** Нижняя панель: назад, мельче, номер страницы, крупнее, вперёд. */
    private void buildBottomBar() {
        bottomBar = Ui.row(this);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackground(U.round(Skin.withAlpha(Skin.paper(theme()), 0.96f), 20f, this));
        U.shadow(bottomBar, 12f);
        Ui.pad(bottomBar, this, 8, 7, 8, 7);

        bottomBar.addView(roundButton(Ico.LEFT, 26f, new Runnable() {
            @Override
            public void run() {
                prevPage();
            }
        }));

        pageLabel = Ui.tv(this, "", 15f, Skin.paperInk(theme()), U.uiMed(this));
        pageLabel.setGravity(Gravity.CENTER);
        bottomBar.addView(roundButton(Ico.MINUS, 24f, new Runnable() {
            @Override
            public void run() {
                zoomText(-1);
            }
        }));
        bottomBar.addView(pageLabel, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Ui.click(pageLabel, new Runnable() {
            @Override
            public void run() {
                jumpSheet();
            }
        });
        bottomBar.addView(roundButton(Ico.PLUS, 24f, new Runnable() {
            @Override
            public void run() {
                zoomText(1);
            }
        }));

        bottomBar.addView(roundButton(Ico.RIGHT, 26f, new Runnable() {
            @Override
            public void run() {
                nextPage();
            }
        }));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, U.dp(this, 58));
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(U.dp(this, 10), 0, U.dp(this, 10), U.dp(this, 8));
        root.addView(bottomBar, lp);
    }

    /** Круглая кнопка с крупной иконкой. */
    private View roundButton(int ico, float sizeDp, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 46);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        IconView iv = new IconView(this, ico, Skin.paperInk(theme()), sizeDp);
        f.addView(iv, Ui.flp(U.dp(this, sizeDp), U.dp(this, sizeDp), Gravity.CENTER));
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        });
        return f;
    }

    private View roundButtonWrap(View inner, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 46);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        inner.setLayoutParams(Ui.flp(U.dp(this, 24), U.dp(this, 24), Gravity.CENTER));
        f.addView(inner);
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        });
        return f;
    }

    /* ==================== Страницы ==================== */

    private Paginator.Opt opt() {
        int w = (int) viewer.pageRect().width();
        int h = (int) viewer.pageRect().height();
        if (w <= 0 || h <= 0) {
            w = U.screenW(this) - U.dp(this, 28);
            h = U.screenH(this) - U.dp(this, 150);
        }
        // лист выше экрана: по вертикали читаем продолжение страницы
        return SettingsPanel.opt(this, w, Math.round(h * LEAF));
    }

    private void rebuild(int startBlock) {
        if (!ready) return;

        pages = paginator.paginate(book, chapter, opt());
        if (pages.pages.isEmpty()) pages.pages.add(new Paginator.Page());
        Book.Toc toc = book.tocAt(chapter);
        chapterStartBlock = toc == null ? 0 : Math.max(0, toc.blockIndex);

        int target = 0;
        for (int i = 0; i < pages.pages.size(); i++) {
            if (pages.pages.get(i).firstBlock <= startBlock) target = i;
        }
        pageIndex = Math.max(0, Math.min(pages.pages.size() - 1, target));
        pageCache.clear();
        viewer.jump();
        preparePages();
        showPage();
    }

    /** Рисует текущую страницу и её соседей — листание получается мгновенным. */
    private void preparePages() {
        if (pages == null || pages.pages.isEmpty()) return;
        viewer.setPages(cache(pageIndex), cache(pageIndex + 1), cache(pageIndex - 1));
    }

    private Bitmap cache(int index) {
        if (pages == null || index < 0 || index >= pages.pages.size()) return null;
        Bitmap bm = pageCache.get(index);
        if (bm != null && !bm.isRecycled()) return bm;
        bm = renderPage(index);
        if (bm != null) pageCache.put(index, bm);
        return bm;
    }

    private Bitmap renderPage(int index) {
        if (pages == null || index < 0 || index >= pages.pages.size()) return null;
        int w = (int) viewer.pageRect().width();
        int h = (int) viewer.pageRect().height();
        if (w <= 0 || h <= 0) return null;

        if (renderer == null) {
            renderer = new PageView(this);
            renderer.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        renderer.setChromeVisible(false);      // название главы и номер рисуют панели
        renderer.setPage(pages.pages.get(index), opt());
        Book.Toc toc = book.tocAt(chapter);
        renderer.setMeta(book.t(Loc.lang()), toc == null ? "" : toc.text, index + 1, pages.pages.size());
        renderer.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        renderer.layout(0, 0, w, h);

        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(bm));
        return bm;
    }

    private void showPage() {
        if (pages == null || pages.pages.isEmpty()) return;
        pageIndex = Math.max(0, Math.min(pages.pages.size() - 1, pageIndex));
        Book.Toc toc = book.tocAt(chapter);
        String title = toc == null ? book.t(Loc.lang()) : toc.text;
        viewer.setTheme(theme());
        viewer.setMeta(title, pageIndex + 1, pages.pages.size());
        viewer.setBookmarked(Lib.get().markAt(book.id, chapter, pageIndex) != null);
        barTitle.setText(title);
        pageLabel.setText(getString(R.string.page_of, pageIndex + 1, pages.pages.size()));
        updateMarkIcon();
    }

    private void afterTurn(boolean forward) {
        if (pages == null || pages.pages.isEmpty()) return;
        if (forward) {
            if (pageIndex >= pages.pages.size() - 1) {
                if (chapter + 1 < book.toc.size()) {
                    chapter++;
                    rebuild(book.tocAt(chapter).blockIndex);
                    saveProgress();
                } else {
                    viewer.jump();
                    preparePages();
                    U.pill(this, getString(R.string.book_finished));
                }
                return;
            }
            pageIndex++;
        } else {
            if (pageIndex <= 0) {
                if (chapter > 0) {
                    chapter--;
                    rebuild(book.tocAt(chapter).blockIndex);
                    if (pages != null && !pages.pages.isEmpty()) {
                        pageIndex = pages.pages.size() - 1;
                        viewer.jump();
                        preparePages();
                        showPage();
                    }
                    saveProgress();
                } else {
                    viewer.jump();
                    preparePages();
                }
                return;
            }
            pageIndex--;
        }
        preparePages();
        showPage();
        Lib.get().addPage();
        saveProgress();
    }

    private void nextPage() {
        if (pages == null || pages.pages.isEmpty()) return;
        if (pageIndex >= pages.pages.size() - 1 && chapter + 1 >= book.toc.size()) {
            U.pill(this, getString(R.string.book_finished));
            return;
        }
        viewer.turn(true);
    }

    private void prevPage() {
        if (pages == null || pages.pages.isEmpty()) return;
        if (pageIndex <= 0 && chapter <= 0) return;
        viewer.turn(false);
    }

    /** Касание листа: слева и справа перелистываем, посередине — прячем панели. */
    private void tapAt(float x) {
        float w = Math.max(1f, viewer.getWidth());
        if (x < w * 0.25f) {
            prevPage();
        } else if (x > w * 0.75f) {
            nextPage();
        } else {
            toggleChrome();
        }
    }

    /** Панели прячутся, чтобы читать «как в книге», и возвращаются по касанию. */
    private void toggleChrome() {
        chromeShown = !chromeShown;
        U.fade(topBar, chromeShown, 180);
        U.fade(bottomBar, chromeShown, 180);
    }

    private void updateMarkIcon() {
        boolean marked = Lib.get().markAt(book.id, chapter, pageIndex) != null;
        markIcon.setColor(marked ? Skin.paperAccent(theme()) : Skin.paperInk(theme()));
        markIcon.setInset(marked ? 0.10f : 0.16f);
    }

    private void toggleBookmark() {
        Book.Toc toc = book.tocAt(chapter);
        boolean added = Lib.get().toggleMark(book, chapter, pageIndex,
                toc == null ? "" : toc.text,
                pages != null && !pages.pages.isEmpty()
                        ? Paginator.pageText(pages.pages.get(Math.min(pageIndex, pages.pages.size() - 1)))
                        : "");
        updateMarkIcon();
        viewer.setBookmarked(added);
        U.pill(this, getString(added ? R.string.bookmark_added : R.string.bookmark_removed));
    }

    /* ==================== Нижние панели ==================== */

    /** Понятное меню: крупные строки с подписями. */
    private void menuSheet() {
        LinearLayout c = Ui.col(this);
        c.addView(menuRow(Ico.LIST, getString(R.string.contents), new Runnable() {
            @Override
            public void run() {
                tocSheet();
            }
        }));
        c.addView(menuRow(Ico.SEARCH_BOOK, getString(R.string.search), new Runnable() {
            @Override
            public void run() {
                searchSheet();
            }
        }));
        c.addView(menuRow(Ico.MARK, getString(R.string.tab_bookmarks), new Runnable() {
            @Override
            public void run() {
                marksSheet();
            }
        }));
        c.addView(menuRow(Ico.GEAR, getString(R.string.reading_settings), new Runnable() {
            @Override
            public void run() {
                settingsSheet();
            }
        }));
        c.addView(menuRow(Ico.MAIL, getString(R.string.report_typo), new Runnable() {
            @Override
            public void run() {
                reportTypo();
            }
        }));
        Chrome.Sheet sheet = new Chrome.Sheet(this, null, c);
        root.addView(sheet.root());
        sheet.show();
    }

    private View menuRow(int icon, String text, final Runnable action) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(r, this, 6, 13, 6, 13);
        r.addView(new IconView(this, icon, Skin.accent(this), 22f));
        TextView t = Ui.tv(this, text, 16f, Skin.ink(this), U.ui(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(this, 15);
        r.addView(t, lp);
        r.addView(new IconView(this, Ico.RIGHT, Skin.withAlpha(Skin.sub(this), 0.7f), 16f));
        Ui.click(r, action);
        return r;
    }

    /** Быстрый переход по книге и размер текста. */
    private void jumpSheet() {
        LinearLayout c = Ui.col(this);
        final Widgets.Slider slider = new Widgets.Slider(this);
        slider.setColors(Skin.surface2(this), Skin.accent(this), Skin.surface(this));
        slider.setLayoutParams(Ui.llpMatchH(U.dp(this, 36)));
        c.addView(slider);
        if (pages != null) {
            final int total = pages.pages.size();
            slider.set(total <= 1 ? 1f : pageIndex / (float) (total - 1));
            slider.setListener(new Widgets.OnSlide() {
                @Override
                public void onSlide(float value, boolean fromUser) {
                    if (!fromUser) return;
                    int target = Math.min(total - 1, Math.round(value * (total - 1)));
                    if (target != pageIndex) {
                        pageIndex = target;
                        pageCache.clear();
                        viewer.jump();
                        preparePages();
                        showPage();
                        saveProgress();
                    }
                }
            });
        }
        c.addView(Ui.space(this, 14));

        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER);
        row.addView(sizeButton(Ico.MINUS, -1));
        final TextView size = Ui.tv(this, getString(R.string.text_size) + ": " + P.i(P.readerSize, 19),
                15f, Skin.ink(this), U.uiMed(this));
        size.setGravity(Gravity.CENTER);
        row.addView(size, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sizeButton(Ico.PLUS, 1));
        c.addView(row);

        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.go_to_page), c);
        root.addView(sheet.root());
        sheet.show();
    }

    private View sizeButton(int ico, final int delta) {
        FrameLayout f = Ui.frame(this);
        f.setBackground(U.round(Skin.surface2(this), 16f, this));
        f.setLayoutParams(new LinearLayout.LayoutParams(U.dp(this, 52), U.dp(this, 48)));
        f.addView(new IconView(this, ico, Skin.ink(this), 24f),
                Ui.flp(U.dp(this, 24), U.dp(this, 24), Gravity.CENTER));
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                zoomText(delta);
            }
        });
        return f;
    }

    private void zoomText(int delta) {
        int size = P.i(P.readerSize, 19);
        int next = Math.max(14, Math.min(32, size + delta));
        if (next == size) return;
        P.si(P.readerSize, next);
        int block = currentBlock();
        paginator.clearCache();
        pageCache.clear();
        rebuild(block);
        U.vibrate(this, 6);
    }

    private int currentBlock() {
        if (pages == null || pages.pages.isEmpty()) return Math.max(0, chapterStartBlock);
        int b = pages.pages.get(Math.max(0, Math.min(pageIndex, pages.pages.size() - 1))).firstBlock;
        return Math.max(0, b < 0 ? chapterStartBlock : b);
    }

    /** Закладки этой книги. */
    private void marksSheet() {
        LinearLayout c = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout in = Ui.col(this);
        int n = 0;
        for (final Lib.Mark m : Lib.get().marks()) {
            if (!book.id.equals(m.bookId)) continue;
            n++;
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(r, this, 6, 12, 6, 12);
            r.addView(new IconView(this, Ico.MARK, Skin.accent(this), 18f));
            LinearLayout col = Ui.col(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = U.dp(this, 14);
            TextView head = Ui.tv(this, U.trimTo(m.chapterTitle, 40), 12f, Skin.accent(this), U.uiMed(this));
            col.addView(head);
            TextView sn = Ui.tv(this, U.trimTo(m.snippet, 110), 14f, Skin.ink(this), U.ui(this));
            sn.setMaxLines(2);
            col.addView(sn);
            r.addView(col, lp);
            Ui.click(r, new Runnable() {
                @Override
                public void run() {
                    chapter = Math.max(0, Math.min(m.chapter, book.toc.size() - 1));
                    rebuild(m.block);
                }
            });
            in.addView(r);
        }
        if (n == 0) in.addView(Ui.tv(this, getString(R.string.no_bookmarks), 15f,
                Skin.sub(this), U.ui(this)));
        sc.addView(in);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(this) * 0.6f), U.dp(this, 520))));
        c.addView(sc);
        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.tab_bookmarks), c);
        root.addView(sheet.root());
        sheet.show();
    }

    private void tocSheet() {
        LinearLayout c = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout in = Ui.col(this);
        for (int i = 0; i < book.toc.size(); i++) {
            final Book.Toc t = book.toc.get(i);
            if (U.empty(t.text)) continue;
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(r, this, t.level == 0 ? 4 : (t.level == 1 ? 12 : 22), 12, 4, 12);
            boolean currentRow = i == chapter;
            TextView tv = Ui.tv(this, t.text, t.level == 0 ? 16.5f : 15f,
                    currentRow ? Skin.accent(this) : Skin.ink(this),
                    t.level == 0 ? U.tf("serif", Typeface.BOLD) : U.ui(this));
            r.addView(tv, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (currentRow) r.addView(new IconView(this, Ico.CHECK, Skin.accent(this), 16f));
            final int index = i;
            Ui.click(r, new Runnable() {
                @Override
                public void run() {
                    chapter = index;
                    int block = book.tocAt(index) == null ? 0 : Math.max(0, book.tocAt(index).blockIndex);
                    rebuild(block);
                    saveProgress();
                }
            });
            in.addView(r);
        }
        sc.addView(in);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(this) * 0.62f), U.dp(this, 560))));
        c.addView(sc);
        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.contents), c);
        root.addView(sheet.root());
        sheet.show();
    }

    private void searchSheet() {
        LinearLayout c = Ui.col(this);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.search_in_book));
        input.setHintTextColor(Skin.withAlpha(Skin.sub(this), 0.7f));
        input.setTextColor(Skin.ink(this));
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setBackground(U.round(Skin.surface2(this), 16f, this));
        Ui.pad(input, this, 16, 14, 16, 14);
        c.addView(input);

        final LinearLayout results = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        sc.addView(results);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(this) * 0.5f), U.dp(this, 460))));
        LinearLayout.LayoutParams slp = Ui.llpMatch();
        slp.topMargin = U.dp(this, 12);
        c.addView(sc, slp);

        final Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.search_in_book), c);
        root.addView(sheet.root());
        sheet.show();

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int cc) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int cc) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().trim();
                results.removeAllViews();
                if (q.length() < 2) return;
                String nq = U.norm(q);
                int found = 0;
                for (int i = 0; i < book.blocks.size() && found < 40; i++) {
                    Block b = book.blocks.get(i);
                    if (!b.hasText()) continue;
                    String plain = b.plain();
                    int at = U.norm(plain).indexOf(nq);
                    if (at < 0) continue;
                    found++;
                    final int blockIndex = i;
                    LinearLayout r = Ui.col(ReaderActivity.this);
                    Ui.pad(r, ReaderActivity.this, 8, 12, 8, 12);
                    Book.Toc t = book.tocAt(book.chapterOf(i));
                    r.addView(Ui.tv(ReaderActivity.this, t == null ? "" : t.text, 12f,
                            Skin.accent(ReaderActivity.this), U.uiMed(ReaderActivity.this)));
                    TextView sn = Ui.tv(ReaderActivity.this, snippet(plain, at, nq.length()), 15f,
                            Skin.ink(ReaderActivity.this), U.ui(ReaderActivity.this));
                    sn.setMaxLines(3);
                    LinearLayout.LayoutParams lp2 = Ui.llpMatch();
                    lp2.topMargin = U.dp(ReaderActivity.this, 4);
                    r.addView(sn, lp2);
                    Ui.click(r, new Runnable() {
                        @Override
                        public void run() {
                            chapter = book.chapterOf(blockIndex);
                            rebuild(blockIndex);
                            sheet.hide();
                        }
                    });
                    results.addView(r);
                }
                if (found == 0) {
                    results.addView(Ui.tv(ReaderActivity.this, getString(R.string.search_nothing), 15f,
                            Skin.sub(ReaderActivity.this), U.ui(ReaderActivity.this)));
                }
            }
        });
    }

    private static String snippet(String text, int at, int len) {
        int start = Math.max(0, at - 30);
        int end = Math.min(text.length(), at + len + 50);
        String s = text.substring(start, end).replace('\n', ' ').trim();
        return (start > 0 ? "… " : "") + s + (end < text.length() ? " …" : "");
    }

    private void settingsSheet() {
        LinearLayout c = (LinearLayout) SettingsPanel.build(this, new SettingsPanel.Changed() {
            @Override
            public void onChanged() {
                paginator.clearCache();
                pageCache.clear();
                applyReaderColors();
                rebuild(currentBlock());
                applyKeepScreenOn();
            }
        }, true);
        c.addView(Ui.hline(this, Skin.line(this), 1f));
        c.addView(menuRow(Ico.MAIL, getString(R.string.report_typo), new Runnable() {
            @Override
            public void run() {
                reportTypo();
            }
        }));
        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.reading_settings), c);
        root.addView(sheet.root());
        sheet.show();
    }

    /** Письмо об ошибке: системное «Поделиться» с готовым текстом. */
    private void reportTypo() {
        if (book == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.report_typo_head)).append("\n\n");
        sb.append(getString(R.string.book_label)).append(": ").append(book.t(Loc.lang())).append('\n');
        Book.Toc t = book.tocAt(chapter);
        if (t != null) sb.append(getString(R.string.chapter)).append(": ").append(t.text).append('\n');
        if (pages != null && !pages.pages.isEmpty()) {
            sb.append(getString(R.string.page)).append(": ")
                    .append(Math.min(pageIndex + 1, pages.pages.size())).append(" / ")
                    .append(pages.pages.size()).append('\n');
        }
        String sample = currentSample();
        if (!U.empty(sample)) sb.append('\n').append(sample).append('\n');
        sb.append('\n').append(getString(R.string.report_typo_hint));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " — " + getString(R.string.report_typo));
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            startActivity(Intent.createChooser(i, getString(R.string.report_typo)));
        } catch (ActivityNotFoundException e) {
            U.pill(this, getString(R.string.no_email_app));
        }
    }

    private String currentSample() {
        StringBuilder sb = new StringBuilder();
        if (pages == null || pages.pages.isEmpty()) return "";
        Paginator.Page pg = pages.pages.get(Math.max(0, Math.min(pageIndex, pages.pages.size() - 1)));
        for (Paginator.Item it : pg.items) {
            int bi = it.blockIndex;
            if (bi < 0 || bi >= book.blocks.size()) continue;
            String line = book.blocks.get(bi).plain();
            if (U.empty(line)) continue;
            sb.append(line).append(' ');
            if (sb.length() > 220) break;
        }
        return U.trimTo(sb.toString().trim(), 260);
    }

    private void applyReaderColors() {
        int t = theme();
        int ink = Skin.paperInk(t);
        int paper = Skin.paper(t);
        root.setBackgroundColor(Skin.readerFrame(t));
        viewer.setTheme(t);
        topBar.setBackground(U.round(Skin.withAlpha(paper, 0.96f), 20f, this));
        bottomBar.setBackground(U.round(Skin.withAlpha(paper, 0.96f), 20f, this));
        barTitle.setTextColor(ink);
        pageLabel.setTextColor(ink);
        markIcon.setColor(ink);
        if (dim != null) dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
        tintIcons(topBar, ink);
        tintIcons(bottomBar, ink);
        pageCache.clear();
        preparePages();
    }

    private void tintIcons(View v, int color) {
        if (v instanceof IconView) {
            ((IconView) v).setColor(color);
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tintIcons(g.getChildAt(i), color);
        }
    }

    private void applyKeepScreenOn() {
        if (P.b(P.readerKeepOn, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /* ==================== Жизненный цикл ==================== */

    @Override
    public void onBackPressed() {
        saveProgress();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Lib.get().addReadingTime(System.currentTimeMillis() - sessionStart);
        saveProgress();
        paginator.clearImages();
    }

    private void saveProgress() {
        if (book == null) return;
        int block = Math.max(0, currentBlock());
        int percent = book.blocks.isEmpty() ? 0
                : Math.max(0, Math.min(100, Math.round(block * 100f / book.blocks.size())));
        Lib.get().saveProg(book.id, chapter, block, pageIndex, percent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            nextPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            prevPage();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
