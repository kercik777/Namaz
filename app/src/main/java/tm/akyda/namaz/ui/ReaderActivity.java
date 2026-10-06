package tm.akyda.namaz.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

import tm.akyda.namaz.Block;
import tm.akyda.namaz.Book;
import tm.akyda.namaz.Chrome;
import tm.akyda.namaz.ContentRepo;
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

/** Читалка: страницы, прокрутка и оригинал скана. */
public class ReaderActivity extends BaseActivity {

    private Book book;
    private Paginator paginator;
    private Paginator.Result pages;

    private int chapter = 0;
    private int pageIndex = 0;
    private int scanPage = 1;

    private FrameLayout stage;
    private PageView pageView;
    private ScrollView scrollWrap;
    private LinearLayout scrollList;
    private FrameLayout scanWrap;
    private Widgets.Zoom zoom;
    private View dim;

    private LinearLayout topBar, bottomBar;
    private TextView barTitle, pageLabel;
    private IconView markIcon;
    private boolean barsVisible = true;
    private boolean scanMode = false;
    private boolean turning = false;

    private long sessionStart = 0;
    private int pagesTurned = 0;
    private long lastAutoHide = 0;

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
        int startBlock = getIntent().getIntExtra(Nav.EXTRA_BLOCK, Lib.get().prog(book.id).block);
        scanPage = Math.max(1, Lib.get().prog(book.id).page);

