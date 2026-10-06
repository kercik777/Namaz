package tm.akyda.namaz.ui;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import tm.akyda.namaz.ContentRepo;
import tm.akyda.namaz.P;
import tm.akyda.namaz.R;
import tm.akyda.namaz.U;
import tm.akyda.namaz.Ui;
import tm.akyda.namaz.Widgets;

/** Заставка: живой логотип на изумрудном фоне. */
public class SplashActivity extends BaseActivity {

    @Override
    protected int themeRes() {
        return R.style.AppTheme_Splash;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF06231A);
        root.addView(new Backdrop(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout center = Ui.col(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        final Widgets.Logo logo = new Widgets.Logo(this);
        int ls = U.dp(this, 148);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
        logo.setLayoutParams(llp);
        center.addView(logo);

        TextView name = Ui.tv(this, getString(R.string.app_name), 31f, 0xFFF7F1E2, U.tf("serif", Typeface.BOLD));
        name.setLetterSpacing(0.045f);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = Ui.llpMatch();
        nlp.topMargin = U.dp(this, 14);
        center.addView(name, nlp);

        TextView tag = Ui.tv(this, getString(R.string.app_tagline), 12.5f, 0xFFDCC07A, U.uiMed(this));
        tag.setLetterSpacing(0.22f);
        tag.setGravity(Gravity.CENTER);
        tag.setText(tag.getText().toString().toUpperCase());
        LinearLayout.LayoutParams tlp = Ui.llpMatch();
        tlp.topMargin = U.dp(this, 10);
        center.addView(tag, tlp);

        final View bar = new View(this);
        final int barW = U.dp(this, 120);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(barW, U.dp(this, 2.4f));
        blp.topMargin = U.dp(this, 30);
        bar.setLayoutParams(blp);
        bar.setBackground(U.round(0x33F2DCA4, 2f, this));
        center.addView(bar);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        root.addView(center, clp);

        // Подпись внизу: офлайн, без рекламы и разрешений
        TextView foot = Ui.tv(this, "100% oflaýn · mahabatsyz · rugsatsyz", 11f, 0x88DCC07A, U.ui(this));
        foot.setLetterSpacing(0.1f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = U.dp(this, 34);
        root.addView(foot, flp);

        setContentView(root);

        logo.play(1150);
        bar.setScaleX(0f);
        bar.setPivotX(0f);
        bar.animate().scaleX(1f).setDuration(1500).start();

        // Прогреваем контент, пока идёт заставка
        ContentRepo.get().preload(this);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent i;
                if (!P.b(P.onboarded, false)) {
                    i = new Intent(SplashActivity.this, OnboardActivity.class);
                } else {
                    i = new Intent(SplashActivity.this, MainActivity.class);
                }
                startActivity(i);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            }
        }, 1750);
    }

    /** Фон заставки: градиент, свечение и восточный орнамент. */
    static class Backdrop extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Backdrop(android.content.Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setShader(new LinearGradient(0, 0, w * 0.4f, h, 0xFF10493A, 0xFF041611, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.5f, h * 0.36f, w * 0.75f,
                    new int[]{0x334FD3A2, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(U.dpf(getContext(), 1f));
            p.setColor(0x1FF2DCA4);
            float cx = w / 2f, cy = h * 0.98f;
            for (int i = 1; i <= 5; i++) {
                float r = w * 0.18f * i;
                RectF rect = new RectF(cx - r, cy - r, cx + r, cy + r);
                c.drawArc(rect, 200, 140, false, p);
            }
            Path star = new Path();
            float sx = w * 0.5f, sy = U.dpf(getContext(), 64f);
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 5;
                float px = sx + (float) Math.cos(a) * U.dpf(getContext(), 7f);
                float py = sy + (float) Math.sin(a) * U.dpf(getContext(), 7f);
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x22F2DCA4);
            c.drawPath(star, p);
        }
    }
}
