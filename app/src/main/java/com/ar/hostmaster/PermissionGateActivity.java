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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionGateActivity extends AppCompatActivity {

    private static final int REQ_NOTIF   = 101;
    private static final int REQ_STORAGE = 102;

    private TextView btnNotif, btnStorage, btnBattery, btnStart;
    private TextView tvNotifHint, tvStorageHint, tvBatteryHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String theme = AppState.get(this).getTheme();
        ThemeHelper.apply(theme);

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
        
        tvNotifHint   = findViewById(R.id.tv_notif_hint);
        tvStorageHint = findViewById(R.id.tv_storage_hint);
        tvBatteryHint = findViewById(R.id.tv_battery_hint);

        btnNotif.setOnClickListener(v -> requestNotifPermission());
        btnStorage.setOnClickListener(v -> requestStoragePermission());
        btnBattery.setOnClickListener(v -> requestBatteryPermission());
        btnStart.setOnClickListener(v -> goToMain());
        
        updateHints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btnStart != null) refreshRows();
    }

    // ── Individual permission checks ───────────────────────────────────────

    private boolean isNotifGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean isStorageGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBatteryGranted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return false;
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean allGranted() {
        return isNotifGranted() && isStorageGranted() && isBatteryGranted();
    }

    // ── Request actions ─────────────────────────────────────────────────────

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            openAppNotificationSettings();
        }
    }

    private void openAppNotificationSettings() {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", getPackageName());
                intent.putExtra("app_uid", getApplicationInfo().uid);
            }
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(i);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void requestBatteryPermission() {
        boolean opened = false;
        String packageName = getPackageName();

        try {
            Intent intent = new Intent();
            intent.setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgauge.PowerConsumptionFeatureActivity");
            startActivity(intent);
            opened = true;
        } catch (Exception e1) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.powermanager", "com.coloros.powermanager.page.AppDetailFeatureActivity");
                intent.putExtra("pkg_name", packageName);
                startActivity(intent);
                opened = true;
            } catch (Exception e2) {
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
                    startActivity(intent);
                    opened = true;
                } catch (Exception ignored) {}
            }
        }

        if (!opened) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            } catch (Exception e) {
                showBatteryHelpDialog();
            }
        }
    }

    private void showBatteryHelpDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("Please disable battery optimization for HostMaster to run smoothly in background.\n\n" +
                       "On OEM devices:\n" +
                       "• Xiaomi: Settings → Battery → App battery saver → HostMaster → No restrictions\n" +
                       "• Realme/Oppo: Settings → Battery → App battery management → Allow background activity\n" +
                       "• Samsung: Settings → Battery → Background usage limits")
            .setPositiveButton("Open Settings", (d, w) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            })
            .setNegativeButton("OK", null)
            .show();
    }

    // ── UI refresh ───────────────────────────────────────────────────────────

    private void refreshRows() {
        boolean notifGranted = isNotifGranted();
        boolean storageGranted = isStorageGranted();
        boolean batteryGranted = isBatteryGranted();

        setRowState(btnNotif, notifGranted);
        setRowState(btnStorage, storageGranted);
        setRowState(btnBattery, batteryGranted);
        
        updateHints();

        boolean all = notifGranted && storageGranted && batteryGranted;
        btnStart.setEnabled(all);
        btnStart.setAlpha(all ? 1f : 0.4f);
        
        TextView tvStartHint = findViewById(R.id.tv_start_hint);
        if (tvStartHint != null) {
            tvStartHint.setText(all ? "All permissions granted! Tap to continue" : "Grant all permissions to continue");
        }
    }

    private void setRowState(TextView btn, boolean granted) {
        if (btn == null) return;
        btn.setText(granted ? "✓ Granted" : "Allow");
        btn.setEnabled(!granted);
        btn.setAlpha(granted ? 0.6f : 1f);
        btn.setTextColor(granted ? 
            getResources().getColor(R.color.col_green) : 
            getResources().getColor(R.color.accent));
    }
    
    private void updateHints() {
        if (tvNotifHint != null) {
            if (isNotifGranted()) {
                tvNotifHint.setText("✓ Notification permission granted");
                tvNotifHint.setTextColor(getResources().getColor(R.color.col_green));
            } else {
                tvNotifHint.setText("Tap 'Allow' to enable notifications");
                tvNotifHint.setTextColor(getResources().getColor(R.color.text_muted_hint));
            }
        }
        
        if (tvStorageHint != null) {
            if (isStorageGranted()) {
                tvStorageHint.setText("✓ Storage access granted");
                tvStorageHint.setTextColor(getResources().getColor(R.color.col_green));
            } else {
                tvStorageHint.setText("Tap 'Allow' to grant storage permission");
                tvStorageHint.setTextColor(getResources().getColor(R.color.text_muted_hint));
            }
        }
        
        if (tvBatteryHint != null) {
            if (isBatteryGranted()) {
                tvBatteryHint.setText("✓ Battery optimization disabled");
                tvBatteryHint.setTextColor(getResources().getColor(R.color.col_green));
            } else {
                tvBatteryHint.setText("Tap 'Allow' to disable battery restrictions");
                tvBatteryHint.setTextColor(getResources().getColor(R.color.text_muted_hint));
            }
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshRows();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refreshRows();
    }
}