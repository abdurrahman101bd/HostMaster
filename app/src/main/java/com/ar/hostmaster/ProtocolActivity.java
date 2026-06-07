package com.ar.hostmaster;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ProtocolActivity extends AppCompatActivity {
    private AppState state;
    private View cardHttp, cardFtp, cardSsh;
    private View dotHttp, dotFtp, dotSsh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_protocol);
        state = AppState.get(this);

        cardHttp = findViewById(R.id.card_http);
        cardFtp  = findViewById(R.id.card_ftp);
        cardSsh  = findViewById(R.id.card_ssh);
        dotHttp  = findViewById(R.id.dot_http);
        dotFtp   = findViewById(R.id.dot_ftp);
        dotSsh   = findViewById(R.id.dot_ssh);

        // Update port labels from state
        ((TextView) findViewById(R.id.tv_http_port)).setText("PORT " + state.getPort("HTTP"));
        ((TextView) findViewById(R.id.tv_ftp_port)).setText("PORT " + state.getPort("FTP"));
        ((TextView) findViewById(R.id.tv_ssh_port)).setText("PORT " + state.getPort("SSH"));

        selectProto(state.getProtocol());

        cardHttp.setOnClickListener(v -> selectProto("HTTP"));
        cardFtp.setOnClickListener(v  -> selectProto("FTP"));
        cardSsh.setOnClickListener(v  -> selectProto("SSH"));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void selectProto(String proto) {
        state.setProtocol(proto);

        // Reset all
        cardHttp.setBackgroundResource(R.drawable.bg_card);
        cardFtp.setBackgroundResource(R.drawable.bg_card);
        cardSsh.setBackgroundResource(R.drawable.bg_card);
        dotHttp.setBackgroundResource(R.drawable.bg_proto_dot_off);
        dotFtp.setBackgroundResource(R.drawable.bg_proto_dot_off);
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
            case "SSH":
                cardSsh.setBackgroundResource(R.drawable.bg_proto_card_selected);
                dotSsh.setBackgroundResource(R.drawable.bg_proto_dot_on);
                break;
        }
    }
}
