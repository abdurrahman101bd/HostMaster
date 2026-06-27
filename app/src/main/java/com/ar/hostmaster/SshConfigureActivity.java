package com.ar.hostmaster;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SshConfigureActivity extends AppCompatActivity {

    private static final int REQ_FOLDER = 1;

    private AppState state;

    private LinearLayout pathDisplay;
    private TextView tvSelectedPath;
    private LinearLayout authFields;
    private Switch swPassword, swSftp, swTermux, swKeyAuth;
    private EditText etUsername, etPassword, etPort;
    private LinearLayout termuxNote;
    private boolean passVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_ssh_configure);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        pathDisplay    = findViewById(R.id.path_display);
        tvSelectedPath = findViewById(R.id.tv_selected_path);
        authFields     = findViewById(R.id.auth_fields);
        swPassword     = findViewById(R.id.sw_password);
        swSftp         = findViewById(R.id.sw_sftp);
        swTermux       = findViewById(R.id.sw_termux);
        swKeyAuth      = findViewById(R.id.sw_key_auth);
        etUsername     = findViewById(R.id.et_username);
        etPassword     = findViewById(R.id.et_password);
        etPort         = findViewById(R.id.et_port);
        termuxNote     = findViewById(R.id.termux_note);

        // Restore saved values
        etPort.setText(String.valueOf(state.getPort("SSH")));
        swPassword.setChecked(state.isPasswordEnabled());
        swSftp.setChecked(state.sp_bool("ssh_sftp", true));
        swTermux.setChecked(state.sp_bool("ssh_termux", false));
        swKeyAuth.setChecked(state.sp_bool("ssh_key_auth", false));
        etUsername.setText(state.getUsername());
        etPassword.setText(state.getPassword());
        authFields.setVisibility(state.isPasswordEnabled() ? View.VISIBLE : View.GONE);
        termuxNote.setVisibility(state.sp_bool("ssh_termux", false) ? View.VISIBLE : View.GONE);

        String path = state.getFolderPath();
        if (!path.isEmpty()) showPath(path);

        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Select folder (used for SFTP root)
        findViewById(R.id.row_select_folder).setOnClickListener(v -> {
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQ_FOLDER);
        });

        findViewById(R.id.btn_clear_selection).setOnClickListener(v -> {
            state.setFolderPath("");
            pathDisplay.setVisibility(View.GONE);
        });

        // Password toggle
        swPassword.setOnCheckedChangeListener((b, on) ->
                authFields.setVisibility(on ? View.VISIBLE : View.GONE));

        // Termux toggle — show/hide note
        swTermux.setOnCheckedChangeListener((b, on) ->
                termuxNote.setVisibility(on ? View.VISIBLE : View.GONE));

        // Password visibility
        findViewById(R.id.btn_toggle_pass_vis).setOnClickListener(v -> {
            passVisible = !passVisible;
            etPassword.setTransformationMethod(passVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etPassword.setSelection(etPassword.getText().length());
        });

        // Save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            try {
                int port = Integer.parseInt(etPort.getText().toString().trim());
                state.setPort("SSH", port);
            } catch (NumberFormatException ignored) {}

            state.setPasswordEnabled(swPassword.isChecked());
            state.setUsername(etUsername.getText().toString().trim());
            state.setPassword(etPassword.getText().toString());
            state.sp_set("ssh_sftp",    swSftp.isChecked());
            state.sp_set("ssh_termux",  swTermux.isChecked());
            state.sp_set("ssh_key_auth",swKeyAuth.isChecked());
            setResult(RESULT_OK);
            finish();
        });
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
