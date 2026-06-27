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

public class SftpConfigureActivity extends AppCompatActivity {

    private static final int REQ_FOLDER = 1;

    private AppState state;

    private LinearLayout pathDisplay, authFields;
    private TextView tvSelectedPath, tvInfo;
    private Switch swAnonymous, swReadOnly;
    private EditText etUsername, etPassword, etPort;
    private ImageView btnTogglePassVis;
    private boolean passVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_sftp_configure);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        pathDisplay      = findViewById(R.id.path_display);
        tvSelectedPath   = findViewById(R.id.tv_selected_path);
        tvInfo           = findViewById(R.id.tv_info_text);
        authFields       = findViewById(R.id.auth_fields);
        swAnonymous      = findViewById(R.id.sw_anonymous);
        swReadOnly       = findViewById(R.id.sw_read_only);
        etUsername       = findViewById(R.id.et_username);
        etPassword       = findViewById(R.id.et_password);
        etPort           = findViewById(R.id.et_port);
        btnTogglePassVis = findViewById(R.id.btn_toggle_pass_vis);

        // Restore saved values
        int port = state.getPort("SFTP");
        etPort.setText(String.valueOf(port));
        swAnonymous.setChecked(state.sp_bool("sftp_anonymous", false));
        swReadOnly.setChecked(state.sp_bool("sftp_read_only", false));
        etUsername.setText(state.getUsername());
        etPassword.setText(state.getPassword());

        setInfoText(port);
        applyAnonymousState(swAnonymous.isChecked());

        String path = state.getFolderPath();
        if (!path.isEmpty()) showPath(path);

        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Select folder
        findViewById(R.id.row_select_folder).setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQ_FOLDER);
        });

        // Clear selection
        findViewById(R.id.btn_clear_selection).setOnClickListener(v -> {
            state.setFolderPath("");
            pathDisplay.setVisibility(View.GONE);
        });

        // Anonymous toggle
        swAnonymous.setOnCheckedChangeListener((b, on) -> applyAnonymousState(on));

        // Live port update in info text
        etPort.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    setInfoText(Integer.parseInt(s.toString().trim()));
                } catch (NumberFormatException ignored) {}
            }
        });

        // Password visibility toggle
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

        // Save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            try {
                int p = Integer.parseInt(etPort.getText().toString().trim());
                state.setPort("SFTP", p);
            } catch (NumberFormatException ignored) {}

            boolean anon = swAnonymous.isChecked();
            state.sp_set("sftp_anonymous", anon);
            state.sp_set("sftp_read_only", swReadOnly.isChecked());
            state.setUsername(etUsername.getText().toString().trim());
            state.setPassword(etPassword.getText().toString());
            state.setPasswordEnabled(!anon);

            setResult(RESULT_OK);
            finish();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setInfoText(int port) {
        String ip = NetworkUtil.getLocalIp(this);
        String connectUrl = "sftp://" + ip + ":" + port;
        String full = "Encrypted file transfer over SSH.\nConnect via " + connectUrl;

        SpannableString ss = new SpannableString(full);
        int start = full.indexOf(connectUrl);
        if (start >= 0) {
            ss.setSpan(new StyleSpan(Typeface.BOLD), start,
                    start + connectUrl.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvInfo.setText(ss);
    }

    private void applyAnonymousState(boolean anonymousOn) {
        float alpha = anonymousOn ? 0.4f : 1f;
        authFields.setAlpha(alpha);
        etUsername.setEnabled(!anonymousOn);
        etPassword.setEnabled(!anonymousOn);
        btnTogglePassVis.setEnabled(!anonymousOn);
    }

    private void showPath(String path) {
        tvSelectedPath.setText(path);
        pathDisplay.setVisibility(View.VISIBLE);
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
