package tm.akyda.namaz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import tm.akyda.namaz.ui.ReaderActivity;

/** Переходы и диалоги. */
public final class Nav {

    public static final String EXTRA_BOOK = "book_id";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_BLOCK = "block";

    public static void openReader(Activity a, String bookId, int chapter, int block) {
        Intent i = new Intent(a, ReaderActivity.class);
        i.putExtra(EXTRA_BOOK, bookId);
        i.putExtra(EXTRA_CHAPTER, chapter);
        i.putExtra(EXTRA_BLOCK, block);
        a.startActivity(i);
        a.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /** Красивый диалог подтверждения. */
    public static void confirm(final Activity a, String title, String message, final Runnable yes) {
        LinearLayout c = Ui.col(a);
        Ui.pad(c, a, 24, 18, 24, 8);
        if (title != null) {
            TextView t = Ui.title(a, title, 19f);
            c.addView(t);
            c.addView(Ui.space(a, 10));
        }
        TextView m = Ui.tv(a, message, 14.5f, Skin.sub(a), U.ui(a));
        m.setLineSpacing(U.dpf(a, 5f), 1.15f);
        c.addView(m);

        final AlertDialog d = new AlertDialog.Builder(a)
                .setView(c)
                .setPositiveButton(R.string.yes_remove, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(U.round(Skin.surface(a), 22f, a));
            d.getWindow().setGravity(Gravity.CENTER);
        }
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Skin.accent(a));
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Skin.sub(a));
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                yes.run();
            }
        });
    }

    /** Простой информационный диалог с прокруткой длинного текста. */
    public static void info(Activity a, String title, String message) {
        LinearLayout c = Ui.col(a);
        Ui.pad(c, a, 22, 20, 22, 6);
        TextView t = Ui.title(a, title, 19f);
        c.addView(t);
        c.addView(Ui.space(a, 12));
        TextView m = Ui.tv(a, message, 14.5f, Skin.ink(a), U.ui(a));
        m.setLineSpacing(U.dpf(a, 6f), 1.18f);
        ScrollView sc = new ScrollView(a);
        sc.addView(m);
        int maxH = (int) (U.screenH(a) * 0.55f);
        c.addView(sc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxH));

        AlertDialog d = new AlertDialog.Builder(a)
                .setView(c)
                .setPositiveButton(R.string.ok, null)
                .create();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(U.round(Skin.surface(a), 22f, a));
        }
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Skin.accent(a));
    }
}
