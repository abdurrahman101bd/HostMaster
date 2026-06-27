package com.ar.hostmaster;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String GITHUB_URL = "https://github.com/abdurrahman101bd/host_master/issues";

    private AppState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_settings);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        updateThemeSub();
        updatePortSub();
        updateTimeoutSub();

        // ── Switches — always ON (logic coming later) ─────────────────────────
        Switch swNotif     = findViewById(R.id.sw_notifications);
        Switch swLocalOnly = findViewById(R.id.sw_local_only);
        Switch swKeepLogs  = findViewById(R.id.sw_keep_logs);
        swNotif.setChecked(true);
        swLocalOnly.setChecked(true);
        swKeepLogs.setChecked(true);
        swNotif.setEnabled(false);
        swLocalOnly.setEnabled(false);
        swKeepLogs.setEnabled(false);

        // Autostart
        Switch swAutostart = findViewById(R.id.sw_autostart_settings);
        swAutostart.setChecked(state.isAutostart());
        swAutostart.setOnCheckedChangeListener((b, on) -> state.setAutostart(on));

        // ── Row clicks ────────────────────────────────────────────────────────
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.row_theme).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.row_port).setOnClickListener(v -> showPortDialog());
        findViewById(R.id.row_timeout).setOnClickListener(v -> showTimeoutDialog());
        findViewById(R.id.row_privacy).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class)));
        findViewById(R.id.row_help_faq).setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));
        findViewById(R.id.row_report_bug).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Sub-text updaters ─────────────────────────────────────────────────────

    private void updateThemeSub() {
        TextView sub = findViewById(R.id.tv_theme_sub);
        if (sub == null) return;
        switch (state.getTheme()) {
            case AppState.THEME_LIGHT:  sub.setText("Light mode");    break;
            case AppState.THEME_DARK:   sub.setText("Dark mode");     break;
            default:                    sub.setText("Follow system"); break;
        }
    }

    private void updatePortSub() {
        TextView sub = findViewById(R.id.tv_port_sub);
        if (sub == null) return;
        sub.setText("HTTP:" + state.getPort("HTTP")
                + "  FTP:" + state.getPort("FTP")
                + "  SFTP:" + state.getPort("SFTP")
                + "  SSH:" + state.getPort("SSH"));
    }

    private void updateTimeoutSub() {
        TextView sub = findViewById(R.id.tv_timeout_sub);
        if (sub == null) return;
        int t = state.sp_int("timeout_sec", 300);
        sub.setText(t + " seconds");
    }

    private void applyLanguage(String langKey) {
        android.content.res.Configuration config =
                new android.content.res.Configuration(getResources().getConfiguration());
        config.setLocale(new Locale("en".equals(langKey) ? "en" : "bn"));
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        recreate();
    }
    
    // ── Theme Dialog ──────────────────────────────────────────────────────────

    private void showThemeDialog() {
        String[] themes = {"Light Mode", "Dark Mode", "Follow System"};
        int[] icons = {R.drawable.ic_sun, R.drawable.ic_moon, R.drawable.ic_mobile};
        String cur = state.getTheme();
        int curIdx = AppState.THEME_LIGHT.equals(cur) ? 0 : AppState.THEME_DARK.equals(cur) ? 1 : 2;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), 0, dp(12), dp(12));

        for (int i = 0; i < themes.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(14), dp(8), dp(14));
            row.setClickable(true);
            row.setFocusable(true);
            if (i == curIdx) row.setBackgroundResource(R.drawable.bg_proto_card_selected);

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(getResources().getColor(R.color.accent, getTheme()));
            icon.setPadding(0, 0, dp(14), 0);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));

            TextView label = new TextView(this);
            label.setText(themes[i]);
            label.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            label.setTextSize(14f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            row.addView(icon);
            row.addView(label);

            if (i == curIdx) {
                TextView tick = new TextView(this);
                tick.setText("✓");
                tick.setTextColor(getResources().getColor(R.color.accent, getTheme()));
                tick.setTextSize(16f);
                row.addView(tick);
            }

            container.addView(row);
            if (i < themes.length - 1) {
                View div = new View(this);
                div.setBackgroundColor(getResources().getColor(R.color.border, getTheme()));
                div.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                container.addView(div);
            }

            row.setOnClickListener(v -> {
                String t = idx == 0 ? AppState.THEME_LIGHT
                         : idx == 1 ? AppState.THEME_DARK
                         : AppState.THEME_SYSTEM;
                state.setTheme(t);
                ThemeHelper.apply(t);
                updateThemeSub();
                recreate();
            });
        }

        showPickerDialog("SELECT THEME", container);
    }

    // ── Port Dialog ───────────────────────────────────────────────────────────

    private void showPortDialog() {
        String[] protos = {"HTTP", "FTP", "SFTP", "SSH"};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView title = makeDialogTitle("CHANGE PORTS");
        root.addView(title);

        EditText[] fields = new EditText[protos.length];
        for (int i = 0; i < protos.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView lbl = new TextView(this);
            lbl.setText(protos[i]);
            lbl.setTextColor(getResources().getColor(R.color.text_muted, getTheme()));
            lbl.setTextSize(12f);
            lbl.setLetterSpacing(0.1f);
            lbl.setMinWidth(dp(50));

            fields[i] = new EditText(this);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            fields[i].setText(String.valueOf(state.getPort(protos[i])));
            fields[i].setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            fields[i].setTextSize(14f);
            fields[i].setBackgroundResource(R.drawable.bg_input);
            fields[i].setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginStart(dp(12));
            fields[i].setLayoutParams(lp);

            row.addView(lbl);
            row.addView(fields[i]);
            root.addView(row);

            if (i < protos.length - 1) {
                View div = new View(this);
                div.setBackgroundColor(getResources().getColor(R.color.border, getTheme()));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                dlp.setMargins(0, dp(6), 0, 0);
                div.setLayoutParams(dlp);
                root.addView(div);
            }
        }

        addDialogDivider(root);

        final EditText[] ff = fields;
        final String[] pp = protos;
        android.app.AlertDialog[] ref = {null};

        LinearLayout btnRow = makeBtnRow(
            "CANCEL", v -> ref[0].dismiss(),
            "SAVE",   v -> {
                for (int i = 0; i < pp.length; i++) {
                    try {
                        int port = Integer.parseInt(ff[i].getText().toString().trim());
                        if (port >= 1 && port <= 65535) state.setPort(pp[i], port);
                    } catch (NumberFormatException ignored) {}
                }
                updatePortSub();
                Toast.makeText(this, "Ports saved", Toast.LENGTH_SHORT).show();
                ref[0].dismiss();
            }
        );
        root.addView(btnRow);

        ref[0] = new android.app.AlertDialog.Builder(this)
                .setView(root).create();
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ref[0].show();
    }

    // ── Timeout Dialog ────────────────────────────────────────────────────────
    // Simple preset picker: 60s / 5min / 15min / 30min / Never

    private void showTimeoutDialog() {
        String[] labels = {"1 minute", "5 minutes", "15 minutes", "30 minutes", "Never"};
        int[]    values = {60, 300, 900, 1800, 0};
        int curVal = state.sp_int("timeout_sec", 300);
        int curIdx = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == curVal) { curIdx = i; break; }
        }

        android.app.AlertDialog[] ref = {null};
        LinearLayout container = buildPickerContainer(labels, null, curIdx, idx -> {
            state.sp_int_set("timeout_sec", values[idx]);
            updateTimeoutSub();
            if (ref[0] != null) ref[0].dismiss();
        });

        ref[0] = showPickerDialog("CONNECTION TIMEOUT", container);
    }

    // ── Generic picker dialog builder ─────────────────────────────────────────

    interface OnPick { void pick(int idx); }

    private LinearLayout buildPickerContainer(String[] names, int[] iconRes, int curIdx, OnPick onPick) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), 0, dp(12), dp(12));

        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            row.setClickable(true);
            row.setFocusable(true);
            if (i == curIdx) row.setBackgroundResource(R.drawable.bg_proto_card_selected);

            if (iconRes != null) {
                ImageView iv = new ImageView(this);
                iv.setImageResource(iconRes[i]);
                iv.setColorFilter(getResources().getColor(R.color.accent, getTheme()));
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
                iv.setPadding(0, 0, dp(12), 0);
                row.addView(iv);
            }

            TextView label = new TextView(this);
            label.setText(names[i]);
            label.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            label.setTextSize(14f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(label);

            if (i == curIdx) {
                TextView tick = new TextView(this);
                tick.setText("✓");
                tick.setTextColor(getResources().getColor(R.color.accent, getTheme()));
                tick.setTextSize(16f);
                row.addView(tick);
            }

            container.addView(row);
            if (i < names.length - 1) {
                View div = new View(this);
                div.setBackgroundColor(getResources().getColor(R.color.border, getTheme()));
                div.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                container.addView(div);
            }

            row.setOnClickListener(v -> onPick.pick(idx));
        }
        return container;
    }

    private android.app.AlertDialog showPickerDialog(String titleText, LinearLayout container) {
        TextView title = makeDialogTitle(titleText);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(container)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
        dialog.show();
        return dialog;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private TextView makeDialogTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.accent, getTheme()));
        tv.setTextSize(12f);
        tv.setLetterSpacing(0.15f);
        tv.setPadding(dp(20), dp(18), dp(20), dp(8));
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private void addDialogDivider(LinearLayout root) {
        View div = new View(this);
        div.setBackgroundColor(getResources().getColor(R.color.border, getTheme()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, dp(16), 0, dp(12));
        div.setLayoutParams(lp);
        root.addView(div);
    }

    private LinearLayout makeBtnRow(String cancelText, View.OnClickListener cancelClick,
                                     String okText,     View.OnClickListener okClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);

        TextView btnCancel = makeDlgBtn(cancelText,
                getResources().getColor(R.color.text_muted, getTheme()));
        btnCancel.setOnClickListener(cancelClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(16));
        btnCancel.setLayoutParams(lp);

        TextView btnOk = makeDlgBtn(okText,
                getResources().getColor(R.color.accent, getTheme()));
        btnOk.setOnClickListener(okClick);

        row.addView(btnCancel);
        row.addView(btnOk);
        return row;
    }

    private TextView makeDlgBtn(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12f);
        tv.setLetterSpacing(0.1f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
