package tm.akyda.namaz.ui;

import android.os.Bundle;

import tm.akyda.namaz.R;

/** Знакомство при первом запуске (в разработке). */
public class OnboardingActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
