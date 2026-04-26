package com.example.freqmul;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class freqmul extends AppCompatActivity {
    private static final String PREFS_NAME = "freqmul_prefs";
    private static final String KEY_MUL_MIN = "mul_min", KEY_MUL_MAX = "mul_max", KEY_ROOT_PATH = "root_path";
    private EditText editMulMin, editMulMax;
    private TextView textRootPath;
    private Mp3play mp3play;
    private String rootPath;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private int currentTrackIndex = 0;
    private boolean isSequentialMode = false;
    private boolean isUpdatingProgrammatically = false;

    private final float DEFAULT_MIN = (float) Math.pow(2.0, -1.0/12.0);
    private final float DEFAULT_MAX = (float) Math.pow(2.0, 1.0/12.0);

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

        mp3play = new Mp3play(this, rootPath);
        mp3play.setListener(path -> {
            if (isSequentialMode) {
                currentTrackIndex++;
                if (currentTrackIndex < mp3List.size()) {
                    playTrackAtIndex(currentTrackIndex);
                } else {
                    UiLog.log("Fin de la liste.");
                }
            }
        });

        TextWatcher liveFreqWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isUpdatingProgrammatically && s.length() > 0 && !s.toString().equals(".") && !s.toString().equals("-")) {
                    applyFreqAndRestart(true);
                }
            }
        };

        editMulMin.addTextChangedListener(liveFreqWatcher);
        editMulMax.addTextChangedListener(liveFreqWatcher);

        findViewById(R.id.button_reset_freq).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText(String.valueOf(DEFAULT_MIN));
            editMulMax.setText(String.valueOf(DEFAULT_MAX));
            isUpdatingProgrammatically = false;
            applyFreqAndRestart(true);
        });

        findViewById(R.id.button_440).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText("1.0");
            editMulMax.setText("1.0");
            isUpdatingProgrammatically = false;
            applyFreqAndRestart(true);
        });

        findViewById(R.id.button_list_mp3).setOnClickListener(v -> {
            mp3play.reloadList(rootPath);
            mp3List.clear();
            if (mp3play.getList() != null) mp3List.addAll(mp3play.getList());
            adapter.notifyDataSetChanged();
            UiLog.log(mp3List.size() + " music files indexed.");
        });

        findViewById(R.id.button_play_all).setOnClickListener(v -> {
            isSequentialMode = true;
            currentTrackIndex = 0;
            playTrackAtIndex(currentTrackIndex);
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            isSequentialMode = false;
            syncBoundsOnly();
            mp3play.playRandom();
        });

        findViewById(R.id.button_next).setOnClickListener(v -> playNext());
        findViewById(R.id.button_stop).setOnClickListener(v -> {
            mp3play.stop();
            stopService(new Intent(this, PlayerService.class));
        });
        findViewById(R.id.button_root_rep).setOnClickListener(v -> { 
            mp3play.stop(); 
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001); 
        });
    }

    private void syncBoundsOnly() {
        try {
            float min = Float.parseFloat(editMulMin.getText().toString());
            float max = Float.parseFloat(editMulMax.getText().toString());
            mp3play.setFrequencyBounds(min, max);
        } catch (Exception ignored) {}
    }

    private void applyFreqAndRestart(boolean shouldLog) {
        syncBoundsOnly();
        if (mp3play.getCurrentPath() != null) {
            mp3play.restartCurrent();
            if (shouldLog) UiLog.log("Frequency parameters updated.");
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
                mp3play = new Mp3play(this, rootPath);
                UiLog.log("New folder: " + rootPath);
            }
        }
    }

    private void playTrackAtIndex(int index) {
        if (mp3List == null || mp3List.isEmpty() || index >= mp3List.size()) return;
        syncBoundsOnly();
        
        // --- LANCEMENT DU SERVICE ---
        Intent serviceIntent = new Intent(this, PlayerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        mp3play.playFile(mp3List.get(index));
    }

    private void playNext() {
        if (isSequentialMode) {
            currentTrackIndex++;
            if (mp3List != null && currentTrackIndex >= mp3List.size()) currentTrackIndex = 0;
            playTrackAtIndex(currentTrackIndex);
        } else {
            // Pour le mode random, on s'assure aussi de réveiller le service
            Intent serviceIntent = new Intent(this, PlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
            
            mp3play.playRandom();
        }
    }
}
