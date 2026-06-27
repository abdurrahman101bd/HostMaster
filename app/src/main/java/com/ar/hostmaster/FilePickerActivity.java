package com.ar.hostmaster;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.*;

/**
 * FilePickerActivity
 *
 * MODE_FOLDER:
 *   - Tap row          → select this folder (confirm button activates)
 *   - Tap › arrow      → enter subfolder to navigate deeper
 *   - Back / btn_back  → go up one level (or finish at root)
 *
 * MODE_FILES:
 *   - Tap folder row   → enter folder
 *   - Tap file row     → toggle in basket
 *   - Basket chips     → persistent across folder navigation, ✕ to remove
 */

public class FilePickerActivity extends AppCompatActivity {

    public static final String MODE_KEY     = "mode";
    public static final String MODE_FOLDER  = "folder";
    public static final String MODE_FILES   = "files";
    public static final String RESULT_PATH  = "path";
    public static final String RESULT_PATHS = "paths";

    private static final boolean SHOW_HIDDEN = false;

    private String mode;
    private File   currentDir;
    private File   selectedFolder;

    private TextView   tvTitle, tvPath, tvBasketCount, tvEmpty;
    private RecyclerView rvFiles, rvBasket;
    private View       basketPanel, folderConfirmPanel;
    private Button     btnConfirm, btnConfirmFolder;

    private FileEntryAdapter fileAdapter;
    private BasketAdapter    basketAdapter;

    private final List<String> basket = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.apply(AppState.get(this).getTheme());
        setContentView(R.layout.activity_file_picker);
        ThemeHelper.applyWithStatusBar(this, AppState.get(this).getTheme());

        mode = getIntent().getStringExtra(MODE_KEY);
        if (mode == null) mode = MODE_FOLDER;

        tvTitle            = findViewById(R.id.tv_picker_title);
        tvPath             = findViewById(R.id.tv_current_path);
        tvBasketCount      = findViewById(R.id.tv_basket_count);
        tvEmpty            = findViewById(R.id.tv_empty);
        rvFiles            = findViewById(R.id.rv_files);
        rvBasket           = findViewById(R.id.rv_basket);
        basketPanel        = findViewById(R.id.basket_panel);
        folderConfirmPanel = findViewById(R.id.folder_confirm_panel);
        btnConfirm         = findViewById(R.id.btn_confirm);
        btnConfirmFolder   = findViewById(R.id.btn_confirm_folder);

        tvTitle.setText(MODE_FOLDER.equals(mode) ? "SELECT FOLDER" : "SELECT FILES");

        if (MODE_FILES.equals(mode)) {
            basketPanel.setVisibility(View.VISIBLE);
            folderConfirmPanel.setVisibility(View.GONE);
        } else {
            basketPanel.setVisibility(View.GONE);
            folderConfirmPanel.setVisibility(View.VISIBLE);
            // Will be set by refreshFolderConfirm() after navigate()
        }

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        rvBasket.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        buildFileAdapter();
        buildBasketAdapter();

        currentDir = Environment.getExternalStorageDirectory();
        navigate(currentDir);

        findViewById(R.id.btn_back).setOnClickListener(v -> goUp());
        findViewById(R.id.btn_home).setOnClickListener(v ->
                navigate(Environment.getExternalStorageDirectory()));

        btnConfirmFolder.setOnClickListener(v -> {
            File toReturn = (selectedFolder != null) ? selectedFolder : currentDir;
            Intent r = new Intent();
            r.putExtra(RESULT_PATH, toReturn.getAbsolutePath());
            setResult(RESULT_OK, r);
            finish();
        });

