package com.ar.hostmaster;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtocolActivity extends AppCompatActivity {
    private AppState state;
    private View cardHttp, cardFtp;
    private View dotHttp, dotFtp;
    private String pendingProto; // not saved to AppState until "Save" is tapped

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
        dotHttp  = findViewById(R.id.dot_http);
        dotFtp   = findViewById(R.id.dot_ftp);

        // Update port labels
        ((TextView) findViewById(R.id.tv_http_port)).setText("PORT " + state.getPort("HTTP"));
        ((TextView) findViewById(R.id.tv_ftp_port)).setText("PORT " + state.getPort("FTP"));

        // Select current protocol — visual only, nothing is saved yet
        pendingProto = state.getProtocol();
        highlightProtocol(pendingProto);

        // Click listeners — just update the local pending choice + highlight
        cardHttp.setOnClickListener(v -> { pendingProto = "HTTP"; highlightProtocol(pendingProto); });
        cardFtp.setOnClickListener(v  -> { pendingProto = "FTP";  highlightProtocol(pendingProto); });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish()); // discard, no save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            state.setProtocol(pendingProto); // commit only here
            finish();
        });
    }

    private void highlightProtocol(String proto) {
        // Reset all cards
        cardHttp.setBackgroundResource(R.drawable.bg_card);
        cardFtp.setBackgroundResource(R.drawable.bg_card);
        
        dotHttp.setBackgroundResource(R.drawable.bg_proto_dot_off);
        dotFtp.setBackgroundResource(R.drawable.bg_proto_dot_off);

        // Highlight selected
        if ("HTTP".equals(proto)) {
            cardHttp.setBackgroundResource(R.drawable.bg_proto_card_selected);
            dotHttp.setBackgroundResource(R.drawable.bg_proto_dot_on);
        } else if ("FTP".equals(proto)) {
            cardFtp.setBackgroundResource(R.drawable.bg_proto_card_selected);
            dotFtp.setBackgroundResource(R.drawable.bg_proto_dot_on);
        }
    }
}