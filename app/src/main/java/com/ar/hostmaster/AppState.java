package com.ar.hostmaster;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class AppState {
    private static final String PREFS = "hostmaster";
    private final SharedPreferences sp;

    public static final String THEME_LIGHT  = "light";
    public static final String THEME_DARK   = "dark";
    public static final String THEME_SYSTEM = "system";

    public static AppState instance;
    public static AppState get(Context ctx) {
        if (instance == null) instance = new AppState(ctx.getApplicationContext());
        return instance;
    }
    private AppState(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Server state ──────────────────────────────────────────────────────────
    public boolean isRunning()              { return sp.getBoolean("running", false); }
    public void    setRunning(boolean v)    { sp.edit().putBoolean("running", v).apply(); }

    public String  getLocalUrl()            { return sp.getString("local_url", ""); }
    public void    setLocalUrl(String v)    { sp.edit().putString("local_url", v).apply(); }

    public int getPort(String proto) {
        int def;
        switch (proto) {
            case "HTTP": def = 8080; break;
            case "FTP":  def = 2121; break;
            default:     def = 8080; break;
        }
        return sp.getInt("port_" + proto, def);
    }
    public void setPort(String proto, int v) { sp.edit().putInt("port_" + proto, v).apply(); }

    public String  getProtocol()            { return sp.getString("protocol", "HTTP"); }
    public void    setProtocol(String v)    { sp.edit().putString("protocol", v).apply(); }

    // ── Source selection ──────────────────────────────────────────────────────
    // Modes: "folder" | "files" | "web"
    public String  getSourceMode()          { return sp.getString("source_mode", "folder"); }
    public void    setSourceMode(String v)  { sp.edit().putString("source_mode", v).apply(); }

    public boolean isFileMode()             { return "files".equals(getSourceMode()); }
    public boolean isWebMode()              { return "web".equals(getSourceMode()); }

    public String  getFolderPath()          { return sp.getString("folder", ""); }
    public void    setFolderPath(String v)  { sp.edit().putString("folder", v).apply(); }

    // Legacy compat shim
    public void setFileMode(boolean v) { setSourceMode(v ? "files" : "folder"); }

    // ── Multi-file list ───────────────────────────────────────────────────────
    public void setSelectedFiles(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            sp.edit().remove("selected_files").apply();
        } else {
            sp.edit().putString("selected_files",
                    android.text.TextUtils.join("|", paths)).apply();
        }
    }

    public List<String> getSelectedFiles() {
        String raw = sp.getString("selected_files", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|")));
    }

    public boolean isMultiFileMode() {
        return isFileMode() && getSelectedFiles().size() > 1;
    }

    // ── Web hosting ───────────────────────────────────────────────────────────
    public String  getWebFolder()           { return sp.getString("web_folder", ""); }
    public void    setWebFolder(String v)   { sp.edit().putString("web_folder", v).apply(); }

    public List<String> getWebFolderList() {
        String raw = sp.getString("web_folder_list", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|")));
    }

    // ── Pinned web folders ───────────────────────────────────────────────────
    public List<String> getWebPinnedFolders() {
        String raw = sp.getString("web_pinned", "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|")));
    }

    public void setWebPinnedFolders(List<String> list) {
        if (list == null || list.isEmpty()) {
            sp.edit().remove("web_pinned").apply();
        } else {
            sp.edit().putString("web_pinned",
                    android.text.TextUtils.join("|", list)).apply();
        }
    }

    public void setWebFolderList(List<String> list) {
        if (list == null || list.isEmpty()) {
            sp.edit().remove("web_folder_list").apply();
        } else {
            sp.edit().putString("web_folder_list",
                    android.text.TextUtils.join("|", list)).apply();
        }
    }
    
    // ── Security ──────────────────────────────────────────────────────────────
    public boolean isPasswordEnabled()           { return sp.getBoolean("pass_enabled", false); }
    public void    setPasswordEnabled(boolean v) { sp.edit().putBoolean("pass_enabled", v).apply(); }

    public String  getUsername()            { return sp.getString("username", ""); }
    public void    setUsername(String v)    { sp.edit().putString("username", v).apply(); }

    public String  getPassword()            { return sp.getString("password", ""); }
    public void    setPassword(String v)    { sp.edit().putString("password", v).apply(); }

    // ── Server options (baked-in, no toggles needed) ──────────────────────────
    public boolean isDirListing()           { return true; }
    public boolean isStreamMedia()          { return true; }
    public boolean isHostWeb()              { return isWebMode(); }

    // Autostart
    public boolean isAutostart()            { return sp.getBoolean("autostart", false); }
    public void    setAutostart(boolean v)  { sp.edit().putBoolean("autostart", v).apply(); }

    // ── Theme ─────────────────────────────────────────────────────────────────
    public String  getTheme()               { return sp.getString("theme", THEME_SYSTEM); }
    public void    setTheme(String v)       { sp.edit().putString("theme", v).apply(); }

    // ── Stats ─────────────────────────────────────────────────────────────────
    public long    getTotalRequests()       { return sp.getLong("total_req", 0); }
    public void    addRequest()             { sp.edit().putLong("total_req", getTotalRequests()+1).apply(); }

    public long    getTotalBytes()          { return sp.getLong("total_bytes", 0); }
    public void    addBytes(long b)         { sp.edit().putLong("total_bytes", getTotalBytes()+b).apply(); }
    public void resetStats() {
        sp.edit()
            .putLong("total_req", 0)
            .putLong("total_bytes", 0)
            .apply();
    }

    // ── Clear all ─────────────────────────────────────────────────────────────
    public void clearAll() { sp.edit().clear().apply(); }

    // ── SharedPreferences helpers ────────────────────────────────────────────
    public boolean sp_bool(String key, boolean def)    { return sp.getBoolean(key, def); }
    public void    sp_set(String key, boolean val)     { sp.edit().putBoolean(key, val).apply(); }
    
    public long sp_long(String key, long def)     { return sp.getLong(key, def); }
    public void sp_long_set(String key, long val) { sp.edit().putLong(key, val).apply(); }

    public String  sp_str(String key, String def)      { return sp.getString(key, def); }
    public void    sp_str_set(String key, String val)  { sp.edit().putString(key, val).apply(); }

    public int     sp_int(String key, int def)         { return sp.getInt(key, def); }
    public void    sp_int_set(String key, int val)     { sp.edit().putInt(key, val).apply(); }
}