package tm.akyda.namaz.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import tm.akyda.namaz.R;

/**
 * Экран запуска: короткая, спокойная анимация знака приложения.
 * Никаких задержек «на пустом месте» — переход выполняется сразу после анимации.
 */
public class SplashActivity extends BaseActivity {

    private static final long ANIM_MS = 900L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final View logoWrap = findViewById(R.id.logo_wrap);
        final ImageView logo = findViewById(R.id.logo);
        final TextView title = findViewById(R.id.title);
        final TextView tagline = findViewById(R.id.tagline);
        final View line = findViewById(R.id.line);
        final View ring = findViewById(R.id.ring);

        logoWrap.setAlpha(0f);
        logo.setScaleX(0.72f);
        logo.setScaleY(0.72f);
        logo.setRotation(-8f);
        title.setAlpha(0f);
        title.setTranslationY(dp(14));
        tagline.setAlpha(0f);
        tagline.setTranslationY(dp(10));
        line.setScaleX(0f);
        ring.setAlpha(0f);
        ring.setScaleX(0.85f);
        ring.setScaleY(0.85f);

        logoWrap.animate().alpha(1f).setDuration(420).start();
        logo.animate()
                .scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(760)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
        ring.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(620)
                .setStartDelay(140).setInterpolator(new DecelerateInterpolator()).start();

        title.animate().alpha(1f).translationY(0f).setDuration(480).setStartDelay(260).start();
        line.animate().scaleX(1f).setDuration(520).setStartDelay(360)
                .setInterpolator(new DecelerateInterpolator()).start();
        tagline.animate().alpha(1f).translationY(0f).setDuration(480).setStartDelay(430).start();

        // Медленное «дыхание» кольца вокруг знака
        ObjectAnimator breath = ObjectAnimator.ofFloat(ring, "alpha", 0.35f, 0.9f);
        breath.setDuration(1400);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setStartDelay(700);
        breath.start();

        logoWrap.postDelayed(new Runnable() {
            @Override
            public void run() {
                goNext();
            }
        }, ANIM_MS);
    }

    private void goNext() {
        if (isFinishing()) {
            return;
        }
        Intent intent = new Intent(this, prefs.isOnboarded() ? MainActivity.class : OnboardingActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_scale_in, R.anim.fade_scale_out);
        finish();
    }
}
