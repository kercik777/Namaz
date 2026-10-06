package tm.akyda.namaz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextPaint;
import android.view.View;

/**
 * Страница книги, нарисованная на Canvas: бумага, текст, буквица,
 * иллюстрации, колонтитулы и номер страницы.
 */
public class PageView extends View {

    private Paginator.Page page;
    private Paginator.Opt opt;
    private String chapterTitle = "";
    private String bookTitle = "";
    private int pageNumber = 1;
    private int totalPages = 1;
    private float pageTurn = 0f;      // -1..1 сдвиг при анимации
    private int turnDir = 0;
    private boolean chromeVisible = true;   // колонтитулы рисует BookView, когда false

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint footer;
    private TextPaint header;
    private BitmapShader grainShader;
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    public PageView(Context c) {
        super(c);
        shadow.setColor(0x22000000);
        shadow.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setPage(Paginator.Page p, Paginator.Opt o) {
        this.page = p;
        this.opt = o;
        invalidate();
    }

    public Paginator.Page getPage() {
        return page;
    }

    public void setMeta(String bookTitle, String chapterTitle, int pageNumber, int totalPages) {
        this.bookTitle = bookTitle == null ? "" : bookTitle;
        this.chapterTitle = chapterTitle == null ? "" : chapterTitle;
        this.pageNumber = pageNumber;
        this.totalPages = totalPages;
        invalidate();
    }

    /** Колонтитулы и номер страницы (их может рисовать рамка книги). */
    public void setChromeVisible(boolean visible) {
        this.chromeVisible = visible;
        invalidate();
    }

    public void setTurn(float t, int dir) {
        this.pageTurn = t;
        this.turnDir = dir;
        invalidate();
    }

    private TextPaint footerPaint() {
        if (footer == null) {
            footer = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            footer.setTypeface(U.tf("sans-serif", Typeface.NORMAL));
            footer.setLetterSpacing(0.08f);
        }
        return footer;
    }

    private TextPaint headerPaint() {
        if (header == null) {
            header = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            header.setTypeface(U.tf("sans-serif-medium", Typeface.NORMAL));
            header.setLetterSpacing(0.18f);
        }
        return header;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (opt == null) return;
        int w = getWidth();
        int h = getHeight();
        int theme = opt.theme;

        // Бумага
        bg.setColor(Skin.paper(theme));
        c.drawRect(0, 0, w, h, bg);

        // Лёгкая тень у края страницы при перелистывании
        if (pageTurn != 0f) {
            int a = (int) (Math.abs(pageTurn) * 90);
            soft.setColor((a << 24));
            float edge = turnDir >= 0 ? w - U.dpf(getContext(), 18f) : 0f;
            c.drawRect(turnDir >= 0 ? w - U.dpf(getContext(), 18f) : 0f, 0,
                    turnDir >= 0 ? w : U.dpf(getContext(), 18f), h, soft);
        }

        // Фактура бумаги
        if (grainShader == null) {
            U.BitmapDrawableHolder holder = U.paperGrain(getContext(), Skin.paperIsDark(theme));
            if (holder.bitmap != null) {
                grainShader = new BitmapShader(holder.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                grainPaint.setShader(grainShader);
            }
        }
        if (grainShader != null) c.drawRect(0, 0, w, h, grainPaint);

        if (page == null) return;

        // Колонтитул
        if (chromeVisible) {
        int padH = U.dp(getContext(), 22);
        float headerY = U.dp(getContext(), 30);
        headerPaint().setTextSize(U.sp(getContext(), 10.5f));
        headerPaint().setColor(Skin.paperSub(theme));
        String head = chapterTitle.toUpperCase(new java.util.Locale("tk", "TM"));
        float headW = headerPaint().measureText(head);
        float maxHead = w - padH * 4;
        if (headW > maxHead) {
            int chars = (int) (head.length() * (maxHead / headW));
            if (chars > 3) head = head.substring(0, chars - 1).trim() + "…";
        }
        c.drawText(head, (w - headerPaint().measureText(head)) / 2f, headerY, headerPaint());
        accent.setColor(Skin.paperLine(theme));
        accent.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 0.8f)));
        float lineW = U.dpf(getContext(), 34f);
        c.drawLine(w / 2f - lineW, headerY + U.dp(getContext(), 8), w / 2f + lineW, headerY + U.dp(getContext(), 8), accent);
        }

