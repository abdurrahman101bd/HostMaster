package com.ar.hostmaster;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtocolActivity extends AppCompatActivity {
    private AppState state;
    private View cardHttp, cardFtp, cardSftp, cardSsh;
    private View dotHttp, dotFtp, dotSftp, dotSsh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_protocol);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        // Initialize views
        cardHttp = findViewById(R.id.card_http);
        cardFtp  = findViewById(R.id.card_ftp);
        cardSftp = findViewById(R.id.card_sftp);
        cardSsh  = findViewById(R.id.card_ssh);
        dotHttp  = findViewById(R.id.dot_http);
        dotFtp   = findViewById(R.id.dot_ftp);
        dotSftp  = findViewById(R.id.dot_sftp);
        dotSsh   = findViewById(R.id.dot_ssh);

        // Update port labels from state
        ((TextView) findViewById(R.id.tv_http_port)).setText("PORT " + state.getPort("HTTP"));
        ((TextView) findViewById(R.id.tv_ftp_port)).setText("PORT " + state.getPort("FTP"));
        ((TextView) findViewById(R.id.tv_sftp_port)).setText("PORT " + state.getPort("SFTP"));
        ((TextView) findViewById(R.id.tv_ssh_port)).setText("PORT " + state.getPort("SSH"));

        // Select initial protocol (only HTTP or FTP)
        String currentProto = state.getProtocol();
        if (currentProto.equals("SFTP") || currentProto.equals("SSH")) {
            // If SFTP or SSH was previously selected, fallback to HTTP
            state.setProtocol("HTTP");
            currentProto = "HTTP";
        }
        selectProto(currentProto);

        // Click listeners
        cardHttp.setOnClickListener(v -> selectProto("HTTP"));
        cardFtp.setOnClickListener(v  -> selectProto("FTP"));
        
        // SFTP - Coming Soon
        cardSftp.setOnClickListener(v -> {
            Toast.makeText(ProtocolActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
        });
        
        // SSH - Coming Soon
        cardSsh.setOnClickListener(v -> {
            Toast.makeText(ProtocolActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> finish());
    }

    private void selectProto(String proto) {
        // Only allow HTTP and FTP
        if (!proto.equals("HTTP") && !proto.equals("FTP")) {
            Toast.makeText(ProtocolActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
            // Keep previous selection
            String currentProto = state.getProtocol();
            if (currentProto.equals("HTTP") || currentProto.equals("FTP")) {
                // Re-select the current valid protocol
                highlightProtocol(currentProto);
            } else {
                // Fallback to HTTP
                state.setProtocol("HTTP");
                highlightProtocol("HTTP");
            }
            return;
        }

        state.setProtocol(proto);
        highlightProtocol(proto);
    }

    private void highlightProtocol(String proto) {
        // Reset all cards - using bg_card for all
        cardHttp.setBackgroundResource(R.drawable.bg_card);
        cardFtp.setBackgroundResource(R.drawable.bg_card);
        cardSftp.setBackgroundResource(R.drawable.bg_card_disabled);
        cardSsh.setBackgroundResource(R.drawable.bg_card_disabled);
        
        dotHttp.setBackgroundResource(R.drawable.bg_proto_dot_off);
        dotFtp.setBackgroundResource(R.drawable.bg_proto_dot_off);
        dotSftp.setBackgroundResource(R.drawable.bg_proto_dot_off);
        dotSsh.setBackgroundResource(R.drawable.bg_proto_dot_off);

        // Highlight selected
        switch (proto) {
            case "HTTP":
                cardHttp.setBackgroundResource(R.drawable.bg_proto_card_selected);
                dotHttp.setBackgroundResource(R.drawable.bg_proto_dot_on);
                break;
            case "FTP":
                cardFtp.setBackgroundResource(R.drawable.bg_proto_card_selected);
                dotFtp.setBackgroundResource(R.drawable.bg_proto_dot_on);
                break;
            case "SFTP":
                // SFTP is disabled
                cardSftp.setBackgroundResource(R.drawable.bg_card_disabled);
                dotSftp.setBackgroundResource(R.drawable.bg_proto_dot_off);
                break;
            case "SSH":
                // SSH is disabled
                cardSsh.setBackgroundResource(R.drawable.bg_card_disabled);
                dotSsh.setBackgroundResource(R.drawable.bg_proto_dot_off);
                break;
        }
    }
}