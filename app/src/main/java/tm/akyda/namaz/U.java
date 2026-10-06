package tm.akyda.namaz;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

/** Мелкие утилиты: размеры, шрифты, фоны, анимации, подсказки. */
public final class U {

    private static final HashMap<String, Typeface> tf = new HashMap<>();
    private static Bitmap grain;
    private static Bitmap grainDark;
    private static final Random RND = new Random(42);

    /* ---------- Размеры ---------- */

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static float dpf(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics()));
    }

    public static int screenW(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.widthPixels;
    }

    public static int screenH(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.heightPixels;
    }

    /* ---------- Шрифты (только системные) ---------- */

    public static Typeface tf(String name, int style) {
        String key = name + "#" + style;
        Typeface t = tf.get(key);
        if (t == null) {
            t = Typeface.create(name, style);
            tf.put(key, t);
        }
        return t;
    }

    public static Typeface ui(Context c) {
        return tf("sans-serif", Typeface.NORMAL);
    }

    public static Typeface uiMed(Context c) {
        return tf("sans-serif-medium", Typeface.NORMAL);
    }

    public static Typeface uiLight(Context c) {
        return tf("sans-serif-light", Typeface.NORMAL);
    }

    public static Typeface serif(Context c) {
        return tf("serif", Typeface.NORMAL);
    }

    public static Typeface serifBold(Context c) {
        return tf("serif", Typeface.BOLD);
    }

    /* ---------- Фоны и «материальность» ---------- */

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dpf(c, radiusDp));
        return d;
    }

    public static GradientDrawable strokeRound(int fill, int stroke, float radiusDp, float strokeDp, Context c) {
        GradientDrawable d = round(fill, radiusDp, c);
        d.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return d;
    }

    public static GradientDrawable gradientRect(int from, int to, float radiusDp, Context c, boolean vertical) {
        GradientDrawable d = new GradientDrawable(
                vertical ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{from, to});
        d.setCornerRadius(dpf(c, radiusDp));
        return d;
    }

    public static GradientDrawable goldGradient(Context c, float radiusDp) {
        return gradientRect(Skin.goldGradStart(), Skin.goldGradEnd(), radiusDp, c, false);
    }

    /** Клик с волной (Ripple) и мягким «нажатием». */
    public static void click(View v, final Runnable action) {
        Drawable content = v.getBackground();
        if (content == null) content = new ColorDrawable(Color.TRANSPARENT);
        RippleDrawable rip = new RippleDrawable(ColorStateList.valueOf(0x33888888), content, null);
        v.setBackground(rip);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                x.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                x.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                            }
                        }).start();
                action.run();
            }
        });
    }

    public static void shadow(View v, float elevationDp) {
        if (Build.VERSION.SDK_INT >= 21) {
            v.setElevation(dpf(v.getContext(), elevationDp));
        }
    }

    public static void fade(View v, boolean show, long dur) {
        v.animate().cancel();
        v.animate().alpha(show ? 1f : 0f).setDuration(dur)
                .setInterpolator(new DecelerateInterpolator()).start();
        if (show) v.setVisibility(View.VISIBLE);
    }

    public static void slideUp(View v, boolean in, long dur, int fromDp) {
        v.animate().cancel();
        Context c = v.getContext();
        if (in) {
            v.setVisibility(View.VISIBLE);
            v.setTranslationY(dpf(c, fromDp));
            v.setAlpha(0f);
            v.animate().translationY(0f).alpha(1f).setDuration(dur)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            v.animate().translationY(dpf(c, fromDp)).alpha(0f).setDuration(dur)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            v.setVisibility(View.GONE);
                        }
                    }).start();
        }
    }

    /* ---------- Бумажная фактура ---------- */

    public static BitmapDrawableHolder paperGrain(Context c, boolean darkTheme) {
        if (darkTheme) {
            if (grainDark == null) grainDark = makeGrain(0x14FFFFFF);
            return new BitmapDrawableHolder(grainDark);
        }
        if (grain == null) grain = makeGrain(0x0F000000);
        return new BitmapDrawableHolder(grain);
    }

    private static Bitmap makeGrain(int color) {
        int s = 128;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        int[] px = new int[s * s];
        for (int i = 0; i < px.length; i++) {
            int a = RND.nextInt(10);
            px[i] = (color & 0x00FFFFFF) | (a << 24);
        }
        b.setPixels(px, 0, s, 0, 0, s, s);
        return b;
    }

    /** Простая обёртка, чтобы не тянуть android.graphics.drawable.BitmapDrawable в API-ветках. */
    public static final class BitmapDrawableHolder {
        public final Bitmap bitmap;

        BitmapDrawableHolder(Bitmap b) {
            this.bitmap = b;
        }
    }

    /* ---------- Текст ---------- */

    public static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(new Locale("ru")) + s.substring(1);
    }

    /** Убирает диакритику — для «мягкого» поиска (ä → a, ň → n, ş → s, ý → y …). */
    public static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(new Locale("ru"));
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            switch (ch) {
                case 'ä': case 'á': case 'à': case 'â': case 'ā': ch = 'a'; break;
                case 'ç': ch = 'c'; break;
                case 'ď': ch = 'd'; break;
                case 'é': case 'è': case 'ê': case 'ë': case 'ē': ch = 'e'; break;
                case 'ğ': ch = 'g'; break;
                case 'ı': case 'í': case 'ì': case 'î': case 'ï': ch = 'i'; break;
                case 'ñ': case 'ň': case 'ń': ch = 'n'; break;
                case 'ö': case 'ó': case 'ò': case 'ô': case 'ō': ch = 'o'; break;
                case 'ş': case 'ś': ch = 's'; break;
                case 'ü': case 'ú': case 'ù': case 'û': case 'ū': ch = 'u'; break;
                case 'ý': case 'ÿ': ch = 'y'; break;
                case 'ž': case 'ź': case 'ż': ch = 'z'; break;
                case 'ё': ch = 'е'; break;
                case 'й': ch = 'и'; break;
                default: break;
            }
            b.append(ch);
        }
        return b.toString().replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    public static String timeAgo(Context c, long when) {
        long d = System.currentTimeMillis() - when;
        long min = d / 60000;
        if (min < 1) return c.getString(R.string.just_now);
        if (min < 60) return c.getResources().getQuantityString(R.plurals.min_ago, (int) min, (int) min);
        long h = min / 60;
        if (h < 24) return c.getResources().getQuantityString(R.plurals.hours_ago, (int) h, (int) h);
        long days = h / 24;
        if (days < 30) return c.getResources().getQuantityString(R.plurals.days_ago, (int) days, (int) days);
        return new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date(when));
    }

    public static String greeting(Context c) {
        int h = new java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return c.getString(R.string.greeting_morning);
        if (h >= 12 && h < 17) return c.getString(R.string.greeting_day);
        if (h >= 17 && h < 23) return c.getString(R.string.greeting_evening);
        return c.getString(R.string.greeting_night);
    }

    /* ---------- Системное ---------- */

    public static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("akyda", text));
    }

    public static void share(Context c, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        c.startActivity(Intent.createChooser(i, c.getString(R.string.share_via)));
    }

    public static void vibrate(Context c, int ms) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(ms);
        } catch (Exception ignored) {
        }
    }

    /** Короткое «пилюльное» уведомление снизу — вместо системного Toast. */
    public static void pill(Activity a, CharSequence text) {
        final ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        final TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14f);
        t.setTypeface(uiMed(a));
        t.setTextColor(0xFFF6F2E8);
        t.setBackground(round(0xF21A2A23, 24f, a));
        t.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12));
        if (Build.VERSION.SDK_INT >= 21) t.setElevation(dpf(a, 8f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(a, 96);
        root.addView(t, lp);
        t.setAlpha(0f);
        t.setTranslationY(dpf(a, 24f));
        t.animate().alpha(1f).translationY(0f).setDuration(180).start();
        t.postDelayed(new Runnable() {
            @Override
            public void run() {
                t.animate().alpha(0f).translationY(dpf(a, 16f)).setDuration(200)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                root.removeView(t);
                            }
                        }).start();
            }
        }, 1900);
    }

    /* ---------- Рисование ---------- */

    public static void roundRect(Canvas c, float l, float t, float r, float b, float rad, Paint p) {
        c.drawRoundRect(new RectF(l, t, r, b), rad, rad, p);
    }

    public static int mix(int a, int b, float t) {
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    public static boolean empty(String s) {
        return TextUtils.isEmpty(s) || s.trim().isEmpty();
    }

    public static String trimTo(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= n ? s : s.substring(0, n - 1).trim() + "…";
    }
}
