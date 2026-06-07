package com.ar.hostmaster;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

public class StatsActivity extends AppCompatActivity {
    
    private TextView tvTotalReq, tvTotalBytes, tvProtocol, tvPort, tvFolder;
    private AppState appState;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        
        appState = AppState.get(this);
        
        // Initialize views
        tvTotalReq = findViewById(R.id.tv_total_req);
        tvTotalBytes = findViewById(R.id.tv_total_bytes);
        tvProtocol = findViewById(R.id.tv_protocol);
        tvPort = findViewById(R.id.tv_port);
        tvFolder = findViewById(R.id.tv_folder);
        
        // Set data
        updateStats();
        
        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        // Reset button with confirmation dialog
        findViewById(R.id.btn_reset).setOnClickListener(v -> showResetConfirmationDialog());
    }
    
    private void updateStats() {
        tvTotalReq.setText(String.valueOf(appState.getTotalRequests()));
        tvTotalBytes.setText(NetworkUtil.formatBytes(appState.getTotalBytes()));
        tvProtocol.setText(appState.getProtocol());
        tvPort.setText(String.valueOf(appState.getPort(appState.getProtocol())));
        
        String folderPath = appState.getFolderPath();
        tvFolder.setText(folderPath.isEmpty() ? "—" : folderPath);
    }
    
    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Statistics")
            .setMessage("Are you sure you want to reset all request and data counters?\n\nThis action cannot be undone.")
            .setPositiveButton("Reset", (dialog, which) -> performReset())
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void performReset() {
        // Reset stats in AppState
        appState.resetStats();
        
        // Update UI
        updateStats();
        
        // Show success message
        Toast.makeText(this, "Statistics reset successfully", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh stats when returning to activity
        updateStats();
    }
}