package com.ar.hostmaster;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.util.*;

public class ServerService extends Service {
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP  = "STOP";
    private static final String CH_ID       = "hm_ch";
    private static final int    NID         = 1;
    private static final int    STOP_REQ    = 99;

    private FileServer server;
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
            startFileServer();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopFileServer();
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startFileServer() {
        stopFileServer();
        AppState st   = AppState.get(this);
        String   path = st.getFolderPath();
        if (path.isEmpty()) return;

        int port = st.getPort(st.getProtocol());

        try {
            String mode = st.getSourceMode();

            if ("web".equals(mode)) {
                // ── MODE: Web hosting ─────────────────────────────────────────
                // Serves folder; index.html auto-runs; CSS/JS all accessible
                File webDir = new File(path);
                if (!webDir.exists() || !webDir.isDirectory()) return;
                server = new FileServer(port, webDir, st, true); // webMode=true

            } else if ("files".equals(mode)) {
                // ── MODE: Selected files ──────────────────────────────────────
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
                // ── MODE: Folder ──────────────────────────────────────────────
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) return;
                server = new FileServer(port, dir, st);
            }

            server.start();
            LogManager.reset();
            st.setRunning(true);

        } catch (IOException e) {
            st.setRunning(false);
        }
    }

    private void stopFileServer() {
        if (server != null) { server.stop(); server = null; }
        AppState.get(this).setRunning(false);
        LogManager.reset();
    }

    public boolean isRunning() { return server != null; }

    @Override public void onDestroy() { stopFileServer(); super.onDestroy(); }

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
