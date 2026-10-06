package tm.akyda.namaz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Простая иконка-вью: рисует векторную иконку нужным цветом. */
public class IconView extends View {

    private int icon;
    private int color = 0xFF000000;
    private float inset = 0.16f;
    private float fallbackDp = 20f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float degrees = 0f;

    public IconView(Context c, int icon, int color, float sizeDp) {
        super(c);
        this.icon = icon;
        this.color = color;
        int s = U.dp(c, sizeDp);
        fallbackDp = sizeDp;
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(s, s));
    }

    public IconView(Context c) {
        super(c);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setInset(float inset) {
        this.inset = inset;
        invalidate();
    }

    public void setDegrees(float d) {
        this.degrees = d;
        invalidate();
    }

    public float getDegrees() {
        return degrees;
    }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
        int s = Math.min(getMeasuredWidth(), getMeasuredHeight());
        if (s <= 0) s = U.dp(getContext(), fallbackDp);   // не даём иконке схлопнуться в ноль
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setColor(color);
        float size = Math.min(getWidth(), getHeight()) * (1f - inset * 2f);
        c.save();
        c.rotate(degrees, getWidth() / 2f, getHeight() / 2f);
        Ico.draw(c, icon, getWidth() / 2f, getHeight() / 2f, size, p);
        c.restore();
    }
}
