package com.ar.hostmaster;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.util.*;

public class FileServer extends NanoHTTPD {

    private final File              rootDir;
    private final AppState          state;
    private final Map<String, File> virtualMap; // null = folder/web mode
    private final boolean           webMode;    // true = auto-serve index.html

    /** Folder mode */
    public FileServer(int port, File root, AppState st) {
        super(port);
        this.rootDir    = root;
        this.state      = st;
        this.virtualMap = null;
        this.webMode    = false;
    }

    /** Virtual map mode (file selection) */
    public FileServer(int port, File root, AppState st, Map<String, File> vMap) {
        super(port);
        this.rootDir    = root;
        this.state      = st;
        this.virtualMap = vMap;
        this.webMode    = false;
    }

    /** Web hosting mode — index.html auto-runs */
    public FileServer(int port, File root, AppState st, boolean webMode) {
        super(port);
        this.rootDir    = root;
        this.state      = st;
        this.virtualMap = null;
        this.webMode    = webMode;
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        String ip  = session.getRemoteIpAddress();
        String uri = session.getUri();
        try { uri = java.net.URLDecoder.decode(uri, "UTF-8"); }
        catch (Exception ignored) {}

        if (uri.equals("/favicon.ico") || uri.startsWith("/apple-touch-icon")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
        }

        LogManager.clientConnected();
        try {
            if (state.isPasswordEnabled() && !state.getPassword().isEmpty()) {
                String auth = session.getHeaders().get("authorization");
                if (auth == null || !checkAuth(auth)) {
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED,
                            MIME_PLAINTEXT, "401 Unauthorized");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Host Master\"");
                    LogManager.add("AUTH", uri, "401", ip);
                    return r;
                }
            }

            String query = session.getQueryParameterString();
            String forcedTheme = null;
            if (query != null) {
                if (query.contains("theme=dark"))  forcedTheme = "dark";
                if (query.contains("theme=light")) forcedTheme = "light";
            }

            if (virtualMap != null) {
                if (uri.equals("/")) {
                    LogManager.add("GET", "/", "200", ip);
                    state.addRequest();
                    return buildVirtualListing(forcedTheme);
                }
                String key = uri.startsWith("/") ? uri.substring(1) : uri;
                File target = virtualMap.get(key);
                if (target == null || !target.exists()) {
                    LogManager.add("GET", uri, "404", ip);
                    return newFixedLengthResponse(Response.Status.NOT_FOUND,
                            MIME_PLAINTEXT, "404 – File not in selected list");
                }
                LogManager.add("GET", uri, "200", ip);
                state.addRequest();
                return serveFileSmart(target, session);
            }
           
            File target = uri.equals("/") ? rootDir : new File(rootDir, uri);

            if (!safeCanonical(target)) {
                LogManager.add("GET", uri, "403", ip);
                return newFixedLengthResponse(Response.Status.FORBIDDEN,
                        MIME_PLAINTEXT, "403 Forbidden");
            }
            if (!target.exists()) {
                LogManager.add("GET", uri, "404", ip);
                return newFixedLengthResponse(Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT, "404 Not Found");
            }

            if (target.isDirectory()) {
				// Web mode: auto-serve index.html if present
				if (webMode) {
					File idx = new File(target, "index.html");
					if (!idx.exists()) idx = new File(target, "index.htm");
					if (idx.exists()) {
						// Redirect to add trailing slash so relative CSS/JS/asset
						// paths inside index.html resolve against THIS folder,
						// not its parent. Without this, sub-folder assets 404.
						if (!uri.endsWith("/")) {
							String loc = uri + "/";
							if (query != null && !query.isEmpty()) loc += "?" + query;
							Response redirect = newFixedLengthResponse(
								Response.Status.REDIRECT, MIME_PLAINTEXT, "");
							redirect.addHeader("Location", loc);
							LogManager.add("GET", uri, "301-redirect", ip);
							return redirect;
						}
						LogManager.add("GET", uri, "200-web", ip);
						state.addRequest();
						return serveFileSmart(idx, session);
					}
				}
				// Folder mode: always show listing
				LogManager.add("GET", uri, "200", ip);
				state.addRequest();
				return buildDirListing(target, uri, forcedTheme);
			}

