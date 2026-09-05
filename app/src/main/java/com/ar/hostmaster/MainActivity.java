package com.ar.hostmaster;

import android.Manifest;
import android.app.Dialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PowerButtonView powerBtn;
    private ChartView chartView;
    private TextView tvUrl, tvPort, tvClients, tvUptime, tvChartVal;
    private TextView tvProtoLabel, tvProtoBottom, tvPortBottom, tvServerStatus;
    private ImageView protocolsIcon;  
    private ImageView ctrlProtoIcon; 
    private TextView tvQrTitle, tvQrDesc;
    private ImageView ivQr;
    private AppState state;

    private boolean serverRunning = false;
    private long startTime = 0;

    // Receives broadcast when notification STOP or RESTART is tapped
    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context ctx, Intent intent) {
            String action = intent.getAction();

            if (ServerService.ACTION_STOPPED.equals(action)) {
                // Server was stopped externally (notification) — sync UI
                if (serverRunning) {
                    serverRunning = false;
                    startTime = 0;
                    stopTicker();
                    updateUrlAndPortForIdle();
                    tvClients.setText("0");
                    tvUptime.setText("00:00");
                    tvChartVal.setText("0 KB/s");
                    ivQr.setImageBitmap(null);
                    powerBtn.setState(false);
                    updateServerStatus(false);
                }
            } else if (ServerService.ACTION_RESTARTED.equals(action)) {
                // Server was restarted externally (notification) — sync UI + timer + QR
                serverRunning = true;
                startTime = System.currentTimeMillis();
                state.sp_long_set("server_start_time", startTime);
                updateServerUrlAndPort();
                generateQr(getCurrentDisplayUrl());
                stopTicker();
                startTicker();
                tvClients.setText("0");
                powerBtn.setState(true);
                updateServerStatus(true);
            }
        }
    };
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQ_CONFIG      = 101;
    private static final int REQ_FTP_CONFIG  = 102;
    private static final int REQ_MANAGE_STOR = 202;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_main);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        bindViews();
        setupClicks();
        restoreOrAutostart();
    }

    private void bindViews() {
        powerBtn    = findViewById(R.id.power_btn);
        chartView   = findViewById(R.id.chart_view);
        tvUrl       = findViewById(R.id.tv_url);
        tvPort      = findViewById(R.id.tv_port);
        tvClients   = findViewById(R.id.tv_clients);
        tvUptime    = findViewById(R.id.tv_uptime);
        tvChartVal  = findViewById(R.id.tv_chart_val);
        ivQr        = findViewById(R.id.iv_qr);
        protocolsIcon = findViewById(R.id.protocols_icons);  
        ctrlProtoIcon = findViewById(R.id.ctrl_proto_icon);  
        tvProtoLabel = findViewById(R.id.tv_proto_label);
        tvProtoBottom = findViewById(R.id.tv_proto_bottom);
        tvPortBottom = findViewById(R.id.tv_port_bottom);
        tvServerStatus = findViewById(R.id.tv_server_status);
        tvQrTitle   = findViewById(R.id.tv_qr_title);
        tvQrDesc    = findViewById(R.id.tv_qr_desc);
    }

    private void setupClicks() {
        powerBtn.setOnClickListener(v -> toggleServer());

        // Copy button
        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            String url = tvUrl.getText().toString();
            if (!url.contains("—")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
            }
        });

        // URL click to open
        tvUrl.setOnClickListener(v -> openUrl());

        // Open button
        findViewById(R.id.btn_open_url).setOnClickListener(v -> openUrl());

        ivQr.setOnClickListener(v -> {
            if (ivQr.getDrawable() != null) showQrFullscreen();
        });

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        View.OnClickListener protoClick = v -> {
            if (serverRunning) {
                Toast.makeText(this, "Stop server to change protocol", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, ProtocolActivity.class));
        };
        
        findViewById(R.id.btn_globe).setOnClickListener(protoClick);
        findViewById(R.id.ctrl_proto).setOnClickListener(protoClick);

        findViewById(R.id.ctrl_config).setOnClickListener(v -> {
            if (serverRunning) {
                Toast.makeText(this, "Stop server to change settings", Toast.LENGTH_SHORT).show();
                return;
            }
            String proto = state.getProtocol();
            if ("FTP".equals(proto)) {
                startActivityForResult(
                        new Intent(this, FtpConfigureActivity.class), REQ_FTP_CONFIG);
            } else {
                // Default: HTTP
                startActivityForResult(
                        new Intent(this, ConfigureActivity.class), REQ_CONFIG);
            }
        });

        findViewById(R.id.ctrl_logs).setOnClickListener(v ->
                startActivity(new Intent(this, LogsActivity.class)));

        findViewById(R.id.ctrl_stats).setOnClickListener(v ->
                startActivity(new Intent(this, StatsActivity.class)));
    }

    private void openUrl() {
        String url = tvUrl.getText().toString();
        if (!url.contains("—")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                Toast.makeText(this, "Cannot open URL", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Server toggle ─────────────────────────────────────────────────────────

    private void toggleServer() {
        if (!serverRunning) startServer();
        else stopServer();
    }

    private void startServer() {
        if (state.getFolderPath().isEmpty()) {
            Toast.makeText(this, getString(R.string.no_source), Toast.LENGTH_SHORT).show();
            powerBtn.setState(false);
            return;
        }
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_START);
        ContextCompat.startForegroundService(this, i);
        serverRunning = true;
        startTime = System.currentTimeMillis();
        state.sp_long_set("server_start_time", startTime);
        state.setRunning(true);

        updateServerUrlAndPort(); 
        generateQr(getCurrentDisplayUrl());
        startTicker();
        powerBtn.setState(true);
        updateServerStatus(true);
    }

    private void stopServer() {
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_STOP);
        startService(i);
        serverRunning = false;
        startTime = 0;
        state.sp_long_set("server_start_time", 0);
        state.setRunning(false);
        state.setLocalUrl("");
        stopTicker();
        
        updateUrlAndPortForIdle(); 
        
        tvClients.setText("0");
        tvUptime.setText("00:00");
        tvChartVal.setText("0 KB/s");
        ivQr.setImageBitmap(null);
        powerBtn.setState(false);
        updateServerStatus(false);
    }

    // ── Server Status UI ─────────────────────────────────────────────────────

    private void updateServerStatus(boolean running) {
        if (running) {
            tvServerStatus.setText("RUNNING");
            tvServerStatus.setTextColor(getResources().getColor(R.color.col_green));
        } else {
            tvServerStatus.setText("STOPPED");
            tvServerStatus.setTextColor(getResources().getColor(R.color.col_red));
        }
    }

    // ── Restore / Autostart ───────────────────────────────────────────────────

    private void restoreOrAutostart() {
        String proto = state.getProtocol();
        String savedUrl = state.getLocalUrl();
        
        updateProtoUI(proto);
        updatePortDisplay(); 
        
        if (state.isRunning() && !savedUrl.isEmpty()) {
            serverRunning = true;
            tvUrl.setText(savedUrl);
            generateQr(savedUrl);
            long saved = state.sp_long("server_start_time", 0);
            startTime = (saved > 0) ? saved : System.currentTimeMillis();
            startTicker();
            powerBtn.setState(true);
            updateServerStatus(true);

        } else if (state.isAutostart() && !state.getFolderPath().isEmpty()) {
            handler.postDelayed(this::startServer, 600);

        } else {
            updateUrlAndPortForIdle();
            powerBtn.setState(false);
            updateServerStatus(false);
        }
    }

    private void updateServerUrlAndPort() {
        String proto  = state.getProtocol();
        String ip     = NetworkUtil.getLocalIp(this);
        int    port   = state.getPort(proto);
        String displayUrl = getDisplayUrl(proto, ip, port);
        String qrUrl = getQrUrl(proto, ip, port);
        
        state.setLocalUrl(displayUrl);
        tvUrl.setText(displayUrl);
        tvPort.setText(String.valueOf(port));
        tvPortBottom.setText(String.valueOf(port));
        tvProtoBottom.setText(proto);
        generateQr(qrUrl);
    }
    
    private void updateUrlAndPortForIdle() {
        String proto = state.getProtocol();
        int port = state.getPort(proto);
        String displayUrl = getIdleDisplayUrl(proto, port);
        
        tvUrl.setText(displayUrl);
        tvPort.setText(String.valueOf(port));
        tvPortBottom.setText(String.valueOf(port));
        tvProtoBottom.setText(proto);
    }
    
    private void updatePortDisplay() {
        String proto = state.getProtocol();
        int port = state.getPort(proto);
        tvPort.setText(String.valueOf(port));
        tvPortBottom.setText(String.valueOf(port));
        tvProtoBottom.setText(proto);
    }
    
    private String getSchemeForProtocol(String proto) {
        if ("FTP".equals(proto)) return "ftp";
        return "http";
    }
    
    private String getDisplayUrl(String proto, String ip, int port) {
        if ("FTP".equals(proto)) {
            return "ftp://" + ip + ":" + port;
        }
        return "http://" + ip + ":" + port;
    }
    
    private String getQrUrl(String proto, String ip, int port) {
        if ("FTP".equals(proto)) {
            return "ftp://" + ip + ":" + port;
        }
        return "http://" + ip + ":" + port;
    }
    
    private String getIdleDisplayUrl(String proto, int port) {
        if ("FTP".equals(proto)) {
            return "ftp://—.—.—.—:" + port;
        }
        return "http://—.—.—.—:" + port;
    }
    
    private String getCurrentDisplayUrl() {
        String proto = state.getProtocol();
        if (serverRunning) {
            String ip = NetworkUtil.getLocalIp(this);
            int port = state.getPort(proto);
            return getDisplayUrl(proto, ip, port);
        } else {
            int port = state.getPort(proto);
            return getIdleDisplayUrl(proto, port);
        }
    }

    // ── Protocol UI ───────────────────────────────────────────────────────────

    private void updateProtoUI(String proto) {
        String label;
        int iconRes;

        if ("FTP".equals(proto)) {
            label = "FTP";
            iconRes = R.drawable.ic_ftp;
            if (tvQrTitle != null) tvQrTitle.setText("FTP ACCESS");
            if (tvQrDesc != null) tvQrDesc.setText("Connect using any FTP client (e.g., Solid Explorer)");
        } else {
            label = "HTTP";
            iconRes = R.drawable.ic_http;
            if (tvQrTitle != null) tvQrTitle.setText("SCAN ME");
            if (tvQrDesc != null) tvQrDesc.setText("Scan QR to access from any device on your network");
        }

        if (protocolsIcon != null) {
            protocolsIcon.setImageResource(iconRes);
        }
        
        if (ctrlProtoIcon != null) {
            ctrlProtoIcon.setImageResource(iconRes);
        }
        
        if (tvProtoLabel != null) {
            tvProtoLabel.setText(label);
        }

        if (!serverRunning) {
            updateUrlAndPortForIdle();
        } else {
            updatePortDisplay();
        }
    }

    // ── QR ────────────────────────────────────────────────────────────────────

    private void generateQr(String url) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            int sz  = (int)(130 * getResources().getDisplayMetrics().density);
            Bitmap bmp = QrUtil.generate(url, sz);
            runOnUiThread(() -> ivQr.setImageBitmap(bmp));
        }).start();
    }

    private void showQrFullscreen() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor("#E0060A10"));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ivQr.getDrawable());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(40, 40, 40, 40);
        frame.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        frame.setOnClickListener(v -> dialog.dismiss());
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(frame);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.parseColor("#E0060A10")));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }

    // ── Ticker ────────────────────────────────────────────────────────────────

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!serverRunning) return;
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            tvUptime.setText(String.format("%02d:%02d", elapsed / 60, elapsed % 60));
            tvClients.setText(String.valueOf(LogManager.getActiveClients()));
            long speed = LogManager.getAndResetSpeed();
            tvChartVal.setText(NetworkUtil.formatSpeed(speed));
            chartView.addPoint(speed);
            handler.postDelayed(this, 1000);
        }
    };

    private void startTicker() { handler.post(ticker); }
    private void stopTicker()  { handler.removeCallbacks(ticker); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        String proto = state.getProtocol();
        updateProtoUI(proto);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ServerService.ACTION_STOPPED);
        filter.addAction(ServerService.ACTION_RESTARTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }

        boolean actuallyRunning = state.isRunning();
        if (serverRunning && !actuallyRunning) {
            serverRunning = false;
            startTime = 0;
            stopTicker();
            updateUrlAndPortForIdle();
            tvClients.setText("0");
            tvUptime.setText("00:00");
            tvChartVal.setText("0 KB/s");
            ivQr.setImageBitmap(null);
            powerBtn.setState(false);
            updateServerStatus(false);
        } else if (!serverRunning) {
            updateUrlAndPortForIdle();
            updateServerStatus(false);
        } else {
            updatePortDisplay();
            updateServerStatus(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stopReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() { stopTicker(); super.onDestroy(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_FTP_CONFIG && res == RESULT_OK && serverRunning) {
            stopServer(); startServer();
        }

        if (req == REQ_CONFIG && res == RESULT_OK && serverRunning) {
            stopServer();
            startServer();
        }

        if (req == REQ_MANAGE_STOR) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Toast.makeText(this,
                            "Storage permission needed to show all file types",
                            Toast.LENGTH_LONG).show();
                }
            }
            if (state.isAutostart() && !state.getFolderPath().isEmpty() && !serverRunning) {
                handler.postDelayed(this::startServer, 500);
            }
        }
    }
}