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

/* * PROTOCOLE DE MAINTENANCE DES VARIABLES D'ÉTAT
 * ---------------------------------------------
 * 1. PERSISTANCE : Sauver dans SharedPreferences (onPause).
 * 2. HANDSHAKE (onServiceConnected) : 
 * - Si Service ACTIF : Appeler aspirateStateFromService().
 * - Si Service NEUF  : Appeler pushStateToService().
 */

public class freqmul extends AppCompatActivity {
    private static final String PREFS_NAME = "freqmul_prefs";
    private EditText editMulMin, editMulMax;
    private TextView textRootPath;
    private String rootPath;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    
    private PlayerService mService;
    private boolean mBound = false;
    private boolean isUpdatingProgrammatically = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((PlayerService.LocalBinder) service).getService();
            mBound = true;
            
            handleHandshake();
            
            mService.getMp3play().setListener(path -> {
                runOnUiThread(() -> {
                    if (mService.isSequentialMode()) mService.playNext();
                    else mService.showNotification("Évasion Random : " + new File(path).getName());
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName arg) { mBound = false; }
    };

    private void handleHandshake() {
        if (mService.getMp3play().getCurrentPath() != null) {
            aspirateStateFromService();
            UiLog.log("Interface synchronisée sur le Service actif.");
        } else {
            pushStateToService();
            UiLog.log("Paramètres poussés vers le nouveau Service.");
        }
        refreshListFromService();
    }

    private void refreshListFromService() {
        if (mService != null && mService.getMp3play().getList() != null) {
            mp3List.clear();
            mp3List.addAll(mService.getMp3play().getList());
            adapter.notifyDataSetChanged();
            mService.setTrackList(mp3List);
        }
    }

    private void aspirateStateFromService() {
        isUpdatingProgrammatically = true;
        editMulMin.setText(String.valueOf(mService.getMp3play().getMulMin()));
        editMulMax.setText(String.valueOf(mService.getMp3play().getMulMax()));
        isUpdatingProgrammatically = false;
    }

    private void pushStateToService() {
        if (!mBound) return;
        try {
            float min = Float.parseFloat(editMulMin.getText().toString());
            float max = Float.parseFloat(editMulMax.getText().toString());
            mService.getMp3play().setFrequencyBounds(min, max);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);

        editMulMin = findViewById(R.id.edit_mul_min);
        editMulMax = findViewById(R.id.edit_mul_max);
        textRootPath = findViewById(R.id.text_root_path);
        UiLog.init(this, findViewById(R.id.list_log));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootPath = prefs.getString("root_path", "/sdcard/Music");
        editMulMin.setText(String.valueOf(prefs.getFloat("mul_min", 0.9438f)));
        editMulMax.setText(String.valueOf(prefs.getFloat("mul_max", 1.0594f)));
        textRootPath.setText(rootPath);

        adapter = new ArrayAdapter<>(this, R.layout.list_item_freq, mp3List);
        ((ListView)findViewById(R.id.list_mp3)).setAdapter(adapter);

        Intent intent = new Intent(this, PlayerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // --- LISTENERS BOUTONS ---
        findViewById(R.id.button_list_mp3).setOnClickListener(v -> {
            if (mBound) {
                mService.getMp3play().reloadList(rootPath);
                refreshListFromService();
                UiLog.log(mp3List.size() + " fichiers indexés.");
            }
        });

        findViewById(R.id.button_play_all).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(true);
                pushStateToService();
                mService.playTrackAtIndex(0);
            }
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            if (mBound) {
                mService.setSequentialMode(false);
                pushStateToService();
                mService.getMp3play().playRandom();
            }
        });

        findViewById(R.id.button_next).setOnClickListener(v -> { if (mBound) mService.playNext(); });
        findViewById(R.id.button_stop).setOnClickListener(v -> { if (mBound) mService.getMp3play().stop(); });

        // LE BOUTON RÉTABLI
        findViewById(R.id.button_root_rep).setOnClickListener(v -> { 
            if (mBound) mService.getMp3play().stop(); 
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001); 
        });

        findViewById(R.id.button_reset_freq).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText(String.valueOf(0.9438f)); // Valeur par défaut
            editMulMax.setText(String.valueOf(1.0594f));
            isUpdatingProgrammatically = false;
            pushStateToService();
            if (mBound && mService.getMp3play().getCurrentPath() != null) mService.getMp3play().restartCurrent();
        });

        findViewById(R.id.button_440).setOnClickListener(v -> {
            isUpdatingProgrammatically = true;
            editMulMin.setText("1.0");
            editMulMax.setText("1.0");
            isUpdatingProgrammatically = false;
            pushStateToService();
            if (mBound && mService.getMp3play().getCurrentPath() != null) mService.getMp3play().restartCurrent();
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isUpdatingProgrammatically && s.length() > 0 && !s.toString().equals(".") && !s.toString().equals("-")) {
                    pushStateToService();
                    if (mService != null && mService.getMp3play().getCurrentPath() != null) {
                        mService.getMp3play().restartCurrent();
                    }
                }
            }
        };
        editMulMin.addTextChangedListener(watcher);
        editMulMax.addTextChangedListener(watcher);
    }

    // MÉTHODE RÉTABLIE
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                rootPath = "/sdcard/" + DocumentsContract.getTreeDocumentId(uri).split(":")[1];
                textRootPath.setText(rootPath);
                if (mBound) {
                    mService.getMp3play().reloadList(rootPath);
                    refreshListFromService();
                }
                UiLog.log("Nouveau dossier : " + rootPath);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putFloat("mul_min", Float.parseFloat(editMulMin.getText().toString()))
            .putFloat("mul_max", Float.parseFloat(editMulMax.getText().toString()))
            .putString("root_path", rootPath).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) { unbindService(connection); mBound = false; }
    }
}