            LogManager.add("GET", uri, "200", ip);
            state.addRequest();
            return serveFileSmart(target, session);

        } finally {
            LogManager.clientDisconnected();
        }
    }

    private Response serveFileSmart(File f, IHTTPSession session) {
        String nl   = f.getName().toLowerCase(Locale.US);
        String mime = betterMime(f.getName());

        // Range request for seeking
        String range = session.getHeaders().get("range");
        if (range != null && (isVideo(nl) || isAudio(nl))) {
            return serveRange(f, mime, range);
        }

        // In web mode: ALL files must be inline so browser can load CSS/JS/fonts/images
        // Without this, sub-folder assets (style.css, script.js) fail to apply
        boolean inline = webMode || isVideo(nl) || isAudio(nl) || isImage(nl)
                || isWebAsset(nl) || nl.endsWith(".pdf") || nl.endsWith(".txt")
                || nl.endsWith(".ttf") || nl.endsWith(".woff") || nl.endsWith(".woff2")
                || nl.endsWith(".otf") || nl.endsWith(".eot");

        return serveFile(f, mime, inline);
    }

    private Response serveFile(File f, String mime, boolean inline) {
        try {
            FileInputStream fis = new FileInputStream(f);
            Response r = newFixedLengthResponse(Response.Status.OK, mime, fis, f.length());
            r.addHeader("Accept-Ranges", "bytes");
            r.addHeader("Access-Control-Allow-Origin", "*");
            r.addHeader("Content-Disposition",
                    (inline ? "inline" : "attachment") + "; filename=\"" + f.getName() + "\"");
            state.addBytes(f.length());
            LogManager.addBytes(f.length());
            return r;
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT, "Read error: " + e.getMessage());
        }
    }

    private Response serveRange(File f, String mime, String rangeHeader) {
        try {
            long fileLen = f.length();
            long start = 0, end = fileLen - 1;
            String range = rangeHeader.replace("bytes=", "").trim();
            String[] parts = range.split("-");
            if (parts.length > 0 && !parts[0].isEmpty()) start = Long.parseLong(parts[0].trim());
            if (parts.length > 1 && !parts[1].isEmpty()) end   = Long.parseLong(parts[1].trim());
            if (end >= fileLen) end = fileLen - 1;
            long length = end - start + 1;

            RandomAccessFile raf = new RandomAccessFile(f, "r");
            raf.seek(start);
            byte[] buf = new byte[(int) Math.min(length, 1024 * 1024L)];
            int read = raf.read(buf, 0, (int) Math.min(length, buf.length));
            raf.close();

            Response r = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT,
                    mime, new ByteArrayInputStream(buf, 0, read), read);
            r.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLen);
            r.addHeader("Accept-Ranges", "bytes");
            r.addHeader("Access-Control-Allow-Origin", "*");
            state.addBytes(read);
            LogManager.addBytes(read);
            return r;
        } catch (Exception e) {
            return serveFile(f, mime, true);
        }
    }

    private Response buildVirtualListing(String forcedTheme) {
        boolean dark = isDark(forcedTheme);
        StringBuilder sb = new StringBuilder();
        sb.append(htmlHead("Selected Files", dark));
        sb.append("<body class='").append(dark ? "dark" : "light").append("'>");
        sb.append(topBar("Selected Files", dark));
        sb.append("<div class='list'>");

        for (Map.Entry<String, File> e : virtualMap.entrySet()) {
            String key  = e.getKey();
            File   file = e.getValue();
            String href = "/" + urlEncode(key);
            String nl   = key.toLowerCase(Locale.US);
            String parentDir = file.getParent() != null ? file.getParent() : "";

            sb.append("<a href='").append(href).append("' class='row'>")
              .append("<span class='ic'>").append(fileIcon(key)).append("</span>")
              .append("<div class='info'>")
              .append("<span class='nm'>").append(esc(key)).append("</span>")
              .append("<span class='meta'>").append(esc(parentDir)).append(" · ").append(fmtSize(file.length())).append("</span>")
              .append("</div>")
              .append(indicator(nl))
              .append("</a>");
        }

        if (virtualMap.isEmpty())
            sb.append("<div class='empty'>📂 No files selected</div>");

        sb.append("</div></body></html>");
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", sb.toString());
    }

    private Response buildDirListing(File dir, String uri, String forcedTheme) {
        if (!state.isDirListing()) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN,
                    MIME_PLAINTEXT, "403 – Directory listing disabled");
        }

        boolean dark = isDark(forcedTheme);
        String parentUri = "/";
        if (!uri.equals("/")) {
            int last = uri.lastIndexOf('/');
            parentUri = (last > 0) ? uri.substring(0, last) : "/";
        }

        List<File> dirs  = new ArrayList<>();
        List<File> files = new ArrayList<>();
        File[] all = dir.listFiles();
        if (all != null) {
            for (File f : all) {
                if (f.isDirectory()) dirs.add(f);
                else                 files.add(f);
            }
            Collections.sort(dirs,  (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(htmlHead(dir.getName(), dark));
        sb.append("<body class='").append(dark ? "dark" : "light").append("'>");
        sb.append(topBar(uri, dark));
        sb.append("<div class='list'>");

        if (!uri.equals("/")) {
            sb.append("<a href='").append(parentUri)
              .append("' class='row back'><span class='ic'>↩</span>")
              .append("<div class='info'><span class='nm'>Parent folder</span></div></a>");
        }

        for (File d : dirs) {
            String href = uri.endsWith("/") ? uri + d.getName() : uri + "/" + d.getName();
            File[] fc = d.listFiles();
            int cnt = fc != null ? fc.length : 0;
            sb.append("<a href='").append(href).append("' class='row'>")
              .append("<span class='ic'>📁</span>")
              .append("<div class='info'>")
              .append("<span class='nm'>").append(esc(d.getName())).append("</span>")
              .append("<span class='meta'>").append(cnt).append(" items</span>")
              .append("</div>")
              .append("<span class='ind dl'>📂</span>")
              .append("</a>");
        }

        for (File f : files) {
            String href = uri.endsWith("/") ? uri + f.getName() : uri + "/" + f.getName();
            String nl   = f.getName().toLowerCase(Locale.US);
            sb.append("<a href='").append(href).append("' class='row'>")
              .append("<span class='ic'>").append(fileIcon(f.getName())).append("</span>")
              .append("<div class='info'>")
              .append("<span class='nm'>").append(esc(f.getName())).append("</span>")
              .append("<span class='meta'>").append(fmtSize(f.length())).append("</span>")
              .append("</div>")
              .append(indicator(nl))
              .append("</a>");
        }

        if (dirs.isEmpty() && files.isEmpty())
            sb.append("<div class='empty'>📂 Empty folder</div>");

        sb.append("</div></body></html>");
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", sb.toString());
    }

    private String indicator(String nl) {
        if (isVideo(nl))    return "<span class='ind play'>▶ Play</span>";
        if (isAudio(nl))    return "<span class='ind play'>♫ Play</span>";
        if (isImage(nl))    return "<span class='ind view'>⊙ View</span>";
        if (nl.endsWith(".pdf")) return "<span class='ind view'>⊡ View</span>";
        if (nl.endsWith(".html") || nl.endsWith(".htm"))
                            return "<span class='ind run'>⚡ Run</span>";
        if (nl.endsWith(".css"))  return "<span class='ind css'>CSS</span>";
        if (nl.endsWith(".js"))   return "<span class='ind js'>JS</span>";
        if (nl.endsWith(".json")) return "<span class='ind json'>JSON</span>";
        return              "<span class='ind dl'>↓ DL</span>";
    }

    private String htmlHead(String title, boolean dark) {
        return "<!DOCTYPE html><html lang='en'><head>"
            + "<meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Host Master — " + esc(title) + "</title>"
            + css() + "</head>";
    }

    /** Top bar — no back arrow */
    private String topBar(String path, boolean dark) {
        String toggleTheme = dark ? "light" : "dark";
        String toggleLabel = dark ? "☀ Light" : "🌙 Dark";
        return "<header>"
            + "<span class='logo'>HOST<span class='acc'>MASTER</span></span>"
            + "<div class='hdr-right'>"
            + "<span class='path'>" + esc(path) + "</span>"
            + "<a href='?theme=" + toggleTheme + "' class='theme-btn'>" + toggleLabel + "</a>"
            + "</div>"
            + "</header>";
    }

    private boolean isDark(String forced) {
        if ("dark".equals(forced))  return true;
        if ("light".equals(forced)) return false;
        return AppState.THEME_DARK.equals(state.getTheme());
    }

    private String css() {
        return "<style>"
            + "*{box-sizing:border-box;margin:0;padding:0}a{text-decoration:none;color:inherit}"

            + "body.light{--bg:#f0f4f8;--surface:#fff;--border:#dde3ec;"
            + "--text:#1a2332;--muted:#64748b;--acc:#0066cc;"
            + "background:var(--bg);color:var(--text);"
            + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;min-height:100vh}"

            + "body.dark{--bg:#0a0e1a;--surface:#111827;--border:#1e2d45;"
            + "--text:#e8f4ff;--muted:#4a7090;--acc:#00d4ff;"
            + "background:var(--bg);color:var(--text);"
            + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;min-height:100vh}"

            + "header{background:var(--surface);border-bottom:1px solid var(--border);"
            + "padding:13px 16px;display:flex;justify-content:space-between;align-items:center;"
            + "position:sticky;top:0;z-index:20;box-shadow:0 1px 6px rgba(0,0,0,.1)}"
            + ".hdr-right{display:flex;align-items:center;gap:8px;min-width:0}"
            + ".logo{font-size:16px;font-weight:900;letter-spacing:.12em;flex-shrink:0}"
            + ".acc{color:var(--acc)}"
            + ".path{font-size:11px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;"
            + "white-space:nowrap;max-width:140px}"
            + ".theme-btn{font-size:11px;padding:5px 12px;border-radius:20px;"
            + "background:var(--border);color:var(--muted);white-space:nowrap;flex-shrink:0}"

            + ".list{padding:12px;max-width:720px;margin:0 auto}"
            + ".row{display:flex;align-items:center;gap:12px;background:var(--surface);"
            + "border:1px solid var(--border);border-radius:14px;padding:12px 14px;"
            + "margin-bottom:8px;transition:border-color .15s}"
            + ".row:hover{border-color:var(--acc)}"
            + ".row:active{transform:scale(.99)}"
            + ".back{opacity:.65}.back:hover{opacity:1}"
            + ".ic{font-size:22px;min-width:32px;text-align:center;flex-shrink:0}"
            + ".info{flex:1;min-width:0}"
            + ".nm{display:block;font-size:14px;font-weight:600;overflow:hidden;"
            + "text-overflow:ellipsis;white-space:nowrap}"
            + ".meta{display:block;font-size:11px;color:var(--muted);margin-top:2px;"
            + "overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".empty{text-align:center;color:var(--muted);padding:80px 20px;font-size:15px}"

            // Indicator pills
            + ".ind{font-size:10px;font-weight:700;padding:4px 9px;border-radius:20px;"
            + "white-space:nowrap;flex-shrink:0;letter-spacing:.04em}"
            + ".ind.play{color:#065f46;background:#d1fae5}"
            + "body.dark .ind.play{color:#6ee7b7;background:#064e3b}"
            + ".ind.view{color:#1e40af;background:#dbeafe}"
            + "body.dark .ind.view{color:#93c5fd;background:#1e3a5f}"
            + ".ind.run{color:#6d28d9;background:#ede9fe}"
            + "body.dark .ind.run{color:#c4b5fd;background:#2d1f5e}"
            + ".ind.dl{color:var(--muted);background:var(--border)}"
            + ".ind.css{color:#1572B6;background:#E3F2FD}"
            + "body.dark .ind.css{color:#64B5F6;background:#0D2137}"
            + ".ind.js{color:#856404;background:#FFF3CD}"
            + "body.dark .ind.js{color:#FFD54F;background:#2D1F00}"
            + ".ind.json{color:#009688;background:#E0F2F1}"
            + "body.dark .ind.json{color:#80CBC4;background:#00251A}"

            + "@media(max-width:480px){.path{max-width:80px}.nm{font-size:13px}}"
            + "</style>";
    }

    private String betterMime(String name) {
        String nl = name.toLowerCase(Locale.US);
        if (nl.endsWith(".mp3"))  return "audio/mpeg";
        if (nl.endsWith(".m4a"))  return "audio/mp4";
        if (nl.endsWith(".aac"))  return "audio/aac";
        if (nl.endsWith(".ogg"))  return "audio/ogg";
        if (nl.endsWith(".flac")) return "audio/flac";
        if (nl.endsWith(".wav"))  return "audio/wav";
        if (nl.endsWith(".opus")) return "audio/opus";
        if (nl.endsWith(".weba")) return "audio/webm";
        if (nl.endsWith(".wma"))  return "audio/x-ms-wma";
        if (nl.endsWith(".mp4"))  return "video/mp4";
        if (nl.endsWith(".m4v"))  return "video/mp4";
        if (nl.endsWith(".mkv"))  return "video/x-matroska";
        if (nl.endsWith(".webm")) return "video/webm";
        if (nl.endsWith(".avi"))  return "video/x-msvideo";
        if (nl.endsWith(".mov"))  return "video/quicktime";
        if (nl.endsWith(".3gp"))  return "video/3gpp";
        if (nl.endsWith(".ts"))   return "video/mp2t";
        if (nl.endsWith(".jpg") || nl.endsWith(".jpeg")) return "image/jpeg";
        if (nl.endsWith(".png"))  return "image/png";
        if (nl.endsWith(".gif"))  return "image/gif";
        if (nl.endsWith(".webp")) return "image/webp";
        if (nl.endsWith(".bmp"))  return "image/bmp";
        if (nl.endsWith(".svg"))  return "image/svg+xml";
        if (nl.endsWith(".heic")) return "image/heic";
        if (nl.endsWith(".avif")) return "image/avif";
        if (nl.endsWith(".html") || nl.endsWith(".htm")) return "text/html; charset=utf-8";
        if (nl.endsWith(".css"))  return "text/css";
        if (nl.endsWith(".js"))   return "application/javascript";
        if (nl.endsWith(".json")) return "application/json";
        if (nl.endsWith(".xml"))  return "application/xml";
        if (nl.endsWith(".pdf"))  return "application/pdf";
        if (nl.endsWith(".txt") || nl.endsWith(".md") || nl.endsWith(".log"))
            return "text/plain; charset=utf-8";
        // Font files — critical for web hosting
        if (nl.endsWith(".ttf"))   return "font/ttf";
        if (nl.endsWith(".otf"))   return "font/otf";
        if (nl.endsWith(".woff"))  return "font/woff";
        if (nl.endsWith(".woff2")) return "font/woff2";
        if (nl.endsWith(".eot"))   return "application/vnd.ms-fontobject";
        return getMimeTypeForFile(name);
    }

    private boolean isVideo(String nl) {
        return nl.endsWith(".mp4") || nl.endsWith(".mkv") || nl.endsWith(".avi")
            || nl.endsWith(".mov") || nl.endsWith(".webm") || nl.endsWith(".3gp")
            || nl.endsWith(".m4v") || nl.endsWith(".ts")   || nl.endsWith(".flv");
    }

    private boolean isAudio(String nl) {
        return nl.endsWith(".mp3") || nl.endsWith(".m4a") || nl.endsWith(".aac")
            || nl.endsWith(".ogg") || nl.endsWith(".flac") || nl.endsWith(".wav")
            || nl.endsWith(".opus")|| nl.endsWith(".weba") || nl.endsWith(".wma")
            || nl.endsWith(".amr") || nl.endsWith(".3ga");
    }

    private boolean isImage(String nl) {
        return nl.endsWith(".jpg") || nl.endsWith(".jpeg") || nl.endsWith(".png")
            || nl.endsWith(".gif") || nl.endsWith(".webp") || nl.endsWith(".bmp")
            || nl.endsWith(".svg") || nl.endsWith(".heic") || nl.endsWith(".avif");
    }

    private boolean isWebAsset(String nl) {
        return nl.endsWith(".html") || nl.endsWith(".htm")
            || nl.endsWith(".css")  || nl.endsWith(".js")
            || nl.endsWith(".json") || nl.endsWith(".xml")
            || nl.endsWith(".txt")  || nl.endsWith(".md");
    }

    // ── Icons ─────────────────────────────────────────────────────────────────

    private String fileIcon(String n) {
        String l = n.toLowerCase(Locale.US);
        if (isVideo(l))  return "🎬";
        if (isAudio(l))  return "🎵";
        if (l.endsWith(".jpg")||l.endsWith(".jpeg")||l.endsWith(".png")||
            l.endsWith(".webp")||l.endsWith(".gif")||l.endsWith(".bmp")||
            l.endsWith(".heic")||l.endsWith(".avif")) return "🖼";
        if (l.endsWith(".svg"))  return "✦";
        if (l.endsWith(".pdf"))  return "📕";
        if (l.endsWith(".zip")||l.endsWith(".rar")||l.endsWith(".7z")||
            l.endsWith(".tar")||l.endsWith(".gz"))    return "🗜";
        if (l.endsWith(".apk"))  return "📦";
        if (l.endsWith(".html")||l.endsWith(".htm"))  return "🌐";
        if (l.endsWith(".css"))  return "🎨";
        if (l.endsWith(".js"))   return "⚡";
        if (l.endsWith(".txt")||l.endsWith(".log")||l.endsWith(".md")) return "📝";
        if (l.endsWith(".json")||l.endsWith(".xml")||l.endsWith(".csv")) return "📋";
        if (l.endsWith(".java")||l.endsWith(".py")||l.endsWith(".kt")||
            l.endsWith(".sh")||l.endsWith(".cpp")||l.endsWith(".c"))   return "💻";
        if (l.endsWith(".db")||l.endsWith(".sqlite")) return "🗄";
        if (l.endsWith(".docx")||l.endsWith(".doc"))  return "📘";
        if (l.endsWith(".xlsx")||l.endsWith(".xls"))  return "📗";
        return "📎";
    }

    private boolean safeCanonical(File f) {
        try { return f.getCanonicalPath().startsWith(rootDir.getCanonicalPath()); }
        catch (IOException e) { return false; }
    }

    private boolean checkAuth(String header) {
        if (!header.startsWith("Basic ")) return false;
        String decoded = new String(android.util.Base64.decode(
                header.substring(6), android.util.Base64.DEFAULT));
        String user = state.getUsername();
        String pass = state.getPassword();
        if (user.isEmpty()) return decoded.endsWith(":" + pass);
        return decoded.equals(user + ":" + pass);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;")
                .replace(">","&gt;").replace("\"","&quot;");
    }

    private String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return s; }
    }

    private String fmtSize(long b) {
        if (b < 1024)            return b + " B";
        if (b < 1024*1024)       return String.format(Locale.US,"%.1f KB",b/1024.0);
        if (b < 1024L*1024*1024) return String.format(Locale.US,"%.1f MB",b/(1024.0*1024));
        return String.format(Locale.US,"%.2f GB",b/(1024.0*1024*1024));
    }
}
