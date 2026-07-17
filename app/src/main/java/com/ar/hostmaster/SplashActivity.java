package com.ar.hostmaster;

import android.content.Intent;
import android.os.*;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String theme = AppState.get(this).getTheme();
        // Apply night mode before setContentView
        ThemeHelper.apply(theme);
        setContentView(R.layout.activity_splash);
        // Apply status bar color after setContentView
        ThemeHelper.applyWithStatusBar(this, theme);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, PermissionGateActivity.class));
            finish();
        }, 1800);
    }
}
