package tm.akyda.namaz;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Иконки, нарисованные кодом (единый стиль: линия 2 из 24, скруглённые стыки).
 * Никаких сторонних библиотек и никаких растровых картинок — всё векторное,
 * поэтому иконки идеально резкие на любом экране.
 */
public final class Ico {

    public static final int HOME = 0, LIBRARY = 1, SEARCH = 2, MARK = 3, MORE = 4,
            BACK = 5, CLOSE = 6, GEAR = 7, TYPE = 8, LIST = 9, SHARE = 10, COPY = 11,
            STAR = 12, INFO = 13, SUN = 14, MOON = 15, CHECK = 16, RIGHT = 17,
            LEFT = 18, PLAY = 19, CLOCK = 20, GLOBE = 21, IMAGE = 22, ZOOM_IN = 23,
            ZOOM_OUT = 24, TRASH = 25, QUOTE = 26, BOOK = 27, SHIELD = 28, HEART = 29,
            REFRESH = 30, PLUS = 31, MINUS = 32, PAGE = 33, DRAG = 34, SEARCH_BOOK = 35,
            PALETTE = 36, STATS = 37, HEADPHONES = 38, DIM = 39, ARROW_UP = 40,
            ANIM = 41, LANGUAGE = 42, MAIL = 43, EDIT = 44, GRID = 45, FLAG = 46;

    private static final Path P = new Path();
    private static final RectF R = new RectF();
    private static final float U = 1f / 24f;   // сетка 24×24

    private static float x(float v, float cx, float s) {
        return cx + (v - 12f) * s;
    }

    private static float y(float v, float cy, float s) {
        return cy + (v - 12f) * s;
    }

