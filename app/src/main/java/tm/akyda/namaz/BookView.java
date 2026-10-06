package tm.akyda.namaz;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.text.TextPaint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * «Живая» страница книги: бумага с тенью и обрезом, перелистывание пальцем
 * с объёмным поворотом вокруг корешка, доводящая анимация после отпускания,
 * перелист по вертикали (вверх/вниз) и свайпы.
 *
 * Вью не знает о содержимом книги: она получает готовые изображения страниц
 * (их рисует ReaderActivity) и сообщает о перелистывании через Listener.
 */
public class BookView extends View {

    public interface Listener {
        /** Нужны соседние страницы (для начала перелистывания). */
        void onNeedPages();

        /** Страница перелистнута: dir — направление (DIR_RIGHT/DIR_LEFT/DIR_UP/DIR_DOWN). */
        void onTurned(int dir);

        /** Короткое нажатие внутри страницы. */
        void onTap(float x, float y);
    }

    public static final int DIR_RIGHT = 1;   // вперёд
    public static final int DIR_LEFT = -1;   // назад
    public static final int DIR_UP = 2;      // вперёд по вертикали
    public static final int DIR_DOWN = -2;   // назад по вертикали

    private Bitmap cur;
    private Bitmap next;
    private Bitmap prev;

    private int dir = 0;
    private float progress = 0f;
    private ValueAnimator animator;
    private boolean completeOnEnd = false;
    private final RectF pageRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Camera camera = new Camera();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path shadePath = new Path();

    private Listener listener;
    private String chapterTitle = "";
    private int pageNumber = 1;
    private int totalPages = 1;
    private int theme = Skin.T_PAPER;
    private boolean bookmarked = false;
    private boolean showChrome = true;

    private float downX, downY;
    private boolean dragging = false;
    private boolean vertical = false;
    private long downTime = 0;
    private float touchSlop;
    private float radius;
    private float edge;

