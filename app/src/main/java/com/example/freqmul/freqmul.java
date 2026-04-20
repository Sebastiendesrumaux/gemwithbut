package com.example.freqmul;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class freqmul extends AppCompatActivity {

    private static final String PREFS_NAME = "freqmul_prefs";
    private static final String KEY_MUL_MIN = "mul_min", KEY_MUL_MAX = "mul_max", KEY_ROOT_PATH = "root_path";

    private EditText editMulMin, editMulMax;
    private TextView textRootPath, textTranscript;
    private Mp3play mp3play;
    private String rootPath;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // Valeurs par défaut (Demi-ton)
    private final float DEFAULT_MIN = (float) Math.pow(2.0, -1.0/12.0);
    private final float DEFAULT_MAX = (float) Math.pow(2.0, 1.0/12.0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);

        editMulMin = findViewById(R.id.edit_mul_min);
        editMulMax = findViewById(R.id.edit_mul_max);
        Button buttonResetFreq = findViewById(R.id.button_reset_freq);
        buttonResetFreq.setOnClickListener(v -> { editMulMin.setText(String.valueOf(DEFAULT_MIN)); editMulMax.setText(String.valueOf(DEFAULT_MAX)); UiLog.log("Fréquences réinitialisées."); });
        textRootPath = findViewById(R.id.text_root_path);
        textTranscript = findViewById(R.id.text_transcript);
        ListView listMp3 = findViewById(R.id.list_mp3);

        UiLog.init(this, textTranscript, findViewById(R.id.scroll_transcript));

        // Chargement des valeurs sauvegardées
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float savedMin = prefs.getFloat(KEY_MUL_MIN, DEFAULT_MIN);
        float savedMax = prefs.getFloat(KEY_MUL_MAX, DEFAULT_MAX);
        rootPath = prefs.getString(KEY_ROOT_PATH, "/sdcard/Music");

        editMulMin.setText(String.valueOf(savedMin));
        editMulMax.setText(String.valueOf(savedMax));
        textRootPath.setText("Dossier : " + rootPath);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3List);
        listMp3.setAdapter(adapter);

        mp3play = new Mp3play(this, rootPath);

        findViewById(R.id.button_list_mp3).setOnClickListener(v -> { mp3play.reloadList(rootPath); displayList(); });
        findViewById(R.id.button_play_random).setOnClickListener(v -> { try { float min = Float.parseFloat(editMulMin.getText().toString()); float max = Float.parseFloat(editMulMax.getText().toString()); mp3play.setFrequencyBounds(min, max); mp3play.playRandom(); } catch (Exception e) { UiLog.log("Erreur bornes"); } });
        findViewById(R.id.button_stop).setOnClickListener(v -> mp3play.stop());
        findViewById(R.id.button_root_rep).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, 1001);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Sauvegarde automatique en quittant ou changeant d'appli
        try {
            float min = Float.parseFloat(editMulMin.getText().toString());
            float max = Float.parseFloat(editMulMax.getText().toString());
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putFloat(KEY_MUL_MIN, min)
                .putFloat(KEY_MUL_MAX, max)
                .apply();
        } catch (Exception ignored) {}
    }

    private void displayList() {
        mp3List.clear();
        if (mp3play.getList() != null) mp3List.addAll(mp3play.getList());
        adapter.notifyDataSetChanged();
        UiLog.log(mp3List.size() + " morceaux indexés.");
    }
    
    // ... Garde tes méthodes onActivityResult et getPathFromTreeUri ...
}
