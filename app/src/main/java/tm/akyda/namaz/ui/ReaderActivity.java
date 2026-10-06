package tm.akyda.namaz.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tm.akyda.namaz.Block;
import tm.akyda.namaz.Book;
import tm.akyda.namaz.BookView;
import tm.akyda.namaz.Chrome;
import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.FlowView;
import tm.akyda.namaz.Ico;
import tm.akyda.namaz.IconView;
import tm.akyda.namaz.Lib;
import tm.akyda.namaz.Loc;
import tm.akyda.namaz.Nav;
import tm.akyda.namaz.P;
import tm.akyda.namaz.PageView;
import tm.akyda.namaz.Paginator;
import tm.akyda.namaz.R;
import tm.akyda.namaz.Skin;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/**
 * Читалка книги: бумажная страница с объёмным перелистыванием, режим прокрутки,
 * масштаб текста прямо на экране, оригинал скана, оглавление, поиск, закладки.
 */
public class ReaderActivity extends BaseActivity {

    private Book book;
    private Paginator paginator;
    private Paginator.Result pages;

    private int chapter = 0;
    private int pageIndex = 0;

    private FrameLayout stage;
    private BookView bookView;
    private PageView renderer;          // невидимый «печатник» страниц в битмапы
    private ScrollView scrollWrap;
    private LinearLayout scrollList;
    private View dim;

    private LinearLayout topBar, bottomBar;
    private TextView barTitle, pageLabel, zoomLabel;
    private IconView markIcon;
    private Widgets.Slider slider;

    private boolean barsVisible = true;
    private boolean scrollMode = false;
    private boolean ready = false;

    private long sessionStart = 0;
    private long lastAutoHide = 0;

