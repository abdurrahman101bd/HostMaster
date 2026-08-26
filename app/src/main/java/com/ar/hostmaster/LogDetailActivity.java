package com.ar.hostmaster;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class LogDetailActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TextView tvDateTitle, tvTotalRequests, tvSuccessCount, tvErrorCount;
    private EditText etSearch;
    private TextView btnClearSearch;
    private AppState state;
    private LogDetailAdapter adapter;
    private String date;
    private String searchText = "";
    private List<LogManager.LogEntry> allEntries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_log_detail);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        date = getIntent().getStringExtra("log_date");
        if (date == null) {
            finish();
            return;
        }

        rv = findViewById(R.id.rv_log_detail);
        tvDateTitle = findViewById(R.id.tv_date_title);
        tvTotalRequests = findViewById(R.id.tv_total_requests);
        tvSuccessCount = findViewById(R.id.tv_success_count);
        tvErrorCount = findViewById(R.id.tv_error_count);
        etSearch = findViewById(R.id.et_log_detail_search);
        btnClearSearch = findViewById(R.id.btn_clear_detail_search);

        // Format date for display
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
            Date d = inputFormat.parse(date);
            tvDateTitle.setText(displayFormat.format(d));
        } catch (Exception e) {
            tvDateTitle.setText(date);
        }

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogDetailAdapter();
        rv.setAdapter(adapter);

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchText = s.toString().toLowerCase();
                btnClearSearch.setVisibility(searchText.isEmpty() ? View.GONE : View.VISIBLE);
                filterLogs();
            }
            public void afterTextChanged(Editable s) {}
        });
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadLogs();
    }

    private void loadLogs() {
        allEntries = LogManager.getLogsForDate(date);
        filterLogs();
    }

    private void filterLogs() {
        List<LogManager.LogEntry> filtered = allEntries.stream().filter(e -> {
            if (searchText.isEmpty()) return true;
            return e.path.toLowerCase().contains(searchText)
                || e.method.toLowerCase().contains(searchText)
                || e.status.contains(searchText)
                || e.client.toLowerCase().contains(searchText);
        }).collect(Collectors.toList());

        adapter.setData(filtered);

        int total = allEntries.size();
        int errors = (int) allEntries.stream().filter(e -> "error".equals(e.type)).count();
        int success = total - errors;

        tvTotalRequests.setText(String.valueOf(total));
        tvSuccessCount.setText(String.valueOf(success));
        tvErrorCount.setText(String.valueOf(errors));
    }

    class LogDetailAdapter extends RecyclerView.Adapter<LogDetailAdapter.VH> {
        private List<LogManager.LogEntry> items = new ArrayList<>();

        void setData(List<LogManager.LogEntry> d) {
            items = d;
            notifyDataSetChanged();
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_log, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            LogManager.LogEntry e = items.get(pos);
            h.tvTime.setText(e.time);
            h.tvMethod.setText(e.method);
            h.tvPath.setText(e.path);
            h.tvStatus.setText(e.status);
            h.tvClient.setText(e.client);
            int color = "success".equals(e.type) ? Color.parseColor("#00FF9D")
                      : "error".equals(e.type)   ? Color.parseColor("#FF4444")
                      : Color.parseColor("#00D4FF");
            h.tvStatus.setTextColor(color);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTime, tvMethod, tvPath, tvStatus, tvClient;
            VH(View v) {
                super(v);
                tvTime = v.findViewById(R.id.tv_log_time);
                tvMethod = v.findViewById(R.id.tv_log_method);
                tvPath = v.findViewById(R.id.tv_log_path);
                tvStatus = v.findViewById(R.id.tv_log_status);
                tvClient = v.findViewById(R.id.tv_log_client);
            }
        }
    }
}