        buildUi();
        rebuild(startBlock);
        applyKeepScreenOn();
        sessionStart = System.currentTimeMillis();
    }

    /* ==================== Интерфейс ==================== */

    private void buildUi() {
        FrameLayout root = Ui.frame(this);
        root.setBackgroundColor(Skin.readerFrame(P.i(P.readerTheme, Skin.T_PAPER)));

        stage = Ui.frame(this);
        root.addView(stage, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        pageView = new PageView(this);
        pageView.setBackground(U.round(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 3f, this));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        int m = U.dp(this, 7);
        plp.setMargins(m, m, m, m);
        stage.addView(pageView, plp);

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                if (Math.abs(e1.getX() - e2.getX()) < 60) return false;
                if (e2.getX() < e1.getX()) nextPage();
                else prevPage();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Paginator.Item it = pageView.itemAt(e.getX(), e.getY() - U.dp(ReaderActivity.this, 7));
                if (it != null && !U.empty(it.text)) quoteSheet(it);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                float x = e.getX();
                float w = stage.getWidth();
                if (x < w * 0.3f && !scanMode) prevPage();
                else if (x > w * 0.7f && !scanMode) nextPage();
                else toggleBars();
                return true;
            }
        });
        stage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return gd.onTouchEvent(e);
            }
        });

        // Затемнение для чтения ночью
        dim = new View(this);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
        root.addView(dim, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        dim.setClickable(false);

        // Верхняя панель
        topBar = Ui.row(this);
        topBar.setBackground(U.gradientRect(Skin.withAlpha(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 0.98f),
                Skin.withAlpha(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 0.92f), 0f, this, true));
        Ui.pad(topBar, this, 8, 8, 8, 8);
        topBar.addView(barButton(Ico.BACK, new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        }));
        barTitle = Ui.tv(this, "", 13.5f, Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), U.uiMed(this));
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = U.dp(this, 6);
        topBar.addView(barTitle, tlp);
        markIcon = new IconView(this, Ico.MARK, Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), 21f);
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
        if (book.hasScan) {
            topBar.addView(barButton(Ico.IMAGE, new Runnable() {
                @Override
                public void run() {
                    toggleScan();
                }
            }));
        }
        topBar.addView(barButton(Ico.TYPE, new Runnable() {
            @Override
            public void run() {
                settingsSheet();
            }
        }));
        FrameLayout.LayoutParams tblp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, U.dp(this, 54));
        tblp.gravity = Gravity.TOP;
        root.addView(topBar, tblp);

        // Нижняя панель
        bottomBar = Ui.col(this);
        bottomBar.setBackground(U.gradientRect(Skin.withAlpha(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 0.92f),
                Skin.withAlpha(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 0.99f), 0f, this, true));
        Ui.pad(bottomBar, this, 16, 6, 16, 10);

        Widgets.Slider slider = new Widgets.Slider(this);
        slider.setColors(Skin.withAlpha(Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), 0.14f),
                Skin.paperAccent(P.i(P.readerTheme, Skin.T_PAPER)),
                Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)));
        slider.setLayoutParams(Ui.llpMatchH(U.dp(this, 30)));
        slider.setListener(new Widgets.OnSlide() {
            @Override
            public void onSlide(float value, boolean fromUser) {
                if (!fromUser || pages == null) return;
                int total = pages.pages.size();
                int target = Math.min(total - 1, Math.round(value * (total - 1)));
                if (target != pageIndex) {
                    pageIndex = target;
                    showPage(false);
                }
            }
        });
        this.slider = slider;
        bottomBar.addView(slider);

        LinearLayout bar = Ui.row(this);
        pageLabel = Ui.tv(this, "", 12.5f, Skin.paperSub(P.i(P.readerTheme, Skin.T_PAPER)), U.uiMed(this));
        bar.addView(pageLabel, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(clickable(icon(Ico.LEFT), new Runnable() {
            @Override
            public void run() {
                prevPage();
            }
        }));
        bar.addView(clickable(icon(Ico.RIGHT), new Runnable() {
            @Override
            public void run() {
                nextPage();
            }
        }));
        bottomBar.addView(bar);
        FrameLayout.LayoutParams bblp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bblp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bblp);

        setContentView(root);
    }

    private Widgets.Slider slider;

    private IconView icon(int ico) {
        IconView v = new IconView(this, ico, Skin.paperInk(P.i(P.readerTheme, Skin.T_PAPER)), 22f);
        return v;
    }

    private View barButton(int ico, final Runnable action) {
        FrameLayout f = Ui.frame(this);
        int s = U.dp(this, 40);
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
        int s = U.dp(this, 40);
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

    /* ==================== Построение страниц ==================== */

    private void rebuild(int startBlock) {
        Paginator.Opt o = SettingsPanel.opt(this, stage.getWidth() == 0 ? U.screenW(this) - U.dp(this, 14) : stage.getWidth(),
                stage.getHeight() == 0 ? U.screenH(this) - U.dp(this, 14) : stage.getHeight());
        pages = paginator.paginate(book, chapter, o);
        if (pages.pages.isEmpty()) pages.pages.add(new Paginator.Page());

        int target = 0;
        for (int i = 0; i < pages.pages.size(); i++) {
            if (pages.pages.get(i).firstBlock <= startBlock) target = i;
        }
        pageIndex = Math.max(0, Math.min(pages.pages.size() - 1, target));
        showPage(false);
    }

    private void showPage(boolean animate) {
        if (pages == null || pages.pages.isEmpty()) return;
        Paginator.Page p = pages.pages.get(Math.max(0, Math.min(pages.pages.size() - 1, pageIndex)));
        Paginator.Opt o = SettingsPanel.opt(this, stage.getWidth(), stage.getHeight());
        pageView.setPage(p, o);
        Book.Toc toc = book.tocAt(chapter);
        pageView.setMeta(book.t(tm.akyda.namaz.Loc.lang()), toc == null ? "" : toc.text, pageIndex + 1, pages.pages.size());
        barTitle.setText((toc == null ? book.t(Loc.lang()) : toc.text));
        pageLabel.setText(getString(R.string.page_of, pageIndex + 1, pages.pages.size()));
        if (slider != null) slider.set(pages.pages.size() <= 1 ? 1f : pageIndex / (float) (pages.pages.size() - 1));
        updateMarkIcon();
        if (scrollModeBuilt) markScrollPosition();
    }

    /* ==================== Перелистывание ==================== */

    private void nextPage() {
        if (turning) return;
        if (chapterEnd()) {
            if (chapter + 1 < book.toc.size()) {
                chapter++;
                rebuild(book.tocAt(chapter).blockIndex);
                if (scrollModeBuilt) rebuildScroll();
                U.pill(this, getString(R.string.chapter) + ": " + book.tocAt(chapter).text);
                saveProgress();
                return;
            } else {
                U.pill(this, getString(R.string.book_finished));
                return;
            }
        }
        if (scrollModeBuilt) {
            scrollWrap.smoothScrollBy(0, U.dp(this, 320));
            return;
        }
        pageIndex++;
        animateTurn(1);
        countPage();
    }

    private void prevPage() {
        if (turning) return;
        if (scrollModeBuilt) {
            scrollWrap.smoothScrollBy(0, -U.dp(this, 320));
            return;
        }
        if (pageIndex == 0) {
            if (chapter > 0) {
                chapter--;
                rebuild(book.chapterRange(chapter)[1] - 1);
                if (pages.pages.size() > 0) pageIndex = pages.pages.size() - 1;
                showPage(false);
                saveProgress();
                return;
            }
            U.pill(this, getString(R.string.page) + " 1");
            return;
        }
        pageIndex--;
        animateTurn(-1);
    }

    private boolean chapterEnd() {
        return pages == null || pageIndex >= pages.pages.size() - 1;
    }

    private void animateTurn(final int dir) {
        int anim = P.i(P.readerAnim, 0);
        if (anim == 2) {
            showPage(false);
            playTurnFeedback();
            return;
        }
        turning = true;
        final float w = stage.getWidth();
        if (anim == 0) {
            pageView.animate().translationX(-dir * w * 0.55f).alpha(0.25f).setDuration(150)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            showPage(false);
                            pageView.setTranslationX(dir * w * 0.55f);
                            pageView.setAlpha(0.25f);
                            pageView.animate().translationX(0f).alpha(1f).setDuration(190)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            turning = false;
                                        }
                                    }).start();
                        }
                    }).start();
        } else {
            pageView.animate().alpha(0f).setDuration(130).withEndAction(new Runnable() {
                @Override
                public void run() {
                    showPage(false);
                    pageView.setAlpha(0f);
                    pageView.animate().alpha(1f).setDuration(170).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            turning = false;
                        }
                    }).start();
                }
            }).start();
        }
        playTurnFeedback();
    }

    private void countPage() {
        pagesTurned++;
        Lib.get().addPage();
    }

    private void playTurnFeedback() {
        if (P.b(P.readerSound, true)) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.35f);
            } catch (Exception ignored) {
            }
        }
        U.vibrate(this, 8);
    }

    /* ==================== Режим прокрутки ==================== */

    private boolean scrollModeBuilt = false;

    private void ensureScrollMode() {
        if (scrollWrap == null) {
            scrollWrap = new ScrollView(this);
            scrollWrap.setClipToPadding(false);
            scrollList = Ui.col(this);
            int m = U.dp(this, 7);
            scrollWrap.setPadding(m, m, m, m);
            scrollWrap.addView(scrollList);
            scrollWrap.setBackground(U.round(Skin.paper(P.i(P.readerTheme, Skin.T_PAPER)), 3f, this));
            stage.addView(scrollWrap, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        rebuildScroll();
    }

    private void rebuildScroll() {
        if (scrollList == null) return;
        Paginator.Opt o = SettingsPanel.opt(this, stage.getWidth() - U.dp(this, 14), stage.getHeight() - U.dp(this, 14));
        scrollList.removeAllViews();
        int[] range = book.chapterRange(chapter);
        java.util.List<Paginator.Item> flow = paginator.flow(book, chapter, o);
        int pad = U.dp(this, P.i(P.readerMargin, 20));
        for (final Paginator.Item it : flow) {
            BlockView bv = new BlockView(this, it, o, pad);
            if (it.blockIndex >= 0) {
                bv.setOnClickListener(null);
                bv.setLongClickable(true);
                bv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (!U.empty(it.text)) quoteSheet(it);
                        return true;
                    }
                });
            }
            scrollList.addView(bv);
        }
        scrollList.setPadding(0, U.dp(this, 40), 0, U.dp(this, 60));
        scrollModeBuilt = true;
    }

    private void markScrollPosition() {
        // при прокрутке сохраняем по верхнему видимому блоку
        if (scrollWrap == null) return;
        int top = scrollWrap.getScrollY();
        for (int i = 0; i < scrollList.getChildCount(); i++) {
            View child = scrollList.getChildAt(i);
            if (child.getTop() <= top + U.dp(this, 120)) {
                BlockView bv = (BlockView) child;
                if (bv.item != null && bv.item.blockIndex >= 0) {
                    lastScrollBlock = bv.item.blockIndex;
                }
            }
        }
    }

    private int lastScrollBlock = 0;

    /* ==================== Оригинал (скан) ==================== */

    private void toggleScan() {
        scanMode = !scanMode;
        if (scanMode) {
            pageView.setVisibility(View.GONE);
            if (scrollWrap != null) scrollWrap.setVisibility(View.GONE);
            if (scanWrap == null) {
                scanWrap = Ui.frame(this);
                zoom = new Widgets.Zoom(this);
                scanWrap.addView(zoom, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                stage.addView(scanWrap, Ui.flp(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            }
            scanWrap.setVisibility(View.VISIBLE);
            if (slider != null) {
                slider.set(book.scanPages <= 1 ? 1f : scanPage / (float) (book.scanPages - 1));
                slider.setListener(new Widgets.OnSlide() {
                    @Override
                    public void onSlide(float value, boolean fromUser) {
                        if (!fromUser) return;
                        int target = 1 + Math.round(value * (book.scanPages - 1));
                        if (target != scanPage) {
                            scanPage = target;
                            showScan();
                        }
                    }
                });
            }
            showScan();
            U.pill(this, getString(R.string.scan_hint));
        } else {
            if (scanWrap != null) scanWrap.setVisibility(View.GONE);
            if (P.i(P.readerMode, 0) == 1) {
                ensureScrollMode();
            } else {
                pageView.setVisibility(View.VISIBLE);
            }
            slider.setListener(pageSliderListener());
            showPage(false);
        }
    }

    private Widgets.OnSlide pageSliderListener() {
        return new Widgets.OnSlide() {
            @Override
            public void onSlide(float value, boolean fromUser) {
                if (!fromUser || pages == null) return;
                int total = pages.pages.size();
                int target = Math.min(total - 1, Math.round(value * (total - 1)));
                if (target != pageIndex) {
                    pageIndex = target;
                    showPage(false);
                }
            }
        };
    }

    private void showScan() {
        int page = scanPageForText() >= 0 ? scanPage : scanPage;
        Bitmap bm = decodeScan(book.id, page);
        if (bm == null) {
            U.pill(this, getString(R.string.scan_missing));
            return;
        }
        zoom.setBitmap(bm);
        pageLabel.setText(getString(R.string.page_of, page, book.scanPages));
        if (scrollModeBuilt) markScrollPosition();
    }

    private int scanPageForText() {
        if (pages == null || pages.pages.isEmpty()) return -1;
        return pages.pages.get(Math.min(pageIndex, pages.pages.size() - 1)).pageMarker;
    }

    private Bitmap decodeScan(String bookId, int page) {
        String path = ContentRepo.get().scanPath(bookId, page);
        if (path == null) return null;
        AssetManager am = getAssets();
        InputStream is = null;
        try {
            is = am.open(path);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            int sample = 1;
            int target = U.screenW(this) * 2;
            while (o.outWidth / (sample * 2) > target) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            is = am.open(path);
            return BitmapFactory.decodeStream(is, null, o2);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
    }

    /* ==================== Панели ==================== */

    private void toggleBars() {
        barsVisible = !barsVisible;
        float dist = U.dpf(this, 80);
        topBar.animate().translationY(barsVisible ? 0 : -dist).alpha(barsVisible ? 1f : 0f).setDuration(200).start();
        bottomBar.animate().translationY(barsVisible ? 0 : dist).alpha(barsVisible ? 1f : 0f).setDuration(200).start();
        lastAutoHide = System.currentTimeMillis();
    }

    private void autoHideBars() {
        if (!barsVisible) return;
        if (!P.b(P.readerFullscreen, true)) return;
        if (System.currentTimeMillis() - lastAutoHide < 4200) return;
        toggleBars();
    }

    /* ==================== Закладки ==================== */

    private void toggleBookmark() {
        Book.Toc toc = book.tocAt(chapter);
        boolean added = Lib.get().toggleMark(book, chapter, pageIndex,
                toc == null ? "" : toc.text,
                pages != null && !pages.pages.isEmpty() ? Paginator.pageText(pages.pages.get(pageIndex)) : "");
        updateMarkIcon();
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
        TextView preview = Ui.tv(this, U.trimTo(item.text, 260), 15f, Skin.ink(this), U.tf(Skin.familyName(P.i(P.readerFont, Skin.F_SERIF)), Typeface.NORMAL));
        preview.setLineSpacing(U.dpf(this, 6f), 1.18f);
        c.addView(preview);
        c.addView(Ui.space(this, 14));

        final Block bl = item.blockIndex >= 0 && item.blockIndex < book.blocks.size() ? book.blocks.get(item.blockIndex) : null;
        final String full = bl != null ? bl.plain() : item.text;

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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
            final int index = i;
            Book.Toc t = book.toc.get(i);
            if (U.empty(t.text)) continue;
            LinearLayout r = Ui.row(this);
            Ui.pad(r, this, t.level == 0 ? 4 : (t.level == 1 ? 10 : 20), 12, 4, 12);
            boolean current = i == chapter;
            TextView tv = Ui.tv(this, t.text, t.level == 0 ? 16f : 15f,
                    current ? Skin.accent(this) : Skin.ink(this),
                    t.level == 0 ? U.tf("serif", Typeface.BOLD) : U.uiMed(this));
            r.addView(tv, Ui.llpW(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (current) r.addView(new IconView(this, Ico.CHECK, Skin.accent(this), 16f));
            Ui.click(r, new Runnable() {
                @Override
                public void run() {
                    chapter = index;
                    rebuild(book.tocAt(index).blockIndex);
                    scanPage = Math.max(1, scanPage);
                    if (scrollModeBuilt) rebuildScroll();
                    if (scanMode) {
                        scanPage = Math.max(1, Math.min(book.scanPages, scanPageForText() > 0 ? scanPageForText() : scanPage));
                        showScan();
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
                    TextView head = Ui.tv(ReaderActivity.this, t == null ? "" : t.text, 11.5f, Skin.accent(ReaderActivity.this), U.uiMed(ReaderActivity.this));
                    r.addView(head);
                    TextView sn = Ui.tv(ReaderActivity.this, snippet(plain, at, nq.length()), 14f, Skin.ink(ReaderActivity.this), U.ui(ReaderActivity.this));
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
                    results.addView(Ui.tv(ReaderActivity.this, getString(R.string.search_nothing), 14f, Skin.sub(ReaderActivity.this), U.ui(ReaderActivity.this)));
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
        rebuild(blockIndex);
        if (scrollModeBuilt) rebuildScroll();
        saveProgress();
    }


    /** Отправка сообщения об опечатке: системное окно «Поделиться» с готовым текстом.
     *  Ничего не отправляется без действия пользователя, разрешения не нужны. */
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
                applyReaderColors();
                if (P.i(P.readerMode, 0) == 1 && !scanMode) ensureScrollMode();
                rebuild(currentBlock());
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

    private int currentBlock() {
        if (scrollModeBuilt) return lastScrollBlock;
        if (pages == null || pages.pages.isEmpty()) return 0;
        return pages.pages.get(Math.max(0, Math.min(pageIndex, pages.pages.size() - 1))).firstBlock;
    }

    private void applyReaderColors() {
        int theme = P.i(P.readerTheme, Skin.T_PAPER);
        int ink = Skin.paperInk(theme);
        int paper = Skin.paper(theme);
        pageView.setBackground(U.round(paper, 3f, this));
        if (scrollWrap != null) scrollWrap.setBackground(U.round(paper, 3f, this));
        View root = findViewById(android.R.id.content);
        root.setBackgroundColor(Skin.readerFrame(theme));
        topBar.setBackground(U.gradientRect(Skin.withAlpha(paper, 0.98f), Skin.withAlpha(paper, 0.92f), 0f, this, true));
        bottomBar.setBackground(U.gradientRect(Skin.withAlpha(paper, 0.92f), Skin.withAlpha(paper, 0.99f), 0f, this, true));
        barTitle.setTextColor(ink);
        pageLabel.setTextColor(Skin.paperSub(theme));
        markIcon.setColor(ink);
        if (slider != null) {
            slider.setColors(Skin.withAlpha(ink, 0.14f), Skin.paperAccent(theme), paper);
            slider.setListener(pageSliderListener());
        }
        if (dim != null) dim.setAlpha(P.i(P.readerBrightness, 0) / 100f);
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
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pages == null) {
            postRebuild();
        }
    }

    private void postRebuild() {
        stage.post(new Runnable() {
            @Override
            public void run() {
                int block = currentBlock();
                applyReaderColors();
                if (P.i(P.readerMode, 0) == 1) {
                    ensureScrollMode();
                    pageView.setVisibility(View.GONE);
                    pageLabel.setText(getString(R.string.mode_scroll));
                    if (slider != null) slider.setVisibility(View.GONE);
                } else {
                    rebuild(block);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        sessionStart = System.currentTimeMillis();
        lastAutoHide = System.currentTimeMillis();
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
        int block = currentBlock();
        int percent = book.blocks.isEmpty() ? 0 : Math.round(block * 100f / book.blocks.size());
        Lib.get().saveProg(book.id, chapter, block, scanMode ? scanPage : pageIndex, percent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (P.i(P.readerMode, 0) == 0 && !scanMode) {
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

    @Override
    public void onBackPressed() {
        saveProgress();
        super.onBackPressed();
    }

    /* ==================== Отдельный блок в режиме прокрутки ==================== */

    static class BlockView extends View {
        final Paginator.Item item;
        private final Paginator.Opt opt;
        private final int pad;
        private final Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        BlockView(Context c, Paginator.Item item, Paginator.Opt opt, int pad) {
            super(c);
            this.item = item;
            this.opt = opt;
            this.pad = pad;
            int h = 6;
            if (item.image != null) {
                h = (int) item.imageHeight + U.dp(c, 24)
                        + Math.round(item.captionLayout != null ? item.captionHeight : 0f);
            } else if (item.layout != null) {
                h = item.layout.getHeight() + (int) (item.spaceBefore + item.spaceAfter);
            } else {
                h = (int) item.height + 8;
            }
            setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            c.save();
            c.translate(pad, item.spaceBefore);
            if (item.image != null) {
                float scale = Math.min(w - pad * 2f, item.image.getWidth()) / (float) item.image.getWidth();
                float dw = item.image.getWidth() * scale;
                float dh = item.image.getHeight() * scale;
                if (dh > item.imageHeight) {
                    dh = item.imageHeight;
                }
                float dx = (w - pad * 2f - dw) / 2f;
                c.drawBitmap(item.image, null, new android.graphics.Rect((int) dx, 0, (int) (dx + dw), (int) dh), imgPaint);
            } else if (item.layout != null) {
                c.save();
                c.translate(0, 0);
                item.layout.draw(c);
                c.restore();
            }
            c.restore();
        }
    }
}
