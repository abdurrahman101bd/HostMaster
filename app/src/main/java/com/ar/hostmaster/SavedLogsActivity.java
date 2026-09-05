package com.ar.hostmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.snackbar.Snackbar;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SavedLogsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private LinearLayout viewEmpty;
    private AppState state;
    private SavedLogAdapter adapter;
    private LinearLayout selectionBar;
    private TextView tvSelectionCount;
    private ImageView btnCloseSelection, btnSelectAll, btnExport, btnDelete;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();

    private static final int REQUEST_EXPORT_FOLDER = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_saved_logs);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        rv = findViewById(R.id.rv_saved_logs);
        viewEmpty = findViewById(R.id.view_empty);
        selectionBar = findViewById(R.id.selection_bar);
        tvSelectionCount = findViewById(R.id.tv_selection_count);
        btnCloseSelection = findViewById(R.id.btn_close_selection);
        btnSelectAll = findViewById(R.id.btn_select_all);
        btnExport = findViewById(R.id.btn_export);
        btnDelete = findViewById(R.id.btn_delete);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedLogAdapter();
        rv.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Selection controls
        btnCloseSelection.setOnClickListener(v -> exitSelectionMode());
        
        btnSelectAll.setOnClickListener(v -> {
            if (selectedPositions.size() == adapter.getItemCount()) {
                selectedPositions.clear();
            } else {
                selectedPositions.clear();
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    selectedPositions.add(i);
                }
            }
            updateSelectionUI();
            adapter.notifyDataSetChanged();
        });

        btnDelete.setOnClickListener(v -> deleteSelectedItems());

        btnExport.setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) {
                Snackbar.make(rv, "No items selected to export", Snackbar.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.MODE_KEY, FilePickerActivity.MODE_FOLDER);
            startActivityForResult(i, REQUEST_EXPORT_FOLDER);
        });

        loadSavedLogs();
    }

    private void loadSavedLogs() {
        boolean isSavingEnabled = state.sp_bool("keep_logs_enabled", true);
        
        if (!isSavingEnabled) {
            viewEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            selectionBar.setVisibility(View.GONE);
            
            TextView emptyTitle = viewEmpty.findViewById(R.id.tv_empty_title);
            TextView emptySub = viewEmpty.findViewById(R.id.tv_empty_sub);
            ImageView emptyIcon = viewEmpty.findViewById(R.id.iv_empty_icon);
            Button btnGoSettings = viewEmpty.findViewById(R.id.btn_go_settings);
            
            if (emptyTitle != null) {
                emptyTitle.setText("LOG SAVING DISABLED");
            }
            if (emptySub != null) {
                emptySub.setText("Enable log saving in Settings to view saved logs");
            }
            if (emptyIcon != null) {
                emptyIcon.setImageResource(R.drawable.ic_logs);
                emptyIcon.setVisibility(View.VISIBLE);
            }
            if (btnGoSettings != null) {
                btnGoSettings.setVisibility(View.VISIBLE);
                btnGoSettings.setOnClickListener(v -> {
                    startActivity(new Intent(this, SettingsActivity.class));
                });
            }
            return;
        }
        
        List<String> dates = LogManager.getAvailableLogDates();
        if (dates.isEmpty()) {
            viewEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            selectionBar.setVisibility(View.GONE);
            
            TextView emptyTitle = viewEmpty.findViewById(R.id.tv_empty_title);
            TextView emptySub = viewEmpty.findViewById(R.id.tv_empty_sub);
            ImageView emptyIcon = viewEmpty.findViewById(R.id.iv_empty_icon);
            Button btnGoSettings = viewEmpty.findViewById(R.id.btn_go_settings);
            
            if (emptyTitle != null) {
                emptyTitle.setText("NO SAVED LOGS");
            }
            if (emptySub != null) {
                emptySub.setText("Logs are saved automatically each day");
            }
            if (emptyIcon != null) {
                emptyIcon.setImageResource(R.drawable.ic_logs);
                emptyIcon.setVisibility(View.VISIBLE);
            }
            if (btnGoSettings != null) {
                btnGoSettings.setVisibility(View.GONE);
            }
        } else {
            viewEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            adapter.setData(dates);
            exitSelectionMode();
        }
    }

    private void enterSelectionMode(int position) {
        isSelectionMode = true;
        selectionBar.setVisibility(View.VISIBLE);
        selectedPositions.add(position);
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectionBar.setVisibility(View.GONE);
        selectedPositions.clear();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        int count = selectedPositions.size();
        int total = adapter.getItemCount();
        tvSelectionCount.setText(count + " selected");
        
        if (count == 0) {
            btnSelectAll.setImageResource(R.drawable.ic_checkbox_unchecked);
        } else if (count == total) {
            btnSelectAll.setImageResource(R.drawable.ic_checkbox_checked);
        } else {
            btnSelectAll.setImageResource(R.drawable.ic_checkbox_indeterminate);
        }
    }

    private void deleteSelectedItems() {
        if (selectedPositions.isEmpty()) return;

        List<String> allDates = adapter.getData();
        List<String> toDelete = new ArrayList<>();
        for (int pos : selectedPositions) {
            toDelete.add(allDates.get(pos));
        }

        for (String date : toDelete) {
            File file = new File(getFilesDir(), "logs_" + date + ".txt");
            if (file.exists()) file.delete();
        }

        String message = toDelete.size() + " log" + (toDelete.size() > 1 ? "s" : "") + " deleted";
        final List<String> backupDates = new ArrayList<>(toDelete);
        
        Snackbar.make(rv, message, Snackbar.LENGTH_LONG)
            .setAction("UNDO", v -> {
                for (String date : backupDates) {
                    try {
                        File file = new File(getFilesDir(), "logs_" + date + ".txt");
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                loadSavedLogs();
            })
            .show();

        exitSelectionMode();
        loadSavedLogs();
    }

    private void exportSelectedItems(String exportPath) {
        if (selectedPositions.isEmpty()) return;

        List<String> allDates = adapter.getData();
        List<String> toExport = new ArrayList<>();
        for (int pos : selectedPositions) {
            toExport.add(allDates.get(pos));
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (String date : toExport) {
            List<LogManager.LogEntry> entries = LogManager.getLogsForDate(date);
            sb.append("========== ").append(date).append(" ==========\n");
            for (LogManager.LogEntry entry : entries) {
                sb.append("[").append(entry.time).append("] ")
                  .append(entry.method).append(" ")
                  .append(entry.path).append(" ")
                  .append(entry.status).append(" ")
                  .append(entry.client)
                  .append("\n");
            }
            sb.append("\n");
        }

        try {
            String fileName = "logs_export_" + System.currentTimeMillis() + ".txt";
            File exportFile = new File(exportPath, fileName);
            FileWriter fw = new FileWriter(exportFile);
            fw.write(sb.toString());
            fw.close();

            Snackbar.make(rv, "Exported to: " + exportFile.getAbsolutePath(), Snackbar.LENGTH_LONG).show();
        } catch (IOException e) {
            Snackbar.make(rv, "Export failed: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }

        exitSelectionMode();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_EXPORT_FOLDER && res == RESULT_OK && data != null) {
            String path = data.getStringExtra(FilePickerActivity.RESULT_PATH);
            if (path != null && !path.isEmpty()) {
                exportSelectedItems(path);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedLogs();
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    class SavedLogAdapter extends RecyclerView.Adapter<SavedLogAdapter.VH> {
        private List<String> dates = new ArrayList<>();
        private final SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        void setData(List<String> d) {
            dates = d;
            notifyDataSetChanged();
        }

        List<String> getData() {
            return dates;
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_saved_log, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            String date = dates.get(pos);
            try {
                Date d = inputFormat.parse(date);
                h.tvDate.setText(displayFormat.format(d));
            } catch (Exception e) {
                h.tvDate.setText(date);
            }

            List<LogManager.LogEntry> entries = LogManager.getLogsForDate(date);
            int total = entries.size();
            int errors = (int) entries.stream().filter(e -> "error".equals(e.type)).count();
            int success = total - errors;
            h.tvLogCount.setText(total + " entries  ·  " + success + " ✓  " + errors + " ✗");

            if (isSelectionMode) {
                h.checkbox.setVisibility(View.VISIBLE);
                h.checkbox.setChecked(selectedPositions.contains(pos));
                h.itemView.setBackgroundColor(selectedPositions.contains(pos) 
                    ? getResources().getColor(R.color.surface_highlight) 
                    : getResources().getColor(android.R.color.transparent));
            } else {
                h.checkbox.setVisibility(View.GONE);
                h.itemView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            }

            h.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(pos);
                } else {
                    Intent i = new Intent(SavedLogsActivity.this, LogDetailActivity.class);
                    i.putExtra("log_date", date);
                    startActivity(i);
                }
            });

            h.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    enterSelectionMode(pos);
                } else {
                    toggleSelection(pos);
                }
                return true;
            });

            h.checkbox.setOnClickListener(v -> toggleSelection(pos));
        }

        private void toggleSelection(int pos) {
            if (selectedPositions.contains(pos)) {
                selectedPositions.remove(pos);
            } else {
                selectedPositions.add(pos);
            }
            if (selectedPositions.isEmpty()) {
                exitSelectionMode();
            } else {
                updateSelectionUI();
                notifyDataSetChanged();
            }
        }

        @Override public int getItemCount() { return dates.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvLogCount;
            CheckBox checkbox;
            VH(View v) {
                super(v);
                tvDate = v.findViewById(R.id.tv_date);
                tvLogCount = v.findViewById(R.id.tv_log_count);
                checkbox = v.findViewById(R.id.cb_select);
            }
        }
    }
}