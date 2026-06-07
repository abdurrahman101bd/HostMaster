package com.ar.hostmaster;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LogManager {
    public static class LogEntry {
        public final String time, method, path, status, client, type;
        public LogEntry(String method, String path, String status, String client) {
            this.time   = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            this.method = method;
            this.path   = path;
            this.status = status;
            this.client = client;
            this.type   = status.startsWith("2") ? "success"
                        : (status.startsWith("4") || status.startsWith("5")) ? "error" : "info";
        }
    }

    private static final int MAX = 300;
    private static final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    // AtomicInteger for thread-safe client count
    private static final AtomicInteger activeClients = new AtomicInteger(0);
    private static volatile long totalBytes      = 0;
    private static volatile long bytesLastSample = 0;
    private static volatile long timeLastSample  = System.currentTimeMillis();

    public static void add(String method, String path, String status, String client) {
        synchronized (logs) {
            if (logs.size() >= MAX) logs.remove(0);
            logs.add(new LogEntry(method, path, status, client));
        }
    }

    public static List<LogEntry> getAll() {
        synchronized (logs) { return new ArrayList<>(logs); }
    }

    public static void clear() {
        synchronized (logs) { logs.clear(); }
    }

    public static int size() {
        synchronized (logs) { return logs.size(); }
    }

    // Client tracking — called from serve() try/finally
    public static void clientConnected()    { activeClients.incrementAndGet(); }
    public static void clientDisconnected() { activeClients.decrementAndGet(); }
    public static int  getActiveClients()   { return Math.max(0, activeClients.get()); }

    public static synchronized void addBytes(long b) { totalBytes += b; }

    public static synchronized long getAndResetSpeed() {
        long now   = System.currentTimeMillis();
        long dt    = now - timeLastSample;
        if (dt < 100) return 0;
        long delta = totalBytes - bytesLastSample;
        long speed = dt > 0 ? (delta * 1000L / dt) : 0;
        bytesLastSample = totalBytes;
        timeLastSample  = now;
        return Math.max(0, speed);
    }

    public static long getTotalBytes() { return totalBytes; }

    public static void reset() {
        synchronized (logs) { logs.clear(); }
        activeClients.set(0);
        totalBytes      = 0;
        bytesLastSample = 0;
        timeLastSample  = System.currentTimeMillis();
    }
}
