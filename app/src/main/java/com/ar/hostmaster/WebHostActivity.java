package com.ar.hostmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.*;
import java.util.*;

/**
 * WebHostActivity
 * Shows a list of saved web folders (from AppState.getWebFolderList()).
 * FAB → FilePickerActivity (folder mode) → adds to list.
 * Each item has a 3-dot popup menu: Start (confirm + return), Rename, Info, Remove.
 * Tapping "Start" confirms that folder as the active web host and returns to ConfigureActivity.
 */
public class WebHostActivity extends AppCompatActivity {

    private static final int REQ_ADD_FOLDER = 1;

    private AppState state;
    private RecyclerView rvFolders;
    private TextView tvEmpty;
    private WebFolderAdapter adapter;
    private List<String> folderList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_web_host);

        rvFolders = findViewById(R.id.rv_web_folders);
        tvEmpty   = findViewById(R.id.tv_web_empty);

        rvFolders.setLayoutManager(new LinearLayoutManager(this));

        folderList = new ArrayList<>(state.getWebFolderList());
        buildAdapter();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // FAB — add a new web folder
        FloatingActionButton fab = findViewById(R.id.fab_add_folder);
        fab.setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQ_ADD_FOLDER);
        });
    }

    private void buildAdapter() {
        adapter = new WebFolderAdapter(folderList,
                // On Start: set this folder as active web host, return to ConfigureActivity
                path -> {
                    state.setWebFolder(path);
                    state.setSourceMode("web");
                    state.setFolderPath(path);
                    Intent result = new Intent();
                    result.putExtra("web_folder", path);
                    setResult(RESULT_OK, result);
                    finish();
                },
                // On Remove
                path -> {
                    folderList.remove(path);
                    state.setWebFolderList(folderList);
                    adapter.notifyDataSetChanged();
                    refreshEmpty();
                    // If removed folder was active, clear
                    if (path.equals(state.getWebFolder())) {
                        state.setWebFolder("");
                        if (state.isWebMode()) state.setSourceMode("folder");
                    }
                },
                // On Rename
                (path, newName) -> {
                    // Rename is just updating the display alias stored alongside path
                    // For simplicity: re-path not possible on Android without root.
                    // Show toast explaining limitation.
                    Toast.makeText(this,
                            "Rename not supported: folder is at " + path,
                            Toast.LENGTH_LONG).show();
                },
                // On Info
                path -> showInfoDialog(path),
                // Active folder getter
                () -> state.getWebFolder()
        );
        rvFolders.setAdapter(adapter);
        refreshEmpty();
    }

    private void refreshEmpty() {
        tvEmpty.setVisibility(folderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvFolders.setVisibility(folderList.isEmpty() ? View.GONE   : View.VISIBLE);
    }

    private void showInfoDialog(String path) {
        File dir = new File(path);
        StringBuilder sb = new StringBuilder();
        sb.append("📁 ").append(dir.getName()).append("\n");
        sb.append("Path: ").append(path).append("\n\n");
        sb.append("Contents:\n");
        appendTree(dir, sb, 0, new int[]{0});

        new android.app.AlertDialog.Builder(this)
                .setTitle("Folder Info")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void appendTree(File dir, StringBuilder sb, int depth, int[] count) {
        File[] all = dir.listFiles();
        if (all == null) return;
        Arrays.sort(all, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : all) {
            if (count[0] > 30) { sb.append("  …more\n"); return; }
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(f.isDirectory() ? "📁 " : "  ").append(f.getName());
            if (!f.isDirectory()) {
                sb.append("  (").append(FilePickerActivity.FileEntryAdapter.formatSize(f.length())).append(")");
            }
            sb.append("\n");
            count[0]++;
            if (f.isDirectory() && depth < 2) appendTree(f, sb, depth + 1, count);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_ADD_FOLDER) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_PATH);
            if (path != null && !path.isEmpty() && !folderList.contains(path)) {
                folderList.add(0, path); // newest on top
                state.setWebFolderList(folderList);
                adapter.notifyItemInserted(0);
                rvFolders.scrollToPosition(0);
                refreshEmpty();
            }
        }
    }

    // ── WebFolderAdapter ──────────────────────────────────────────────────────

    static class WebFolderAdapter extends RecyclerView.Adapter<WebFolderAdapter.VH> {

        interface OnStart  { void start(String path); }
        interface OnRemove { void remove(String path); }
        interface OnRename { void rename(String path, String newName); }
        interface OnInfo   { void info(String path); }
        interface ActiveGetter { String get(); }

        private final List<String> data;
        private final OnStart  onStart;
        private final OnRemove onRemove;
        private final OnRename onRename;
        private final OnInfo   onInfo;
        private final ActiveGetter activeGetter;

        WebFolderAdapter(List<String> data, OnStart s, OnRemove r,
                         OnRename rn, OnInfo i, ActiveGetter ag) {
            this.data = data; this.onStart = s; this.onRemove = r;
            this.onRename = rn; this.onInfo = i; this.activeGetter = ag;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_web_folder, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            String path = data.get(pos);
            File dir    = new File(path);
            h.tvName.setText(dir.getName());
            h.tvPath.setText(path);

            // Active indicator
            boolean active = path.equals(activeGetter.get());
            h.tvActive.setVisibility(active ? View.VISIBLE : View.GONE);
            h.tvName.setTextColor(active
                    ? h.itemView.getContext().getResources().getColor(R.color.accent)
                    : h.itemView.getContext().getResources().getColor(R.color.text_primary));

            // File count
            File[] children = dir.listFiles();
            int cnt = children != null ? children.length : 0;
            h.tvCount.setText(cnt + " items");

            // Check for index.html
            boolean hasIndex = new File(dir, "index.html").exists()
                    || new File(dir, "index.htm").exists();
            h.tvIndex.setVisibility(hasIndex ? View.VISIBLE : View.GONE);

            // 3-dot menu
            h.btnMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(h.itemView.getContext(), h.btnMenu);
                popup.getMenu().add(0, 1, 0, "▶  Start");
                popup.getMenu().add(0, 2, 1, "ℹ  Info");
                popup.getMenu().add(0, 3, 2, "✎  Rename");
                popup.getMenu().add(0, 4, 3, "✕  Remove");
                popup.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case 1: onStart.start(path);        return true;
                        case 2: onInfo.info(path);          return true;
                        case 3: onRename.rename(path, "");  return true;
                        case 4: onRemove.remove(path);      return true;
                    }
                    return false;
                });
                popup.show();
            });

            // Tap row = start
            h.itemView.setOnClickListener(v -> onStart.start(path));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPath, tvCount, tvActive, tvIndex;
            View btnMenu;
            VH(View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_wf_name);
                tvPath   = v.findViewById(R.id.tv_wf_path);
                tvCount  = v.findViewById(R.id.tv_wf_count);
                tvActive = v.findViewById(R.id.tv_wf_active);
                tvIndex  = v.findViewById(R.id.tv_wf_index);
                btnMenu  = v.findViewById(R.id.btn_wf_menu);
            }
        }
    }
}
