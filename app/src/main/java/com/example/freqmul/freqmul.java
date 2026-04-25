package com.example.freqmul;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
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

    private final float DEFAULT_MIN = (float) Math.pow(2.0, -1.0/12.0);
    private final float DEFAULT_MAX = (float) Math.pow(2.0, 1.0/12.0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);

        editMulMin = findViewById(R.id.edit_mul_min);
        editMulMax = findViewById(R.id.edit_mul_max);
        textRootPath = findViewById(R.id.text_root_path);
        textTranscript = findViewById(R.id.text_transcript);
        UiLog.init(this, textTranscript, findViewById(R.id.scroll_transcript));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootPath = prefs.getString(KEY_ROOT_PATH, "/sdcard/Music");
        editMulMin.setText(String.valueOf(prefs.getFloat(KEY_MUL_MIN, DEFAULT_MIN)));
        editMulMax.setText(String.valueOf(prefs.getFloat(KEY_MUL_MAX, DEFAULT_MAX)));
        textRootPath.setText(rootPath);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3List);
        ((ListView)findViewById(R.id.list_mp3)).setAdapter(adapter);

        mp3play = new Mp3play(this, rootPath);

        findViewById(R.id.button_reset_freq).setOnClickListener(v -> {
            editMulMin.setText(String.valueOf(DEFAULT_MIN));
            editMulMax.setText(String.valueOf(DEFAULT_MAX));
            UiLog.log("Frequences réinitialisées.");
        });

        findViewById(R.id.button_list_mp3).setOnClickListener(v -> {
            mp3play.reloadList(rootPath);
            mp3List.clear();
            if (mp3play.getList() != null) mp3List.addAll(mp3play.getList());
            adapter.notifyDataSetChanged();
            UiLog.log(mp3List.size() + " morceaux indexés.");
        });

        findViewById(R.id.button_play_random).setOnClickListener(v -> {
            try {
                mp3play.setFrequencyBounds(Float.parseFloat(editMulMin.getText().toString()), 
                                           Float.parseFloat(editMulMax.getText().toString()));
                mp3play.playRandom();
            } catch (Exception e) { UiLog.log("Erreur de saisie"); }
        });

        findViewById(R.id.button_stop).setOnClickListener(v -> mp3play.stop());
        findViewById(R.id.button_root_rep).setOnClickListener(v -> { mp3play.stop(); startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001); });
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
                UiLog.log("Nouveau dossier : " + rootPath);
            }
        }
    }
}
