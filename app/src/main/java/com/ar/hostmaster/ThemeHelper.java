package com.ar.hostmaster;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    /** Sets global night mode. Call BEFORE setContentView(). */
    public static void apply(String theme) {
        switch (theme) {
            case AppState.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case AppState.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /**
     * Call AFTER setContentView() in every Activity.
     * Reads the ACTUAL current UI mode so "Follow System" works correctly.
     */
    public static void applyWithStatusBar(Activity activity, String theme) {
        apply(theme);

        // Check real current dark state — handles "follow system" correctly
        boolean isDark = isCurrentlyDark(activity, theme);

        Window window = activity.getWindow();
        int bgColor = isDark ? Color.parseColor("#0D1120") : Color.parseColor("#FFFFFF");
        window.setStatusBarColor(bgColor);
        window.setNavigationBarColor(bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController ic = window.getInsetsController();
            if (ic != null) {
                int lightFlags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                // dark mode → light icons (clear flags); light mode → dark icons (set flags)
                ic.setSystemBarsAppearance(isDark ? 0 : lightFlags, lightFlags);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View dv = window.getDecorView();
            int flags = dv.getSystemUiVisibility();
            if (isDark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else        flags |=  View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            dv.setSystemUiVisibility(flags);
        }
    }

    /**
     * True if UI is currently dark — respects "Follow System" by checking
     * the actual Configuration instead of the stored theme string.
     */
    public static boolean isCurrentlyDark(Activity activity, String theme) {
        if (AppState.THEME_DARK.equals(theme))  return true;
        if (AppState.THEME_LIGHT.equals(theme)) return false;
        // "system" — read what Android actually decided right now
        int uiMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
