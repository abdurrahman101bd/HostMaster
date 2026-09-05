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
  
    public static void applyWithStatusBar(Activity activity, String theme) {
        apply(theme);

        boolean isDark = isCurrentlyDark(activity, theme);

        Window window = activity.getWindow();
        int bgColor = isDark ? Color.parseColor("#0D1420") : Color.parseColor("#FFFFFF");
        window.setStatusBarColor(bgColor);
        window.setNavigationBarColor(bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController ic = window.getInsetsController();
            if (ic != null) {
                int lightFlags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
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

    public static boolean isCurrentlyDark(Activity activity, String theme) {
        if (AppState.THEME_DARK.equals(theme))  return true;
        if (AppState.THEME_LIGHT.equals(theme)) return false;
        int uiMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