        btnConfirm.setOnClickListener(v -> {
            if (basket.isEmpty()) return;
            Intent r = new Intent();
            r.putStringArrayListExtra(RESULT_PATHS, new ArrayList<>(basket));
            setResult(RESULT_OK, r);
            finish();
        });
    }

    private void navigate(File dir) {
        if (dir == null || !dir.exists()) return;
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());

        List<FileEntry> entries = buildEntries(dir);
        boolean empty = entries.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvFiles.setVisibility(empty ? View.GONE   : View.VISIBLE);
        fileAdapter.setData(entries);

        if (MODE_FOLDER.equals(mode)) {
            refreshFolderConfirm();
        } else {
            refreshConfirmBtn();
        }
    }

    private List<FileEntry> buildEntries(File dir) {
        List<FileEntry> result = new ArrayList<>();
        File[] all = dir.listFiles();
        if (all == null) return result;

        List<File> dirs  = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : all) {
            if (!SHOW_HIDDEN && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) dirs.add(f);
            else if (MODE_FILES.equals(mode)) files.add(f);
        }
        Collections.sort(dirs,  (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File d : dirs)  result.add(new FileEntry(d, true));
        for (File f : files) result.add(new FileEntry(f, false));
        return result;
    }

    private void goUp() {
        File parent = currentDir.getParentFile();
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (parent != null && !currentDir.getAbsolutePath().equals(rootPath)) {
            navigate(parent);
        } else if (currentDir.getAbsolutePath().equals(rootPath)) {
            // Already at root — back button exits
            finish();
        } else {
            finish();
        }
    }

    @Override public void onBackPressed() { goUp(); }

    private void selectFolder(File dir) {
        selectedFolder = dir;
        refreshFolderConfirm();
    }

    /** Select the currently browsed folder itself (useful for root or any parent dir). */
    private void selectCurrentDir() {
        selectedFolder = currentDir;
        refreshFolderConfirm();
    }

    private void refreshFolderConfirm() {
        if (selectedFolder != null) {
            btnConfirmFolder.setText("✓  USE: " + selectedFolder.getName());
            btnConfirmFolder.setEnabled(true);
            btnConfirmFolder.setAlpha(1f);
        } else {
            // No folder tapped — offer to use current browsed folder
            String name = currentDir.getName().isEmpty() ? "/ (root)" : currentDir.getName();
            btnConfirmFolder.setText("USE THIS FOLDER: " + name);
            btnConfirmFolder.setEnabled(true);
            btnConfirmFolder.setAlpha(0.85f);
        }
        fileAdapter.notifyDataSetChanged();
    }

    private void addToBasket(String path) {
        if (basket.contains(path)) return;
        basket.add(path);
        basketAdapter.notifyItemInserted(basket.size() - 1);
        rvBasket.scrollToPosition(basket.size() - 1);
        refreshBasketCount();
        refreshConfirmBtn();
    }

    private void removeFromBasket(int index) {
        if (index < 0 || index >= basket.size()) return;
        basket.remove(index);
        basketAdapter.notifyItemRemoved(index);
        refreshBasketCount();
        refreshConfirmBtn();
        fileAdapter.notifyDataSetChanged();
    }

    private void refreshBasketCount() {
        tvBasketCount.setText(basket.size() + " selected");
    }

    private void refreshConfirmBtn() {
        boolean has = !basket.isEmpty();
        btnConfirm.setText(has ? "CONFIRM (" + basket.size() + ")" : "SELECT FILES");
        btnConfirm.setAlpha(has ? 1f : 0.5f);
    }

    private void buildFileAdapter() {
        fileAdapter = new FileEntryAdapter(
                new ArrayList<>(),
                entry -> {
                    if (MODE_FOLDER.equals(mode)) {
                        if (entry.isDir) {
                            selectFolder(entry.file);
                        }
                    } else {
                        if (entry.isDir) {
                            navigate(entry.file);
                        } else {
                            String path = entry.file.getAbsolutePath();
                            if (basket.contains(path)) removeFromBasket(basket.indexOf(path));
                            else addToBasket(path);
                            fileAdapter.notifyDataSetChanged();
                        }
                    }
                },
                entry -> {
                    if (entry.isDir) navigate(entry.file);
                },
                basket,
                mode,
                () -> selectedFolder != null ? selectedFolder.getAbsolutePath() : null
        );
        rvFiles.setAdapter(fileAdapter);
    }

    private void buildBasketAdapter() {
        basketAdapter = new BasketAdapter(basket, this::removeFromBasket);
        rvBasket.setAdapter(basketAdapter);
    }

    static class FileEntry {
        final File file; final boolean isDir;
        FileEntry(File f, boolean d) { file = f; isDir = d; }
    }
    
    static class FileEntryAdapter extends RecyclerView.Adapter<FileEntryAdapter.VH> {

        interface OnTap     { void tap(FileEntry e); }
        interface OnArrow   { void arrow(FileEntry e); }
        interface SelGetter { String get(); }

        private List<FileEntry> data;
        private final OnTap   listener;
        private final OnArrow arrowListener;
        private final List<String> basket;
        private final String mode;
        private final SelGetter selGetter;

        FileEntryAdapter(List<FileEntry> data, OnTap l, OnArrow al,
                         List<String> basket, String mode, SelGetter sg) {
            this.data         = data;
            this.listener     = l;
            this.arrowListener = al;
            this.basket       = basket;
            this.mode         = mode;
            this.selGetter    = sg;
        }

        void setData(List<FileEntry> d) { this.data = d; notifyDataSetChanged(); }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            FileEntry e = data.get(pos);
            h.tvName.setText(e.file.getName());

            if (e.isDir) {
                // Folder icon
                h.ivIcon.setImageResource(R.drawable.ic_folder);
                h.ivIcon.setColorFilter(h.itemView.getContext().getResources().getColor(R.color.accent),
                        PorterDuff.Mode.SRC_IN);
                
                File[] children = e.file.listFiles();
                int cnt = children != null ? children.length : 0;
                h.tvMeta.setText(cnt + " items");
                h.ivCheck.setVisibility(View.GONE);

                if (MODE_FOLDER.equals(mode)) {
                    h.btnEnter.setVisibility(View.VISIBLE);
                    h.btnEnter.setOnClickListener(v -> arrowListener.arrow(e));
                    String sel = selGetter.get();
                    boolean isSelected = sel != null &&
                            sel.equals(e.file.getAbsolutePath());
                    h.tvName.setTextColor(isSelected
                            ? h.itemView.getContext().getResources().getColor(R.color.accent)
                            : h.itemView.getContext().getResources().getColor(R.color.text_primary));
                } else {
                    h.btnEnter.setVisibility(View.GONE);
                }

            } else {
				// File icon based on extension
				int iconRes = getIconResForFile(e.file.getName());
				int tintColor = getTintColorForFile(e.file.getName());
				h.ivIcon.setImageResource(iconRes);
				h.ivIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN); 
				h.tvMeta.setText(formatSize(e.file.length()));
                h.btnEnter.setVisibility(View.GONE);
                boolean sel = basket.contains(e.file.getAbsolutePath());
                h.ivCheck.setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
            }

            h.itemView.setOnClickListener(v -> listener.tap(e));
        }

        public static int getIconResForFile(String fileName) {
			String n = fileName.toLowerCase();

			// Video files
			if (n.endsWith(".mp4")) return R.drawable.ic_mp4;
			if (n.endsWith(".mkv")) return R.drawable.ic_mkv;
			if (n.endsWith(".avi")) return R.drawable.ic_avi;
			if (n.endsWith(".mov")) return R.drawable.ic_mov;
			if (n.endsWith(".webm")) return R.drawable.ic_webm;
			if (n.endsWith(".3gp")) return R.drawable.ic_3gp;
			if (n.endsWith(".flv")) return R.drawable.ic_flv;
			if (n.endsWith(".wmv")) return R.drawable.ic_wmv;
			if (n.endsWith(".vob")) return R.drawable.ic_vob; 
			
		
			// Audio files
			if (n.endsWith(".mp3")) return R.drawable.ic_mp3;
			if (n.endsWith(".ogg")) return R.drawable.ic_ogg;
			if (n.endsWith(".m4a")) return R.drawable.ic_m4a;
			if (n.endsWith(".aac")) return R.drawable.ic_aac;
			if (n.endsWith(".wav")) return R.drawable.ic_wav;
			if (n.endsWith(".flac")) return R.drawable.ic_flac;

			// Image files
			if (n.endsWith(".jpg")) return R.drawable.ic_jpg;
			if (n.endsWith(".jpeg")) return R.drawable.ic_jpeg;
			if (n.endsWith(".png")) return R.drawable.ic_png;
			if (n.endsWith(".gif")) return R.drawable.ic_gif;
			if (n.endsWith(".webp")) return R.drawable.ic_webp;
			if (n.endsWith(".bmp")) return R.drawable.ic_bmp;
			if (n.endsWith(".heic")) return R.drawable.ic_heic;
			if (n.endsWith(".psd")) return R.drawable.ic_psd; 
			if (n.endsWith(".ai")) return R.drawable.ic_ai;
			if (n.endsWith(".cr2")) return R.drawable.ic_cr2; 
            if (n.endsWith(".nef")) return R.drawable.ic_nef;
			if (n.endsWith(".ico")) return R.drawable.ic_ico;
			
			
			// SVG & PDF
			if (n.endsWith(".svg")) return R.drawable.ic_svg;
			if (n.endsWith(".pdf")) return R.drawable.ic_pdf;

			// Archive files
			if (n.endsWith(".zip")) return R.drawable.ic_zip;
			if (n.endsWith(".rar")) return R.drawable.ic_rar;
			if (n.endsWith(".tar")) return R.drawable.ic_tar;
			if (n.endsWith(".gzip")) return R.drawable.ic_gzip;
			if (n.endsWith(".7z")) return R.drawable.ic_7z;
			if (n.endsWith(".tar.gz")) return R.drawable.ic_targz; 
			if (n.endsWith(".gz")) return R.drawable.ic_gz; 
			if (n.endsWith(".tgz")) return R.drawable.ic_tgz;
			if (n.endsWith(".bz2")) return R.drawable.ic_bz2;
			if (n.endsWith(".xz")) return R.drawable.ic_xz;
			if (n.endsWith(".alz")) return R.drawable.ic_alz;
			if (n.endsWith(".arc")) return R.drawable.ic_arc;
			if (n.endsWith(".iso")) return R.drawable.ic_iso;
			
			// APK
			if (n.endsWith(".apk") || n.endsWith(".aab")) return R.drawable.ic_apk;
			if (n.endsWith(".xapk")) return R.drawable.ic_xapk;

			// Web Files (HTML, JS, CSS)
			if (n.endsWith(".htm")) return R.drawable.ic_htm;
			if (n.endsWith(".html")) return R.drawable.ic_html;		
			if (n.endsWith(".xhtml")) return R.drawable.ic_xhtml;	
			if (n.endsWith(".js")) return R.drawable.ic_javascript;
			if (n.endsWith(".jsx")) return R.drawable.ic_jsx;
			if (n.endsWith(".ts")) return R.drawable.ic_ts;
			if (n.endsWith(".tsx")) return R.drawable.ic_tsx;
			if (n.endsWith(".css")) return R.drawable.ic_css;

			// Text & Configuration files
			if (n.endsWith(".txt")) return R.drawable.ic_text;
			if (n.endsWith(".tex")) return R.drawable.ic_tex;
			if (n.endsWith(".md")) return R.drawable.ic_markdown;
			if (n.endsWith(".log")) return R.drawable.ic_logs;
			if (n.endsWith(".json")) return R.drawable.ic_json;
			if (n.endsWith(".csv")) return R.drawable.ic_csv;
			if (n.endsWith(".ini")) return R.drawable.ic_ini;
			if (n.endsWith(".config")) return R.drawable.ic_config;
			if (n.endsWith(".properties")) return R.drawable.ic_text;
			if (n.endsWith(".yaml")) return R.drawable.ic_yaml;
            if (n.endsWith(".yml")) return R.drawable.ic_yml;
			if (n.endsWith(".toml")) return R.drawable.ic_toml;
			if (n.endsWith(".env")) return R.drawable.ic_env;
			
				
			// Programming Languages (Specific Icons)
			if (n.endsWith(".java")) return R.drawable.ic_java;
			if (n.endsWith(".jar")) return R.drawable.ic_jar;
			if (n.endsWith(".py"))   return R.drawable.ic_python;
			if (n.endsWith(".git"))  return R.drawable.ic_git;
			if (n.endsWith(".php"))  return R.drawable.ic_php;
			if (n.endsWith(".dart")) return R.drawable.ic_dart;
			if (n.endsWith(".swift")) return R.drawable.ic_swift;
			if (n.endsWith(".vue")) return R.drawable.ic_vue;
			

			// Generic Code Files
			if (n.endsWith(".xml")) return R.drawable.ic_xml;
			if (n.endsWith(".sh")) return R.drawable.ic_gnubash;
			if (n.endsWith(".cpp")) return R.drawable.ic_cpp;
			if (n.endsWith(".kt")) return R.drawable.ic_kt;
			if (n.endsWith(".c")) return R.drawable.ic_c;
			if (n.endsWith(".h")) return R.drawable.ic_h;
			if (n.endsWith(".cs")) return R.drawable.ic_cs;
			if (n.endsWith(".go")) return R.drawable.ic_go;
			if (n.endsWith(".rb")) return R.drawable.ic_rb;		
			if (n.endsWith(".jshtm") || n.endsWith(".rs")) return R.drawable.ic_code;

			// Documents (Word, RTF, OpenOffice, Apple Pages)
			if (n.endsWith(".doc")) return R.drawable.ic_doc;
			if (n.endsWith(".docx")) return R.drawable.ic_docx;
			if (n.endsWith(".odt")) return R.drawable.ic_odt;
			if (n.endsWith(".wps")) return R.drawable.ic_wps;
			if (n.endsWith(".pages")) return R.drawable.ic_pages;
			if (n.endsWith(".docm")) return R.drawable.ic_docm;
			if (n.endsWith(".dotx")) return R.drawable.ic_dotx;
			if (n.endsWith(".rtf") || n.endsWith(".fodt")) return R.drawable.ic_document;

			// Presentations (PowerPoint, Keynote)
			if (n.endsWith(".pptx")) return R.drawable.ic_pptx;
			if (n.endsWith(".ppt")) return R.drawable.ic_ppt;
			if (n.endsWith(".ppsx")) return R.drawable.ic_presentation;
			if (n.endsWith(".odp")) return R.drawable.ic_odp;
			
			
			// Spreadsheets (Excel, Numbers)
			if (n.endsWith(".xls")) return R.drawable.ic_xls;
			if (n.endsWith(".xlsx")) return R.drawable.ic_xlsx;
			if (n.endsWith(".xlr")) return R.drawable.ic_xlr;
			if (n.endsWith(".ods")) return R.drawable.ic_ods;		
			if (n.endsWith(".xlsm")) return R.drawable.ic_xlsm;
			if (n.endsWith(".xltm")) return R.drawable.ic_xltm;
			if (n.endsWith(".xlsb")) return R.drawable.ic_xlsb;
			if (n.endsWith(".xltx")) return R.drawable.ic_spreadsheet;
			if (n.endsWith(".xlw")) return R.drawable.ic_xlw;
			if (n.endsWith(".numbers")) return R.drawable.ic_numbers;
			
			// eBooks
			if (n.endsWith(".epub")) return R.drawable.ic_epub;
			if (n.endsWith(".mobi")) return R.drawable.ic_mobi;
			if (n.endsWith(".azw3")) return R.drawable.ic_azw3;
			if (n.endsWith(".djvu")) return R.drawable.ic_djvu;
			if (n.endsWith(".chm")) return R.drawable.ic_chm;
			if (n.endsWith(".wps")) return R.drawable.ic_wps; 
			
			
			// Database files
			if (n.endsWith(".db")) return R.drawable.ic_database;
			if (n.endsWith(".mdb")) return R.drawable.ic_mdb;
			if (n.endsWith(".sql")) return R.drawable.ic_sql;
			if (n.endsWith(".sqlite")) return R.drawable.ic_sqllite;
			
			// Security files
			if (n.endsWith(".key")) return R.drawable.ic_key_file;
			if (n.endsWith(".pem")) return R.drawable.ic_pem;
			if (n.endsWith(".cer")) return R.drawable.ic_cer;
            if (n.endsWith(".keystore")) return R.drawable.ic_lock_key;
			if (n.endsWith(".jks")) return R.drawable.ic_shield_lock_file;

			// font files
			if (n.endsWith(".ttf")) return R.drawable.ic_ttf;
			if (n.endsWith(".otf")) return R.drawable.ic_otf;   
			if (n.endsWith(".woff")) return R.drawable.ic_font; 
			if (n.endsWith(".woff2")) return R.drawable.ic_font; 
			if (n.endsWith(".eot")) return R.drawable.ic_font;   
			if (n.endsWith(".fon")) return R.drawable.ic_font;   
			if (n.endsWith(".fnt")) return R.drawable.ic_fnt;   
			if (n.endsWith(".ttc")) return R.drawable.ic_font;   
			
			// Executable / System files
			if (n.endsWith(".exe")) return R.drawable.ic_windows;
			if (n.endsWith(".msi")) return R.drawable.ic_msi;
			if (n.endsWith(".deb")) return R.drawable.ic_debian;
			if (n.endsWith(".rpm")) return R.drawable.ic_linux;
			if (n.endsWith(".dmg")) return R.drawable.ic_mach_os;
			if (n.endsWith(".bat")) return R.drawable.ic_bat;
			if (n.endsWith(".pkg")) return R.drawable.ic_pkg;
			if (n.endsWith(".dat")) return R.drawable.ic_dat;
			if (n.endsWith(".cmd")) return R.drawable.ic_terminal_cmd;
			
			// Default
			return R.drawable.ic_file;		
		}

		public static int getTintColorForFile(String fileName) {
			String n = fileName.toLowerCase();

			// Video files - MX Player / Video Blue (#2196F3)
			if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
				n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".3gp") ||
				n.endsWith(".flv") || n.endsWith(".wmv") || n.endsWith(".vob")) return 0xFF2196F3;

			// Audio files - Spotify / Music Green (#1DB954)
			if (n.endsWith(".mp3") || n.endsWith(".aac") || n.endsWith(".ogg") ||
				n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".m4a")) return 0xFF1DB954;

			// Image files - Gallery Orange (#FF9800)
			if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
				n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp") ||
				n.endsWith(".heic") || n.endsWith(".ico")) return 0xFFFF9800;

			// Photoshop & Illustrator (Original Branding)
			if (n.endsWith(".psd")) return 0xFF31A8FF; // Photoshop Blue
			if (n.endsWith(".ai")) return 0xFFFF9A00;  // Illustrator Amber/Orange
			if (n.endsWith(".cr2") || n.endsWith(".nef")) return 0xFFE91E63; // Camera RAW (Pink/Magenta)

			// SVG - Orange/Red (#FF5722)
			if (n.endsWith(".svg")) return 0xFFFF5722;

			// PDF - Adobe Adobe Red (#E53935)
			if (n.endsWith(".pdf")) return 0xFFE53935;

			// Archive files - WinRAR / Zip Amber (#FFC107)
			if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") ||
				n.endsWith(".tar") || n.endsWith(".gz") || n.endsWith(".gzip") ||
				n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".bz2") ||
				n.endsWith(".xz") || n.endsWith(".alz") || n.endsWith(".arc") ||
				n.endsWith(".iso")) return 0xFFFFC107;

			// APK / Android Apps - Android Green (#3DDC84)
			if (n.endsWith(".apk") || n.endsWith(".xapk") || n.endsWith(".aab")) return 0xFF3DDC84;

			// HTML / Web - HTML5 Orange (#E34F26)
			if (n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".xhtml")) return 0xFFE34F26;

			// JavaScript / TypeScript - Official Yellow/Blue
			if (n.endsWith(".js") || n.endsWith(".jsx")) return 0xFFF7DF1E; // JS Yellow
			if (n.endsWith(".ts") || n.endsWith(".tsx")) return 0xFF3178C6; // TS Blue

			// CSS - CSS3 Blue (#1572B6)
			if (n.endsWith(".css")) return 0xFF1572B6;

			// Text, Logs & Configs - Documents Grey (#607D8B)
			if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".log") || 
				n.endsWith(".ini") || n.endsWith(".config") || n.endsWith(".properties") ||
				n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".toml") || 
				n.endsWith(".env") || n.endsWith(".tex")) return 0xFF607D8B;

			// JSON / CSV - Teal (#009688)
			if (n.endsWith(".json") || n.endsWith(".csv")) return 0xFF009688;

			// --- Programming Languages (Original Brand Colors) ---
			if (n.endsWith(".java") || n.endsWith(".jar")) return 0xFF5382A1; // Java Corporate Blue
			if (n.endsWith(".py")) return 0xFF3776AB;   // Python Blue
			if (n.endsWith(".kt")) return 0xFF7F52FF;   // Kotlin Purple
			if (n.endsWith(".dart")) return 0xFF0175C2; // Dart Cyan/Blue
			if (n.endsWith(".swift")) return 0xFFF05138; // Swift Orange
			if (n.endsWith(".vue")) return 0xFF4FC08D;   // Vue Green
			if (n.endsWith(".go")) return 0xFF00ADD8;    // Go Cyan
			if (n.endsWith(".rb")) return 0xFFCC342D;    // Ruby Red
			if (n.endsWith(".git")) return 0xFFF05032;   // Git Orange/Red
			if (n.endsWith(".php")) return 0xFF777BB4;   // PHP Purple

			// Generic Code Files (C, C++, C#, Shell)
			if (n.endsWith(".xml") || n.endsWith(".jshtm") || n.endsWith(".rs")) return 0xFF00BCD4; // Cyan Code
			if (n.endsWith(".c") || n.endsWith(".h")) return 0xFFA8B9CC; // C Grey/Blue
			if (n.endsWith(".cpp")) return 0xFF00599C;  // C++ Standard Blue
			if (n.endsWith(".cs")) return 0xFF239120;   // C# Green
			if (n.endsWith(".sh")) return 0xFF4EAA25;   // Bash Green

			// --- Microsoft Office & Documents ---
			if (n.endsWith(".docx") || n.endsWith(".doc") || n.endsWith(".docm") || n.endsWith(".dotx")) 
				return 0xFF42A5F5; // Word Blue (বা একটু উজ্জ্বল নীল)

			if (n.endsWith(".xlsx") || n.endsWith(".xls") || n.endsWith(".xlsm") || n.endsWith(".xlsb") || 
				n.endsWith(".xlr") || n.endsWith(".xltm") || n.endsWith(".xltx") || n.endsWith(".xlw")) 
				return 0xFF107C41; // Excel Dark Green

			if (n.endsWith(".pptx") || n.endsWith(".ppt") || n.endsWith(".ppsx")) 
				return 0xFFB7472A; // PowerPoint Terracotta Red/Orange

			if (n.endsWith(".rtf") || n.endsWith(".odt") || n.endsWith(".fodt") || 
				n.endsWith(".wps") || n.endsWith(".pages") || n.endsWith(".key") || 
				n.endsWith(".numbers") || n.endsWith(".odp") || n.endsWith(".ods")) 
				return 0xFF78909C; // OpenOffice / Apple Documents (Neutral Blue-Grey)

			// eBooks - Kindle/Epub Purple (#673AB7)
			if (n.endsWith(".epub") || n.endsWith(".mobi") || n.endsWith(".azw3") || 
				n.endsWith(".djvu") || n.endsWith(".chm")) return 0xFF673AB7;

			// Database files - SQL/DB Dark Purple (#4527A0)
			if (n.endsWith(".db") || n.endsWith(".sqlite") || n.endsWith(".mdb") || n.endsWith(".sql")) return 0xFF4527A0;

			// Security files - Lock Red/Gold (#D32F2F)
			if (n.endsWith(".key") || n.endsWith(".pem") || n.endsWith(".jks") || n.endsWith(".keystore") || n.endsWith(".cer")) return 0xFFD32F2F;

			// Font files - Typography Slate/Deep Pink (#9C27B0)
			if (n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".woff") || 
				n.endsWith(".woff2") || n.endsWith(".eot") || n.endsWith(".fon") || 
				n.endsWith(".fnt") || n.endsWith(".ttc")) return 0xFF9C27B0;

			// Executable / System platforms
			if (n.endsWith(".exe") || n.endsWith(".msi") || n.endsWith(".bat") || n.endsWith(".cmd")) return 0xFF0078D7; // Windows Blue
			if (n.endsWith(".deb") || n.endsWith(".rpm")) return 0xFFD70A53; // Linux/Debian Red
			if (n.endsWith(".dmg") || n.endsWith(".pkg")) return 0xFF000000; // macOS Dark/Black
			if (n.endsWith(".dat")) return 0xFF455A64; // System Dat File (Dark Grey)

			// Default File Color - Slate Gold (#FFD700)
			return 0xFFFFD700;
		}	

        public static String formatSize(long bytes) {
            if (bytes < 1024)           return bytes + " B";
            if (bytes < 1024*1024)      return String.format("%.1f KB", bytes/1024.0);
            if (bytes < 1024L*1024*1024) return String.format("%.1f MB", bytes/(1024.0*1024));
            return String.format("%.2f GB", bytes/(1024.0*1024*1024));
        }
		

        @Override 
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvMeta;
            ImageView ivCheck;
            View btnEnter;
            
            VH(View v) {
                super(v);
                ivIcon   = v.findViewById(R.id.iv_file_icon);
                tvName   = v.findViewById(R.id.tv_file_name);
                tvMeta   = v.findViewById(R.id.tv_file_meta);
                ivCheck  = v.findViewById(R.id.iv_check);
                btnEnter = v.findViewById(R.id.btn_enter_folder);
            }
        }
    }

    static class BasketAdapter extends RecyclerView.Adapter<BasketAdapter.VH> {
        interface OnRemove { void remove(int i); }
        private final List<String> paths;
        private final OnRemove listener;

        BasketAdapter(List<String> paths, OnRemove l) {
            this.paths = paths; 
            this.listener = l;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_basket_chip, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            h.tvName.setText(new File(paths.get(pos)).getName());
            h.btnRemove.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.remove(p);
            });
        }

        @Override 
        public int getItemCount() { return paths.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName; 
            View btnRemove;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tv_chip_name);
                btnRemove = v.findViewById(R.id.btn_chip_remove);
            }
        }
    }
}
