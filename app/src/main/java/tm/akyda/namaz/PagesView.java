package tm.akyda.namaz;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Лист книги.
 *
 * Страница выглядит как настоящий книжный лист: бумага, тень от блока страниц,
 * золотая рамка, тень у корешка. Лист выше экрана, поэтому вертикальный свайп
 * читает его до конца (вверх и вниз), а перелистывается книга только влево
 * и вправо — лист поднимается и поворачивается вокруг корешка, на обороте
 * просвечивает текст.
 */
public class PagesView extends View {

    public interface Listener {
        /** Нужны изображения текущей и соседних страниц. */
        void onNeedPages();

        /** Лист перелистнут: forward — вперёд. */
        void onTurned(boolean forward);

        /** Короткое касание: x, y в координатах вида. */
        void onTap(float x, float y);
    }

    private float insetTop = 12f, insetBottom = 12f;
    private Bitmap cur, next, prev;
    private float progress = 0f;
    private boolean forward = true;
    private boolean turning = false;

    private float scroll = 0f;        // окно чтения внутри листа (px вида)
    private float fling = 0f;         // инерция после свайпа
    private float pull = 0f;          // «пружинка» за краем листа

    private ValueAnimator animator;
    private ValueAnimator spring;
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Matrix matrix = new Matrix();
    private final Camera camera = new Camera();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    private Listener listener;
    private String chapter = "";
    private int pageNumber = 1;
    private int pageCount = 1;
    private boolean bookmarked = false;
    private int theme = Skin.T_PAPER;

    private float downX, downY, lastX, lastY;
    private long downTime, lastTime;
    private float vx = 0f, vy = 0f;
    private boolean dragging = false;
    private boolean verticalDrag = false;
    private final float touchSlop;

    public PagesView(Context c) {
        super(c);
        touchSlop = U.dpf(c, 8f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setTheme(int t) {
        theme = t;
        invalidate();
    }

    public void setMeta(String chapter, int pageNumber, int pageCount) {
        this.chapter = chapter == null ? "" : chapter;
        this.pageNumber = pageNumber;
        this.pageCount = pageCount;
        invalidate();
    }

    public void setBookmarked(boolean b) {
        bookmarked = b;
        invalidate();
    }

    /** Страница может быть выше вида — тогда её читают вертикальным свайпом. */
    public void setPages(Bitmap current, Bitmap nextPage, Bitmap prevPage) {
        cur = current;
        next = nextPage;
        prev = prevPage;
        clampScroll();
        invalidate();
    }

    public Bitmap current() {
        return cur;
    }

    /* ==================== Геометрия ==================== */

    /** Отступы листа сверху и снизу — чтобы панели не закрывали текст. */
    public void setInsets(float topDp, float bottomDp) {
        insetTop = topDp;
        insetBottom = bottomDp;
        layoutRect();
    }

    private void layoutRect() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float side = U.dpf(getContext(), 14f);
        float top = U.dpf(getContext(), insetTop);
        float bottom = U.dpf(getContext(), insetBottom);
        float pw = Math.min(w - side * 2f, U.dpf(getContext(), 620f));
        float left = (w - pw) / 2f;
        rect.set(left, top, left + pw, Math.max(top + 40f, h - bottom));
        clampScroll();
    }

    public RectF pageRect() {
        if (rect.width() <= 0) layoutRect();
        return rect;
    }

    /** Сколько пикселей листа скрыто снизу (для индикатора). */
    public float readProgress() {
        Bitmap bm = cur;
        if (bm == null || bm.getWidth() <= 0) return 1f;
        float scale = rect.width() / bm.getWidth();
        float visible = rect.height();
        float total = bm.getHeight() * scale;
        if (total <= visible) return 1f;
        return Math.max(0f, Math.min(1f, (scroll + visible) / total));
    }

    public boolean hasMoreBelow() {
        return maxScroll() - scroll > U.dpf(getContext(), 2f);
    }

    private float maxScroll() {
        Bitmap bm = cur;
        if (bm == null || bm.getWidth() <= 0) return 0f;
        float scale = rect.width() / bm.getWidth();
        float total = bm.getHeight() * scale;
        return Math.max(0f, total - rect.height());
    }

    private void clampScroll() {
        scroll = Math.max(0f, Math.min(maxScroll(), scroll));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutRect();
        if (listener != null) listener.onNeedPages();
    }

    /* ==================== Рисование ==================== */

    @Override
    protected void onDraw(Canvas c) {
        if (rect.width() <= 0) layoutRect();
        float pad = U.dpf(getContext(), 9f);

        c.save();
        c.translate(0, pull);   // мягкий отклик на жест за краем листа

        // стол под книгой: мягкая тень и «обрез» листов
        solid.setColor(0x30000000);
        U.roundRect(c, rect.left + pad * 0.5f, rect.top + pad * 0.8f,
                rect.right + pad, rect.bottom + pad, pad, solid);
        for (int i = 3; i >= 1; i--) {
            solid.setColor(U.mix(Skin.paper(theme), 0xFF000000, 0.04f + 0.02f * i));
            U.roundRect(c, rect.left + i, rect.top + i * 1.4f, rect.right + i * 1.2f,
                    rect.bottom + i * 1.2f, pad, solid);
        }

        boolean base = progress > 0.001f;
        drawLeaf(c, base ? (forward ? next : cur) : cur, base ? 0f : scroll);

        if (base) {
            drawTurning(c);
            drawFoldShadow(c);
        }
        c.restore();
    }

    /** Целый лист бумаги с текстом. */
    private void drawLeaf(Canvas c, Bitmap bm, float offset) {
        float pad = U.dpf(getContext(), 9f);
        c.save();
        clip.reset();
        clip.addRoundRect(rect, pad, pad, Path.Direction.CW);
        c.clipPath(clip);

        solid.setColor(Skin.paper(theme));
        c.drawRect(rect, solid);

        if (bm != null && bm.getWidth() > 0) {
            float scale = rect.width() / bm.getWidth();
            int winH = Math.min(bm.getHeight(), Math.round(rect.height() / scale));
            int top = Math.max(0, Math.min(bm.getHeight() - winH, Math.round(offset / scale)));
            src.set(0, top, bm.getWidth(), top + winH);
            dst.set(Math.round(rect.left), Math.round(rect.top),
                    Math.round(rect.right), Math.round(rect.top + winH * scale));
            c.drawBitmap(bm, src, dst, paint);
        } else {
            // пустая страница — только отлив бумаги
            solid.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    Skin.withAlpha(0xFFFFFFFF, Skin.paperIsDark(theme) ? 0.03f : 0.5f),
                    Skin.withAlpha(0xFF000000, Skin.paperIsDark(theme) ? 0.12f : 0.035f),
                    Shader.TileMode.CLAMP));
            c.drawRect(rect, solid);
            solid.setShader(null);
        }

