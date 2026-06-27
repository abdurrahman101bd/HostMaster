package com.ar.hostmaster;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppState state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_help);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Expand/collapse FAQ items
        setupFaq(R.id.faq_1_header, R.id.faq_1_body);
        setupFaq(R.id.faq_2_header, R.id.faq_2_body);
        setupFaq(R.id.faq_3_header, R.id.faq_3_body);
        setupFaq(R.id.faq_4_header, R.id.faq_4_body);
        setupFaq(R.id.faq_5_header, R.id.faq_5_body);
    }

    private void setupFaq(int headerId, int bodyId) {
        View header = findViewById(headerId);
        View body   = findViewById(bodyId);
        if (header == null || body == null) return;

        body.setVisibility(View.GONE);
        header.setOnClickListener(v -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);

            // Rotate arrow indicator if present
            TextView arrow = header.findViewWithTag("arrow");
            if (arrow != null) arrow.setRotation(open ? 0 : 90);
        });
    }
}
