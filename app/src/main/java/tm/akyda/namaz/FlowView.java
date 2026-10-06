package tm.akyda.namaz;

import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.util.List;

/**
 * Кусок «ленты» текста для режима прокрутки.
 *
 * Поток книги (см. {@link Paginator#flow}) режется на части по границам абзацев,
 * и каждая часть рисуется на своём полотне. Стыки точные, поэтому читатель
 * свободно прокручивает текст вверх и вниз, как обычную длинную страницу,
 * но вёрстка остаётся книжной: поля, красная строка, буквица, иллюстрации.
 */
public class FlowView extends View {

    private final List<Paginator.Item> items;
    private final float[] tops;
    private final float from;
    private final float to;
    private final int startIdx;
    private final int endIdx;
    private final Paginator.Opt opt;
    private final boolean first;
    private final boolean last;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private TextPaint hint;
    private BitmapShader grainShader;
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FlowView(Context c, List<Paginator.Item> items, float[] tops, int start, int end,
                    Paginator.Opt opt, boolean first, boolean last) {
        super(c);
        this.items = items;
        this.tops = tops;
        this.opt = opt;
        this.startIdx = start;
        this.endIdx = end;
        this.from = tops[start];
        float t = end < tops.length ? tops[end] : tops[tops.length - 1] + opt.textSize * 2f;
        this.first = first;
        this.last = last;
        this.to = t;
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height()));
    }

    /** Верхняя граница куска в координатах ленты. */
    public float flowFrom() {
        return from;
    }

    /** Нижняя граница куска в координатах ленты. */
    public float flowTo() {
        return to;
    }

    /** Высота куска в пикселях: только сам текст, без «пустых» полей страницы. */
    public int height() {
        float h = to - from;
        if (first) h += opt.marginTop;
        if (last) h += opt.marginTop * 2f;
        return Math.max(1, Math.round(h));
    }

    /** Считает вертикальные позиции всех элементов потока. */
    public static float[] computeTops(List<Paginator.Item> items, Paginator.Opt o) {
        float[] tops = new float[items.size()];
        float y = 0;
        for (int i = 0; i < items.size(); i++) {
            tops[i] = y;
            y += itemHeight(items.get(i), o);
        }
        return tops;
    }

    /** Полная высота потока (без полей). */
    public static float totalHeight(float[] tops, List<Paginator.Item> items, Paginator.Opt o) {
        if (items.isEmpty()) return 0;
        return tops[tops.length - 1] + itemHeight(items.get(items.size() - 1), o);
    }

    private static float itemHeight(Paginator.Item it, Paginator.Opt o) {
        if (it.image != null) {
            float h = it.spaceBefore + it.imageHeight;
            if (it.captionLayout != null) h += o.textSize * 0.5f + it.captionHeight;
            return h + it.spaceAfter;
        }
        if (it.ornament) return it.height;
        if (it.layout == null) return it.height;
        int lastLine = it.lineEnd <= 0 ? it.layout.getLineCount() - 1 : Math.min(it.lineEnd - 1, it.layout.getLineCount() - 1);
        float h = it.layout.getLineBottom(Math.max(0, lastLine)) - it.layout.getLineTop(it.lineStart);
        return it.spaceBefore + h + it.spaceAfter;
    }

    /** Номер блока книги, который находится на этой высоте куска. */
    public int blockAt(float localY) {
        float y = localY + from - (first ? opt.marginTop : 0f);
        int lo = 0, hi = tops.length - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (tops[mid] <= y) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (best < 0) best = 0;
        if (best >= items.size()) best = items.size() - 1;
        return items.isEmpty() ? 0 : items.get(best).blockIndex;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (opt == null) return;
        int w = getWidth();
        int h = getHeight();
        int theme = opt.theme;

        bg.setColor(Skin.paper(theme));
        c.drawRect(0, 0, w, h, bg);

        if (grainShader == null) {
            U.BitmapDrawableHolder holder = U.paperGrain(getContext(), Skin.paperIsDark(theme));
            if (holder.bitmap != null) {
                grainShader = new BitmapShader(holder.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                grainPaint.setShader(grainShader);
            }
        }
        if (grainShader != null) c.drawRect(0, 0, w, h, grainPaint);

        c.save();
        c.translate(opt.marginLeft(), -from + (first ? opt.marginTop : 0f));

        float bandStart = from - (first ? opt.marginTop : 0f);
        float bandEnd = bandStart + h;
        float margin = U.dpf(getContext(), 4f);
        int prevMarker = -1;

        for (int i = startIdx; i < endIdx && i < items.size(); i++) {
            Paginator.Item it = items.get(i);
            float itemTop = tops[i];
            if (itemTop > bandEnd) break;

            if (it.image != null) {
                float availW = opt.width;
                int bw = it.image.getWidth();
                int bh = it.image.getHeight();
                float scale = Math.min(availW / bw, it.imageHeight / (float) bh);
                float dw = bw * scale;
                float dh = bh * scale;
                float dx = (availW - dw) / 2f;
                soft.setColor(0x14000000);
                U.roundRect(c, dx + 2, tops[i] + it.spaceBefore + 3, dx + dw + 2,
                        tops[i] + it.spaceBefore + dh + 3, U.dpf(getContext(), 6f), soft);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setFilterBitmap(true);
                float iy = tops[i] + it.spaceBefore;
                c.drawBitmap(it.image, null, new Rect((int) dx, (int) iy, (int) (dx + dw), (int) (iy + dh)), p);
                if (it.captionLayout != null) {
                    c.save();
                    c.translate(0, iy + dh + opt.textSize * 0.5f);
                    it.captionLayout.draw(c);
                    c.restore();
                }
                continue;
            }

            if (it.ornament) {
                drawOrnament(c, opt.width / 2f, tops[i] + it.height / 2f, theme);
                continue;
            }
            if (it.layout == null) continue;

            float top = it.layout.getLineTop(it.lineStart);
            float bottom = it.lineEnd <= 0 ? it.layout.getHeight()
                    : it.layout.getLineBottom(Math.min(it.lineEnd - 1, it.layout.getLineCount() - 1));
            float hh = bottom - top;
            float yy = tops[i] + it.spaceBefore;

            if (it.indentLeft && Block.QUOTE.equals(it.type)) {
                accent.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.55f));
                accent.setStrokeWidth(U.dpf(getContext(), 2.2f));
                c.drawLine(U.dpf(getContext(), 2f), yy + 2, U.dpf(getContext(), 2f), yy + hh - 2, accent);
            }

            c.save();
            clip.reset();
            clip.addRect(0f, yy - 1, opt.width, yy + hh + 1, Path.Direction.CW);
            c.clipPath(clip);
            c.translate(0, yy - top);
            it.layout.draw(c);
            c.restore();
        }
        c.restore();
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
        Paint.Style old = accent.getStyle();
        accent.setStyle(Paint.Style.FILL);
        c.drawPath(p, accent);
        accent.setStyle(old);
    }
}
