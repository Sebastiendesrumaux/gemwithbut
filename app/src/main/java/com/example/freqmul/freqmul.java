package com.example.freqmul;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import java.util.ArrayList;

public class freqmul extends AppCompatActivity {
    private static final String PREFS_NAME = "freqmul_prefs";
    private EditText editMulMin, editMulMax;
    private TextView textRootPath;
    private Button btnUpdateList;
    private String rootUriStr = "";
    private final ArrayList<String> mp3List = new ArrayList<>();
    private Mp3Adapter adapter;
    private PlayerService mService;
    private boolean mBound = false;
    private boolean isUpdatingProgrammatically = false;

    private class Mp3Adapter extends ArrayAdapter<String> {
        public Mp3Adapter(Context context, ArrayList<String> objects) {
            super(context, R.layout.list_item_freq, objects);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = getLayoutInflater().inflate(R.layout.list_item_freq, parent, false);
            TextView tv = convertView.findViewById(android.R.id.text1);
            Button btnPlay = convertView.findViewById(R.id.btn_play_single);
            String uriStr = getItem(position);
            try { 
                String name = Uri.parse(uriStr).getLastPathSegment();
                if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);
                tv.setText(name); 
            } catch (Exception e) { tv.setText("Audio"); }
            
            int mode = (mService != null) ? mService.getLoopMode() : 0;
            if (mode == 1) {
                btnPlay.setText("∞1");
                btnPlay.setTextColor(Color.parseColor("#FFA500"));
            } else {
                btnPlay.setText("▶");
                btnPlay.setTextColor(Color.WHITE);
            }

            btnPlay.setOnClickListener(v -> {
                if (mBound) {
                    if (mService.getLoopMode() == 2) {
                        mService.setLoopMode(0);
                        updateUI();
                    }
                    mService.setSingleTrackMode(true);
                    mService.playTrackAtIndex(position);
                }
            });
            return convertView;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((PlayerService.LocalBinder) service).getService();
            mBound = true;
            
            // Installation du listener de scan
            mService.setScanListener(new PlayerService.ScanListener() {
                @Override
                public void onScanStarted() {
                    runOnUiThread(() -> {
                        btnUpdateList.setText("SCANNING...");
                        btnUpdateList.setEnabled(false);
                        btnUpdateList.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.DKGRAY));
                    });
                }

                @Override
                public void onScanFinished(int count) {
                    runOnUiThread(() -> {
                        btnUpdateList.setText("UPDATE LIST");
                        btnUpdateList.setEnabled(true);
                        btnUpdateList.setBackgroundTintList(null); // Reset couleur
                        refreshListFromService();
                        Toast.makeText(freqmul.this, "Scan terminé : " + count + " fichiers.", Toast.LENGTH_SHORT).show();
                    });
                }
            });

            handleHandshake();
        }
        @Override public void onServiceDisconnected(ComponentName arg) { mBound = false; }
    };

    private void handleHandshake() {
        if (mService.getMp3play().getCurrentPath() != null) {
            isUpdatingProgrammatically = true;
            editMulMin.setText(String.valueOf(mService.getMp3play().getMulMin()));
            editMulMax.setText(String.valueOf(mService.getMp3play().getMulMax()));
            isUpdatingProgrammatically = false;
        }
        refreshListFromService();
        updateUI();
    }

    private void refreshListFromService() {
        if (mService != null && mService.getMp3play().getList() != null) {
            mp3List.clear();
            mp3List.addAll(mService.getMp3play().getList());
            adapter.notifyDataSetChanged();
            mService.setTrackList(mp3List);
        }
    }

    private void updateUI() {
        if (!mBound) return;
        int mode = mService.getLoopMode();
        Button btnInf = findViewById(R.id.button_infinite);
        Button btnAll = findViewById(R.id.button_play_all);
        
        if (mode == 1) {
            btnInf.setText("∞ TRK ON");
            btnInf.setTextColor(Color.parseColor("#FFA500"));
            btnAll.setText("ALL");
            btnAll.setTextColor(Color.WHITE);
        } else if (mode == 2) {
            btnInf.setText("∞ ALL ON");
            btnInf.setTextColor(Color.parseColor("#FFA500"));
            btnAll.setText("ALL ∞");
            btnAll.setTextColor(Color.parseColor("#FFA500"));
        } else {
            btnInf.setText("∞ OFF");
            btnInf.setTextColor(Color.WHITE);
            btnAll.setText("ALL");
            btnAll.setTextColor(Color.WHITE);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);
        
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO}, 101);

        editMulMin = findViewById(R.id.edit_mul_min);
        editMulMax = findViewById(R.id.edit_mul_max);
        textRootPath = findViewById(R.id.text_root_path);
        btnUpdateList = findViewById(R.id.button_list_mp3);
        UiLog.init(this, findViewById(R.id.list_log));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootUriStr = prefs.getString("root_uri", "");
        editMulMin.setText(String.valueOf(prefs.getFloat("mul_min", 0.9438f)));
        editMulMax.setText(String.valueOf(prefs.getFloat("mul_max", 1.0594f)));
        textRootPath.setText(rootUriStr.isEmpty() ? "Choisir dossier" : "SAF Actif");

        adapter = new Mp3Adapter(this, mp3List);
        ((ListView)findViewById(R.id.list_mp3)).setAdapter(adapter);

        Intent intent = new Intent(this, PlayerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        findViewById(R.id.button_infinite).setOnClickListener(v -> {
            if (mBound) {
                int nextMode = (mService.getLoopMode() + 1) % 3;
                mService.setLoopMode(nextMode);
                updateUI();
            }
        });

        btnUpdateList.setOnClickListener(v -> {
            if (mBound && !rootUriStr.isEmpty()) {
                mService.reloadListAsync(rootUriStr);
            }
        });

        findViewById(R.id.button_play_all).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(true);
                mService.setSingleTrackMode(false);
                mService.playTrackAtIndex(0);
            }
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(false);
                mService.setSingleTrackMode(false);
                mService.playNext();
            }
        });

        findViewById(R.id.button_next).setOnClickListener(v -> { if (mBound) mService.playNext(); });
        findViewById(R.id.button_stop).setOnClickListener(v -> { if (mBound) mService.stopPlayback(); });
        
        findViewById(R.id.button_root_rep).setOnClickListener(v -> { 
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001); 
        });

        findViewById(R.id.button_reset_freq).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText("0.9438"); editMulMax.setText("1.0594");
            isUpdatingProgrammatically = false;
            if (mBound) {
                mService.getMp3play().setFrequencyBounds(0.9438f, 1.0594f);
                if (mService.getMp3play().getCurrentPath() != null) mService.getMp3play().restartCurrent();
            }
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mBound && !isUpdatingProgrammatically && s.length() > 0) {
                    try {
                        float min = Float.parseFloat(editMulMin.getText().toString());
                        float max = Float.parseFloat(editMulMax.getText().toString());
                        mService.getMp3play().setFrequencyBounds(min, max);
                    } catch (Exception ignored) {}
                }
            }
        };
        editMulMin.addTextChangedListener(watcher);
        editMulMax.addTextChangedListener(watcher);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                rootUriStr = treeUri.toString();
                textRootPath.setText("SAF Actif");
                if (mBound) {
                    mService.reloadListAsync(rootUriStr);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putFloat("mul_min", Float.parseFloat(editMulMin.getText().toString()))
            .putFloat("mul_max", Float.parseFloat(editMulMax.getText().toString()))
            .putString("root_uri", rootUriStr).apply();
    }
}
