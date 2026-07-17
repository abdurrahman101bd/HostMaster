package com.ar.hostmaster;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class PermissionGateActivity extends AppCompatActivity {

    private static final int REQ_NOTIF    = 101;
    private static final int REQ_STORAGE  = 102;
    private static final int REQ_BATTERY  = 103;

    private TextView btnNotif, btnStorage, btnBattery, btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String theme = AppState.get(this).getTheme();
        ThemeHelper.apply(theme);

        // Returning user who already granted everything — skip straight to Main
        if (allGranted()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_permission_gate);
        ThemeHelper.applyWithStatusBar(this, theme);

        btnNotif   = findViewById(R.id.btn_perm_notification);
        btnStorage = findViewById(R.id.btn_perm_storage);
        btnBattery = findViewById(R.id.btn_perm_battery);
        btnStart   = findViewById(R.id.btn_gate_start);

        btnNotif.setOnClickListener(v -> requestNotifPermission());
        btnStorage.setOnClickListener(v -> requestStoragePermission());
        btnBattery.setOnClickListener(v -> requestBatteryExemption());
        btnStart.setOnClickListener(v -> goToMain());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btnStart != null) refreshRows(); // layout already inflated
    }

    // ── Individual permission checks ───────────────────────────────────────

    private boolean isNotifGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isStorageGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        }
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBatteryGranted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean allGranted() {
        return isNotifGranted() && isStorageGranted() && isBatteryGranted();
    }

    // ── Request actions ─────────────────────────────────────────────────────

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_STORAGE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void requestBatteryExemption() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(i, REQ_BATTERY);
    }

    // ── UI refresh ───────────────────────────────────────────────────────────

    private void refreshRows() {
        setRowState(btnNotif,   isNotifGranted());
        setRowState(btnStorage, isStorageGranted());
        setRowState(btnBattery, isBatteryGranted());
        btnStart.setEnabled(allGranted());
        btnStart.setAlpha(allGranted() ? 1f : 0.4f);
    }

    private void setRowState(TextView btn, boolean granted) {
        btn.setText(granted ? "Granted" : "Allow");
        btn.setEnabled(!granted);
        btn.setAlpha(granted ? 0.6f : 1f);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshRows();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refreshRows();
    }
}