package com.example.freqmul;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.io.File;

public class freqmul extends AppCompatActivity {
    private static final String PREFS_NAME = "freqmul_prefs";
    private static final String KEY_MUL_MIN = "mul_min", KEY_MUL_MAX = "mul_max", KEY_ROOT_PATH = "root_path";
    
    private EditText editMulMin, editMulMax;
    private TextView textRootPath;
    private String rootPath;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    
    private PlayerService mService;
    private boolean mBound = false;
    private boolean isSequentialMode = false;
    private int currentTrackIndex = 0;
    private boolean isUpdatingProgrammatically = false;

    private final float DEFAULT_MIN = (float) Math.pow(2.0, -1.0/12.0);
    private final float DEFAULT_MAX = (float) Math.pow(2.0, 1.0/12.0);

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            
            if (mService.getMp3play().getList() != null) {
                mp3List.clear();
                mp3List.addAll(mService.getMp3play().getList());
                adapter.notifyDataSetChanged();
            }
            
            mService.getMp3play().setListener(path -> {
                runOnUiThread(() -> {
                    mService.showNotification("Playing: " + new File(path).getName());
                    if (isSequentialMode) {
                        currentTrackIndex++;
                        if (currentTrackIndex < mp3List.size()) {
                            playTrackAtIndex(currentTrackIndex);
                        } else {
                            UiLog.log("Fin de la liste.");
                        }
                    }
                });
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) { mBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);

        editMulMin = findViewById(R.id.edit_mul_min);
        editMulMax = findViewById(R.id.edit_mul_max);
        textRootPath = findViewById(R.id.text_root_path);
        UiLog.init(this, findViewById(R.id.list_log));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootPath = prefs.getString(KEY_ROOT_PATH, "/sdcard/Music");
        editMulMin.setText(String.valueOf(prefs.getFloat(KEY_MUL_MIN, DEFAULT_MIN)));
        editMulMax.setText(String.valueOf(prefs.getFloat(KEY_MUL_MAX, DEFAULT_MAX)));
        textRootPath.setText(rootPath);

        adapter = new ArrayAdapter<>(this, R.layout.list_item_freq, mp3List);
        ((ListView)findViewById(R.id.list_mp3)).setAdapter(adapter);

        Intent intent = new Intent(this, PlayerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // --- LISTENERS RÉTABLIS ---

        findViewById(R.id.button_list_mp3).setOnClickListener(v -> {
            if (mBound) {
                mService.getMp3play().reloadList(rootPath);
                mp3List.clear();
                mp3List.addAll(mService.getMp3play().getList());
                adapter.notifyDataSetChanged();
                UiLog.log(mp3List.size() + " files indexed.");
            }
        });

        findViewById(R.id.button_play_all).setOnClickListener(v -> {
            isSequentialMode = true;
            currentTrackIndex = 0;
            playTrackAtIndex(currentTrackIndex);
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            isSequentialMode = false;
            if (mBound) {
                syncBounds();
                mService.getMp3play().playRandom();
            }
        });

        findViewById(R.id.button_next).setOnClickListener(v -> playNext());

        findViewById(R.id.button_stop).setOnClickListener(v -> {
            if (mBound) mService.getMp3play().stop();
        });

        findViewById(R.id.button_reset_freq).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText(String.valueOf(DEFAULT_MIN));
            editMulMax.setText(String.valueOf(DEFAULT_MAX));
            isUpdatingProgrammatically = false;
            applyFreqAndRestart();
        });

        findViewById(R.id.button_440).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText("1.0");
            editMulMax.setText("1.0");
            isUpdatingProgrammatically = false;
            applyFreqAndRestart();
        });

        findViewById(R.id.button_root_rep).setOnClickListener(v -> { 
            if (mBound) mService.getMp3play().stop(); 
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001); 
        });

        TextWatcher liveFreqWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isUpdatingProgrammatically && s.length() > 0 && !s.toString().equals(".") && !s.toString().equals("-")) {
                    applyFreqAndRestart();
                }
            }
        };
        editMulMin.addTextChangedListener(liveFreqWatcher);
        editMulMax.addTextChangedListener(liveFreqWatcher);
    }

    private void syncBounds() {
        if (!mBound) return;
        try {
            float min = Float.parseFloat(editMulMin.getText().toString());
            float max = Float.parseFloat(editMulMax.getText().toString());
            mService.getMp3play().setFrequencyBounds(min, max);
        } catch (Exception ignored) {}
    }

    private void applyFreqAndRestart() {
        syncBounds();
        if (mBound && mService.getMp3play().getCurrentPath() != null) {
            mService.getMp3play().restartCurrent();
            UiLog.log("Freq updated.");
        }
    }

    private void playTrackAtIndex(int index) {
        if (!mBound || mp3List.isEmpty() || index >= mp3List.size()) return;
        syncBounds();
        mService.getMp3play().playFile(mp3List.get(index));
    }

    private void playNext() {
        if (isSequentialMode) {
            currentTrackIndex = (currentTrackIndex + 1) % mp3List.size();
            playTrackAtIndex(currentTrackIndex);
        } else {
            if (mBound) mService.getMp3play().playRandom();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putFloat(KEY_MUL_MIN, Float.parseFloat(editMulMin.getText().toString()))
                .putFloat(KEY_MUL_MAX, Float.parseFloat(editMulMax.getText().toString()))
                .putString(KEY_ROOT_PATH, rootPath).apply();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                rootPath = "/sdcard/" + DocumentsContract.getTreeDocumentId(uri).split(":")[1];
                textRootPath.setText(rootPath);
                UiLog.log("New root: " + rootPath);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(connection);
            mBound = false;
        }
    }
}
