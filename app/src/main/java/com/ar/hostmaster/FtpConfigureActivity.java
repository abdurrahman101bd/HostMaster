package com.ar.hostmaster;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;

public class FtpConfigureActivity extends AppCompatActivity {

    private static final int REQ_FOLDER = 1;
    // When true, jump straight into the folder picker after opening — used when
    // MainActivity sends the user here because no source was selected yet.
    public static final String EXTRA_AUTO_PICK_FOLDER = "auto_pick_folder";

    private AppState state;

    private LinearLayout pathDisplay;
    private TextView tvSelectedPath, tvInfo;
    private LinearLayout authFields;
    private Switch swAnonymous, swPassiveMode, swReadOnly;
    private EditText etUsername, etPassword, etPort;
    private ImageView btnTogglePassVis;
    private boolean passVisible = false;
    
    // For snackbar undo
    private String lastRemovedPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_ftp_configure);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        pathDisplay      = findViewById(R.id.path_display);
        tvSelectedPath   = findViewById(R.id.tv_selected_path);
        tvInfo           = findViewById(R.id.tv_info_text);
        authFields       = findViewById(R.id.auth_fields);
        swAnonymous      = findViewById(R.id.sw_anonymous);
        swPassiveMode    = findViewById(R.id.sw_passive_mode);
        swReadOnly       = findViewById(R.id.sw_read_only);
        etUsername       = findViewById(R.id.et_username);
        etPassword       = findViewById(R.id.et_password);
        etPort           = findViewById(R.id.et_port);
        btnTogglePassVis = findViewById(R.id.btn_toggle_pass_vis);

        // Restore saved values
        int port = state.getPort("FTP");
        etPort.setText(String.valueOf(port));
        
        String savedUsername = state.getUsername("FTP");
        String savedPassword = state.getPassword("FTP");
        boolean savedAnonymous = state.sp_bool("ftp_anonymous", false);
        
        // If username or password is empty, force anonymous ON
        if (savedUsername.isEmpty() || savedPassword.isEmpty()) {
            savedAnonymous = true;
            swAnonymous.setChecked(true);
            applyAnonymousState(true);
            etUsername.setText("");
            etPassword.setText("");
        } else {
            swAnonymous.setChecked(savedAnonymous);
            etUsername.setText(savedUsername);
            etPassword.setText(savedPassword);
            applyAnonymousState(savedAnonymous);
        }
        
        swPassiveMode.setChecked(state.sp_bool("ftp_passive", true));
        swReadOnly.setChecked(state.sp_bool("ftp_read_only", false));

        // Info card text — dynamic IP:PORT, bold connect URL
        setInfoText(port);

        String path = state.getFolderPath();
        if (!path.isEmpty()) showPath(path);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Select folder
        findViewById(R.id.row_select_folder).setOnClickListener(v -> openFolderPicker());

        // Clear selection - with Snackbar undo support
        findViewById(R.id.btn_clear_selection).setOnClickListener(v -> clearFolderSelection(true));

        if (getIntent().getBooleanExtra(EXTRA_AUTO_PICK_FOLDER, false)) {
            openFolderPicker();
        }

        // Anonymous toggle
        swAnonymous.setOnCheckedChangeListener((b, on) -> applyAnonymousState(on));

        // Update info text live when port changes
        etPort.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    int p = Integer.parseInt(s.toString().trim());
                    setInfoText(p);
                } catch (NumberFormatException ignored) {}
            }
        });

        // Password visibility toggle (icon)
        btnTogglePassVis.setOnClickListener(v -> {
            passVisible = !passVisible;
            etPassword.setTransformationMethod(passVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etPassword.setSelection(etPassword.getText().length());
            btnTogglePassVis.setImageResource(passVisible
                    ? R.drawable.ic_visibility_on
                    : R.drawable.ic_visibility_off);
        });

        // Save - auto toggle anonymous ON if username/password empty
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            boolean anonymous = swAnonymous.isChecked();
            
            // If username or password is empty, force anonymous ON
            if (username.isEmpty() || password.isEmpty()) {
                anonymous = true;
                swAnonymous.setChecked(true);
                applyAnonymousState(true);
                state.setUsername("FTP", "");
                state.setPassword("FTP", "");
            } else {
                state.setUsername("FTP", username);
                state.setPassword("FTP", password);
            }
            
            try {
                int p = Integer.parseInt(etPort.getText().toString().trim());
                state.setPort("FTP", p);
            } catch (NumberFormatException ignored) {}

            state.sp_set("ftp_anonymous", anonymous);
            state.sp_set("ftp_passive",   swPassiveMode.isChecked());
            state.sp_set("ftp_read_only", swReadOnly.isChecked());
            state.setPasswordEnabled("FTP", !anonymous);

            setResult(RESULT_OK);
            finish();
        });
    }

    private void setInfoText(int port) {
        String ip = NetworkUtil.getLocalIp(this);
        String connectUrl = "ftp://" + ip + ":" + port;
        String full = "Connect using any FTP client\n" + connectUrl;
    
        SpannableString ss = new SpannableString(full);
        int start = full.indexOf(connectUrl);
        if (start >= 0) {
            ss.setSpan(new StyleSpan(Typeface.BOLD), start, start + connectUrl.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvInfo.setText(ss);
    }

    /** When Anonymous login is ON, hide credential fields completely */
    private void applyAnonymousState(boolean anonymousOn) {
        if (anonymousOn) {
            // Anonymous ON - fields completely hidden (GONE)
            authFields.setVisibility(View.GONE);
            authFields.setAlpha(0.4f);
            etUsername.setEnabled(false);
            etPassword.setEnabled(false);
            btnTogglePassVis.setEnabled(false);
        } else {
            // Anonymous OFF - fields visible and active
            authFields.setVisibility(View.VISIBLE);
            authFields.setAlpha(1f);
            etUsername.setEnabled(true);
            etPassword.setEnabled(true);
            btnTogglePassVis.setEnabled(true);
        }
    }

    private void showPath(String path) {
        tvSelectedPath.setText(path);
        pathDisplay.setVisibility(View.VISIBLE);
    }
    
    private void hidePathDisplay() {
        pathDisplay.setVisibility(View.GONE);
    }
    
    private void openFolderPicker() {
        Intent i = new Intent(this, FilePickerActivity.class);
        i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
        startActivityForResult(i, REQ_FOLDER);
    }

    private void clearFolderSelection(boolean withUndo) {
        String currentPath = state.getFolderPath();
        if (currentPath.isEmpty()) return;
        
        lastRemovedPath = currentPath;
        state.setFolderPath("");
        state.setSourceMode("folder");
        hidePathDisplay();
        
        if (withUndo) {
            showUndoSnackbar("Folder selection removed", () -> {
                state.setFolderPath(lastRemovedPath);
                state.setSourceMode("folder");
                showPath(lastRemovedPath);
            });
        }
    }
    
    private void showUndoSnackbar(String message, Runnable undoAction) {
        View anchor = findViewById(R.id.snackbar_anchor);
        if (anchor == null) {
            anchor = findViewById(android.R.id.content);
        }
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> undoAction.run())
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FOLDER) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_PATH);
            if (path != null && !path.isEmpty()) {
                state.setFolderPath(path);
                state.setSourceMode("folder");
                showPath(path);
            }
        }
    }
}