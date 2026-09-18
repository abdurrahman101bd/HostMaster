package com.ar.hostmaster;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LogManager {
    public static class LogEntry {
        public final String time, method, path, status, client, type;
        public final long timestamp;
        
        public LogEntry(String method, String path, String status, String client) {
            this.timestamp = System.currentTimeMillis();
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
            this.method = method;
            this.path = path;
            this.status = status;
            this.client = client;
            this.type = status.startsWith("2") ? "success"
                        : (status.startsWith("4") || status.startsWith("5")) ? "error" : "info";
        }
        
        // For loading from file
        public LogEntry(long timestamp, String method, String path, String status, String client) {
            this.timestamp = timestamp;
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
            this.method = method;
            this.path = path;
            this.status = status;
            this.client = client;
            this.type = status.startsWith("2") ? "success"
                        : (status.startsWith("4") || status.startsWith("5")) ? "error" : "info";
        }
    }

    private static final int MAX = 300;
    private static final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // AtomicInteger for thread-safe client count
    private static final AtomicInteger activeClients = new AtomicInteger(0);
    private static volatile long totalBytes      = 0;
    private static volatile long bytesLastSample = 0;
    private static volatile long timeLastSample  = System.currentTimeMillis();
    
    private static Context appContext;
    private static boolean isSavingEnabled = true;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        loadLogsFromFile();
    }

    public static void add(String method, String path, String status, String client) {
        synchronized (logs) {
            if (logs.size() >= MAX) logs.remove(0);
            LogEntry entry = new LogEntry(method, path, status, client);
            logs.add(entry);
            
            // Save to file
            if (isSavingEnabled) {
                saveLogToFile(entry);
            }
        }
    }

    public static List<LogEntry> getAll() {
        synchronized (logs) { return new ArrayList<>(logs); }
    }

    public static void clear() {
        synchronized (logs) { logs.clear(); }
        // Delete every day's log file, not just today's — the Privacy screen
        // promises "Delete all saved logs?" so it must actually clear history.
        if (appContext != null) {
            File[] files = appContext.getFilesDir().listFiles((dir, name) ->
                name.startsWith("logs_") && name.endsWith(".txt"));
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
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
        // NOTE: deliberately NOT clearing `logs` here — this is called on every
        // server stop/start/restart, and clearing would wipe today's visible
        // log history from the UI even though the on-disk file still has it.
        // Only the per-session counters should reset.
        activeClients.set(0);
        totalBytes      = 0;
        bytesLastSample = 0;
        timeLastSample  = System.currentTimeMillis();
    }
    
    // ─── Save/Load Methods ────────────────────────────────────────────────────
    
    private static void saveLogToFile(LogEntry entry) {
        if (appContext == null) return;
        try {
            String today = DATE_FORMAT.format(new Date(entry.timestamp));
            File file = new File(appContext.getFilesDir(), "logs_" + today + ".txt");
            
            try (FileWriter fw = new FileWriter(file, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(entry.timestamp + "|" + entry.method + "|" + entry.path + "|" + 
                         entry.status + "|" + entry.client);
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e("LogManager", "Failed to save log: " + e.getMessage());
        }
    }
    
    private static void loadLogsFromFile() {
        if (appContext == null) return;
        
        // Load today's logs only (for memory)
        String today = DATE_FORMAT.format(new Date());
        File file = new File(appContext.getFilesDir(), "logs_" + today + ".txt");
        if (!file.exists()) return;
        
        synchronized (logs) {
            logs.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\\|", 5);
                    if (parts.length == 5) {
                        long timestamp = Long.parseLong(parts[0]);
                        LogEntry entry = new LogEntry(timestamp, parts[1], parts[2], parts[3], parts[4]);
                        if (logs.size() < MAX) {
                            logs.add(entry);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("LogManager", "Failed to load logs: " + e.getMessage());
            }
        }
    }
    
    // ─── Get logs by date ────────────────────────────────────────────────────
    
    public static List<LogEntry> getLogsForDate(String date) {
        List<LogEntry> result = new ArrayList<>();
        if (appContext == null) return result;
        
        File file = new File(appContext.getFilesDir(), "logs_" + date + ".txt");
        if (!file.exists()) return result;
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 5);
                if (parts.length == 5) {
                    long timestamp = Long.parseLong(parts[0]);
                    LogEntry entry = new LogEntry(timestamp, parts[1], parts[2], parts[3], parts[4]);
                    result.add(entry);
                }
            }
        } catch (IOException e) {
            Log.e("LogManager", "Failed to load logs for date " + date + ": " + e.getMessage());
        }
        return result;
    }
    
    public static List<String> getAvailableLogDates() {
        List<String> dates = new ArrayList<>();
        if (appContext == null) return dates;
        
        File[] files = appContext.getFilesDir().listFiles((dir, name) -> 
            name.startsWith("logs_") && name.endsWith(".txt"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String date = name.substring(5, name.length() - 4); // remove "logs_" and ".txt"
                dates.add(date);
            }
            Collections.sort(dates, Collections.reverseOrder()); // newest first
        }
        return dates;
    }
    
    public static void setSavingEnabled(boolean enabled) {
        isSavingEnabled = enabled;
    }
}