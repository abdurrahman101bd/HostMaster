package com.ar.hostmaster;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class PrivacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppState state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_privacy);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Clear logs
        findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            showConfirmDialog("Clear Logs",
                    "Delete all saved logs?",
                    () -> {
                        LogManager.clear();
                        Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show();
                    });
        });

        // Reset stats
        findViewById(R.id.btn_reset_stats).setOnClickListener(v ->
                showConfirmDialog("Reset Statistics",
                        "This will reset total requests and data transferred.",
                        () -> {
                            state.resetStats();
                            Toast.makeText(this, "Statistics reset", Toast.LENGTH_SHORT).show();
                        })
        );

        // Clear all settings
        findViewById(R.id.btn_clear_all).setOnClickListener(v ->
                showConfirmDialog("Clear All Data",
                        "This will erase all settings, credentials, and paths.\n\nServer will need to be reconfigured.",
                        () -> {
                            state.clearAll();
                            Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                            finishAffinity();
                        })
        );
    }

    private void showConfirmDialog(String title, String msg, Runnable onConfirm) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("CONFIRM", (d, w) -> onConfirm.run())
                .setNegativeButton("CANCEL", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}