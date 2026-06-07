package com.ar.hostmaster;

import android.graphics.PorterDuff;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.util.*;

public class ConfigureActivity extends AppCompatActivity {

    private static final int REQ_FOLDER     = 1;
    private static final int REQ_FILE       = 2;
    private static final int REQ_WEB_FOLDER = 3;

    private AppState state;

    // Tabs
    private LinearLayout tabSource, tabWebsite;
    private ImageView tabSourceIcon, tabWebsiteIcon;
    private TextView tabSourceText, tabWebsiteText;
    private View sectionSource, sectionWebsite;
    private boolean isWebMode = false;

    // Source preview
    private View     sourcePreviewBox;
    private TextView tvSourceEmptyHint, btnClearAllSource;
    private LinearLayout sourceItemsContainer;

    // Web preview
    private View     webPreviewBox;
    private TextView tvWebEmptyHint, btnClearWeb;
    private View     webFolderInfo;
    private TextView tvWebFolderName, tvWebFolderPath, btnRemoveWebFolder;
    private LinearLayout webFileTree;

    // Security
    private LinearLayout authFields;
    private Switch swPassword;
    private EditText etUsername, etPassword;
    private ImageView btnTogglePassVis;
    private boolean passVisible = false;

    // In-memory state for snackbar undo
    private String lastRemovedPath   = null;
    private boolean lastWasFolder    = false;
    private List<String> lastFiles   = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_configure);

        // Tabs
        tabSource    = findViewById(R.id.tab_source);
        tabWebsite   = findViewById(R.id.tab_website);
        tabSourceIcon = findViewById(R.id.img_tab_source_icon);
        tabWebsiteIcon = findViewById(R.id.img_tab_website_icon);
        tabSourceText = findViewById(R.id.tab_source_text);
        tabWebsiteText = findViewById(R.id.tab_website_text);
        
        sectionSource  = findViewById(R.id.section_source);
        sectionWebsite = findViewById(R.id.section_website);

        // Source preview
        sourcePreviewBox    = findViewById(R.id.source_preview_box);
        tvSourceEmptyHint   = findViewById(R.id.tv_source_empty_hint);
        btnClearAllSource   = findViewById(R.id.btn_clear_all_source);
        sourceItemsContainer = findViewById(R.id.source_items_container);

        // Web preview
        webPreviewBox       = findViewById(R.id.web_preview_box);
        tvWebEmptyHint      = findViewById(R.id.tv_web_empty_hint);
        btnClearWeb         = findViewById(R.id.btn_clear_web);
        webFolderInfo       = findViewById(R.id.web_folder_info);
        tvWebFolderName     = findViewById(R.id.tv_web_folder_name);
        tvWebFolderPath     = findViewById(R.id.tv_web_folder_path);
        btnRemoveWebFolder  = findViewById(R.id.btn_remove_web_folder);
        webFileTree         = findViewById(R.id.web_file_tree);

        // Security / options
        authFields  = findViewById(R.id.auth_fields);
        swPassword  = findViewById(R.id.sw_password);       
        etUsername  = findViewById(R.id.et_username);
        etPassword  = findViewById(R.id.et_password);
        btnTogglePassVis = findViewById(R.id.btn_toggle_pass_vis);

        // Restore saved state
        swPassword.setChecked(state.isPasswordEnabled());
        etUsername.setText(state.getUsername());
        etPassword.setText(state.getPassword());
        authFields.setVisibility(state.isPasswordEnabled() ? View.VISIBLE : View.GONE);

        // Restore active tab based on saved mode
        isWebMode = state.isWebMode();
        applyTabSelection(isWebMode);
        refreshSourcePreview();
        refreshWebPreview();

        // ── Clicks ────────────────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tabSource.setOnClickListener(v -> {
            isWebMode = false;
            applyTabSelection(false);
        });
        
        tabWebsite.setOnClickListener(v -> {
            isWebMode = true;
            applyTabSelection(true);
        });

        // Select folder (source)
        findViewById(R.id.row_select_folder).setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQ_FOLDER);
        });

        // Select files (source)
        findViewById(R.id.row_select_file).setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FILES);
            startActivityForResult(i, REQ_FILE);
        });

        // Clear all source
        btnClearAllSource.setOnClickListener(v -> clearAllSource(true));

        // Open WebHostActivity
        findViewById(R.id.row_open_web_host).setOnClickListener(v -> {
            startActivityForResult(
                    new Intent(this, WebHostActivity.class), REQ_WEB_FOLDER);
        });

        // Remove web folder
        btnRemoveWebFolder.setOnClickListener(v -> removeWebFolder(true));
        btnClearWeb.setOnClickListener(v -> removeWebFolder(true));

        // Password switch
        swPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            authFields.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Password visibility toggle with icon change
        updatePasswordVisibilityIcon();
        btnTogglePassVis.setOnClickListener(v -> {
            passVisible = !passVisible;
            if (passVisible) {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            etPassword.setSelection(etPassword.getText().length());
            updatePasswordVisibilityIcon();
        });

        // Save button
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            state.setPasswordEnabled(swPassword.isChecked());
            state.setUsername(etUsername.getText().toString().trim());
            state.setPassword(etPassword.getText().toString());
            
            // Persist active mode
            if (isWebMode && !state.getWebFolder().isEmpty()) {
                state.setSourceMode("web");
            } else if (!isWebMode) {
                // keep whatever source mode was set
            }
            
            setResult(RESULT_OK);
            finish();
        });
    }

    // ── Helper Methods ─────────────────────────────────────────────────────────

    private void updatePasswordVisibilityIcon() {
        if (passVisible) {
            btnTogglePassVis.setImageResource(R.drawable.ic_visibility_off);
        } else {
            btnTogglePassVis.setImageResource(R.drawable.ic_visibility_on);
        }
        btnTogglePassVis.setColorFilter(getResources().getColor(R.color.accent));
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private void applyTabSelection(boolean web) {
        if (web) {
            // Website Tab Active
            tabWebsite.setBackgroundResource(R.drawable.bg_tab_active);
            tabWebsiteIcon.setColorFilter(getResources().getColor(R.color.accent));
            tabWebsiteText.setTextColor(getResources().getColor(R.color.accent));
            
            // Source Tab Inactive
            tabSource.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            tabSourceIcon.setColorFilter(getResources().getColor(R.color.text_muted));
            tabSourceText.setTextColor(getResources().getColor(R.color.text_muted));
            
            sectionSource.setVisibility(View.GONE);
            sectionWebsite.setVisibility(View.VISIBLE);
        } else {
            // Source Tab Active
            tabSource.setBackgroundResource(R.drawable.bg_tab_active);
            tabSourceIcon.setColorFilter(getResources().getColor(R.color.accent));
            tabSourceText.setTextColor(getResources().getColor(R.color.accent));
            
            // Website Tab Inactive
            tabWebsite.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            tabWebsiteIcon.setColorFilter(getResources().getColor(R.color.text_muted));
            tabWebsiteText.setTextColor(getResources().getColor(R.color.text_muted));
            
            sectionSource.setVisibility(View.VISIBLE);
            sectionWebsite.setVisibility(View.GONE);
        }
    }

    // ── Source preview ────────────────────────────────────────────────────────

    private void refreshSourcePreview() {
        sourceItemsContainer.removeAllViews();
        String mode  = state.getSourceMode();
        boolean hasFolder = "folder".equals(mode) && !state.getFolderPath().isEmpty();
        boolean hasFiles  = "files".equals(mode)  && !state.getSelectedFiles().isEmpty();

        if (!hasFolder && !hasFiles) {
            tvSourceEmptyHint.setVisibility(View.VISIBLE);
            sourceItemsContainer.setVisibility(View.GONE);
            btnClearAllSource.setVisibility(View.GONE);
            return;
        }

        tvSourceEmptyHint.setVisibility(View.GONE);
        sourceItemsContainer.setVisibility(View.VISIBLE);
        btnClearAllSource.setVisibility(View.VISIBLE);

        if (hasFolder) {
            addSourceChip(state.getFolderPath(), "📁", true);
        } else {
            for (String p : state.getSelectedFiles()) {
                addSourceChip(p, "📄", false);
            }
        }
    }

    private void addSourceChip(String path, String icon, boolean isFolder) {
        View chip = getLayoutInflater().inflate(R.layout.item_source_chip, sourceItemsContainer, false);
        TextView tvIcon = chip.findViewById(R.id.tv_chip_icon);
        TextView tvName = chip.findViewById(R.id.tv_chip_name);
        TextView tvMeta = chip.findViewById(R.id.tv_chip_meta);
        TextView btnX   = chip.findViewById(R.id.btn_chip_remove);

        tvIcon.setText(icon);
        tvName.setText(new File(path).getName());
        tvMeta.setText(path);

        btnX.setOnClickListener(v -> {
            if (isFolder) {
                removeSourceFolder(path, true);
            } else {
                removeSourceFile(path, true);
            }
        });

        sourceItemsContainer.addView(chip);
    }

    private void removeSourceFolder(String path, boolean withUndo) {
        lastRemovedPath = path;
        lastWasFolder   = true;
        lastFiles       = null;
        state.setFolderPath("");
        state.setSourceMode("folder");
        refreshSourcePreview();
        if (withUndo) showUndoSnackbar("Folder removed", () -> {
            state.setFolderPath(lastRemovedPath);
            state.setSourceMode("folder");
            refreshSourcePreview();
        });
    }

    private void removeSourceFile(String path, boolean withUndo) {
        List<String> files = state.getSelectedFiles();
        lastFiles = new ArrayList<>(files);
        files.remove(path);
        state.setSelectedFiles(files);
        if (files.isEmpty()) state.setSourceMode("folder");
        else state.setFolderPath(files.get(0));
        refreshSourcePreview();
        if (withUndo) showUndoSnackbar("File removed", () -> {
            state.setSelectedFiles(lastFiles);
            if (!lastFiles.isEmpty()) {
                state.setSourceMode("files");
                state.setFolderPath(lastFiles.get(0));
            }
            refreshSourcePreview();
        });
    }

    private void clearAllSource(boolean withUndo) {
        lastRemovedPath = state.getFolderPath();
        lastWasFolder   = !"files".equals(state.getSourceMode());
        lastFiles       = new ArrayList<>(state.getSelectedFiles());
        state.setFolderPath("");
        state.setSelectedFiles(null);
        state.setSourceMode("folder");
        refreshSourcePreview();
        if (withUndo) showUndoSnackbar("Selection cleared", () -> {
            if (lastWasFolder && lastRemovedPath != null && !lastRemovedPath.isEmpty()) {
                state.setFolderPath(lastRemovedPath);
                state.setSourceMode("folder");
            } else if (!lastWasFolder && lastFiles != null && !lastFiles.isEmpty()) {
                state.setSelectedFiles(lastFiles);
                state.setSourceMode("files");
                state.setFolderPath(lastFiles.get(0));
            }
            refreshSourcePreview();
        });
    }

    // ── Web preview ───────────────────────────────────────────────────────────

    private void refreshWebPreview() {
        String webPath = state.getWebFolder();
        if (webPath.isEmpty()) {
            tvWebEmptyHint.setVisibility(View.VISIBLE);
            webFolderInfo.setVisibility(View.GONE);
            btnClearWeb.setVisibility(View.GONE);
            return;
        }

        tvWebEmptyHint.setVisibility(View.GONE);
        webFolderInfo.setVisibility(View.VISIBLE);
        btnClearWeb.setVisibility(View.VISIBLE);

        File webDir = new File(webPath);
        tvWebFolderName.setText(webDir.getName());
        tvWebFolderPath.setText(webPath);

        // Build file tree (max 20 items to avoid overflow)
        buildFileTree(webDir, webFileTree, 0, 0);
    }

private int buildFileTree(File dir, LinearLayout container, int depth, int count) {
    container.removeAllViews();
    File[] all = dir.listFiles();
    if (all == null) return count;
    
    Arrays.sort(all, (a, b) -> {
        if (a.isDirectory() != b.isDirectory())
            return a.isDirectory() ? -1 : 1;
        return a.getName().compareToIgnoreCase(b.getName());
    });
    
    for (File f : all) {
        if (count >= 20) {
            addTreeMoreHint(container, "…more files");
            break;
        }
        View row = getLayoutInflater().inflate(R.layout.item_tree_entry, container, false);
        
        ImageView ivTreeIcon = row.findViewById(R.id.iv_tree_icon); 
        TextView tvTreeName = row.findViewById(R.id.tv_tree_name);
        row.setPadding(depth * 16, 0, 0, 0);
        
        if (f.isDirectory()) {
            ivTreeIcon.setImageResource(R.drawable.ic_folder);
            ivTreeIcon.setColorFilter(getResources().getColor(R.color.accent), PorterDuff.Mode.SRC_IN);
        } else {
            int iconRes = FilePickerActivity.FileEntryAdapter.getIconResForFile(f.getName());
            ivTreeIcon.setImageResource(iconRes);
            ivTreeIcon.setColorFilter(null);
        }
        tvTreeName.setText(f.getName());
        container.addView(row);
        count++;
    }
    return count;
}

    private void addTreeMoreHint(LinearLayout container, String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(getResources().getColor(R.color.text_muted));
        tv.setTextSize(10);
        container.addView(tv);
    }

    private void removeWebFolder(boolean withUndo) {
        String old = state.getWebFolder();
        state.setWebFolder("");
        if (state.isWebMode()) state.setSourceMode("folder");
        refreshWebPreview();
        if (withUndo) showUndoSnackbar("Web folder removed", () -> {
            state.setWebFolder(old);
            state.setSourceMode("web");
            refreshWebPreview();
        });
    }

    // ── Snackbar undo ─────────────────────────────────────────────────────────

    private void showUndoSnackbar(String msg, Runnable undoAction) {
        View anchor = findViewById(R.id.snackbar_anchor);
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> undoAction.run())
                .show();
    }

    // ── Activity results ──────────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        if (req == REQ_FOLDER) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_PATH);
            if (path != null && !path.isEmpty()) {
                state.setFolderPath(path);
                state.setSourceMode("folder");
                state.setSelectedFiles(null);
                refreshSourcePreview();
            }

        } else if (req == REQ_FILE) {
            ArrayList<String> paths =
                    data.getStringArrayListExtra(FilePickerActivity.RESULT_PATHS);
            if (paths != null && !paths.isEmpty()) {
                state.setSelectedFiles(paths);
                state.setFolderPath(paths.get(0));
                state.setSourceMode("files");
                refreshSourcePreview();
            }

        } else if (req == REQ_WEB_FOLDER) {
            String path = data.getStringExtra("web_folder");
            if (path != null && !path.isEmpty()) {
                state.setWebFolder(path);
                state.setSourceMode("web");
                state.setFolderPath(path);
                refreshWebPreview();
            }
        }
    }
}