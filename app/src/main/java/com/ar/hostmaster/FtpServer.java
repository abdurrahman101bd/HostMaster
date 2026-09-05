package com.ar.hostmaster;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class FtpServer {
    private static final String TAG = "FtpServer";

    private final int      port;
    private final File     rootDir;
    private final AppState state;
    private final Context  context;

    private ServerSocket      serverSocket;
    private Thread            acceptThread;
    private volatile boolean  running = false;
    private int idleTimeoutMs = 300_000;

    public interface OnIdleTimeout { void onTimeout(); }
    private OnIdleTimeout timeoutCallback = null;
    private volatile long  lastActivityMs  = 0;
    private Thread         idleWatchdog    = null;

    private static final int PASV_PORT_START = 50000;
    private static final int PASV_PORT_END   = 50100;
    private static final int DATA_TIMEOUT_MS = 15000;

    public FtpServer(int port, File rootDir, AppState state, Context ctx) {
        this.port    = port;
        this.rootDir = rootDir;
        this.state   = state;
        this.context = ctx;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "FTP-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        lastActivityMs = System.currentTimeMillis();
        if (idleTimeoutMs != Integer.MAX_VALUE) {
            idleWatchdog = new Thread(() -> {
                while (running) {
                    try { Thread.sleep(30_000); } catch (InterruptedException e) { break; }
                    long idle = System.currentTimeMillis() - lastActivityMs;
                    if (idle >= idleTimeoutMs && LogManager.getActiveClients() == 0) {
                        Log.i(TAG, "FTP idle timeout — stopping server");
                        if (timeoutCallback != null) timeoutCallback.onTimeout();
                        break;
                    }
                }
            }, "FTP-IdleWatchdog");
            idleWatchdog.setDaemon(true);
            idleWatchdog.start();
        }
        Log.i(TAG, "FTP server started on port " + port);
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); }
        catch (Exception ignored) {}
        if (idleWatchdog != null) { idleWatchdog.interrupt(); idleWatchdog = null; }
        Log.i(TAG, "FTP server stopped");
    }

    public boolean isRunning() { return running; }

    public void setIdleTimeoutSeconds(int seconds) {
        this.idleTimeoutMs = (seconds <= 0) ? Integer.MAX_VALUE : seconds * 1000;
    }

    public void setOnIdleTimeout(OnIdleTimeout cb) {
        this.timeoutCallback = cb;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(idleTimeoutMs);
                client.setKeepAlive(true);
                Thread t = new Thread(new ClientHandler(client), "FTP-Client");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (running) Log.e(TAG, "Accept error: " + e.getMessage());
            }
        }
    }

    // ── Client handler ────────────────────────────────────────────────────────

    private class ClientHandler implements Runnable {
        private final Socket ctrl;

        ClientHandler(Socket s) { this.ctrl = s; }

        @Override
        public void run() {
            String ip = ctrl.getInetAddress().getHostAddress();

            if (state.sp_bool("notifications_enabled", true)) {
                NotificationHelper.showNotification(context, "FTP Client Connected", 
                    "Client connected from " + ip);
            }

            LogManager.clientConnected();
            lastActivityMs = System.currentTimeMillis();
            Session session = new Session();

            try {
                OutputStream rawOut = ctrl.getOutputStream();
                InputStream  rawIn  = ctrl.getInputStream();

                PrintWriter out = new PrintWriter(new BufferedOutputStream(rawOut), false);
                BufferedReader in = new BufferedReader(new InputStreamReader(rawIn));

                writeLine(out, "220 Host Master FTP Server Ready");

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    int sp   = line.indexOf(' ');
                    String cmd = sp >= 0 ? line.substring(0, sp).toUpperCase(Locale.US)
                                        : line.toUpperCase(Locale.US);
                    String arg = sp >= 0 ? line.substring(sp + 1).trim() : "";

                    boolean quit = dispatch(cmd, arg, out, session, ip);
                    if (quit) break;
                }
            } catch (Exception e) {
                Log.d(TAG, "Client " + ip + " ended: " + e.getMessage());
            } finally {
                if (state.sp_bool("notifications_enabled", true)) {
                    NotificationHelper.showNotification(context, "FTP Client Disconnected", 
                        "Client disconnected from " + ip);
                }

                closePasv(session);
                LogManager.clientDisconnected();
                try { ctrl.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    private boolean dispatch(String cmd, String arg,
                             PrintWriter out, Session s, String ip) {

        switch (cmd) {
            case "USER":
                s.username = arg;
                writeLineLogged(out, "331 Password required for " + arg, cmd, arg, ip);
                return false;

            case "PASS":
                if (authenticate(s.username, arg)) {
                    s.loggedIn    = true;
                    s.currentDir  = rootDir;
                    writeLineLogged(out, "230 User " + s.username + " logged in", cmd, arg, ip);
                } else {
                    writeLineLogged(out, "530 Login incorrect", cmd, arg, ip);
                }
                return false;

            case "SYST": writeLineLogged(out, "215 UNIX Type: L8", cmd, arg, ip); return false;

            case "FEAT":
                writeLineLogged(out, "211-Features:", cmd, arg, ip);
                writeLineLogged(out, " PASV", cmd, arg, ip);
                writeLineLogged(out, " EPSV", cmd, arg, ip);
                writeLineLogged(out, " SIZE", cmd, arg, ip);
                writeLineLogged(out, " MDTM", cmd, arg, ip);
                writeLineLogged(out, "211 End", cmd, arg, ip);
                return false;

            case "OPTS": writeLineLogged(out, "200 OK", cmd, arg, ip); return false;
            case "NOOP": writeLineLogged(out, "200 OK", cmd, arg, ip); return false;
            case "TYPE": writeLineLogged(out, "200 Type set", cmd, arg, ip); return false;

            case "PWD": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                writeLineLogged(out, "257 \"" + vpath(s.currentDir) + "\" is current directory", cmd, arg, ip);
                return false;
            }

            case "CWD": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File t = resolve(s.currentDir, arg);
                if (t != null && t.isDirectory() && safe(t)) {
                    s.currentDir = t;
                    writeLineLogged(out, "250 Directory changed to " + vpath(t), cmd, arg, ip);
                } else {
                    writeLineLogged(out, "550 No such directory: " + arg, cmd, arg, ip);
                }
                return false;
            }

            case "CDUP": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File p = s.currentDir.getParentFile();
                if (p != null && safe(p)) {
                    s.currentDir = p;
                    writeLineLogged(out, "250 OK", cmd, arg, ip);
                } else {
                    writeLineLogged(out, "550 Already at root", cmd, arg, ip);
                }
                return false;
            }

            case "PASV": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                closePasv(s);
                try {
                    int dataPort = findFreePort();
                    if (dataPort < 0) {
                        writeLineLogged(out, "425 No free port available", cmd, arg, ip);
                        return false;
                    }
                    ServerSocket pasvSock = new ServerSocket();
                    pasvSock.setReuseAddress(true);
                    pasvSock.bind(new InetSocketAddress(dataPort));
                    pasvSock.setSoTimeout(DATA_TIMEOUT_MS);
                    s.pasvServerSocket = pasvSock;

                    s.pendingData = new FutureTask<>(() -> {
                        try { return pasvSock.accept(); }
                        catch (Exception e) { return null; }
                    });
                    Thread t = new Thread(s.pendingData, "FTP-PASV-Accept");
                    t.setDaemon(true);
                    t.start();

                    String ip2  = NetworkUtil.getLocalIp(context).replace('.', ',');
                    int    p1   = dataPort >> 8;
                    int    p2   = dataPort & 0xFF;
                    writeLineLogged(out, "227 Entering Passive Mode (" + ip2 + "," + p1 + "," + p2 + ")", cmd, arg, ip);
                } catch (Exception e) {
                    Log.e(TAG, "PASV error: " + e.getMessage());
                    writeLineLogged(out, "425 Cannot open passive connection", cmd, arg, ip);
                }
                return false;
            }

            case "EPSV": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                closePasv(s);
                try {
                    int dataPort = findFreePort();
                    if (dataPort < 0) {
                        writeLineLogged(out, "425 No free port available", cmd, arg, ip);
                        return false;
                    }
                    ServerSocket pasvSock = new ServerSocket();
                    pasvSock.setReuseAddress(true);
                    pasvSock.bind(new InetSocketAddress(dataPort));
                    pasvSock.setSoTimeout(DATA_TIMEOUT_MS);
                    s.pasvServerSocket = pasvSock;

                    s.pendingData = new FutureTask<>(() -> {
                        try { return pasvSock.accept(); }
                        catch (Exception e) { return null; }
                    });
                    Thread t = new Thread(s.pendingData, "FTP-EPSV-Accept");
                    t.setDaemon(true);
                    t.start();

                    writeLineLogged(out, "229 Entering Extended Passive Mode (|||" + dataPort + "|)", cmd, arg, ip);
                } catch (Exception e) {
                    Log.e(TAG, "EPSV error: " + e.getMessage());
                    writeLineLogged(out, "425 Cannot open passive connection", cmd, arg, ip);
                }
                return false;
            }

            case "PORT": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                closePasv(s);
                try {
                    String[] parts = arg.split(",");
                    s.activeAddr = parts[0]+"."+parts[1]+"."+parts[2]+"."+parts[3];
                    s.activePort = (Integer.parseInt(parts[4]) << 8) | Integer.parseInt(parts[5]);
                    writeLineLogged(out, "200 PORT command OK", cmd, arg, ip);
                } catch (Exception e) {
                    writeLineLogged(out, "501 Bad PORT argument", cmd, arg, ip);
                }
                return false;
            }

            case "EPRT": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                closePasv(s);
                try {
                    String[] parts = arg.split("\\|");
                    s.activeAddr = parts[2];
                    s.activePort = Integer.parseInt(parts[3]);
                    writeLineLogged(out, "200 EPRT OK", cmd, arg, ip);
                } catch (Exception e) {
                    writeLineLogged(out, "501 Bad EPRT argument", cmd, arg, ip);
                }
                return false;
            }

            case "LIST":
            case "NLST": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                String listArg = arg;
                for (String token : arg.split("\\s+")) {
                    if (token.startsWith("-")) {
                        listArg = listArg.replace(token, "").trim();
                    }
                }
                File dir = listArg.isEmpty() ? s.currentDir : resolve(s.currentDir, listArg);
                if (dir == null || !dir.exists()) { writeLineLogged(out, "550 Not found", cmd, arg, ip); return false; }
                writeLineLogged(out, "150 Opening data connection", cmd, arg, ip);
                try (Socket data = getDataSocket(s)) {
                    if (data == null) { writeLineLogged(out, "425 Cannot open data connection", cmd, arg, ip); return false; }
                    OutputStream dos = data.getOutputStream();
                    for (File f : safeList(dir)) {
                        String entry = "LIST".equals(cmd) ? listEntry(f) : f.getName();
                        dos.write((entry + "\r\n").getBytes("UTF-8"));
                    }
                    dos.flush();
                    state.addRequest();
                    writeLineLogged(out, "226 Transfer complete", cmd, arg, ip);
                } catch (Exception e) {
                    Log.e(TAG, "LIST error: " + e.getMessage());
                    writeLineLogged(out, "426 Connection closed", cmd, arg, ip);
                }
                return false;
            }

            case "RETR": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f == null || !f.isFile()) { writeLineLogged(out, "550 File not found", cmd, arg, ip); return false; }
                if (!safe(f)) { writeLineLogged(out, "550 Access denied", cmd, arg, ip); return false; }
                writeLineLogged(out, "150 Opening data connection for " + f.getName()
                        + " (" + f.length() + " bytes)", cmd, arg, ip);
                try (Socket data = getDataSocket(s);
                     FileInputStream fis = new FileInputStream(f)) {
                    if (data == null) { writeLineLogged(out, "425 Cannot open data connection", cmd, arg, ip); return false; }
                    byte[] buf = new byte[65536];
                    int n;
                    OutputStream dos = data.getOutputStream();
                    while ((n = fis.read(buf)) != -1) dos.write(buf, 0, n);
                    dos.flush();
                    state.addBytes(f.length());
                    state.addRequest();
                    LogManager.addBytes(f.length());
                    writeLineLogged(out, "226 Transfer complete", cmd, arg, ip);
                } catch (Exception e) {
                    Log.e(TAG, "RETR error: " + e.getMessage());
                    writeLineLogged(out, "426 Connection aborted", cmd, arg, ip);
                }
                return false;
            }

            case "STOR": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                if (state.sp_bool("ftp_read_only", false)) { writeLineLogged(out, "550 Read-only", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f == null) { writeLineLogged(out, "550 Invalid path", cmd, arg, ip); return false; }
                writeLineLogged(out, "150 Ready to receive", cmd, arg, ip);
                try (Socket data = getDataSocket(s);
                     FileOutputStream fos = new FileOutputStream(f)) {
                    if (data == null) { writeLineLogged(out, "425 Cannot open data connection", cmd, arg, ip); return false; }
                    byte[] buf = new byte[65536];
                    int n;
                    InputStream dis = data.getInputStream();
                    while ((n = dis.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.flush();
                    writeLineLogged(out, "226 Transfer complete", cmd, arg, ip);
                } catch (Exception e) {
                    writeLineLogged(out, "426 Transfer aborted", cmd, arg, ip);
                }
                return false;
            }

            case "SIZE": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f != null && f.isFile()) writeLineLogged(out, "213 " + f.length(), cmd, arg, ip);
                else writeLineLogged(out, "550 File not found", cmd, arg, ip);
                return false;
            }

            case "MDTM": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f != null && f.exists()) {
                    String ts = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                            .format(new Date(f.lastModified()));
                    writeLineLogged(out, "213 " + ts, cmd, arg, ip);
                } else {
                    writeLineLogged(out, "550 File not found", cmd, arg, ip);
                }
                return false;
            }

            case "DELE": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                if (state.sp_bool("ftp_read_only", false)) { writeLineLogged(out, "550 Read-only", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f == null) {
                    writeLineLogged(out, "550 Invalid path", cmd, arg, ip);
                } else if (!f.exists()) {
                    writeLineLogged(out, "550 File not found: " + f.getName(), cmd, arg, ip);
                } else if (!f.isFile()) {
                    writeLineLogged(out, "550 Not a regular file", cmd, arg, ip);
                } else if (!safe(f)) {
                    writeLineLogged(out, "550 Access denied (outside root)", cmd, arg, ip);
                } else if (!f.canWrite()) {
                    writeLineLogged(out, "550 Permission denied: " + f.getName(), cmd, arg, ip);
                } else if (f.delete()) {
                    writeLineLogged(out, "250 File deleted", cmd, arg, ip);
                } else {
                    writeLineLogged(out, "550 Delete failed", cmd, arg, ip);
                }
                return false;
            }

            case "MKD": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                if (state.sp_bool("ftp_read_only", false)) { writeLineLogged(out, "550 Read-only", cmd, arg, ip); return false; }
                File d = resolve(s.currentDir, arg);
                if (d != null && d.mkdirs()) writeLineLogged(out, "257 \"" + arg + "\" created", cmd, arg, ip);
                else writeLineLogged(out, "550 Cannot create", cmd, arg, ip);
                return false;
            }

            case "RMD": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                if (state.sp_bool("ftp_read_only", false)) { writeLineLogged(out, "550 Read-only", cmd, arg, ip); return false; }
                File d = resolve(s.currentDir, arg);
                if (d != null && d.isDirectory() && safe(d) && d.delete()) writeLineLogged(out, "250 Removed", cmd, arg, ip);
                else writeLineLogged(out, "550 Cannot remove", cmd, arg, ip);
                return false;
            }

            case "RNFR": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                File f = resolve(s.currentDir, arg);
                if (f != null && f.exists() && safe(f)) {
                    s.renameFrom = f;
                    writeLineLogged(out, "350 Ready for RNTO", cmd, arg, ip);
                } else {
                    writeLineLogged(out, "550 File not found", cmd, arg, ip);
                }
                return false;
            }

            case "RNTO": {
                if (!s.loggedIn) { writeLineLogged(out, "530 Not logged in", cmd, arg, ip); return false; }
                if (state.sp_bool("ftp_read_only", false)) { writeLineLogged(out, "550 Read-only", cmd, arg, ip); return false; }
                if (s.renameFrom == null) { writeLineLogged(out, "503 No RNFR", cmd, arg, ip); return false; }
                File dest = resolve(s.currentDir, arg);
                if (dest != null && s.renameFrom.renameTo(dest)) writeLineLogged(out, "250 Renamed", cmd, arg, ip);
                else writeLineLogged(out, "550 Rename failed", cmd, arg, ip);
                s.renameFrom = null;
                return false;
            }

            case "QUIT":
                writeLineLogged(out, "221 Goodbye", cmd, arg, ip);
                return true;

            default:
                writeLineLogged(out, "502 Command not implemented: " + cmd, cmd, arg, ip);
                return false;
        }
    }

    // ── Data connection ───────────────────────────────────────────────────────

    private Socket getDataSocket(Session s) {
        if (s.pendingData != null) {
            try {
                Socket ds = s.pendingData.get(DATA_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                s.pendingData = null;
                closePasv(s);
                return ds;
            } catch (Exception e) {
                Log.e(TAG, "PASV accept failed: " + e.getMessage());
                s.pendingData = null;
                closePasv(s);
                return null;
            }
        }
        if (s.activeAddr != null && s.activePort > 0) {
            try {
                Socket ds = new Socket();
                ds.connect(new InetSocketAddress(s.activeAddr, s.activePort), DATA_TIMEOUT_MS);
                return ds;
            } catch (Exception e) {
                Log.e(TAG, "Active connect failed: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private void closePasv(Session s) {
        if (s.pasvServerSocket != null) {
            try { s.pasvServerSocket.close(); } catch (Exception ignored) {}
            s.pasvServerSocket = null;
        }
    }

    private int findFreePort() {
        for (int p = PASV_PORT_START; p <= PASV_PORT_END; p++) {
            try (ServerSocket test = new ServerSocket(p)) {
                return p;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private boolean authenticate(String user, String pass) {
        if (state.sp_bool("ftp_anonymous", false)) return true;
        if (!state.isPasswordEnabled()) return true;
        String u = state.getUsername();
        String p = state.getPassword();
        if (u.isEmpty()) return p.equals(pass);
        return u.equals(user) && p.equals(pass);
    }

    // ── Path utils ────────────────────────────────────────────────────────────

    private File resolve(File cwd, String path) {
        if (path == null || path.isEmpty()) return cwd;
        try {
            File f = path.startsWith("/") ? new File(rootDir, path) : new File(cwd, path);
            return f.getCanonicalFile();
        } catch (Exception e) { return null; }
    }

    private boolean safe(File f) {
        try {
            return f.getCanonicalPath().startsWith(rootDir.getCanonicalPath());
        } catch (Exception e) { return false; }
    }

    private String vpath(File dir) {
        try {
            String root = rootDir.getCanonicalPath();
            String here = dir.getCanonicalPath();
            if (here.equals(root)) return "/";
            String rel = here.substring(root.length()).replace(File.separatorChar, '/');
            return rel.startsWith("/") ? rel : "/" + rel;
        } catch (Exception e) { return "/"; }
    }

    private List<File> safeList(File dir) {
        List<File> result = new ArrayList<>();
        File[] all = dir.listFiles();
        if (all == null) return result;
        Arrays.sort(all, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        Collections.addAll(result, all);
        return result;
    }

    private String listEntry(File f) {
        String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
        long   size  = f.isDirectory() ? 0 : f.length();
        String date  = new SimpleDateFormat("MMM dd HH:mm", Locale.US)
                .format(new Date(f.lastModified()));
        return String.format(Locale.US, "%s  1 ftp ftp %10d %s %s",
                perms, size, date, f.getName());
    }

    // ── Write helper ──────────────────────────────────────────────────────────

    private void writeLine(PrintWriter out, String msg) {
        out.print(msg + "\r\n");
        out.flush();
    }

    private void writeLineLogged(PrintWriter out, String msg, String cmd, String arg, String ip) {
        writeLine(out, msg);
        String code = msg.length() >= 3 ? msg.substring(0, 3) : "000";
        LogManager.add(cmd, arg.isEmpty() ? "/" : arg, code, ip);
    }

    // ── Session state ─────────────────────────────────────────────────────────

    private static class Session {
        String      username        = "anonymous";
        boolean     loggedIn        = false;
        File        currentDir      = null;
        ServerSocket pasvServerSocket = null;
        FutureTask<Socket> pendingData = null;
        String      activeAddr      = null;
        int         activePort      = -1;
        File        renameFrom      = null;
    }
}