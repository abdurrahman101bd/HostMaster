package com.ar.hostmaster;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private AppState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        state = AppState.get(this);

        updateThemeSub();
        updatePortSub();

        // Row clicks
        findViewById(R.id.row_theme).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.row_port).setOnClickListener(v -> showPortDialog());
        findViewById(R.id.row_language).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.row_timeout).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.row_max_conn).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.row_privacy).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.row_help_faq).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());   
        findViewById(R.id.row_report_bug).setOnClickListener(v -> 
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show());
     
        Switch swNotif     = findViewById(R.id.sw_notifications);
        Switch swLocalOnly = findViewById(R.id.sw_local_only);
        Switch swKeepLogs  = findViewById(R.id.sw_keep_logs);
		// Auto-start switch
		Switch swAutostart = findViewById(R.id.sw_autostart_settings);
		swAutostart.setChecked(state.isAutostart());
		swAutostart.setOnCheckedChangeListener((b, isChecked) -> {
			state.setAutostart(isChecked);
			if (isChecked) {
				Toast.makeText(this, "Auto start enabled", Toast.LENGTH_SHORT).show();
			}
		});

        // Set switch states
        swNotif.setChecked(state.sp_bool("notif", true));
        swLocalOnly.setChecked(state.sp_bool("local_only", true));
        swKeepLogs.setChecked(state.sp_bool("keep_logs", false));

        // Switch listeners
        swNotif.setOnCheckedChangeListener((b, isChecked) -> {
            state.sp_set("notif", isChecked);
            if (isChecked) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            }
        });
        
        swLocalOnly.setOnCheckedChangeListener((b, isChecked) -> {
            state.sp_set("local_only", isChecked);
            if (isChecked) {
                Toast.makeText(this, "Local only mode enabled", Toast.LENGTH_SHORT).show();
            }
        });
        
        swKeepLogs.setOnCheckedChangeListener((b, isChecked) -> {
            state.sp_set("keep_logs", isChecked);
            if (isChecked) {
                Toast.makeText(this, "Logs will be saved", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

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
                + " · FTP:" + state.getPort("FTP")
                + " · SSH:" + state.getPort("SSH"));
    }

    // ── Custom Theme Dialog with Icons ───────────────────────────────────────
    private void showThemeDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        // Custom title
        TextView title = new TextView(this);
        title.setText("SELECT THEME");
        title.setTextColor(getResources().getColor(R.color.accent, getTheme()));
        title.setTextSize(12f);
        title.setLetterSpacing(0.15f);
        title.setPadding(dp(20), dp(18), dp(20), dp(8));
        title.setTypeface(null, android.graphics.Typeface.BOLD);

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
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(14), dp(8), dp(14));
            row.setClickable(true);
            row.setFocusable(true);

            if (i == curIdx) {
                row.setBackgroundResource(R.drawable.bg_proto_card_selected);
            }

            // Icon ImageView - বড়サイズ
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(getResources().getColor(R.color.accent, getTheme()));
            icon.setPadding(0, 0, dp(14), 0);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            icon.setLayoutParams(iconParams);

            TextView label = new TextView(this);
            label.setText(themes[i]);
            label.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            label.setTextSize(14f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            if (i == curIdx) {
                TextView tick = new TextView(this);
                tick.setText("✓");
                tick.setTextColor(getResources().getColor(R.color.accent, getTheme()));
                tick.setTextSize(16f);
                row.addView(icon);
                row.addView(label);
                row.addView(tick);
            } else {
                row.addView(icon);
                row.addView(label);
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

        android.app.AlertDialog dialog = builder
                .setCustomTitle(title)
                .setView(container)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
        dialog.show();
    }

    // ── Custom Port Dialog ───────────────────────────────────────────────────
    private void showPortDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        // Title
        TextView title = new TextView(this);
        title.setText("CHANGE PORTS");
        title.setTextColor(getResources().getColor(R.color.accent, getTheme()));
        title.setTextSize(12f);
        title.setLetterSpacing(0.15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        String[] protos = {"HTTP", "FTP", "SSH"};
        EditText[] fields = new EditText[3];

        for (int i = 0; i < protos.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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
            fields[i].setBackgroundResource(R.drawable.bg_stat_card);
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

        // Buttons row
        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.border, getTheme()));
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dlp2.setMargins(0, dp(16), 0, dp(12));
        divider.setLayoutParams(dlp2);
        root.addView(divider);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);

        final EditText[] finalFields = fields;
        final String[]   finalProtos = protos;

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        TextView btnCancel = makeDlgBtn("CANCEL", getResources().getColor(R.color.text_muted, getTheme()));
        btnCancel.setOnClickListener(v -> dialogRef[0].dismiss());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMarginEnd(dp(16));
        btnCancel.setLayoutParams(btnLp);

        TextView btnSave = makeDlgBtn("SAVE", getResources().getColor(R.color.accent, getTheme()));
        btnSave.setOnClickListener(v -> {
            for (int i = 0; i < finalProtos.length; i++) {
                try {
                    int port = Integer.parseInt(finalFields[i].getText().toString().trim());
                    if (port >= 1 && port <= 65535) state.setPort(finalProtos[i], port);
                } catch (NumberFormatException ignored) {}
            }
            updatePortSub();
            Toast.makeText(this, "Ports saved", Toast.LENGTH_SHORT).show();
            dialogRef[0].dismiss();
        });

        btnRow.addView(btnCancel);
        btnRow.addView(btnSave);
        root.addView(btnRow);

        dialogRef[0] = builder.setView(root).create();
        if (dialogRef[0].getWindow() != null)
            dialogRef[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialogRef[0].show();
    }

    private TextView makeDlgBtn(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12f);
        tv.setLetterSpacing(0.1f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
