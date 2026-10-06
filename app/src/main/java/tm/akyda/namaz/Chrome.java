package tm.akyda.namaz;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Элементы «оправы» приложения: нижняя навигация и нижняя панель (sheet). */
public final class Chrome {

    /* ==================== НИЖНЯЯ НАВИГАЦИЯ ==================== */

    public interface OnTab {
        void onTab(int index);
    }

    public static class TabBar extends View {
        private final String[] labels;
        private final int[] icons;
        private int selected = 0;
        private float slide = 0f;      // анимированная позиция «пилюли»
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint topLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private OnTab listener;

        public TabBar(Context c, String[] labels, int[] icons) {
            super(c);
            this.labels = labels;
            this.icons = icons;
            textPaint.setTypeface(U.tf("sans-serif-medium", Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        public void setListener(OnTab l) {
            listener = l;
        }

        public int getSelected() {
            return selected;
        }

        public void select(int index, boolean animate) {
            if (index < 0 || index >= labels.length) return;
            selected = index;
            if (animate) {
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(slide, index);
                a.setDuration(260);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                        slide = (Float) animation.getAnimatedValue();
                        invalidate();
                    }
                });
                a.start();
            } else {
                slide = index;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            boolean dark = Skin.dark(getContext());
            pill.setColor(Skin.withAlpha(Skin.accent(getContext()), dark ? 0.18f : 0.14f));
            topLine.setColor(Skin.line(getContext()));
            c.drawRect(0, 0, w, U.dpf(getContext(), 1f), topLine);

            float itemW = w / labels.length;
            float pillW = itemW * 0.56f;
            float pillH = U.dpf(getContext(), 30f);
            float cx = itemW * (slide + 0.5f);
            float cy = U.dpf(getContext(), 22f);
            U.roundRect(c, cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f, pillH / 2f, pill);

            for (int i = 0; i < labels.length; i++) {
                float icx = itemW * (i + 0.5f);
                boolean sel = i == selected;
                int color = sel ? Skin.accent(getContext()) : Skin.sub(getContext());
                iconPaint.setColor(color);
                Ico.draw(c, icons[i], icx, cy, U.dpf(getContext(), 21f), iconPaint);
                textPaint.setColor(color);
                textPaint.setTextSize(U.sp(getContext(), 10.4f));
                c.drawText(labels[i], icx, h - U.dpf(getContext(), 9f), textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                int idx = (int) (e.getX() / (getWidth() / (float) labels.length));
                idx = Math.max(0, Math.min(labels.length - 1, idx));
                if (idx != selected) {
                    select(idx, true);
                    U.vibrate(getContext(), 10);
                    if (listener != null) listener.onTab(idx);
                }
            } else if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            return true;
        }
    }

    /* ==================== НИЖНЯЯ ПАНЕЛЬ ==================== */

    /** Панель снизу с затемнением, «ручкой» и закрытием свайпом вниз. */
    public static class Sheet {
        private final Activity activity;
        private final FrameLayout overlay;
        private final LinearLayout box;
        private final View dim;
        private boolean shown;
        private float dragY = 0;

        public Sheet(Activity a, String title, View content) {
            this.activity = a;
            Context c = a;
            overlay = new FrameLayout(c);
            overlay.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setVisibility(View.GONE);

            dim = new View(c);
            dim.setBackgroundColor(0xB3000000);
            dim.setAlpha(0f);
            overlay.addView(dim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            U.click(dim, new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            });

            box = Ui.col(c);
            // скругление только сверху: снизу панель прилегает к краю экрана
            box.setBackground(U.roundTop(Skin.surface(c), 28f, c));
            U.shadow(box, 18f);
            int pad = U.dp(c, 20);
            box.setPadding(pad, U.dp(c, 10), pad, U.dp(c, 20));

            // тонкая золотая линия у верхней кромки панели
            View gold = new View(c);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, U.dp(c, 2f));
            gold.setLayoutParams(glp);
            gold.setBackground(U.gradientRect(Skin.withAlpha(Skin.accent(c), 0f),
                    Skin.withAlpha(Skin.accent(c), 0.85f), 0f, c, true));
            box.addView(gold);

            // «Ручка»
            View handle = new View(c);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(U.dp(c, 44), U.dp(c, 4.5f));
            hlp.gravity = Gravity.CENTER_HORIZONTAL;
            hlp.topMargin = U.dp(c, 8);
            hlp.bottomMargin = U.dp(c, 12);
            handle.setLayoutParams(hlp);
            handle.setBackground(U.round(Skin.withAlpha(Skin.sub(c), 0.42f), 3f, c));
            box.addView(handle);

            if (title != null && !title.isEmpty()) {
                TextView t = Ui.title(c, title, 19f);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                box.addView(t);
                box.addView(Ui.space(c, 12));
            }

            box.addView(content, Ui.llpMatch());

            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.BOTTOM;
            overlay.addView(box, blp);

            // Перетаскивание вниз для закрытия — за «ручку» панели
            final View handleView = handle;
            handleView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            dragY = e.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dy = e.getRawY() - dragY;
                            if (dy > 0) {
                                box.setTranslationY(dy);
                                dim.setAlpha(Math.max(0f, 0.7f - dy / (box.getHeight() + 1f) * 0.7f));
                                return true;
                            }
                            return false;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (box.getTranslationY() > box.getHeight() * 0.25f) {
                                hide();
                            } else {
                                box.animate().translationY(0f).setDuration(160).start();
                                dim.animate().alpha(0.7f).setDuration(160).start();
                            }
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }

        public ViewGroup root() {
            return overlay;
        }

        public void show() {
            if (shown) return;
            shown = true;
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(180).start();
            dim.setAlpha(0f);
            dim.animate().alpha(0.7f).setDuration(200).start();
            box.post(new Runnable() {
                @Override
                public void run() {
                    box.setTranslationY(box.getHeight() + U.dp(activity, 40));
                    box.animate().translationY(0f).setDuration(260)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }
            });
        }

        public void hide() {
            if (!shown) return;
            shown = false;
            box.animate().translationY(box.getHeight() + U.dp(activity, 40)).setDuration(200).start();
            dim.animate().alpha(0f).setDuration(180).start();
            overlay.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    overlay.setVisibility(View.GONE);
                }
            }).start();
        }

        public boolean isShown() {
            return shown;
        }
    }
}
