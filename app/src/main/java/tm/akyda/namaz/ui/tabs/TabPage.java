package tm.akyda.namaz.ui.tabs;

import android.view.View;

import tm.akyda.namaz.ui.MainActivity;

/** Вкладка главного экрана. */
public interface TabPage {
    View build(MainActivity host);

    void refresh();
}