        if (offset > 0f) {
            // текст продолжается выше — лёгкая тень у верхнего края листа
            solid.setShader(new LinearGradient(0, rect.top, 0, rect.top + U.dpf(getContext(), 22f),
                    0x2E000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(rect.left, rect.top, rect.right, rect.top + U.dpf(getContext(), 22f), solid);
            solid.setShader(null);
        }
        if (hasMoreBelow()) {
            float y = rect.bottom;
            solid.setShader(new LinearGradient(0, y - U.dpf(getContext(), 26f), 0, y,
                    0x00000000, 0x33000000, Shader.TileMode.CLAMP));
            c.drawRect(rect.left, y - U.dpf(getContext(), 26f), rect.right, y, solid);
            solid.setShader(null);
        }
        c.restore();

        // золотая рамка листа и индикатор прочитанного
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 0.8f)));
        edge.setColor(Skin.withAlpha(Skin.paperAccent(theme), Skin.paperIsDark(theme) ? 0.35f : 0.28f));
        U.roundRect(c, rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad,
                pad * 0.7f, edge);
        edge.setStyle(Paint.Style.FILL);

        float p = readProgress();
        if (p < 0.999f) {
            float x = rect.right - pad * 1.6f;
            float h = rect.height() - pad * 3.2f;
            solid.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.22f));
            U.roundRect(c, x, rect.top + pad * 1.6f, x + U.dpf(getContext(), 2.2f),
                    rect.top + pad * 1.6f + h, U.dpf(getContext(), 1.2f), solid);
            solid.setColor(Skin.withAlpha(Skin.paperAccent(theme), 0.75f));
            U.roundRect(c, x, rect.top + pad * 1.6f, x + U.dpf(getContext(), 2.2f),
                    rect.top + pad * 1.6f + h * p, U.dpf(getContext(), 1.2f), solid);
        }
    }

    /** Поворачивающийся лист. */
    private void drawTurning(Canvas c) {
        float pad = U.dpf(getContext(), 9f);
        float p = progress;
        float angle = (forward ? -1f : 1f) * 180f * p;
        float pivotX = rect.left;
        float pivotY = rect.centerY();

        float w = rect.width() * Math.abs((float) Math.cos(Math.PI * p));
        float left = forward ? rect.left : rect.right - w;
        float right = left + w;

        c.save();
        clip.reset();
        clip.addRect(left, rect.top, right, rect.bottom, Path.Direction.CW);
        c.clipPath(clip);

        camera.save();
        camera.rotateY(angle);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-pivotX, -pivotY);
        matrix.postTranslate(pivotX, pivotY);
        c.concat(matrix);

        clip.reset();
        clip.addRoundRect(rect, pad, pad, Path.Direction.CW);
        c.clipPath(clip);

        float fade = (float) Math.sin(Math.PI * p);
        solid.setColor(Skin.paper(theme));
        c.drawRect(rect, solid);

        Bitmap face = forward ? cur : prev;
        if (face != null && face.getWidth() > 0) {
            float scale = rect.width() / face.getWidth();
            int winH = Math.min(face.getHeight(), Math.round(rect.height() / scale));
            int top = Math.max(0, Math.min(face.getHeight() - winH, Math.round((forward ? scroll : 0f) / scale)));
            src.set(0, top, face.getWidth(), top + winH);
            dst.set(Math.round(rect.left), Math.round(rect.top),
                    Math.round(rect.right), Math.round(rect.top + winH * scale));
            if (p > 0.5f) {
                // оборот листа: текст просвечивает
                paint.setAlpha(70);
                c.save();
                c.scale(-1f, 1f, rect.centerX(), rect.centerY());
                c.drawBitmap(face, src, dst, paint);
                c.restore();
                paint.setAlpha(255);
            } else {
                c.drawBitmap(face, src, dst, paint);
            }
        }
        solid.setShader(new LinearGradient(rect.left, 0, rect.left + rect.width() * 0.35f, 0,
                (int) (90 * fade) << 24, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(rect.left, rect.top, rect.left + rect.width() * 0.35f, rect.bottom, solid);
        solid.setShader(null);
        c.restore();
    }

    /** Тень у сгиба листа. */
    private void drawFoldShadow(Canvas c) {
        float p = progress;
        if (p > 0.5f) return;
        float w = rect.width() * Math.abs((float) Math.cos(Math.PI * p));
        float foldX = forward ? rect.left + w : rect.right - w;
        float max = U.dpf(getContext(), 26f);

        float x0 = forward ? foldX : foldX - max;
        float x1 = forward ? foldX + max : foldX;
        solid.setShader(new LinearGradient(x0, 0, x1, 0, 0x4A000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(forward ? foldX : foldX - max, rect.top,
                forward ? foldX + max : foldX, rect.bottom, solid);
        solid.setShader(null);

        edge.setColor(0x33000000);
        edge.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 1.4f)));
        c.drawLine(foldX, rect.top, foldX, rect.bottom, edge);
        edge.setColor(0x22FFFFFF);
        edge.setStrokeWidth(Math.max(1f, U.dpf(getContext(), 0.6f)));
        c.drawLine(foldX, rect.top, foldX, rect.bottom, edge);
    }

    /* ==================== Анимация ==================== */

    private void run(float from, float to, long ms, final boolean complete) {
        cancelTurn();
        turning = true;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(ms);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                animator = null;
                turning = false;
                progress = 0f;
                invalidate();
                if (complete && listener != null) listener.onTurned(forward);
            }
        });
        animator.start();
    }

    private void cancelTurn() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        turning = false;
    }

    /** Перелистнуть программно (кнопки «назад»/«вперёд»). */
    public void turn(boolean fwd) {
        if (turning) return;
        forward = fwd;
        if (listener != null) listener.onNeedPages();
        run(0.001f, 1f, 460, true);
    }

    /** Показать новую страницу без анимации (оглавление, поиск, старт). */
    public void jump() {
        cancelTurn();
        progress = 0f;
        pull = 0f;
        scroll = 0f;
        fling = 0f;
        invalidate();
    }

    public boolean isAnimating() {
        return turning;
    }

    /* ==================== Касания ==================== */

    private void springBack() {
        if (spring != null) spring.cancel();
        spring = ValueAnimator.ofFloat(pull, 0f);
        spring.setDuration(240);
        spring.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pull = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        spring.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (turning) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                downTime = lastTime = SystemClock.uptimeMillis();
                vx = vy = 0f;
                dragging = false;
                verticalDrag = false;
                fling = 0f;
                if (listener != null) listener.onNeedPages();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                long now = SystemClock.uptimeMillis();
                if (now > lastTime) {
                    vx = (e.getX() - lastX) / (float) (now - lastTime);
                    vy = (e.getY() - lastY) / (float) (now - lastTime);
                    lastX = e.getX();
                    lastY = e.getY();
                    lastTime = now;
                }
                if (!dragging) {
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        // что взяло верх — то и делает: вбок листаем, вниз читаем
                        verticalDrag = Math.abs(dy) >= Math.abs(dx);
                        dragging = true;
                    }
                } else {
                    // решение не меняем до конца жеста
                    verticalDrag = verticalDrag && Math.abs(dy) >= Math.abs(dx) * 0.7f;
                }
                if (dragging) {
                    if (verticalDrag) {
                        progress = 0f;
                        float next = scroll - dy;
                        float max = maxScroll();
                        if (next < 0f) {
                            scroll = 0f;
                            pull = Math.min(U.dpf(getContext(), 26f), -next * 0.35f);
                        } else if (next > max) {
                            scroll = max;
                            pull = -Math.min(U.dpf(getContext(), 26f), (next - max) * 0.35f);
                        } else {
                            scroll = next;
                            pull = 0f;
                        }
                        if (listener != null && hasMoreBelow() && max - scroll < U.dpf(getContext(), 120f)) {
                            listener.onNeedPages();
                        }
                    } else {
                        pull = 0f;
                        forward = dx < 0;
                        progress = Math.min(1f, Math.abs(dx) / Math.max(1f, rect.width() * 0.85f));
                    }
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                long dt = SystemClock.uptimeMillis() - downTime;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (dragging && verticalDrag) {
                    pull = 0f;
                    if (Math.abs(vy) > 0.4f) {
                        // инерция: докручиваем лист и мягко останавливаемся
                        final float target = Math.max(0f, Math.min(maxScroll(), scroll - vy * 240f));
                        final float from = scroll;
                        ValueAnimator f = ValueAnimator.ofFloat(0f, 1f);
                        f.setDuration((long) Math.min(620, 220 + Math.abs(target - from) / 2f));
                        f.setInterpolator(new DecelerateInterpolator());
                        f.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator a) {
                                scroll = from + (target - from) * (Float) a.getAnimatedValue();
                                invalidate();
                            }
                        });
                        f.start();
                    } else {
                        clampScroll();
                    }
                    dragging = false;
                    verticalDrag = false;
                    return true;
                }
                if (dragging) {
                    boolean complete = progress > 0.35f
                            || (Math.abs(vx) > 0.35f && progress > 0.06f);
                    if (complete) forward = progress > 0.5f ? forward : vx < 0;
                    run(progress, complete ? 1f : 0f,
                            complete ? (long) (330 + 200 * (1 - progress)) : 260, complete);
                    dragging = false;
                    return true;
                }
                if (pull != 0f) springBack();
                if (dt < 420 && Math.abs(dx) < touchSlop * 2f && Math.abs(dy) < touchSlop * 2f) {
                    if (listener != null) listener.onTap(e.getX(), e.getY());
                }
                return true;
            }
            default:
                return true;
        }
    }

    /** Сколько листа уже прочитано сверху вниз (0 — начало, 1 — низ листа). */
    public void scrollTo(float value) {
        Bitmap bm = cur;
        if (bm == null) return;
        scroll = Math.max(0f, Math.min(1f, value)) * maxScroll();
        invalidate();
    }

    public float scrollValue() {
        float max = maxScroll();
        return max <= 0f ? 1f : scroll / max;
    }

    public void setVertical(float v) {
        scroll = v;
        invalidate();
    }

    public String chapter() {
        return chapter;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public int pageCount() {
        return pageCount;
    }

    public boolean bookmarked() {
        return bookmarked;
    }
}
