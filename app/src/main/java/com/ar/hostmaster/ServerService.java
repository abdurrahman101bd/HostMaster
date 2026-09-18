package com.ar.hostmaster;

import android.app.*;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.util.*;

public class ServerService extends Service {
    public static final String ACTION_STOPPED    = "com.ar.hostmaster.SERVER_STOPPED";
    public static final String ACTION_RESTARTED  = "com.ar.hostmaster.SERVER_RESTARTED";
    public static final String ACTION_START      = "START";
    public static final String ACTION_STOP       = "STOP";
    public static final String ACTION_RESTART    = "RESTART";
    private static final String CH_ID            = "hm_ch_v3"; // bumped again — vibration setting changed below, and channel properties don't update retroactively on-device
    private static final int    NID              = 1;
    private static final int    STOP_REQ         = 99;
    private static final int    RESTART_REQ      = 98;

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
            // ── NEW: Server Started Vibration ──
            vibrateDevice(new long[]{0, 200});
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopAllServers();
            stopForeground(true);
            broadcastStopped();
            stopSelf();
            // ── NEW: Server Stopped Vibration ──
            vibrateDevice(new long[]{0, 300, 150, 300});
        } else if (ACTION_RESTART.equals(intent.getAction())) {
            stopAllServers();
            startServer();
            AppState.get(this).sp_long_set("server_start_time", System.currentTimeMillis());
            startForeground(NID, buildNotif());
            broadcastRestarted();
            // ── NEW: Server Restarted Vibration ──
            vibrateDevice(new long[]{0, 100, 100, 100, 100, 100});
        }
        return START_STICKY;
    }

    private void startServer() {
        stopAllServers();
        AppState st = AppState.get(this);
        String proto = st.getProtocol();
        
        if ("FTP".equals(proto)) {
            startFtpServer(st);
        } else {
            startHttpServer(st);
        }
    }

    private void startHttpServer(AppState st) {
        String path = st.getFolderPath();
        if (path.isEmpty()) {
            notifyError("HTTP: No source selected. Go to Configure.");
            stopForeground(true); stopSelf();
            return;
        }
        int port = st.getPort("HTTP");
        try {
            String mode = st.getSourceMode();
            if ("web".equals(mode)) {
                File webDir = new File(path);
                if (!webDir.exists() || !webDir.isDirectory()) {
                    notifyError("HTTP: Web folder does not exist.");
                    stopForeground(true); stopSelf();
                    return;
                }
                server = new FileServer(port, webDir, st, true);
            } else if ("files".equals(mode)) {
                List<String> filePaths = st.getSelectedFiles();
                if (filePaths.isEmpty()) {
                    notifyError("HTTP: No files selected.");
                    stopForeground(true); stopSelf();
                    return;
                }
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
                if (!dir.exists() || !dir.isDirectory()) {
                    notifyError("HTTP: Selected folder does not exist.");
                    stopForeground(true); stopSelf();
                    return;
                }
                server = new FileServer(port, dir, st);
            }
            server.start();
            LogManager.reset();
            st.setRunning(true);
            Log.i("ServerService", "HTTP server started on port " + port);
        } catch (IOException e) {
            st.setRunning(false);
            notifyError("HTTP server failed: " + e.getMessage());
            stopForeground(true); stopSelf();
        }
    }

    private void startFtpServer(AppState st) {
        String path = st.getFolderPath(); 
    
        if (path.isEmpty()) {
            notifyError("FTP: No root folder selected. Go to Configure.");
            stopForeground(true); stopSelf(); 
            return;
        }
		File dir = new File(path);
		if (!dir.exists() || !dir.isDirectory()) {
			notifyError("FTP: Selected folder does not exist.");
			stopForeground(true); stopSelf(); 
			return;
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
			Log.i("ServerService", "FTP server started on port " + port + " with root: " + path);
		} catch (IOException e) {
			st.setRunning(false);
			notifyError("FTP server failed: " + e.getMessage());
			stopForeground(true); stopSelf();
		}
	}

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

    private void broadcastRestarted() {
        sendBroadcast(new Intent(ACTION_RESTARTED));
    }

    public boolean isRunning() { return server != null || ftpServer != null; }

    @Override public void onDestroy() { stopAllServers(); super.onDestroy(); }

    // ── Vibration Helper ──────────────────────────────────────────────────────

    private void vibrateDevice(long[] pattern) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Notification Channel ──────────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Host Master Server", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("HTTP/FTP file server status");
            ch.setShowBadge(true);
            
            // Vibration is handled manually per-action (see vibrateDevice() calls in
            // onStartCommand) so each action gets its own distinct pattern — leaving
            // the channel's own vibration ALSO enabled would double-vibrate on
            // every START/RESTART, since posting/reposting this notification
            // re-triggers the channel's vibration too.
            ch.enableVibration(false);
            
            ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build());
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

        Intent restartI = new Intent(this, ServerService.class);
        restartI.setAction(ACTION_RESTART);
        PendingIntent restartPi = PendingIntent.getService(
                this, RESTART_REQ, restartI, PendingIntent.FLAG_IMMUTABLE);

        AppState st  = AppState.get(this);
        String   url = st.getLocalUrl();
        String proto = st.getProtocol();
        String  text = url.isEmpty() ? proto + " server is running…" : "Running · " + url;

        return new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle("Host Master - " + proto)
                .setContentText(text)
                .setSmallIcon(R.drawable.hostmaster_notification_icon)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_rotate, "RESTART", restartPi)
                .addAction(android.R.drawable.ic_media_pause, "STOP", stopPi)
                .build();
    }
}