    public BookView(Context c) {
        super(c);
        touchSlop = U.dpf(c, 9f);
        radius = U.dpf(c, 9f);
        edge = U.dpf(c, 4f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setTheme(int theme) {
        this.theme = theme;
        invalidate();
    }

    public void setChromeVisible(boolean visible) {
        this.showChrome = visible;
        invalidate();
    }

    public void setMeta(String chapterTitle, int pageNumber, int totalPages) {
        this.chapterTitle = chapterTitle == null ? "" : chapterTitle;
        this.pageNumber = pageNumber;
        this.totalPages = totalPages;
        invalidate();
    }

    public void setBookmarked(boolean marked) {
        this.bookmarked = marked;
        invalidate();
    }

    public void setPages(Bitmap current, Bitmap nextPage, Bitmap prevPage) {
        this.cur = current;
        this.next = nextPage;
        this.prev = prevPage;
        invalidate();
    }

    public Bitmap current() {
        return cur;
    }

    public boolean isAnimating() {
        return animator != null;
    }

    /* ==================== Геометрия ==================== */

    private void layoutRects() {
        int w = getWidth(), h = getHeight();
        float padX = U.dpf(getContext(), 15f);
        float padTop = U.dpf(getContext(), 10f);
        float padBottom = U.dpf(getContext(), 10f);
        pageRect.set(padX, padTop, Math.max(padX + 10, w - padX), Math.max(padTop + 10, h - padBottom));
    }

    public RectF pageRect() {
        if (pageRect.width() <= 0) layoutRects();
        return pageRect;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutRects();
        if (listener != null) listener.onNeedPages();
    }

    /* ==================== Рисование ==================== */

    @Override
    protected void onDraw(Canvas c) {
        if (pageRect.width() <= 0) layoutRects();

        // тень книги на «столе»
        solid.setColor(0x3D000000);
        U.roundRect(c, pageRect.left + U.dpf(getContext(), 2f), pageRect.top + U.dpf(getContext(), 4f),
                pageRect.right + U.dpf(getContext(), 7f), pageRect.bottom + U.dpf(getContext(), 7f),
                radius, solid);

        // обрез книги — стопка листов под страницей
        for (int i = (int) edge; i >= 1; i--) {
            solid.setColor(U.mix(Skin.paper(theme), 0xFF000000, 0.05f + 0.025f * i));
            U.roundRect(c, pageRect.left + i * 0.9f, pageRect.top + i, pageRect.right + i,
                    pageRect.bottom + i, radius, solid);
        }

        boolean forward = isForward(dir);
        Bitmap base = progress > 0f ? (forward ? next : cur) : cur;
        drawPage(c, base);

        if (progress > 0f && dir != 0) {
            drawTurn(c, forward);
        }
        if (showChrome) drawChrome(c);
    }

    private boolean isForward(int direction) {
        return direction == DIR_RIGHT || direction == DIR_UP;
    }

    private boolean isVertical(int direction) {
        return direction == DIR_UP || direction == DIR_DOWN;
    }

    /** Страница целиком. */
    private void drawPage(Canvas c, Bitmap bm) {
        c.save();
        clip.reset();
        clip.addRoundRect(pageRect, radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        solid.setColor(Skin.paper(theme));
        c.drawRect(pageRect, solid);
        if (bm != null) c.drawBitmap(bm, null, pageRect, paint);
        c.restore();
    }

    /** Поворот листа. */
    private void drawTurn(Canvas c, boolean forward) {
        boolean vert = isVertical(dir);
        float p = progress;
        float angle = forward ? -180f * p : -180f + 180f * p;

        float pivotX = pageRect.centerX();
        float pivotY = vert ? pageRect.top : pageRect.centerY();
        if (!vert) pivotX = pageRect.left;

        // мягкая тень от поворачиваемого листа на открывающейся странице
        int shAlpha = (int) (120 * Math.sin(Math.PI * Math.min(1f, p)) + 20);
        shade.setColor((shAlpha << 24));
        shadePath.reset();
        if (vert) {
            float from = forward ? pageRect.top + pageRect.height() * p * 0.35f : pageRect.top;
            float to = forward ? pageRect.bottom : pageRect.top + pageRect.height() * (1f - p) * 0.65f;
            shadePath.addRect(pageRect.left, from, pageRect.right, to, Path.Direction.CW);
        } else {
            float from = forward ? pageRect.left + pageRect.width() * p * 0.35f : pageRect.left;
            float to = forward ? pageRect.right : pageRect.left + pageRect.width() * (1f - p) * 0.65f;
            shadePath.addRect(from, pageRect.top, to, pageRect.bottom, Path.Direction.CW);
        }
        c.drawPath(shadePath, shade);

        c.save();
        camera.save();
        if (vert) camera.rotateX(angle);
        else camera.rotateY(angle);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-pivotX, -pivotY);
        matrix.postTranslate(pivotX, pivotY);
        c.concat(matrix);

        clip.reset();
        clip.addRoundRect(pageRect, radius, radius, Path.Direction.CW);
        c.clipPath(clip);

        // лицевая сторона видна до 90°, дальше — оборот листа
        float front = forward ? (p < 0.5f ? 1f : 0f) : (p > 0.5f ? 1f : 0f);
        Bitmap face = forward ? cur : prev;
        float dark = (float) Math.sin(Math.PI * p) * (Skin.paperIsDark(theme) ? 0.34f : 0.13f);

        if (front > 0f && face != null) {
            solid.setColor(U.mix(Skin.paper(theme), 0xFF000000, 0.06f + dark));
            c.drawRect(pageRect, solid);
            paint.setAlpha((int) (255 * front));
            c.drawBitmap(face, null, pageRect, paint);
            paint.setAlpha(255);
            // тень у корешка
            shade.setColor(((int) (86 * p) << 24));
            if (vert) {
                c.drawRect(pageRect.left, pageRect.top, pageRect.right,
                        pageRect.top + pageRect.height() * 0.32f, shade);
            } else {
                c.drawRect(pageRect.left, pageRect.top,
                        pageRect.left + pageRect.width() * 0.32f, pageRect.bottom, shade);
            }
        } else {
            solid.setColor(U.mix(Skin.paper(theme), 0xFF000000, 0.04f + dark));
            c.drawRect(pageRect, solid);
            solid.setColor(U.mix(Skin.paper(theme), Skin.paperSub(theme), 0.18f));
            for (int i = 1; i < 6; i++) {
                if (vert) {
                    float x = pageRect.left + pageRect.width() * i / 6f;
                    c.drawRect(x, pageRect.top, x + 1f, pageRect.bottom, solid);
                } else {
                    float y = pageRect.top + pageRect.height() * i / 6f;
                    c.drawRect(pageRect.left, y, pageRect.right, y + 1f, solid);
                }
            }
        }

        // блеск на сгибе
        shade.setColor(((int) (64 * Math.sin(Math.PI * p)) << 24));
        if (vert) {
            c.drawRect(pageRect.left, pageRect.top + pageRect.height() * 0.88f, pageRect.right,
                    pageRect.bottom, shade);
        } else {
            c.drawRect(pageRect.right - pageRect.width() * 0.07f, pageRect.top, pageRect.right,
                    pageRect.bottom, shade);
        }
        c.restore();
    }

    /** Колонтитул, номер страницы и лента закладки. */
    private void drawChrome(Canvas c) {
        boolean dark = Skin.paperIsDark(theme);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(U.tf("sans-serif-medium", android.graphics.Typeface.NORMAL));
        text.setLetterSpacing(0.12f);

        String head = chapterTitle == null ? "" : chapterTitle;
        if (head.length() > 44) head = head.substring(0, 42).trim() + "…";
        if (!U.empty(head)) {
            text.setTextSize(U.sp(getContext(), 10f));
            text.setColor(Skin.withAlpha(Skin.paperSub(theme), dark ? 0.92f : 0.95f));
            c.drawText(head.toUpperCase(), pageRect.centerX(),
                    pageRect.top + U.dpf(getContext(), 24f), text);
        }
        text.setTextSize(U.sp(getContext(), 10.5f));
        text.setColor(Skin.withAlpha(Skin.paperSub(theme), dark ? 0.85f : 0.9f));
        c.drawText(pageNumber + " / " + Math.max(1, totalPages), pageRect.centerX(),
                pageRect.bottom - U.dpf(getContext(), 10f), text);

        if (bookmarked) {
            Paint ribbon = new Paint(Paint.ANTI_ALIAS_FLAG);
            ribbon.setColor(Skin.paperAccent(theme));
            float w = U.dpf(getContext(), 13f);
            float x = pageRect.right - U.dpf(getContext(), 36f);
            float top = pageRect.top;
            float bottom = pageRect.top + U.dpf(getContext(), 42f);
            Path path = new Path();
            path.moveTo(x, top);
            path.lineTo(x + w, top);
            path.lineTo(x + w, bottom);
            path.lineTo(x + w / 2f, bottom - U.dpf(getContext(), 8f));
            path.lineTo(x, bottom);
            path.close();
            c.drawPath(path, ribbon);
        }

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 0.7f)));
        line.setColor(Skin.withAlpha(Skin.paperAccent(theme), dark ? 0.32f : 0.26f));
        U.roundRect(c, pageRect.left + U.dpf(getContext(), 8f), pageRect.top + U.dpf(getContext(), 8f),
                pageRect.right - U.dpf(getContext(), 8f), pageRect.bottom - U.dpf(getContext(), 8f),
                U.dpf(getContext(), 5f), line);
    }

    /* ==================== Анимация ==================== */

    private void run(final int direction, float from, float to, long dur, final boolean complete) {
        cancelAnim();
        dir = direction;
        completeOnEnd = complete;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(dur);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                animator = null;
                if (complete) {
                    progress = 0f;
                    dir = 0;
                    invalidate();
                    if (listener != null) listener.onTurned(direction);
                } else {
                    progress = 0f;
                    dir = 0;
                    invalidate();
                }
            }
        });
        animator.start();
    }

    private void cancelAnim() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }

    /** Перелистнуть вперёд (DIR_RIGHT или DIR_UP). */
    public void flipForward(int direction) {
        progress = 0.001f;
        run(direction, 0.001f, 1f, 460, true);
    }

    /** Перелистнуть назад (DIR_LEFT или DIR_DOWN). */
    public void flipBack(int direction) {
        progress = 0.001f;
        run(direction, 0.001f, 1f, 460, true);
    }

    /** Плавно «переехать» на новую страницу без пальца (например, из оглавления). */
    public void softSwap() {
        cancelAnim();
        dir = 0;
        progress = 0f;
        invalidate();
    }

    public void stopAnimation() {
        cancelAnim();
        progress = 0f;
        dir = 0;
        invalidate();
    }

    /* ==================== Касания ==================== */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (animator != null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downTime = SystemClock.uptimeMillis();
                dragging = false;
                vertical = false;
                if (listener != null) listener.onNeedPages();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging) {
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        dragging = true;
                        vertical = false;
                    } else if (Math.abs(dy) > touchSlop * 1.3f && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                        dragging = true;
                        vertical = true;
                    }
                }
                if (dragging) {
                    if (vertical) {
                        dir = dy < 0 ? DIR_UP : DIR_DOWN;
                        progress = Math.min(1f, Math.abs(dy) / (pageRect.height() * 0.9f));
                    } else {
                        dir = dx < 0 ? DIR_RIGHT : DIR_LEFT;
                        progress = Math.min(1f, Math.abs(dx) / (pageRect.width() * 0.9f));
                    }
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                long dt = SystemClock.uptimeMillis() - downTime;
                if (dragging) {
                    boolean complete = progress > 0.3f || (dt < 340 && progress > 0.12f);
                    run(dir, progress, complete ? 1f : 0f, complete ? (long) (280 + 240 * (1 - progress)) : 240, complete);
                    dragging = false;
                    return true;
                }
                if (dt < 420 && Math.abs(dx) < touchSlop * 2f && Math.abs(dy) < touchSlop * 2f) {
                    if (listener != null) listener.onTap(e.getX(), e.getY());
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    /** Плавная прокрутка страницы вперёд/назад без анимации листа. */
    public void slideSwap(final int direction) {
        cancelAnim();
        dir = direction;
        progress = 0.001f;
        run(direction, 0.001f, 1f, 380, true);
    }

    public boolean isDragging() {
        return dragging;
    }

    /** Совместимость с API: Android 4.1+ умеет postInvalidateOnAnimation. */
    private void invalidateAnim() {
        if (Build.VERSION.SDK_INT >= 16) postInvalidateOnAnimation();
        else invalidate();
    }
}
