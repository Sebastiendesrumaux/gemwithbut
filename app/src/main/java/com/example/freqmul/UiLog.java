package com.example.freqmul;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;

public class UiLog {
    private static Activity activity;
    private static ListView listView;
    private static ArrayList<String> logLines = new ArrayList<>();
    private static ArrayAdapter<String> adapter;

    public static void init(Activity act, ListView lv) {
        activity = act; listView = lv;
        adapter = new ArrayAdapter<String>(act, R.layout.list_item_log, logLines) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) convertView = activity.getLayoutInflater().inflate(R.layout.list_item_log, parent, false);
                String line = getItem(position);
                TextView tv = convertView.findViewById(R.id.log_text);
                Button btnFull = convertView.findViewById(R.id.btn_copy_full);
                Button btnName = convertView.findViewById(R.id.btn_copy_name);
                tv.setText(line);
                if (line.contains(" : ")) {
                    btnFull.setVisibility(View.VISIBLE); btnName.setVisibility(View.VISIBLE);
                    final String path = line.split(" : ")[1];
                    final String fileName = new File(path).getName();
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    btnFull.setOnClickListener(v -> { cm.setPrimaryClip(ClipData.newPlainText("full", path)); Toast.makeText(activity, "Full path copied!", Toast.LENGTH_SHORT).show(); });
                    btnName.setOnClickListener(v -> { cm.setPrimaryClip(ClipData.newPlainText("name", fileName)); Toast.makeText(activity, "Filename copied!", Toast.LENGTH_SHORT).show(); });
                } else {
                    btnFull.setVisibility(View.GONE); btnName.setVisibility(View.GONE);
                }
                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }
    public static void log(String msg) {
        if (activity == null || adapter == null) return;
        activity.runOnUiThread(() -> { logLines.add(msg); adapter.notifyDataSetChanged(); listView.setSelection(adapter.getCount() - 1); });
    }
}
