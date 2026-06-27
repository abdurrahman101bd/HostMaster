package com.ar.hostmaster;

import android.app.*;
import android.widget.Toast;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.util.*;

public class ServerService extends Service {
    public static final String ACTION_STOPPED = "com.ar.hostmaster.SERVER_STOPPED";
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP  = "STOP";
    private static final String CH_ID       = "hm_ch";
    private static final int    NID         = 1;
    private static final int    STOP_REQ    = 99;

    private FileServer server;
    private FtpServer   ftpServer;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public ServerService get() { return ServerService.this; }
    }

    @Override public IBinder onBind(Intent i) { return binder; }
    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_START.equals(intent.getAction())) {
            startForeground(NID, buildNotif());
            startServer();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopAllServers();
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startServer() {
        stopAllServers();
        AppState st = AppState.get(this);
        switch (st.getProtocol()) {
            case "FTP":  startFtpServer(st);       break;
            case "SFTP": startSftpPlaceholder(st);   break;
            case "SSH":  startSshPlaceholder(st);       break;
            default:    startHttpServer(st); break;
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private void startHttpServer(AppState st) {
        String path = st.getFolderPath();
        if (path.isEmpty()) return;
        int port = st.getPort("HTTP");
        try {
            String mode = st.getSourceMode();
            if ("web".equals(mode)) {
                File webDir = new File(path);
                if (!webDir.exists() || !webDir.isDirectory()) return;
                server = new FileServer(port, webDir, st, true);
            } else if ("files".equals(mode)) {
                List<String> filePaths = st.getSelectedFiles();
                Map<String, File> vMap = new LinkedHashMap<>();
                for (String p : filePaths) {
                    File f = new File(p);
                    if (!f.exists() || !f.isFile()) continue;
                    String key = f.getName();
                    if (vMap.containsKey(key))
                        key = f.getParentFile().getName() + "_" + key;
                    int n = 2; String base = key;
                    while (vMap.containsKey(key)) key = base + "_" + (n++);
                    vMap.put(key, f);
                }
                File anyParent = filePaths.isEmpty()
                        ? Environment.getExternalStorageDirectory()
                        : new File(filePaths.get(0)).getParentFile();
                if (anyParent == null) anyParent = Environment.getExternalStorageDirectory();
                server = new FileServer(port, anyParent, st, vMap);
            } else {
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) return;
                server = new FileServer(port, dir, st);
            }
            server.start();
            LogManager.reset();
            st.setRunning(true);
        } catch (IOException e) {
            st.setRunning(false);
            notifyError("HTTP server failed: " + e.getMessage());
        }
    }

    // ── FTP ───────────────────────────────────────────────────────────────────

    private void startFtpServer(AppState st) {
        String path = st.getFolderPath();
        if (path.isEmpty()) {
            notifyError("FTP: No root folder selected. Go to Configure.");
            stopForeground(true); stopSelf(); return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            notifyError("FTP: Selected folder does not exist.");
            stopForeground(true); stopSelf(); return;
        }
        int port = st.getPort("FTP");
        try {
            ftpServer = new FtpServer(port, dir, st, getApplicationContext());
            ftpServer.setIdleTimeoutSeconds(st.sp_int("timeout_sec", 300));
            ftpServer.setOnIdleTimeout(() -> {
                stopAllServers();
                broadcastStopped();
                new Handler(Looper.getMainLooper()).post(() -> {
                    stopForeground(true);
                    stopSelf();
                });
            });
            ftpServer.start();
            LogManager.reset();
            st.setRunning(true);
        } catch (IOException e) {
            st.setRunning(false);
            notifyError("FTP server failed: " + e.getMessage());
            stopForeground(true); stopSelf();
        }
    }

    // ── SSH (placeholder) ─────────────────────────────────────────────────────

    private void startSftpPlaceholder(AppState st) {
        st.setRunning(false);
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(),
                    "SFTP — Coming Soon", Toast.LENGTH_LONG).show());
        stopForeground(true);
        stopSelf();
    }

    private void startSshPlaceholder(AppState st) {
        st.setRunning(false);
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(),
                    "SSH/SFTP is not yet implemented.",
                    Toast.LENGTH_LONG).show());
        stopForeground(true);
        stopSelf();
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    private void stopAllServers() {
        if (server    != null) { server.stop();    server    = null; }
        if (ftpServer != null) { ftpServer.stop(); ftpServer = null; }
        AppState.get(this).setRunning(false);
        LogManager.reset();
    }

    private void notifyError(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }
    
    private void broadcastStopped() {
        sendBroadcast(new Intent(ACTION_STOPPED));
    }

    public boolean isRunning() { return server != null || ftpServer != null; }

    @Override public void onDestroy() { stopAllServers(); super.onDestroy(); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Host Master Server", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("HTTP file server status");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        Intent openI = new Intent(this, MainActivity.class);
        openI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openI, PendingIntent.FLAG_IMMUTABLE);

        Intent stopI = new Intent(this, ServerService.class);
        stopI.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, STOP_REQ, stopI, PendingIntent.FLAG_IMMUTABLE);

        AppState st  = AppState.get(this);
        String   url = st.getLocalUrl();
        String  text = url.isEmpty() ? "Server is running…" : "Running · " + url;

        return new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle("Host Master")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "STOP", stopPi)
                .build();
    }
}