    /** Кэш готовых страниц-картинок (мало памяти, мгновенное перелистывание). */
    private final Map<Integer, Bitmap> pageCache = new LinkedHashMap<Integer, Bitmap>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
            return size() > 4;
        }
    };

    private int chapterStartBlock = 0;

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
            finish();
            return;
        }
        paginator = new Paginator(this);
        chapter = Math.max(0, getIntent().getIntExtra(Nav.EXTRA_CHAPTER, Lib.get().prog(book.id).chapter));
        if (chapter >= book.toc.size()) chapter = 0;
        final int startBlock = getIntent().getIntExtra(Nav.EXTRA_BLOCK, Lib.get().prog(book.id).block);

        buildUi();
        applyKeepScreenOn();
        sessionStart = System.currentTimeMillis();
        lastAutoHide = sessionStart;

        stage.post(new Runnable() {
            @Override
            public void run() {
                ready = true;
                if (P.i(P.readerMode, 0) == 1) {
                    enterScrollMode(startBlock);
                } else {
                    rebuild(startBlock);
                }
            }
        });
    }

    /* ==================== Интерфейс ==================== */

    private void buildUi() {
        FrameLayout root = Ui.frame(this);
        root.setBackgroundColor(Skin.readerFrame(P.i(P.readerTheme, Skin.T_PAPER)));

        stage = Ui.frame(this);
        root.addView(stage, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        bookView = new BookView(this);
        bookView.setTheme(P.i(P.readerTheme, Skin.T_PAPER));
        bookView.setListener(new BookView.Listener() {
            @Override
            public void onNeedPages() {
                prepareNeighbours();
            }

            @Override
            public void onTurned(int dir) {
                afterTurn(dir);
            }

            @Override
            public void onTap(float x, float y) {
                tapAt(x, y);
            }
        });
        stage.addView(bookView, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Затемнение для чтения в темноте
        dim = new View(this);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
        dim.setClickable(false);
        root.addView(dim, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        buildBars(root);
        setContentView(root);
    }

    /** Панели: «плавающие» карточки с мягкими углами, как обложка книги. */
    private void buildBars(FrameLayout root) {
        int theme = P.i(P.readerTheme, Skin.T_PAPER);
        int paper = Skin.paper(theme);

        // --- верхняя панель ---
        topBar = Ui.row(this);
        topBar.setBackground(U.round(Skin.withAlpha(paper, 0.97f), 22f, this));
        U.shadow(topBar, 10f);
        Ui.pad(topBar, this, 6, 6, 6, 6);
        topBar.addView(barButton(Ico.BACK, new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        }));
        barTitle = Ui.tv(this, "", 13.5f, Skin.paperInk(theme), U.uiMed(this));
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 4);
        topBar.addView(barTitle, tlp);
        markIcon = new IconView(this, Ico.MARK, Skin.paperInk(theme), 21f);
        topBar.addView(clickable(markIcon, new Runnable() {
            @Override
            public void run() {
                toggleBookmark();
            }
        }));
        topBar.addView(barButton(Ico.LIST, new Runnable() {
            @Override
            public void run() {
                tocSheet();
            }
        }));
        topBar.addView(barButton(Ico.SEARCH_BOOK, new Runnable() {
            @Override
            public void run() {
                searchSheet();
            }
        }));
        topBar.addView(barButton(Ico.GEAR, new Runnable() {
            @Override
            public void run() {
                settingsSheet();
            }
        }));
        FrameLayout.LayoutParams tblp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, U.dp(this, 56));
        tblp.gravity = Gravity.TOP;
        tblp.setMargins(U.dp(this, 10), U.dp(this, 10), U.dp(this, 10), 0);
        root.addView(topBar, tblp);

        // --- нижняя панель ---
        bottomBar = Ui.col(this);
        bottomBar.setBackground(U.round(Skin.withAlpha(paper, 0.97f), 22f, this));
        U.shadow(bottomBar, 10f);
        Ui.pad(bottomBar, this, 14, 8, 14, 10);

        slider = new Widgets.Slider(this);
        slider.setColors(Skin.withAlpha(Skin.paperInk(theme), 0.14f), Skin.paperAccent(theme), paper);
        slider.setLayoutParams(Ui.llpMatchH(U.dp(this, 26)));
        slider.setListener(pageSliderListener());
        bottomBar.addView(slider);

        LinearLayout tools = Ui.row(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);

        // Масштаб текста — прямо на экране
        tools.addView(smallButton(Ico.MINUS, new Runnable() {
            @Override
            public void run() {
                zoomText(-1);
            }
        }));
        zoomLabel = Ui.tv(this, String.valueOf(P.i(P.readerSize, 19)), 12.5f,
                Skin.paperInk(theme), U.uiMed(this));
        zoomLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(U.dp(this, 30),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tools.addView(zoomLabel, zlp);
        tools.addView(smallButton(Ico.PLUS, new Runnable() {
            @Override
            public void run() {
                zoomText(1);
            }
        }));

        pageLabel = Ui.tv(this, "", 12.5f, Skin.paperSub(theme), U.uiMed(this));
        pageLabel.setGravity(Gravity.CENTER);
        tools.addView(pageLabel, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tools.addView(smallButton(Ico.LEFT, new Runnable() {
            @Override
            public void run() {
                prevPage();
            }
        }));
        tools.addView(smallButton(Ico.RIGHT, new Runnable() {
            @Override
            public void run() {
                nextPage();
            }
        }));
        tools.addView(smallButton(Ico.LIST, new Runnable() {
            @Override
            public void run() {
                tocSheet();
            }
        }));
        bottomBar.addView(tools);

        FrameLayout.LayoutParams bblp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bblp.gravity = Gravity.BOTTOM;
        bblp.setMargins(U.dp(this, 10), 0, U.dp(this, 10), U.dp(this, 10));
        root.addView(bottomBar, bblp);
    }

    private View smallButton(int ico, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 38);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        f.addView(new IconView(this, ico, Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), 19f),
                Ui.flp(U.dp(this, 19), U.dp(this, 19), Gravity.CENTER));
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        });
        return f;
    }

    private View barButton(int ico, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 42);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        f.addView(new IconView(this, ico, Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), 21f),
                Ui.flp(U.dp(this, 21), U.dp(this, 21), Gravity.CENTER));
        Ui.click(f, new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        });
        return f;
    }

    private View clickable(View v, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 42);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        v.setLayoutParams(Ui.flp(U.dp(this, 21), U.dp(this, 21), Gravity.CENTER));
        f.addView(v);
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
        int w = (int) bookView.pageRect().width();
        int h = (int) bookView.pageRect().height();
        if (w <= 0 || h <= 0) {
            w = U.screenW(this) - U.dp(this, 30);
            h = U.screenH(this) - U.dp(this, 20);
        }
        return SettingsPanel.opt(this, w, h);
    }

    private void rebuild(int startBlock) {
        if (!ready) return;
        scrollMode = false;
        if (scrollWrap != null) scrollWrap.setVisibility(View.GONE);
        bookView.setVisibility(View.VISIBLE);
        if (slider != null) slider.setVisibility(View.VISIBLE);

        pages = paginator.paginate(book, chapter, opt());
        if (pages.pages.isEmpty()) pages.pages.add(new Paginator.Page());
        chapterStartBlock = book.tocAt(chapter) == null ? 0 : book.tocAt(chapter).blockIndex;

        int target = 0;
        for (int i = 0; i < pages.pages.size(); i++) {
            if (pages.pages.get(i).firstBlock <= startBlock) target = i;
        }
        pageIndex = Math.max(0, Math.min(pages.pages.size() - 1, target));
        pageCache.clear();
        showPage();
        bookView.softSwap();
    }

    private void showPage() {
        if (pages == null || pages.pages.isEmpty()) return;
        pageIndex = Math.max(0, Math.min(pages.pages.size() - 1, pageIndex));
        pageCache.clear();
        prepareNeighbours();

        Book.Toc toc = book.tocAt(chapter);
        String title = toc == null ? book.t(Loc.lang()) : toc.text;
        bookView.setTheme(P.i(P.readerTheme, Skin.T_PAPER));
        bookView.setMeta(title, pageIndex + 1, pages.pages.size());
        bookView.setBookmarked(Lib.get().markAt(book.id, chapter, pageIndex) != null);
        barTitle.setText(title);
        pageLabel.setText(getString(R.string.page_of, pageIndex + 1, pages.pages.size()));
        if (slider != null) {
            slider.set(pages.pages.size() <= 1 ? 1f : pageIndex / (float) (pages.pages.size() - 1));
        }
        updateMarkIcon();
        if (zoomLabel != null) zoomLabel.setText(String.valueOf(P.i(P.readerSize, 19)));
    }

    /** Рисует текущую и соседние страницы в битмапы и отдаёт их книге. */
    private void prepareNeighbours() {
        if (pages == null || pages.pages.isEmpty() || scrollMode) return;
        Bitmap cur = renderPage(pageIndex);
        Bitmap next = renderPage(pageIndex + 1);
        Bitmap prev = renderPage(pageIndex - 1);
        bookView.setPages(cur, next, prev);
    }

    private Bitmap renderPage(int index) {
        if (pages == null || index < 0 || index >= pages.pages.size()) return null;
        Bitmap cached = pageCache.get(index);
        if (cached != null && !cached.isRecycled()) return cached;

        int w = (int) bookView.pageRect().width();
        int h = (int) bookView.pageRect().height();
        if (w <= 0 || h <= 0) return null;

        if (renderer == null) {
            renderer = new PageView(this);
            renderer.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        renderer.setChromeVisible(false);
        renderer.setPage(pages.pages.get(index), opt());
        Book.Toc toc = book.tocAt(chapter);
        renderer.setMeta(book.t(Loc.lang()), toc == null ? "" : toc.text, index + 1, pages.pages.size());
        int wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        renderer.measure(wSpec, hSpec);
        renderer.layout(0, 0, w, h);

        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(bm));
        pageCache.put(index, bm);
        return bm;
    }

    /* ==================== Перелистывание ==================== */

    private void tapAt(float x, float y) {
        float w = bookView.getWidth();
        if (x < w * 0.28f) {
            prevPage();
        } else if (x > w * 0.72f) {
            nextPage();
        } else {
            toggleBars();   // только панели — ничего лишнего не всплывает
        }
    }

    private void afterTurn(int dir) {
        if (pages == null || pages.pages.isEmpty()) return;
        if (dir == BookView.DIR_RIGHT || dir == BookView.DIR_UP) {
            if (pageIndex >= pages.pages.size() - 1) {
                if (chapter + 1 < book.toc.size()) {
                    chapter++;
                    rebuild(book.tocAt(chapter).blockIndex);
                    U.pill(this, book.tocAt(chapter).text);
                    saveProgress();
                } else {
                    showPage();
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
                    if (!pages.pages.isEmpty()) pageIndex = pages.pages.size() - 1;
                    showPage();
                    saveProgress();
                } else {
                    showPage();
                }
                return;
            }
            pageIndex--;
        }
        pageCache.clear();
        prepareNeighbours();
        showPage();
        countPage();
        saveProgress();
    }

    private void nextPage() {
        if (scrollMode) {
            scrollWrap.smoothScrollBy(0, (int) (scrollWrap.getHeight() * 0.82f));
            return;
        }
        if (pages == null || pages.pages.isEmpty()) return;
        if (pageIndex >= pages.pages.size() - 1 && chapter + 1 >= book.toc.size()) {
            U.pill(this, getString(R.string.book_finished));
            return;
        }
        if (P.i(P.readerAnim, 0) == 2) {
            pageIndex = Math.min(pageIndex + 1, pages.pages.size() - 1);
            showPage();
            countPage();
            saveProgress();
            return;
        }
        bookView.flipForward(BookView.DIR_RIGHT);
        playTurnFeedback();
    }

    private void prevPage() {
        if (scrollMode) {
            scrollWrap.smoothScrollBy(0, -(int) (scrollWrap.getHeight() * 0.82f));
            return;
        }
        if (pages == null || pages.pages.isEmpty() || (pageIndex <= 0 && chapter <= 0)) return;
        if (P.i(P.readerAnim, 0) == 2) {
            if (pageIndex > 0) {
                pageIndex--;
                showPage();
            } else {
                chapter--;
                rebuild(book.tocAt(chapter).blockIndex);
                pageIndex = Math.max(0, pages.pages.size() - 1);
                showPage();
            }
            saveProgress();
            return;
        }
        bookView.flipBack(BookView.DIR_LEFT);
        playTurnFeedback();
    }

    private void countPage() {
        Lib.get().addPage();
    }

    private void playTurnFeedback() {
        if (P.b(P.readerSound, true)) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.28f);
            } catch (Exception ignored) {
            }
        }
        U.vibrate(this, 8);
    }

    /** Изменение размера текста прямо в читалке. */
    private void zoomText(int delta) {
        int size = P.i(P.readerSize, 19);
        int next = Math.max(14, Math.min(32, size + delta));
        if (next == size) return;
        P.si(P.readerSize, next);
        int block = pages == null || pages.pages.isEmpty() ? 0 : currentBlock();
        paginator.clearCache();
        pageCache.clear();
        if (scrollMode) {
            rebuildScroll(block);
        } else {
            rebuild(block);
        }
        if (zoomLabel != null) zoomLabel.setText(String.valueOf(next));
        U.vibrate(this, 6);
    }

    private int currentBlock() {
        if (scrollMode) return lastScrollBlock;
        if (pages == null || pages.pages.isEmpty()) return Math.max(0, chapterStartBlock);
        int b = pages.pages.get(Math.max(0, Math.min(pageIndex, pages.pages.size() - 1))).firstBlock;
        return Math.max(0, b < 0 ? chapterStartBlock : b);
    }

    private Widgets.OnSlide pageSliderListener() {
        return new Widgets.OnSlide() {
            @Override
            public void onSlide(float value, boolean fromUser) {
                if (!fromUser) return;
                if (scrollMode) {
                    if (scrollList != null && scrollWrap != null) {
                        int total = scrollList.getHeight() - scrollWrap.getHeight();
                        scrollWrap.scrollTo(0, (int) (Math.max(0, total) * value));
                    }
                    return;
                }
                if (pages == null || pages.pages.isEmpty()) return;
                int total = pages.pages.size();
                int target = Math.min(total - 1, Math.round(value * (total - 1)));
                if (target != pageIndex) {
                    pageIndex = target;
                    showPage();
                    saveProgress();
                }
            }
        };
    }

    /* ==================== Режим прокрутки ==================== */

    private int lastScrollBlock = 0;
    private List<Paginator.Item> flow;
    private float[] flowTops;
    private float flowTotal;
    private FlowView[] chunks;
    private float[] chunkTops;
    private boolean scrollWatcherAdded = false;

    private void enterScrollMode(int startBlock) {
        if (scrollWrap == null) {
            scrollWrap = new ScrollView(this);
            scrollWrap.setClipToPadding(false);
            scrollWrap.setVerticalScrollBarEnabled(false);
            scrollList = Ui.col(this);
            scrollWrap.addView(scrollList, Ui.llpMatch());
            stage.addView(scrollWrap, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        bookView.setVisibility(View.GONE);
        scrollWrap.setVisibility(View.VISIBLE);
        scrollMode = true;
        rebuildScroll(startBlock);
    }

    /** Собирает «ленту» главы и режет её на точные куски — прокрутка идёт без стыков. */
    private void rebuildScroll(int startBlock) {
        if (scrollList == null) return;
        scrollList.removeAllViews();
        scrollMode = true;
        bookView.setVisibility(View.GONE);
        scrollWrap.setVisibility(View.VISIBLE);

        Paginator.Opt o = opt();
        paginator.clearCache();
        flow = paginator.flow(book, chapter, o);
        if (flow.isEmpty()) {
            flow.add(emptyItem());
        }
        flowTops = FlowView.computeTops(flow, o);
        flowTotal = FlowView.totalHeight(flowTops, flow, o);

        // куски примерно по две страницы — быстрый старт и точные стыки
        float maxChunk = Math.max(o.height, 1) * 2f;
        List<int[]> bounds = new ArrayList<int[]>();
        int i = 0;
        while (i < flow.size()) {
            int j = i;
            while (j + 1 < flow.size() && flowTops[j + 1] - flowTops[i] < maxChunk) j++;
            bounds.add(new int[]{i, j + 1});
            i = j + 1;
        }
        chunks = new FlowView[bounds.size()];
        for (int k = 0; k < bounds.size(); k++) {
            int[] bd = bounds.get(k);
            FlowView fv = new FlowView(this, flow, flowTops, bd[0], bd[1], o,
                    k == 0, k == bounds.size() - 1);
            fv.setTag(Integer.valueOf(k));
            chunks[k] = fv;
            scrollList.addView(fv);
        }
        lastScrollBlock = startBlock;

        // столбик весов, чтобы точно попадать в нужное место
        int startItem = 0;
        for (int k = 0; k < flow.size(); k++) {
            if (flow.get(k).blockIndex <= startBlock) startItem = k;
        }
        final int targetChunk = chunkOfItem(startItem);
        pageIndex = targetChunk;
        updateScrollLabel();
        addScrollWatcher();
        scrollWrap.post(new Runnable() {
            @Override
            public void run() {
                if (targetChunk >= 0 && targetChunk < scrollList.getChildCount()) {
                    scrollWrap.scrollTo(0, scrollList.getChildAt(targetChunk).getTop()
                            + U.dp(ReaderActivity.this, 1));
                }
                updateScrollLabel();
            }
        });
    }

    private int chunkOfItem(int item) {
        if (chunks == null || chunks.length == 0) return 0;
        int idx = Math.max(0, Math.min(item, flowTops.length - 1));
        float y = flowTops[idx];
        for (int k = 0; k < chunks.length; k++) {
            if (chunks[k] == null) continue;
            if (chunks[k].flowTo() > y) return k;
        }
        return chunks.length - 1;
    }

    private Paginator.Item emptyItem() {
        Paginator.Item it = new Paginator.Item();
        it.type = Block.P;
        it.text = "";
        it.height = opt().textSize * 2f;
        it.blockIndex = 0;
        return it;
    }

    private void addScrollWatcher() {
        if (scrollWatcherAdded) return;
        scrollWatcherAdded = true;
        scrollWrap.getViewTreeObserver().addOnScrollChangedListener(
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        if (scrollWrap != null) onScrolled(scrollWrap.getScrollY());
                    }
                });
    }

    private void onScrolled(int y) {
        if (scrollList == null || chunks == null || chunks.length == 0) return;
        int probe = y + Math.max(U.dp(this, 60), scrollWrap.getHeight() / 3);
        int best = 0;
        for (int i = 0; i < scrollList.getChildCount(); i++) {
            View child = scrollList.getChildAt(i);
            if (child.getTop() <= probe) best = i;
        }
        if (best != pageIndex) {
            pageIndex = best;
            FlowView fv = chunks[Math.min(best, chunks.length - 1)];
            lastScrollBlock = fv.blockAt(probe - fv.getTop());
            autoHideBars();
        }
        updateScrollLabel();
    }

    private void updateScrollLabel() {
        if (scrollList == null || scrollWrap == null) return;
        int total = Math.max(1, scrollList.getHeight() - scrollWrap.getHeight());
        int percent = Math.max(0, Math.min(100, Math.round(scrollWrap.getScrollY() * 100f / total)));
        pageLabel.setText(getString(R.string.book_progress, percent));
        if (slider != null) {
            slider.set(Math.max(0f, Math.min(1f, scrollWrap.getScrollY() / (float) total)));
        }
        Book.Toc toc = book.tocAt(chapter);
        if (toc != null) barTitle.setText(toc.text);
    }


    /* ==================== Панели ==================== */

    private void toggleBars() {
        barsVisible = !barsVisible;
        float dist = U.dpf(this, 90);
        topBar.animate().translationY(barsVisible ? 0 : -dist).alpha(barsVisible ? 1f : 0f)
                .setDuration(210).start();
        bottomBar.animate().translationY(barsVisible ? 0 : dist).alpha(barsVisible ? 1f : 0f)
                .setDuration(210).start();
        if (bookView != null) bookView.setChromeVisible(!barsVisible);
        lastAutoHide = System.currentTimeMillis();
    }

    private void autoHideBars() {
        if (!barsVisible) return;
        if (System.currentTimeMillis() - lastAutoHide < 5200) return;
        toggleBars();
    }

    /* ==================== Закладки ==================== */

    private void toggleBookmark() {
        Book.Toc toc = book.tocAt(chapter);
        boolean added = Lib.get().toggleMark(book, chapter, pageIndex,
                toc == null ? "" : toc.text,
                pages != null && !pages.pages.isEmpty()
                        ? Paginator.pageText(pages.pages.get(Math.min(pageIndex, pages.pages.size() - 1)))
                        : "");
        updateMarkIcon();
        bookView.setBookmarked(added);
        U.pill(this, getString(added ? R.string.bookmark_added : R.string.bookmark_removed));
    }

    private void updateMarkIcon() {
        boolean marked = Lib.get().markAt(book.id, chapter, pageIndex) != null;
        markIcon.setColor(marked ? Skin.paperAccent(P.i(P.readerTheme, Skin.T_PAPER))
                : Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)));
        markIcon.setInset(marked ? 0.14f : 0.18f);
    }

    /* ==================== Нижние панели ==================== */

    private void quoteSheet(final Paginator.Item item) {
        LinearLayout c = Ui.col(this);
        String previewText = item.blockIndex >= 0 && item.blockIndex < book.blocks.size()
                ? book.blocks.get(item.blockIndex).plain() : item.text;
        TextView preview = Ui.tv(this, U.trimTo(previewText, 260), 15f, Skin.ink(this),
                U.tf(Skin.familyName(P.i(P.readerFont, Skin.F_SERIF)), Typeface.NORMAL));
        preview.setLineSpacing(U.dpf(this, 6f), 1.18f);
        c.addView(preview);
        c.addView(Ui.space(this, 14));

        final String full = previewText;
        c.addView(sheetRow(Ico.QUOTE, getString(R.string.save_quote), new Runnable() {
            @Override
            public void run() {
                Lib.get().addQuote(book, chapter, item.blockIndex, full);
                toast(getString(R.string.quote_saved));
            }
        }));
        c.addView(sheetRow(Ico.COPY, getString(R.string.copy), new Runnable() {
            @Override
            public void run() {
                U.copy(ReaderActivity.this, full);
                toast(getString(R.string.copied));
            }
        }));
        c.addView(sheetRow(Ico.SHARE, getString(R.string.share), new Runnable() {
            @Override
            public void run() {
                U.share(ReaderActivity.this, full + "\n— " + book.t(Loc.lang()));
            }
        }));
        Chrome.Sheet sheet = new Chrome.Sheet(this, null, c);
        rootView().addView(sheet.root());
        sheet.show();
    }

    private View sheetRow(int icon, String text, final Runnable action) {
        LinearLayout r = Ui.row(this);
        Ui.pad(r, this, 4, 14, 4, 14);
        r.addView(new IconView(this, icon, Skin.accent(this), 20f));
        TextView t = Ui.tv(this, text, 15.5f, Skin.ink(this), U.ui(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = U.dp(this, 14);
        r.addView(t, lp);
        Ui.click(r, action);
        return r;
    }

    private void tocSheet() {
        LinearLayout c = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout in = Ui.col(this);
        for (int i = 0; i < book.toc.size(); i++) {
            Book.Toc t = book.toc.get(i);
            if (U.empty(t.text)) continue;
            LinearLayout r = Ui.row(this);
            Ui.pad(r, this, t.level == 0 ? 4 : (t.level == 1 ? 10 : 20), 12, 4, 12);
            boolean current = i == chapter;
            final int index = i;
            TextView tv = Ui.tv(this, t.text, t.level == 0 ? 16f : 15f,
                    current ? Skin.accent(this) : Skin.ink(this),
                    t.level == 0 ? U.tf("serif", Typeface.BOLD) : U.uiMed(this));
            r.addView(tv, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (current) r.addView(new IconView(this, Ico.CHECK, Skin.accent(this), 16f));
            Ui.click(r, new Runnable() {
                @Override
                public void run() {
                    chapter = index;
                    int block = book.tocAt(index) == null ? 0 : book.tocAt(index).blockIndex;
                    if (scrollMode) {
                        rebuildScroll(block);
                    } else {
                        rebuild(block);
                    }
                    saveProgress();
                }
            });
            in.addView(r);
        }
        sc.addView(in);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(this) * 0.52f), U.dp(this, 460))));
        c.addView(sc);
        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.contents), c);
        rootView().addView(sheet.root());
        sheet.show();
    }

    private void searchSheet() {
        LinearLayout c = Ui.col(this);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(getString(R.string.search_in_book));
        input.setHintTextColor(Skin.withAlpha(Skin.sub(this), 0.7f));
        input.setTextColor(Skin.ink(this));
        input.setTextSize(15.5f);
        input.setBackground(U.round(Skin.surface2(this), 16f, this));
        Ui.pad(input, this, 16, 12, 16, 12);
        c.addView(input);

        final LinearLayout results = Ui.col(this);
        ScrollView sc = new ScrollView(this);
        sc.addView(results);
        LinearLayout.LayoutParams slp = Ui.llpMatch();
        slp.topMargin = U.dp(this, 10);
        sc.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min((int) (U.screenH(this) * 0.45f), U.dp(this, 400))));
        c.addView(sc, slp);

        final Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.search_in_book), c);
        rootView().addView(sheet.root());
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
                    int ch = book.chapterOf(i);
                    Book.Toc t = book.tocAt(ch);
                    TextView head = Ui.tv(ReaderActivity.this, t == null ? "" : t.text, 11.5f,
                            Skin.accent(ReaderActivity.this), U.uiMed(ReaderActivity.this));
                    r.addView(head);
                    TextView sn = Ui.tv(ReaderActivity.this, snippet(plain, at, nq.length()), 14f,
                            Skin.ink(ReaderActivity.this), U.ui(ReaderActivity.this));
                    sn.setMaxLines(3);
                    LinearLayout.LayoutParams slp2 = Ui.llpMatch();
                    slp2.topMargin = U.dp(ReaderActivity.this, 4);
                    r.addView(sn, slp2);
                    Ui.click(r, new Runnable() {
                        @Override
                        public void run() {
                            chTo(blockIndex);
                            sheet.hide();
                        }
                    });
                    results.addView(r);
                }
                if (found == 0) {
                    results.addView(Ui.tv(ReaderActivity.this, getString(R.string.search_nothing), 14f,
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

    private void chTo(int blockIndex) {
        chapter = book.chapterOf(blockIndex);
        if (scrollMode) {
            rebuildScroll(blockIndex);
        } else {
            rebuild(blockIndex);
        }
        saveProgress();
    }

    /** Отправка сообщения об опечатке: системное окно «Поделиться» с готовым текстом. */
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
        String sample = currentPageSample();
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

    /** Первые строки текущей страницы — чтобы было понятно, о каком месте речь. */
    private String currentPageSample() {
        if (pages == null || pages.pages.isEmpty()) return "";
        int idx = Math.max(0, Math.min(pageIndex, pages.pages.size() - 1));
        StringBuilder sb = new StringBuilder();
        Paginator.Page pg = pages.pages.get(idx);
        for (Paginator.Item it : pg.items) {
            int bi = it.blockIndex;
            if (book == null || bi < 0 || bi >= book.blocks.size()) continue;
            String line = book.blocks.get(bi).plain();
            if (U.empty(line)) continue;
            sb.append(line).append(' ');
            if (sb.length() > 220) break;
        }
        return U.trimTo(sb.toString().trim(), 260);
    }

    private void settingsSheet() {
        LinearLayout c = (LinearLayout) SettingsPanel.build(this, new SettingsPanel.Changed() {
            @Override
            public void onChanged() {
                paginator.clearCache();
                pageCache.clear();
                applyReaderColors();
                int block = currentBlock();
                if (P.i(P.readerMode, 0) == 1) {
                    if (!scrollMode) enterScrollMode(block);
                    else rebuildScroll(block);
                } else {
                    if (scrollMode) {
                        scrollMode = false;
                        if (scrollWrap != null) scrollWrap.setVisibility(View.GONE);
                        bookView.setVisibility(View.VISIBLE);
                        rebuild(block);
                    } else {
                        rebuild(block);
                    }
                }
                applyKeepScreenOn();
            }
        }, true);
        c.addView(Ui.hline(this, Skin.line(this), 1f));
        c.addView(sheetRow(Ico.MAIL, getString(R.string.report_typo), new Runnable() {
            @Override
            public void run() {
                reportTypo();
            }
        }));
        Chrome.Sheet sheet = new Chrome.Sheet(this, getString(R.string.reading_settings), c);
        rootView().addView(sheet.root());
        sheet.show();
    }

    private void applyReaderColors() {
        int theme = P.i(P.readerTheme, Skin.T_PAPER);
        int ink = Skin.paperInk(theme);
        int paper = Skin.paper(theme);
        View root = findViewById(android.R.id.content);
        root.setBackgroundColor(Skin.readerFrame(theme));
        if (bookView != null) bookView.setTheme(theme);
        topBar.setBackground(U.round(Skin.withAlpha(paper, 0.97f), 22f, this));
        bottomBar.setBackground(U.round(Skin.withAlpha(paper, 0.97f), 22f, this));
        barTitle.setTextColor(ink);
        pageLabel.setTextColor(Skin.paperSub(theme));
        if (zoomLabel != null) zoomLabel.setTextColor(ink);
        markIcon.setColor(ink);
        if (slider != null) {
            slider.setColors(Skin.withAlpha(ink, 0.14f), Skin.paperAccent(theme), paper);
            slider.setListener(pageSliderListener());
        }
        if (dim != null) dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
        // перекрашиваем кнопки панелей
        tintIcons(topBar, ink);
        tintIcons(bottomBar, ink);
        if (!scrollMode) prepareNeighbours();
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
        long spent = System.currentTimeMillis() - sessionStart;
        Lib.get().addReadingTime(spent);
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
        if (!scrollMode) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                nextPage();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                prevPage();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
