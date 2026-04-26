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
                mService.setTrackList(mp3List);
            }
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

        findViewById(R.id.button_list_mp3).setOnClickListener(v -> {
            if (mBound) {
                mService.getMp3play().reloadList(rootPath);
                mp3List.clear();
                mp3List.addAll(mService.getMp3play().getList());
                adapter.notifyDataSetChanged();
                mService.setTrackList(mp3List);
            }
        });

        findViewById(R.id.button_play_all).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(true);
                syncBounds();
                mService.playTrackAtIndex(0);
            }
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(false);
                syncBounds();
                mService.getMp3play().playRandom();
            }
        });

        findViewById(R.id.button_stop).setOnClickListener(v -> {
            if (mBound) mService.getMp3play().stop();
        });

        findViewById(R.id.button_next).setOnClickListener(v -> {
            if (mBound) mService.playNext();
        });

        // Les boutons de reset et TextWatcher restent identiques...
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
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                rootPath = "/sdcard/" + DocumentsContract.getTreeDocumentId(uri).split(":")[1];
                textRootPath.setText(rootPath);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) { unbindService(connection); mBound = false; }
    }
}
