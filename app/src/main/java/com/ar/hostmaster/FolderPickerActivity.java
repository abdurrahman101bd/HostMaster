package com.ar.hostmaster;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.*;

public class FolderPickerActivity extends AppCompatActivity {
    public static final String RESULT_PATH = "path";

    private TextView tvPath;
    private RecyclerView rv;
    private Button btnSelect;
    private File currentDir, selectedFolder;
    private FolderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_picker);

        tvPath    = findViewById(R.id.tv_current_path);
        rv        = findViewById(R.id.rv_folders);
        btnSelect = findViewById(R.id.btn_select_folder);
        rv.setLayoutManager(new LinearLayoutManager(this));

        currentDir = Environment.getExternalStorageDirectory();
        navigateTo(currentDir);

        btnSelect.setOnClickListener(v -> {
            Intent r = new Intent();
            r.putExtra(RESULT_PATH, selectedFolder != null
                    ? selectedFolder.getAbsolutePath() : currentDir.getAbsolutePath());
            setResult(RESULT_OK, r);
            finish();
        });
    }

    private void navigateTo(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return;
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());

        List<File> list = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) if (f.isDirectory() && !f.isHidden()) list.add(f);
            Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        adapter = new FolderAdapter(list, f -> navigateTo(f), f -> {
            selectedFolder = f;
            btnSelect.setText("Select: " + f.getName());
        });
        rv.setAdapter(adapter);
    }

    @Override public void onBackPressed() {
        File parent = currentDir.getParentFile();
        if (parent != null && !currentDir.equals(Environment.getExternalStorageDirectory()))
            navigateTo(parent);
        else super.onBackPressed();
    }

    static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        interface Nav { void go(File f); }
        private final List<File> items; private final Nav onNav, onSel;
        private int sel = -1;
        FolderAdapter(List<File> items, Nav nav, Nav sel) { this.items = items; onNav = nav; onSel = sel; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_folder, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            File f = items.get(pos);
            h.name.setText(f.getName());
            int cnt = f.listFiles() != null ? f.listFiles().length : 0;
            h.count.setText(cnt + " items");
            h.arrow.setOnClickListener(v -> onNav.go(f));
            h.itemView.setOnClickListener(v -> {
                int old = sel; sel = h.getAdapterPosition();
                notifyItemChanged(old); notifyItemChanged(sel); onSel.go(f);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView name, count; View arrow;
            VH(View v) { super(v); name = v.findViewById(R.id.tv_folder_name); count = v.findViewById(R.id.tv_folder_count); arrow = v.findViewById(R.id.iv_folder_arrow); }
        }
    }
}
