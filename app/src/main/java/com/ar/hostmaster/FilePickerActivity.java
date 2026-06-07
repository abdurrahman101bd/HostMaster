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
            btnConfirmFolder.setEnabled(false);
            btnConfirmFolder.setAlpha(0.4f);
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
            if (selectedFolder == null) return;
            Intent r = new Intent();
            r.putExtra(RESULT_PATH, selectedFolder.getAbsolutePath());
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
        if (parent != null &&
                !currentDir.getAbsolutePath()
                        .equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            navigate(parent);
        } else {
            finish();
        }
    }

    @Override public void onBackPressed() { goUp(); }

    private void selectFolder(File dir) {
        selectedFolder = dir;
        refreshFolderConfirm();
    }

    private void refreshFolderConfirm() {
        if (selectedFolder != null) {
            btnConfirmFolder.setText("✓  " + selectedFolder.getName());
            btnConfirmFolder.setEnabled(true);
            btnConfirmFolder.setAlpha(1f);
        } else {
            btnConfirmFolder.setText("TAP A FOLDER TO SELECT");
            btnConfirmFolder.setEnabled(false);
            btnConfirmFolder.setAlpha(0.4f);
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

    // ─────────────────────────────────────────────────────────────────────────
    // FileEntryAdapter with ImageView instead of TextView for icons
    // ─────────────────────────────────────────────────────────────────────────

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
                h.ivIcon.setImageResource(iconRes);
                h.ivIcon.setColorFilter(null);
                h.tvMeta.setText(formatSize(e.file.length()));
                h.btnEnter.setVisibility(View.GONE);
                boolean sel = basket.contains(e.file.getAbsolutePath());
                h.ivCheck.setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
            }

            h.itemView.setOnClickListener(v -> listener.tap(e));
        }

        public static int getIconResForFile(String fileName) {
            String n = fileName.toLowerCase();
            if (n.endsWith(".mp4")||n.endsWith(".mkv")||n.endsWith(".avi")||
                n.endsWith(".mov")||n.endsWith(".webm")||n.endsWith(".3gp")) return R.drawable.ic_video;
            if (n.endsWith(".mp3")||n.endsWith(".aac")||n.endsWith(".ogg")||
                n.endsWith(".flac")||n.endsWith(".wav")||n.endsWith(".m4a")) return R.drawable.ic_music;
            if (n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||
                n.endsWith(".gif")||n.endsWith(".webp")||n.endsWith(".bmp")||
                n.endsWith(".heic")) return R.drawable.ic_image;
            if (n.endsWith(".svg")) return R.drawable.ic_svg;    
            if (n.endsWith(".pdf"))  return R.drawable.ic_pdf;
            if (n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".7z")||
                n.endsWith(".tar")||n.endsWith(".gz")) return R.drawable.ic_zip;
            if (n.endsWith(".apk")||n.endsWith(".xapk")) return R.drawable.ic_apk;
            if (n.endsWith(".html")||n.endsWith(".htm")) return R.drawable.ic_html;
            if (n.endsWith(".js"))   return R.drawable.ic_javascript;
            if (n.endsWith(".css"))  return R.drawable.ic_css;
            if (n.endsWith(".txt")) return R.drawable.ic_text;
            if (n.endsWith(".md")) return R.drawable.ic_markdown;
            if (n.endsWith(".log")) return R.drawable.ic_logs;
            if (n.endsWith(".json")) return R.drawable.ic_json;
            if (n.endsWith(".csv")) return R.drawable.ic_spreadsheet;
            if (n.endsWith(".java")) return R.drawable.ic_java;
            if (n.endsWith(".py")) return R.drawable.ic_python;
            if (n.endsWith(".git")) return R.drawable.ic_git;
            if (n.endsWith(".php")) return R.drawable.ic_php;
            
            if (n.endsWith(".xml")||n.endsWith(".kt")||
                n.endsWith(".sh")||n.endsWith(".cpp")||n.endsWith(".c")) return R.drawable.ic_code;
            return R.drawable.ic_file;
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

    // ─────────────────────────────────────────────────────────────────────────
    // BasketAdapter
    // ─────────────────────────────────────────────────────────────────────────

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