package com.ar.hostmaster;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable; 
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.*;
import java.util.*;


public class WebHostActivity extends AppCompatActivity {

    private static final int REQ_ADD_FOLDER = 1;

    private AppState state;
    private RecyclerView rvFolders;
    private TextView tvEmpty;
    private WebFolderAdapter adapter;
    private List<String> folderList  = new ArrayList<>();
    private Set<String>  pinnedPaths = new LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_web_host);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        rvFolders = findViewById(R.id.rv_web_folders);
        tvEmpty   = findViewById(R.id.tv_web_empty);
        rvFolders.setLayoutManager(new LinearLayoutManager(this));

        folderList  = new ArrayList<>(state.getWebFolderList());
        pinnedPaths = new LinkedHashSet<>(state.getWebPinnedFolders());

        buildAdapter();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        FloatingActionButton fab = findViewById(R.id.fab_add_folder);
        fab.setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQ_ADD_FOLDER);
        });
    }

    private void buildAdapter() {
        adapter = new WebFolderAdapter(
                folderList, pinnedPaths,
                path -> { // Start
                    state.setWebFolder(path);
                    state.setSourceMode("web");
                    state.setFolderPath(path);
                    Intent r = new Intent();
                    r.putExtra("web_folder", path);
                    setResult(RESULT_OK, r);
                    finish();
                },
                path -> { // Remove
                    folderList.remove(path);
                    pinnedPaths.remove(path);
                    saveLists();
                    adapter.notifyDataSetChanged();
                    refreshEmpty();
                    if (path.equals(state.getWebFolder())) {
                        state.setWebFolder("");
                        if (state.isWebMode()) state.setSourceMode("folder");
                    }
                },
                path -> { // Pin / Unpin
                    if (pinnedPaths.contains(path)) {
                        pinnedPaths.remove(path);
                    } else {
                        pinnedPaths.add(path);
                    }
                    saveLists();
                    adapter.notifyDataSetChanged();
                },
                path -> showTreeDialog(path, false), // Info
                () -> state.getWebFolder()
        );
        rvFolders.setAdapter(adapter);
        refreshEmpty();
    }

    private void saveLists() {
        state.setWebFolderList(folderList);
        state.setWebPinnedFolders(new ArrayList<>(pinnedPaths));
    }

    private void refreshEmpty() {
        tvEmpty.setVisibility(folderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvFolders.setVisibility(folderList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Tree view dialog ──────────────────────────────────────────────────────

    private void showTreeDialog(String path, boolean fullscreen) {
        File dir = new File(path);

        Dialog dialog = new Dialog(this, fullscreen
                ? android.R.style.Theme_Black_NoTitleBar_Fullscreen
                : android.R.style.Theme_DeviceDefault_Light_Dialog);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View root = getLayoutInflater().inflate(R.layout.dialog_tree_view, null);
        dialog.setContentView(root);

        if (dialog.getWindow() != null) {
            if (fullscreen) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(getResources().getColor(R.color.bg, getTheme())));
            } else {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
                dialog.getWindow().setLayout(
                        (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        // Header
        TextView tvTitle  = root.findViewById(R.id.tv_tree_title);
        ImageView btnFull = root.findViewById(R.id.btn_tree_fullscreen);
        ImageView btnClose = root.findViewById(R.id.btn_tree_close);
        LinearLayout treeContainer = root.findViewById(R.id.tree_container);

        tvTitle.setText(fullscreen ? "File Tree — " + dir.getName() : "File Tree View");
        btnFull.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        btnClose.setVisibility(fullscreen ? View.VISIBLE : View.GONE);

        if (!fullscreen) {
            btnFull.setImageResource(R.drawable.ic_maximize);
            btnFull.setOnClickListener(v -> {
                dialog.dismiss();
                showTreeDialog(path, true);
            });
        } else {
            btnClose.setImageResource(R.drawable.ic_compress);
            btnClose.setOnClickListener(v -> {
                dialog.dismiss();
                showTreeDialog(path, false);
            });
        }

        // Build expandable tree
        buildTreeView(treeContainer, dir, 0);

        dialog.show();
    }

    /**
     * Recursively builds an expandable tree view.
     * Folders are collapsed by default; tap to expand/collapse.
     */
    private void buildTreeView(LinearLayout container, File dir, int depth) {
        File[] all = dir.listFiles();
        if (all == null) return;
        Arrays.sort(all, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : all) {
            // Row view
            View row = getLayoutInflater().inflate(R.layout.item_tree_entry, container, false);
            ImageView ivIcon = row.findViewById(R.id.iv_tree_icon);
            TextView  tvName = row.findViewById(R.id.tv_tree_name);
            ImageView ivArrow = row.findViewById(R.id.iv_tree_arrow);

            // Indent
            int indentDp = depth * 20;
            int indentPx = (int)(indentDp * getResources().getDisplayMetrics().density);
            row.setPadding(row.getPaddingLeft() + indentPx,
                    row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());

            tvName.setText(f.getName());

            if (f.isDirectory()) {
                ivIcon.setImageResource(R.drawable.ic_folder);
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
                ivArrow.setVisibility(View.VISIBLE);
                ivArrow.setRotation(0f); // collapsed = 0°

                // Child container (hidden by default)
                LinearLayout childContainer = new LinearLayout(this);
                childContainer.setOrientation(LinearLayout.VERTICAL);
                childContainer.setVisibility(View.GONE);

                container.addView(row);
                container.addView(childContainer);

                final boolean[] expanded = {false};
                row.setOnClickListener(v -> {
                    expanded[0] = !expanded[0];
                    if (expanded[0]) {
                        if (childContainer.getChildCount() == 0) {
                            buildTreeView(childContainer, f, depth + 1);
                        }
                        childContainer.setVisibility(View.VISIBLE);
                        ivArrow.animate().rotation(90f).setDuration(200).start();
                    } else {
                        childContainer.setVisibility(View.GONE);
                        ivArrow.animate().rotation(0f).setDuration(200).start();
                    }
                });
            } else {
                // File
                int iconRes = FilePickerActivity.FileEntryAdapter.getIconResForFile(f.getName());
                int tint    = FilePickerActivity.FileEntryAdapter.getTintColorForFile(f.getName());
                ivIcon.setImageResource(iconRes);
                ivIcon.setColorFilter(tint);
                ivArrow.setVisibility(View.GONE);
                tvName.setTextColor(getResources().getColor(R.color.text_muted, getTheme()));

                // File size
                String size = formatSize(f.length());
                tvName.setText(f.getName() + "  (" + size + ")");

                container.addView(row);
            }
        }
    }

    private String formatSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024*1024) return String.format(Locale.US, "%.1f KB", b/1024.0);
        return String.format(Locale.US, "%.1f MB", b/(1024.0*1024));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_ADD_FOLDER) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_PATH);
            if (path != null && !path.isEmpty() && !folderList.contains(path)) {
                folderList.add(0, path);
                saveLists();
                adapter.notifyDataSetChanged();   
                rvFolders.scrollToPosition(0);
                refreshEmpty();
            }
        }
    }

    // ── WebFolderAdapter ──────────────────────────────────────────────────────

    static class WebFolderAdapter extends RecyclerView.Adapter<WebFolderAdapter.VH> {

        interface OnStart        { void start(String path); }
        interface OnRemove       { void remove(String path); }
        interface OnPin          { void pin(String path); }
        interface OnInfo         { void info(String path); }
        interface ActiveGetter   { String get(); }

        private final List<String> data;
        private final Set<String>  pinnedPaths;
        private final OnStart    onStart;
        private final OnRemove   onRemove;
        private final OnPin      onPin;
        private final OnInfo     onInfo;
        private final ActiveGetter activeGetter;

        WebFolderAdapter(List<String> data, Set<String> pinned,
                         OnStart s, OnRemove r, OnPin p, OnInfo i, ActiveGetter ag) {
            this.data         = data;
            this.pinnedPaths  = pinned;
            this.onStart      = s;
            this.onRemove     = r;
            this.onPin        = p;
            this.onInfo       = i;
            this.activeGetter = ag;
        }

        @Override
        public int getItemCount() { return data.size(); }

        // Pinned items first
        private List<String> sortedData() {
            List<String> pinned   = new ArrayList<>();
            List<String> unpinned = new ArrayList<>();
            for (String p : data) {
                if (pinnedPaths.contains(p)) pinned.add(p);
                else                         unpinned.add(p);
            }
            String active = activeGetter.get();
            if (active != null) {
                if (pinnedPaths.contains(active)) {
                     pinned.remove(active);
                     pinned.add(0, active);
                } else if (unpinned.contains(active)) {
                    unpinned.remove(active);
                    unpinned.add(0, active);
                }
            }
            List<String> result = new ArrayList<>(pinned);
            result.addAll(unpinned);
            return result;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_web_folder, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            List<String> sorted = sortedData();
            String path = sorted.get(pos);
            File dir    = new File(path);
            boolean isPinned = pinnedPaths.contains(path);
            boolean active   = path.equals(activeGetter.get());

            if (isPinned) {
                h.ivIcon.setImageResource(R.drawable.ic_pin);
                h.ivIcon.setColorFilter(h.itemView.getContext().getResources().getColor(R.color.accent));
            } else if (active) {
                h.ivIcon.setImageResource(R.drawable.ic_folder);
                h.ivIcon.setColorFilter(h.itemView.getContext().getResources().getColor(R.color.col_green));
            } else {
                h.ivIcon.setImageResource(R.drawable.ic_folder);
                h.ivIcon.setColorFilter(h.itemView.getContext().getResources().getColor(R.color.text_muted));
            }

            if (isPinned) {
                h.tvName.setTextColor(h.itemView.getContext().getResources().getColor(R.color.accent));
            } else if (active) {
                h.tvName.setTextColor(h.itemView.getContext().getResources().getColor(R.color.col_green));
            } else {
                h.tvName.setTextColor(h.itemView.getContext().getResources().getColor(R.color.text_primary));
            }

            h.tvName.setText(dir.getName());
            h.tvPath.setText(path);
            h.tvActive.setVisibility(active ? View.VISIBLE : View.GONE);

            // Section label: show "PINNED" divider before first pinned item
            if (pos == 0 && isPinned) {
                h.tvSectionLabel.setVisibility(View.VISIBLE);
                h.tvSectionLabel.setText("PINNED WEBSITE FOLDER");
            } else if (!isPinned && pos > 0 && pinnedPaths.contains(sorted.get(pos - 1))) {
                h.tvSectionLabel.setVisibility(View.VISIBLE);
                h.tvSectionLabel.setText("WEBSITE FOLDERS");
            } else if (pos == 0 && !isPinned) {
                h.tvSectionLabel.setVisibility(View.VISIBLE);
                h.tvSectionLabel.setText("WEBSITE FOLDERS");
            } else {
                h.tvSectionLabel.setVisibility(View.GONE);
            }

            File[] children  = dir.listFiles();
            int    cnt       = children != null ? children.length : 0;
            boolean hasIndex = new File(dir, "index.html").exists()
                    || new File(dir, "index.htm").exists();
            h.tvCount.setText(cnt + " items");
            h.indexIndicator.setVisibility(hasIndex ? View.VISIBLE : View.GONE);

            // 3-dot popup menu with icons
            h.btnMenu.setOnClickListener(v -> {
				PopupMenu popup = new PopupMenu(h.itemView.getContext(), h.btnMenu);
				Menu menu = popup.getMenu();
				Context context = h.itemView.getContext();

				// Start 
				String startTitle = "Start";
				SpannableString spannableStart = new SpannableString(startTitle);
				spannableStart.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.col_green)), 
									   0, startTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				MenuItem miStart = menu.add(0, 1, 0, spannableStart);
				Drawable playIcon = ContextCompat.getDrawable(context, R.drawable.ic_play);
				if (playIcon != null) {
					playIcon.setTint(ContextCompat.getColor(context, R.color.col_green));
					miStart.setIcon(playIcon);
				}

				// File Tree View 
				String infoTitle = "File Tree View";
				SpannableString spannableInfo = new SpannableString(infoTitle);
				spannableInfo.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.col_yellow)), 
									  0, infoTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				MenuItem miInfo = menu.add(0, 2, 1, spannableInfo);
				Drawable infoIcon = ContextCompat.getDrawable(context, R.drawable.ic_information);
				if (infoIcon != null) {
					infoIcon.setTint(ContextCompat.getColor(context, R.color.col_yellow));
					miInfo.setIcon(infoIcon);
				}

				// Pin/Unpin 
				String pinTitle = isPinned ? "Unpin" : "Pin to top";
				SpannableString spannablePin = new SpannableString(pinTitle);
				spannablePin.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent)), 
									 0, pinTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				MenuItem miPin = menu.add(0, 3, 2, spannablePin);
				Drawable pinIcon = ContextCompat.getDrawable(context, R.drawable.ic_pin);
				if (pinIcon != null) {
					pinIcon.setTint(ContextCompat.getColor(context, R.color.accent));
					miPin.setIcon(pinIcon);
				}

				// Remove 
				String removeTitle = "Remove";
				SpannableString spannableRemove = new SpannableString(removeTitle);
				spannableRemove.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.col_red2)), 
										0, removeTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				MenuItem miRemove = menu.add(0, 4, 3, spannableRemove);
				Drawable removeIcon = ContextCompat.getDrawable(context, R.drawable.ic_close);
				if (removeIcon != null) {
					removeIcon.setTint(ContextCompat.getColor(context, R.color.col_red2));
					miRemove.setIcon(removeIcon);
				}

				popup.setForceShowIcon(true);
				popup.setOnMenuItemClickListener(item -> {
					switch (item.getItemId()) {
						case 1: onStart.start(path);  return true;
						case 2: onInfo.info(path);    return true;
						case 3: onPin.pin(path);      return true;
						case 4: onRemove.remove(path);return true;
					}
					return false;
				});
				popup.show();
			});
            
            h.itemView.setOnClickListener(v -> onStart.start(path));
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvSectionLabel, tvName, tvPath, tvCount, tvActive;
            ImageView ivIcon, btnMenu;
            LinearLayout indexIndicator;
            VH(View v) {
                super(v);
                tvSectionLabel = v.findViewById(R.id.tv_section_label);
                tvName         = v.findViewById(R.id.tv_wf_name);
                tvPath         = v.findViewById(R.id.tv_wf_path);
                tvCount        = v.findViewById(R.id.tv_wf_count);
                tvActive       = v.findViewById(R.id.tv_wf_active);
                ivIcon         = v.findViewById(R.id.iv_wf_icon);
                btnMenu        = v.findViewById(R.id.btn_wf_menu);
                indexIndicator = v.findViewById(R.id.index_indicator);
            }
        }
    }
}