    /**
     * @param c    холст
     * @param icon идентификатор иконки
     * @param cx   центр по X
     * @param cy   центр по Y
     * @param size размер (сторона квадрата), px
     * @param p    краска (цвет)
     */
    public static void draw(Canvas c, int icon, float cx, float cy, float size, Paint p) {
        float s = size * U * 1.0f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.4f, size * 0.085f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setAntiAlias(true);

        switch (icon) {
            case HOME: {
                line(c, p, cx, cy, s, 4.5f, 11f, 12f, 4.2f);
                line(c, p, cx, cy, s, 12f, 4.2f, 19.5f, 11f);
                line(c, p, cx, cy, s, 6.6f, 9.6f, 6.6f, 19.6f);
                line(c, p, cx, cy, s, 6.6f, 19.6f, 17.4f, 19.6f);
                line(c, p, cx, cy, s, 17.4f, 19.6f, 17.4f, 9.6f);
                line(c, p, cx, cy, s, 10.2f, 19.6f, 10.2f, 14.6f);
                line(c, p, cx, cy, s, 10.2f, 14.6f, 13.8f, 14.6f);
                line(c, p, cx, cy, s, 13.8f, 14.6f, 13.8f, 19.6f);
                break;
            }
            case LIBRARY: {
                line(c, p, cx, cy, s, 9f, 4.5f, 9f, 19.5f);
                line(c, p, cx, cy, s, 15f, 4.5f, 15f, 19.5f);
                line(c, p, cx, cy, s, 3f, 7f, 3f, 19.5f);
                line(c, p, cx, cy, s, 3f, 19.5f, 21f, 19.5f);
                line(c, p, cx, cy, s, 9f, 4.5f, 3f, 7f);
                line(c, p, cx, cy, s, 15f, 4.5f, 9f, 4.5f);
                line(c, p, cx, cy, s, 19.5f, 8.5f, 15f, 4.5f);
                line(c, p, cx, cy, s, 19.5f, 8.5f, 19.5f, 19.5f);
                break;
            }
            case SEARCH:
            case SEARCH_BOOK: {
                R.set(x(4f, cx, s), y(4f, cy, s), x(16f, cx, s), y(16f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                line(c, p, cx, cy, s, 14.6f, 14.6f, 20f, 20f);
                if (icon == SEARCH_BOOK) {
                    line(c, p, cx, cy, s, 7.5f, 11.5f, 12.5f, 11.5f);
                }
                break;
            }
            case MARK: {
                P.reset();
                P.moveTo(x(6f, cx, s), y(3.4f, cy, s));
                P.lineTo(x(18f, cx, s), y(3.4f, cy, s));
                P.lineTo(x(18f, cx, s), y(20.6f, cy, s));
                P.lineTo(x(12f, cx, s), y(16.2f, cy, s));
                P.lineTo(x(6f, cx, s), y(20.6f, cy, s));
                P.close();
                c.drawPath(P, p);
                break;
            }
            case MORE: {
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 5f, 12f, 1.6f);
                dot(c, p, cx, cy, s, 12f, 12f, 1.6f);
                dot(c, p, cx, cy, s, 19f, 12f, 1.6f);
                break;
            }
            case BACK: {
                line(c, p, cx, cy, s, 19.5f, 12f, 4.5f, 12f);
                line(c, p, cx, cy, s, 11f, 5.5f, 4.5f, 12f);
                line(c, p, cx, cy, s, 4.5f, 12f, 11f, 18.5f);
                break;
            }
            case CLOSE: {
                line(c, p, cx, cy, s, 6f, 6f, 18f, 18f);
                line(c, p, cx, cy, s, 18f, 6f, 6f, 18f);
                break;
            }
            case GEAR: {
                R.set(x(8.6f, cx, s), y(8.6f, cy, s), x(15.4f, cx, s), y(15.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float c1 = (float) Math.cos(a), s1 = (float) Math.sin(a);
                    line(c, p, cx, cy, s,
                            12f + c1 * 5.6f, 12f + s1 * 5.6f,
                            12f + c1 * 8.6f, 12f + s1 * 8.6f);
                }
                break;
            }
            case TYPE: {
                line(c, p, cx, cy, s, 4.5f, 19.5f, 9.6f, 5f);
                line(c, p, cx, cy, s, 9.6f, 5f, 14.7f, 19.5f);
                line(c, p, cx, cy, s, 6.6f, 15f, 12.6f, 15f);
                line(c, p, cx, cy, s, 17.2f, 12.5f, 19.6f, 19.5f);
                break;
            }
            case LIST: {
                for (int i = 0; i < 3; i++) {
                    float yy = 6.5f + i * 5.5f;
                    p.setStyle(Paint.Style.FILL);
                    dot(c, p, cx, cy, s, 4f, yy, 1.35f);
                    p.setStyle(Paint.Style.STROKE);
                    line(c, p, cx, cy, s, 8.4f, yy, 19.5f, yy);
                }
                break;
            }
            case SHARE: {
                p.setStyle(Paint.Style.STROKE);
                line(c, p, cx, cy, s, 8.6f, 10.6f, 15.4f, 6.6f);
                line(c, p, cx, cy, s, 8.6f, 13.4f, 15.4f, 17.4f);
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 18f, 5f, 3f);
                dot(c, p, cx, cy, s, 6f, 12f, 3f);
                dot(c, p, cx, cy, s, 18f, 19f, 3f);
                break;
            }
            case COPY: {
                R.set(x(3.5f, cx, s), y(3.5f, cy, s), x(15f, cx, s), y(15f, cy, s));
                c.drawRoundRect(R, 2.6f * s, 2.6f * s, p);
                R.set(x(9f, cx, s), y(9f, cy, s), x(20.5f, cx, s), y(20.5f, cy, s));
                c.drawRoundRect(R, 2.6f * s, 2.6f * s, p);
                break;
            }
            case STAR: {
                P.reset();
                float cxr = cx, cyr = cy - size * 0.02f;
                float outer = size * 0.40f, inner = size * 0.17f;
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float r = (i % 2 == 0) ? outer : inner;
                    float px = cxr + (float) Math.cos(ang) * r;
                    float py = cyr + (float) Math.sin(ang) * r;
                    if (i == 0) P.moveTo(px, py);
                    else P.lineTo(px, py);
                }
                P.close();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(P, p);
                break;
            }
            case INFO: {
                R.set(x(3.6f, cx, s), y(3.6f, cy, s), x(20.4f, cx, s), y(20.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 12f, 8.2f, 1.25f);
                p.setStyle(Paint.Style.STROKE);
                line(c, p, cx, cy, s, 12f, 11.4f, 12f, 16.6f);
                break;
            }
            case SUN: {
                R.set(x(7.6f, cx, s), y(7.6f, cy, s), x(16.4f, cx, s), y(16.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float c1 = (float) Math.cos(a), s1 = (float) Math.sin(a);
                    line(c, p, cx, cy, s, 12f + c1 * 4.9f, 12f + s1 * 4.9f, 12f + c1 * 7.6f, 12f + s1 * 7.6f);
                }
                break;
            }
            case MOON: {
                P.reset();
                P.setFillType(Path.FillType.EVEN_ODD);
                R.set(x(4.5f, cx, s), y(4.5f, cy, s), x(19.5f, cx, s), y(19.5f, cy, s));
                P.addOval(R, Path.Direction.CW);
                R.set(x(9.5f, cx, s), y(2f, cy, s), x(24.5f, cx, s), y(17f, cy, s));
                P.addOval(R, Path.Direction.CW);
                p.setStyle(Paint.Style.FILL);
                c.drawPath(P, p);
                break;
            }
            case CHECK: {
                line(c, p, cx, cy, s, 4.5f, 12.8f, 9.6f, 18f);
                line(c, p, cx, cy, s, 9.6f, 18f, 19.5f, 6.4f);
                break;
            }
            case RIGHT: {
                line(c, p, cx, cy, s, 9.5f, 5f, 16.5f, 12f);
                line(c, p, cx, cy, s, 16.5f, 12f, 9.5f, 19f);
                break;
            }
            case LEFT: {
                line(c, p, cx, cy, s, 14.5f, 5f, 7.5f, 12f);
                line(c, p, cx, cy, s, 7.5f, 12f, 14.5f, 19f);
                break;
            }
            case ARROW_UP: {
                line(c, p, cx, cy, s, 12f, 19.5f, 12f, 4.5f);
                line(c, p, cx, cy, s, 5.5f, 11f, 12f, 4.5f);
                line(c, p, cx, cy, s, 12f, 4.5f, 18.5f, 11f);
                break;
            }
            case PLAY: {
                P.reset();
                P.moveTo(x(7f, cx, s), y(4.5f, cy, s));
                P.lineTo(x(19f, cx, s), y(12f, cy, s));
                P.lineTo(x(7f, cx, s), y(19.5f, cy, s));
                P.close();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(P, p);
                break;
            }
            case CLOCK: {
                R.set(x(3.6f, cx, s), y(3.6f, cy, s), x(20.4f, cx, s), y(20.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                line(c, p, cx, cy, s, 12f, 6.8f, 12f, 12f);
                line(c, p, cx, cy, s, 12f, 12f, 16.2f, 14.6f);
                break;
            }
            case GLOBE:
            case LANGUAGE: {
                R.set(x(3.6f, cx, s), y(3.6f, cy, s), x(20.4f, cx, s), y(20.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                R.set(x(8.2f, cx, s), y(3.6f, cy, s), x(15.8f, cx, s), y(20.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                line(c, p, cx, cy, s, 3.6f, 12f, 20.4f, 12f);
                break;
            }
            case IMAGE: {
                R.set(x(3.6f, cx, s), y(5f, cy, s), x(20.4f, cx, s), y(19f, cy, s));
                c.drawRoundRect(R, 2.4f * s, 2.4f * s, p);
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 8.6f, 10f, 1.5f);
                p.setStyle(Paint.Style.STROKE);
                line(c, p, cx, cy, s, 4.6f, 17.6f, 10f, 12.6f);
                line(c, p, cx, cy, s, 10f, 12.6f, 14.4f, 16.4f);
                line(c, p, cx, cy, s, 14.4f, 16.4f, 17f, 14f);
                line(c, p, cx, cy, s, 17f, 14f, 20f, 17.6f);
                break;
            }
            case ZOOM_IN:
            case ZOOM_OUT: {
                R.set(x(3.6f, cx, s), y(3.6f, cy, s), x(15.4f, cx, s), y(15.4f, cy, s));
                c.drawArc(R, 0, 360, false, p);
                line(c, p, cx, cy, s, 14.2f, 14.2f, 20.4f, 20.4f);
                line(c, p, cx, cy, s, 6.5f, 9.5f, 12.5f, 9.5f);
                if (icon == ZOOM_IN) line(c, p, cx, cy, s, 9.5f, 6.5f, 9.5f, 12.5f);
                break;
            }
            case TRASH: {
                line(c, p, cx, cy, s, 4.5f, 7f, 19.5f, 7f);
                line(c, p, cx, cy, s, 9.5f, 7f, 9.5f, 4.6f);
                line(c, p, cx, cy, s, 9.5f, 4.6f, 14.5f, 4.6f);
                line(c, p, cx, cy, s, 14.5f, 4.6f, 14.5f, 7f);
                line(c, p, cx, cy, s, 6.5f, 7f, 7.6f, 20f);
                line(c, p, cx, cy, s, 17.5f, 7f, 16.4f, 20f);
                line(c, p, cx, cy, s, 7.6f, 20f, 16.4f, 20f);
                line(c, p, cx, cy, s, 10.4f, 10.5f, 10.9f, 17f);
                line(c, p, cx, cy, s, 13.6f, 10.5f, 13.1f, 17f);
                break;
            }
            case QUOTE: {
                p.setStyle(Paint.Style.FILL);
                quoteMark(c, p, cx, cy, s, 8.6f);
                quoteMark(c, p, cx, cy, s, 16.4f);
                break;
            }
            case BOOK: {
                P.reset();
                P.moveTo(x(12f, cx, s), y(7f, cy, s));
                P.cubicTo(x(9.5f, cx, s), y(5.2f, cy, s), x(6.4f, cx, s), y(4.6f, cy, s), x(4f, cx, s), y(4.6f, cy, s));
                P.lineTo(x(4f, cx, s), y(18.4f, cy, s));
                P.cubicTo(x(6.4f, cx, s), y(18.4f, cy, s), x(9.5f, cx, s), y(19f, cy, s), x(12f, cx, s), y(20.6f, cy, s));
                P.cubicTo(x(14.5f, cx, s), y(19f, cy, s), x(17.6f, cx, s), y(18.4f, cy, s), x(20f, cx, s), y(18.4f, cy, s));
                P.lineTo(x(20f, cx, s), y(4.6f, cy, s));
                P.cubicTo(x(17.6f, cx, s), y(4.6f, cy, s), x(14.5f, cx, s), y(5.2f, cy, s), x(12f, cx, s), y(7f, cy, s));
                P.close();
                c.drawPath(P, p);
                line(c, p, cx, cy, s, 12f, 7f, 12f, 20.6f);
                break;
            }
            case SHIELD: {
                P.reset();
                P.moveTo(x(12f, cx, s), y(3.2f, cy, s));
                P.lineTo(x(19.6f, cx, s), y(6.4f, cy, s));
                P.lineTo(x(19.6f, cx, s), y(12.4f, cy, s));
                P.cubicTo(x(19.6f, cx, s), y(16.6f, cy, s), x(16.4f, cx, s), y(19.6f, cy, s), x(12f, cx, s), y(21f, cy, s));
                P.cubicTo(x(7.6f, cx, s), y(19.6f, cy, s), x(4.4f, cx, s), y(16.6f, cy, s), x(4.4f, cx, s), y(12.4f, cy, s));
                P.lineTo(x(4.4f, cx, s), y(6.4f, cy, s));
                P.close();
                c.drawPath(P, p);
                break;
            }
            case HEART: {
                P.reset();
                P.moveTo(x(12f, cx, s), y(20f, cy, s));
                P.cubicTo(x(4f, cx, s), y(15f, cy, s), x(3f, cx, s), y(9.6f, cy, s), x(6.4f, cx, s), y(6.6f, cy, s));
                P.cubicTo(x(9f, cx, s), y(4.4f, cy, s), x(11.4f, cx, s), y(5.6f, cy, s), x(12f, cx, s), y(7.6f, cy, s));
                P.cubicTo(x(12.6f, cx, s), y(5.6f, cy, s), x(15f, cx, s), y(4.4f, cy, s), x(17.6f, cx, s), y(6.6f, cy, s));
                P.cubicTo(x(21f, cx, s), y(9.6f, cy, s), x(20f, cx, s), y(15f, cy, s), x(12f, cx, s), y(20f, cy, s));
                P.close();
                c.drawPath(P, p);
                break;
            }
            case REFRESH: {
                R.set(x(4.4f, cx, s), y(4.4f, cy, s), x(19.6f, cx, s), y(19.6f, cy, s));
                c.drawArc(R, -60, 280, false, p);
                line(c, p, cx, cy, s, 19.6f, 6.2f, 20.4f, 12.2f);
                line(c, p, cx, cy, s, 19.6f, 6.2f, 14f, 5.2f);
                break;
            }
            case PLUS: {
                line(c, p, cx, cy, s, 12f, 5f, 12f, 19f);
                line(c, p, cx, cy, s, 5f, 12f, 19f, 12f);
                break;
            }
            case MINUS: {
                line(c, p, cx, cy, s, 5f, 12f, 19f, 12f);
                break;
            }
            case PAGE: {
                P.reset();
                P.moveTo(x(6f, cx, s), y(3.6f, cy, s));
                P.lineTo(x(14f, cx, s), y(3.6f, cy, s));
                P.lineTo(x(18.6f, cx, s), y(8.2f, cy, s));
                P.lineTo(x(18.6f, cx, s), y(20.4f, cy, s));
                P.lineTo(x(6f, cx, s), y(20.4f, cy, s));
                P.close();
                c.drawPath(P, p);
                line(c, p, cx, cy, s, 14f, 3.6f, 14f, 8.2f);
                line(c, p, cx, cy, s, 14f, 8.2f, 18.6f, 8.2f);
                line(c, p, cx, cy, s, 9f, 12.6f, 15.6f, 12.6f);
                line(c, p, cx, cy, s, 9f, 16.2f, 13.4f, 16.2f);
                break;
            }
            case DRAG: {
                line(c, p, cx, cy, s, 4f, 9f, 20f, 9f);
                line(c, p, cx, cy, s, 4f, 15f, 20f, 15f);
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 8f, 5.4f, 1.4f);
                dot(c, p, cx, cy, s, 16f, 5.4f, 1.4f);
                dot(c, p, cx, cy, s, 8f, 18.6f, 1.4f);
                dot(c, p, cx, cy, s, 16f, 18.6f, 1.4f);
                break;
            }
            case PALETTE: {
                P.reset();
                P.addCircle(cx, cy, size * 0.40f, Path.Direction.CW);
                c.drawPath(P, p);
                p.setStyle(Paint.Style.FILL);
                dot(c, p, cx, cy, s, 9f, 8.6f, 1.5f);
                dot(c, p, cx, cy, s, 15f, 9.6f, 1.5f);
                dot(c, p, cx, cy, s, 10.4f, 14.6f, 1.5f);
                break;
            }
            case STATS: {
                line(c, p, cx, cy, s, 4.5f, 20f, 4.5f, 4.5f);
                line(c, p, cx, cy, s, 4.5f, 20f, 20f, 20f);
                p.setStyle(Paint.Style.FILL);
                U.roundRect(c, x(7.5f, cx, s), y(12f, cy, s), x(11f, cx, s), y(20f, cy, s), 1.4f * s, p);
                U.roundRect(c, x(13f, cx, s), y(7.5f, cy, s), x(16.5f, cx, s), y(20f, cy, s), 1.4f * s, p);
                break;
            }
            case HEADPHONES: {
                R.set(x(3.6f, cx, s), y(4f, cy, s), x(20.4f, cx, s), y(20f, cy, s));
                c.drawArc(R, 180, 180, false, p);
                R.set(x(3.4f, cx, s), y(12f, cy, s), x(8.4f, cx, s), y(18.6f, cy, s));
                c.drawRoundRect(R, 2.4f * s, 2.4f * s, p);
                R.set(x(15.6f, cx, s), y(12f, cy, s), x(20.6f, cx, s), y(18.6f, cy, s));
                c.drawRoundRect(R, 2.4f * s, 2.4f * s, p);
                break;
            }
            case DIM: {
                p.setStyle(Paint.Style.FILL);
                R.set(x(9.8f, cx, s), y(4f, cy, s), x(14.2f, cx, s), y(20f, cy, s));
                c.drawRoundRect(R, 2f * s, 2f * s, p);
                break;
            }
            case ANIM: {
                line(c, p, cx, cy, s, 3.5f, 12f, 12.5f, 12f);
                line(c, p, cx, cy, s, 12.5f, 12f, 9.5f, 8.6f);
                line(c, p, cx, cy, s, 12.5f, 12f, 9.5f, 15.4f);
                R.set(x(11.5f, cx, s), y(6f, cy, s), x(21f, cx, s), y(18f, cy, s));
                c.drawArc(R, -70, 140, false, p);
                break;
            }
            case MAIL: {
                R.set(x(3.4f, cx, s), y(5.4f, cy, s), x(20.6f, cx, s), y(18.6f, cy, s));
                c.drawRoundRect(R, 2.4f * s, 2.4f * s, p);
                line(c, p, cx, cy, s, 4f, 7f, 12f, 13f);
                line(c, p, cx, cy, s, 20f, 7f, 12f, 13f);
                break;
            }
            case EDIT: {
                P.reset();
                P.moveTo(x(4f, cx, s), y(20f, cy, s));
                P.lineTo(x(4.6f, cx, s), y(15.6f, cy, s));
                P.lineTo(x(15.6f, cx, s), y(4.6f, cy, s));
                P.lineTo(x(19.4f, cx, s), y(8.4f, cy, s));
                P.lineTo(x(8.4f, cx, s), y(19.4f, cy, s));
                P.close();
                c.drawPath(P, p);
                line(c, p, cx, cy, s, 13.6f, 6.6f, 17.4f, 10.4f);
                break;
            }
            case GRID: {
                R.set(x(3.6f, cx, s), y(3.6f, cy, s), x(10.6f, cx, s), y(10.6f, cy, s));
                c.drawRoundRect(R, 1.8f * s, 1.8f * s, p);
                R.set(x(13.4f, cx, s), y(3.6f, cy, s), x(20.4f, cx, s), y(10.6f, cy, s));
                c.drawRoundRect(R, 1.8f * s, 1.8f * s, p);
                R.set(x(3.6f, cx, s), y(13.4f, cy, s), x(10.6f, cx, s), y(20.4f, cy, s));
                c.drawRoundRect(R, 1.8f * s, 1.8f * s, p);
                R.set(x(13.4f, cx, s), y(13.4f, cy, s), x(20.4f, cx, s), y(20.4f, cy, s));
                c.drawRoundRect(R, 1.8f * s, 1.8f * s, p);
                break;
            }
            case FLAG: {
                line(c, p, cx, cy, s, 5.6f, 3.6f, 5.6f, 20.4f);
                P.reset();
                P.moveTo(x(5.6f, cx, s), y(4.6f, cy, s));
                P.lineTo(x(18.6f, cx, s), y(7.6f, cy, s));
                P.lineTo(x(5.6f, cx, s), y(12.8f, cy, s));
                P.close();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(P, p);
                break;
            }
            default:
                break;
        }
        p.setStyle(Paint.Style.STROKE);
    }

    private static void line(Canvas c, Paint p, float cx, float cy, float s,
                             float x1, float y1, float x2, float y2) {
        c.drawLine(x(x1, cx, s), y(y1, cy, s), x(x2, cx, s), y(y2, cy, s), p);
    }

    private static void dot(Canvas c, Paint p, float cx, float cy, float s, float dx, float dy, float r) {
        c.drawCircle(x(dx, cx, s), y(dy, cy, s), r * s, p);
    }

    private static void quoteMark(Canvas c, Paint p, float cx, float cy, float s, float px) {
        P.reset();
        P.addCircle(x(px, cx, s), y(10f, cy, s), 2.9f * s, Path.Direction.CW);
        P.moveTo(x(px - 1.4f, cx, s), y(12.6f, cy, s));
        P.lineTo(x(px - 0.2f, cx, s), y(17.4f, cy, s));
        P.lineTo(x(px - 3.2f, cx, s), y(17.4f, cy, s));
        P.close();
        c.drawPath(P, p);
    }
}
