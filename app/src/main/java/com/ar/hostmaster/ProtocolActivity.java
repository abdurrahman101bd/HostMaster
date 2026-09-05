package com.ar.hostmaster;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtocolActivity extends AppCompatActivity {
    private AppState state;
    private View cardHttp, cardFtp;
    private View dotHttp, dotFtp;

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

        // Select current protocol
        String currentProto = state.getProtocol();
        selectProto(currentProto);

        // Click listeners
        cardHttp.setOnClickListener(v -> selectProto("HTTP"));
        cardFtp.setOnClickListener(v  -> selectProto("FTP"));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> finish());
    }

    private void selectProto(String proto) {
        state.setProtocol(proto);
        highlightProtocol(proto);
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