        // Текст
        c.save();
        c.translate(opt.marginLeft(), opt.marginTop());
        float y = 0;
        for (Paginator.Item it : page.items) {
            if (it.image != null) {
                y += it.spaceBefore;
                float availW = opt.width;
                int bw = it.image.getWidth();
                int bh = it.image.getHeight();
                float scale = Math.min(availW / bw, it.imageHeight / (float) bh);
                float dw = bw * scale;
                float dh = bh * scale;
                float dx = (availW - dw) / 2f;
                soft.setColor(0x14000000);
                U.roundRect(c, dx + 2, y + 3, dx + dw + 2, y + dh + 3, U.dpf(getContext(), 6f), soft);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setFilterBitmap(true);
                c.drawBitmap(it.image, null, new Rect((int) dx, (int) y, (int) (dx + dw), (int) (y + dh)), p);
                y += dh;
                if (it.captionLayout != null) {
                    y += U.dpf(getContext(), 6f);
                    c.save();
                    c.translate(0, y);
                    it.captionLayout.draw(c);
                    c.restore();
                    y += it.captionHeight;
                }
                y += it.spaceAfter;
                continue;
            }

            if (it.ornament) {
                drawOrnament(c, opt.width / 2f, y + it.height / 2f, theme);
                y += it.height;
                continue;
            }

            if (it.layout == null) {
                y += it.height;
                continue;
            }

            y += it.spaceBefore;
            float top = it.layout.getLineTop(it.lineStart);
            float bottom = it.lineEnd <= 0 ? it.layout.getHeight()
                    : it.layout.getLineBottom(Math.min(it.lineEnd - 1, it.layout.getLineCount() - 1));
            float hh = bottom - top;

            // Вертикальная золотая линия у цитат
            if (it.indentLeft && (Block.QUOTE.equals(it.type))) {
                accent.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.55f));
                accent.setStrokeWidth(U.dpf(getContext(), 2.2f));
                c.drawLine(U.dpf(getContext(), 2f), y + 2, U.dpf(getContext(), 2f), y + hh - 2, accent);
            }

            c.save();
            clip.reset();
            clip.addRect(0f, y, opt.width, y + hh, Path.Direction.CW);
            c.clipPath(clip);
            c.translate(0, y - top);
            it.layout.draw(c);
            c.restore();
            y += hh;

            if (it.lastPiece) y += it.spaceAfter;
        }
        c.restore();

        // Нижний колонтитул: номер страницы с орнаментом
        if (chromeVisible) {
        float footY = h - U.dp(getContext(), 26);
        footerPaint().setTextSize(U.sp(getContext(), 11f));
        footerPaint().setColor(Skin.paperSub(theme));
        String num = String.valueOf(pageNumber);
        float nw = footerPaint().measureText(num);
        c.drawText(num, (w - nw) / 2f, footY, footerPaint());
        accent.setColor(Skin.paperLine(theme));
        accent.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 0.8f)));
        c.drawLine(w / 2f - nw / 2f - U.dp(getContext(), 22), footY - U.dp(getContext(), 4),
                w / 2f - nw / 2f - U.dp(getContext(), 8), footY - U.dp(getContext(), 4), accent);
        c.drawLine(w / 2f + nw / 2f + U.dp(getContext(), 8), footY - U.dp(getContext(), 4),
                w / 2f + nw / 2f + U.dp(getContext(), 22), footY - U.dp(getContext(), 4), accent);

        // Прогресс главы тонкой линией у самого низа
        if (totalPages > 1) {
            float frac = (pageNumber - 1) / (float) Math.max(1, totalPages - 1);
            accent.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.10f));
            accent.setStrokeWidth(U.dpf(getContext(), 2f));
            c.drawLine(0, h - 1, w, h - 1, accent);
            accent.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.55f));
            c.drawLine(0, h - 1, Math.max(2f, w * frac), h - 1, accent);
        }
        }
    }

    private void drawOrnament(Canvas c, float cx, float cy, int theme) {
        accent.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.75f));
        accent.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 1.1f)));
        float r = U.dpf(getContext(), 3.2f);
        float gap = U.dpf(getContext(), 9f);
        float len = U.dpf(getContext(), 40f);
        c.drawLine(cx - gap - len, cy, cx - gap, cy, accent);
        c.drawLine(cx + gap, cy, cx + gap + len, cy, accent);
        Path p = new Path();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r, cy);
        p.lineTo(cx, cy + r);
        p.lineTo(cx - r, cy);
        p.close();
        accent.setStyle(Paint.Style.FILL);
        c.drawPath(p, accent);
        accent.setStyle(Paint.Style.STROKE);
    }

    /** Найти элемент, в который попало нажатие (для цитат и закладок). */
    public Paginator.Item itemAt(float x, float y) {
        if (page == null || opt == null) return null;
        float yy = y - opt.marginTop();
        for (Paginator.Item it : page.items) {
            if (it.layout == null) {
                yy -= it.height;
                continue;
            }
            float top = it.layout.getLineTop(it.lineStart);
            float bottom = it.lineEnd <= 0 ? it.layout.getHeight()
                    : it.layout.getLineBottom(Math.min(it.lineEnd - 1, it.layout.getLineCount() - 1));
            float hh = bottom - top;
            if (yy >= it.spaceBefore + 0 && yy <= it.spaceBefore + hh) return it;
            yy -= it.spaceBefore + hh + (it.lastPiece ? it.spaceAfter : 0);
        }
        return null;
    }

    public Layout layoutOf(Paginator.Item it) {
        return it.layout;
    }
}
