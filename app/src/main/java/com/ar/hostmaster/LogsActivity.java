package com.ar.hostmaster;

import android.graphics.Color;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.List;
import java.util.stream.Collectors;

public class LogsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private LogAdapter adapter;
    private LinearLayout viewEmpty;
    private EditText etSearch;
    private TextView btnClearSearch;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String searchText = "";
    private String filter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        rv            = findViewById(R.id.rv_logs);
        viewEmpty     = findViewById(R.id.view_empty);
        etSearch      = findViewById(R.id.et_log_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter();
        rv.setAdapter(adapter);

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchText = s.toString().toLowerCase();
                btnClearSearch.setVisibility(searchText.isEmpty() ? View.GONE : View.VISIBLE);
                refresh();
            }
            public void afterTextChanged(Editable s) {}
        });
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        // Filters
        setupFilter(R.id.btn_filter_all,     "ALL");
        setupFilter(R.id.btn_filter_success, "SUCCESS");
        setupFilter(R.id.btn_filter_error,   "ERROR");

        findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            LogManager.clear();
            refresh();
        });
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        h.post(ticker);
    }

    private void setupFilter(int id, String f) {
        TextView btn = findViewById(id);
        if (btn != null) btn.setOnClickListener(v -> { filter = f; refresh(); });
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1500); }
    };

    private void refresh() {
        List<LogManager.LogEntry> all = LogManager.getAll();
        List<LogManager.LogEntry> filtered = all.stream().filter(e -> {
            boolean matchFilter = "ALL".equals(filter)
                || ("SUCCESS".equals(filter) && "success".equals(e.type))
                || ("ERROR".equals(filter)   && "error".equals(e.type));
            boolean matchSearch = searchText.isEmpty()
                || e.path.toLowerCase().contains(searchText)
                || e.method.toLowerCase().contains(searchText)
                || e.status.contains(searchText)
                || e.client.contains(searchText);
            return matchFilter && matchSearch;
        }).collect(Collectors.toList());

        adapter.setData(filtered);
        boolean isEmpty = filtered.isEmpty();
        viewEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override protected void onDestroy() { h.removeCallbacks(ticker); super.onDestroy(); }

    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
        private List<LogManager.LogEntry> items = new java.util.ArrayList<>();
        void setData(List<LogManager.LogEntry> d) { items = d; notifyDataSetChanged(); }

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

        static class VH extends RecyclerView.ViewHolder {
            TextView tvTime, tvMethod, tvPath, tvStatus, tvClient;
            VH(View v) {
                super(v);
                tvTime   = v.findViewById(R.id.tv_log_time);
                tvMethod = v.findViewById(R.id.tv_log_method);
                tvPath   = v.findViewById(R.id.tv_log_path);
                tvStatus = v.findViewById(R.id.tv_log_status);
                tvClient = v.findViewById(R.id.tv_log_client);
            }
        }
    }
}
