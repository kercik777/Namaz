package tm.akyda.namaz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Набор фирменных элементов: логотип, обложка книги, кольцо прогресса, слайдер, просмотр скана. */
public final class Widgets {

    /* ==================== ЛОГОТИП ==================== */

    /** Анимированный логотип: полумесяц и раскрытая книга. */
    public static class Logo extends View {
        private float progress = 0f;   // 0..1
        private boolean loop = false;
        private int colorPaper = 0xFFF3EEE1;
        private int colorGold = 0xFFE3C36F;

        public Logo(Context c) {
            super(c);
        }

        public void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        public float getProgress() {
            return progress;
        }

        public void setColors(int paper, int gold) {
            colorPaper = paper;
            colorGold = gold;
            invalidate();
        }

        public void play(final long dur) {
            progress = 0f;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(dur);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator());
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                    setProgress((Float) animation.getAnimatedValue());
                }
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float S = Math.min(w, h);
            float p = progress;

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);

            float moonR = S * 0.19f;
            float moonCx = cx - S * 0.012f;
            float moonCy = cy - S * 0.14f;

            // Мягкое свечение за полумесяцем
            int glow = (int) (26 * p);
            paint.setColor((glow << 24) | (colorGold & 0x00FFFFFF));
            c.drawCircle(moonCx, moonCy, moonR * 2.1f, paint);

            // Полумесяц
            float sweep = Math.min(1f, p / 0.62f);
            Path moon = new Path();
            moon.setFillType(Path.FillType.EVEN_ODD);
            RectF r1 = new RectF(moonCx - moonR, moonCy - moonR, moonCx + moonR, moonCy + moonR);
            moon.addOval(r1, Path.Direction.CW);
            float cut = moonR * 0.86f;
            float off = moonR * 0.40f;
            RectF r2 = new RectF(moonCx - cut + off, moonCy - cut, moonCx + cut + off, moonCy + cut);
            moon.addOval(r2, Path.Direction.CW);
            paint.setAlpha((int) (255 * sweep));
            paint.setColor(colorGold);
            c.drawPath(moon, paint);

            // Звезда
            float starA = Math.max(0f, (p - 0.35f) / 0.65f);
            if (starA > 0) {
                paint.setAlpha((int) (255 * starA));
                float stx = cx + S * 0.155f, sty = cy - S * 0.195f;
                Path star = new Path();
                float ro = S * 0.045f, ri = S * 0.018f;
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float r = (i % 2 == 0) ? ro : ri;
                    float px = stx + (float) Math.cos(ang) * r;
                    float py = sty + (float) Math.sin(ang) * r;
                    if (i == 0) star.moveTo(px, py);
                    else star.lineTo(px, py);
                }
                star.close();
                c.drawPath(star, paint);
            }

            // Книга
            float bookA = Math.max(0f, (p - 0.28f) / 0.72f);
            if (bookA > 0) {
                float rise = (1f - bookA) * S * 0.06f;
                c.save();
                c.translate(0, rise);
                paint.setAlpha((int) (255 * bookA));
                paint.setColor(colorPaper);
                float bt = cy + S * 0.06f, bb = cy + S * 0.26f;
                float halfW = S * 0.30f, spine = S * 0.012f;
                Path left = new Path();
                left.moveTo(cx - halfW, bb);
                left.lineTo(cx - halfW * 0.94f, bt + S * 0.012f);
                left.lineTo(cx - spine, bt);
                left.lineTo(cx - spine, bb - S * 0.008f);
                left.close();
                c.drawPath(left, paint);
                paint.setColor(U.mix(colorPaper, 0xFF9A8B6B, 0.22f));
                Path right = new Path();
                right.moveTo(cx + halfW, bb);
                right.lineTo(cx + halfW * 0.94f, bt + S * 0.012f);
                right.lineTo(cx + spine, bt);
                right.lineTo(cx + spine, bb - S * 0.008f);
                right.close();
                c.drawPath(right, paint);
                paint.setColor(U.mix(colorGold, 0xFF8A6C22, 0.35f));
                c.drawRect(cx - spine * 0.9f, bt, cx + spine * 0.9f, bb, paint);
                c.restore();
            }
        }
    }

    /* ==================== ОБЛОЖКА КНИГИ ==================== */

    /** Обложка книги, нарисованная кодом: градиент, орнамент, золотая рамка. */
    public static class Cover extends View {
        private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String t1 = "", t2 = "";
        private int from = 0xFF0E4A36, to = 0xFF06251A;
        private float progress = -1f;

        public Cover(Context c) {
            super(c);
            title.setTypeface(U.tf("serif", Typeface.BOLD));
            title.setColor(0xFFF6EFDD);
            sub.setTypeface(U.tf("sans-serif", Typeface.NORMAL));
            sub.setColor(0xCCF6EFDD);
            sub.setLetterSpacing(0.12f);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(U.dpf(c, 1.1f));
            line.setColor(0x66E3C36F);
        }

        public void setBook(Book b) {
            t1 = b.t(Loc.lang());
            t2 = b.auth(Loc.lang());
            int a = b.accent;
            if (a == 0) {
                from = 0xFF0E4A36;
                to = 0xFF06251A;
            } else {
                from = a;
                to = U.mix(a, 0xFF000000, 0.62f);
            }
            invalidate();
        }

        public void setTitle(String a, String b) {
            t1 = a;
            t2 = b;
            invalidate();
        }

        public void setColors(int from, int to) {
            this.from = from;
            this.to = to;
            invalidate();
        }

        public void setProgress(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float r = U.dpf(getContext(), 14f);
            // Светлая тень под обложкой
            fill.setColor(0x22000000);
            U.roundRect(c, 3, 5, w + 3, h + 5, r, fill);
            fill.setShader(new LinearGradient(0, 0, w * 0.7f, h, from, to, Shader.TileMode.CLAMP));
            U.roundRect(c, 0, 0, w, h, r, fill);
            fill.setShader(null);

            // Корешок
            fill.setColor(0x33000000);
            c.drawRect(0, 0, U.dpf(getContext(), 9f), h, fill);
            fill.setColor(0x22FFFFFF);
            c.drawRect(U.dpf(getContext(), 9f), 0, U.dpf(getContext(), 10.6f), h, fill);

            // Золотая рамка
            float m = U.dpf(getContext(), 12f);
            U.roundRect(c, m, m, w - m, h - m, r * 0.6f, line);

            // Орнамент-полукольца
            Paint orn = new Paint(Paint.ANTI_ALIAS_FLAG);
            orn.setStyle(Paint.Style.STROKE);
            orn.setStrokeWidth(U.dpf(getContext(), 1f));
            orn.setColor(0x33F2DCA4);
            float ornCx = w / 2f, ornCy = h * 0.80f;
            for (int i = 1; i <= 3; i++) {
                float rad = U.dpf(getContext(), 18f * i);
                RectF rect = new RectF(ornCx - rad, ornCy - rad, ornCx + rad, ornCy + rad);
                c.drawArc(rect, 200, 140, false, orn);
            }
            orn.setColor(0x22F2DCA4);
            RectF big = new RectF(ornCx - w * 0.42f, ornCy - w * 0.42f, ornCx + w * 0.42f, ornCy + w * 0.42f);
            c.drawArc(big, 195, 150, false, orn);

            // Заголовок
            float pad = U.dpf(getContext(), 22f);
            title.setTextSize(Math.min(w * 0.155f, h * 0.11f));
            sub.setTextSize(Math.min(w * 0.058f, h * 0.042f));
            float ty = h * 0.30f;
            drawWrapped(c, title, t1, pad, ty, w - pad * 2, h * 0.10f);
            if (t2 != null && !t2.isEmpty()) {
                String s = t2.toUpperCase(new java.util.Locale("tk", "TM"));
                sub.setTextSize(Math.min(w * 0.045f, h * 0.033f));
                c.drawText(s, pad, h - pad * 1.6f, sub);
            }

            // Прогресс чтения
            if (progress >= 0f) {
                float py = h - U.dpf(getContext(), 12f);
                fill.setColor(0x33FFFFFF);
                U.roundRect(c, pad, py, w - pad, py + U.dpf(getContext(), 3f), 2, fill);
                fill.setColor(0xFFE3C36F);
                U.roundRect(c, pad, py, pad + (w - pad * 2) * Math.max(0.02f, progress), py + U.dpf(getContext(), 3f), 2, fill);
            }
        }

        private void drawWrapped(Canvas c, TextPaint p, String text, float x, float y, float maxW, float lineH) {
            if (text == null) return;
            String[] words = text.split(" ");
            StringBuilder cur = new StringBuilder();
            float cy = y;
            for (String word : words) {
                String test = cur.length() == 0 ? word : cur + " " + word;
                if (p.measureText(test) > maxW && cur.length() > 0) {
                    c.drawText(cur.toString(), x, cy, p);
                    cy += lineH;
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            if (cur.length() > 0) c.drawText(cur.toString(), x, cy, p);
        }
    }

    /* ==================== КОЛЬЦО ПРОГРЕССА ==================== */

    public static class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint txt = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private float value = 0f;
        private float shown = 0f;
        private String center = "";

        public Ring(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(U.dpf(c, 4f));
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(0x22000000);
            bar.setStyle(Paint.Style.STROKE);
            bar.setStrokeWidth(U.dpf(c, 4f));
            bar.setStrokeCap(Paint.Cap.ROUND);
            bar.setColor(0xFFC9A24A);
            txt.setTypeface(U.tf("sans-serif-medium", Typeface.NORMAL));
            txt.setTextSize(U.sp(c, 11f));
            txt.setTextAlign(Paint.Align.CENTER);
        }

        public void set(float v, int color, String center) {
            value = Math.max(0f, Math.min(1f, v));
            bar.setColor(color);
            this.center = center;
            animateTo(value);
        }

        private void animateTo(final float target) {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(shown, target);
            a.setDuration(600);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator());
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                    shown = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float pad = Math.max(bar.getStrokeWidth(), U.dpf(getContext(), 4f)) / 2f + U.dpf(getContext(), 1f);
            RectF r = new RectF(pad, pad, w - pad, h - pad);
            c.drawArc(r, 0, 360, false, track);
            c.drawArc(r, -90, 360 * shown, false, bar);
            if (center != null && !center.isEmpty()) {
                float cy = h / 2f - (txt.descent() + txt.ascent()) / 2f;
                c.drawText(center, w / 2f, cy, txt);
            }
        }
    }

    /* ==================== СЛАЙДЕР ==================== */

    public interface OnSlide {
        void onSlide(float value, boolean fromUser);
    }

    public static class Slider extends View {
        private final Paint trackP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float value = 0f;      // 0..1
        private OnSlide listener;

        public Slider(Context c) {
            super(c);
            trackP.setColor(0x22000000);
            barP.setColor(0xFFC9A24A);
            thumbP.setColor(0xFFFFFFFF);
            thumbP.setShadowLayer(U.dpf(c, 3f), 0, U.dpf(c, 1f), 0x40000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        public void setColors(int track, int bar, int thumb) {
            trackP.setColor(track);
            barP.setColor(bar);
            thumbP.setColor(thumb);
            invalidate();
        }

        public void set(float v) {
            value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        public float get() {
            return value;
        }

        public void setListener(OnSlide l) {
            listener = l;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cy = h / 2f;
            float th = U.dpf(getContext(), 3f);
            U.roundRect(c, 0, cy - th / 2f, w, cy + th / 2f, th / 2f, trackP);
            U.roundRect(c, 0, cy - th / 2f, Math.max(th, w * value), cy + th / 2f, th / 2f, barP);
            c.drawCircle(w * value, cy, U.dpf(getContext(), 8f), thumbP);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    value = Math.max(0f, Math.min(1f, e.getX() / Math.max(1f, getWidth())));
                    invalidate();
                    if (listener != null) listener.onSlide(value, true);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (listener != null) listener.onSlide(value, false);
                    return true;
                }
                default:
                    return super.onTouchEvent(e);
            }
        }
    }

    /* ==================== ПРОСМОТР СКАНА СТРАНИЦЫ ==================== */

    /** Страница-скан: увеличение щипком, двойным касанием и перетаскивание. */
    public static class Zoom extends View {
        private Bitmap bmp;
        private final Matrix m = new Matrix();
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private float scale = 1f, minScale = 0.2f, maxScale = 4f;
        private float tx = 0, ty = 0;
        private ScaleGestureDetector scaleDet;
        private GestureDetector tapDet;
        private float lastX, lastY;

        public Zoom(Context c) {
            super(c);
            scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    float ns = Math.max(minScale, Math.min(maxScale, scale * d.getScaleFactor()));
                    float k = ns / scale;
                    scale = ns;
                    tx = d.getFocusX() + (tx - d.getFocusX()) * k;
                    ty = d.getFocusY() + (ty - d.getFocusY()) * k;
                    apply();
                    return true;
                }
            });
            tapDet = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > getFitScale() * 1.4f) {
                        fit();
                    } else {
                        zoomTo(scale * 2f, e.getX(), e.getY());
                    }
                    return true;
                }
            });
        }

        public void setBitmap(Bitmap b) {
            bmp = b;
            fit();
        }

        private float getFitScale() {
            if (bmp == null) return 1f;
            return Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
        }

        public void fit() {
            if (bmp == null || getWidth() == 0) return;
            scale = getFitScale();
            minScale = scale * 0.9f;
            tx = (getWidth() - bmp.getWidth() * scale) / 2f;
            ty = (getHeight() - bmp.getHeight() * scale) / 2f;
            apply();
        }

        private void zoomTo(float ns, float fx, float fy) {
            ns = Math.max(minScale, Math.min(maxScale, ns));
            float k = ns / scale;
            scale = ns;
            tx = fx + (tx - fx) * k;
            ty = fy + (ty - fy) * k;
            apply();
        }

        private void apply() {
            m.reset();
            m.postScale(scale, scale);
            m.postTranslate(tx, ty);
            invalidate();
        }

        public float getScale() {
            return scale;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bmp == null) return;
            c.drawBitmap(bmp, m, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaleDet.onTouchEvent(e);
            tapDet.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDet.isInProgress() && e.getPointerCount() == 1) {
                        tx += e.getX() - lastX;
                        ty += e.getY() - lastY;
                        lastX = e.getX();
                        lastY = e.getY();
                        apply();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }
}
