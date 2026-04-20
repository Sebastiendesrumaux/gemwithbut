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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

public class freqmul extends AppCompatActivity {

    private static final int REQ_PICK_ROOT = 1001, REQ_PERMISSION_STORAGE = 2001, REQ_PERMISSION_AUDIO = 2002, REQ_PERMISSION_CAMERA = 2003;
    private static final String PREFS_NAME = "freqmul_prefs", KEY_ROOT_PATH = "root_path";

    private Button buttonListMp3, buttonRootRep, buttonPlayRandom, buttonStop, buttonRecord, buttonStopRec, buttonToggleSpeech;
    private TextView textRootPath, textTranscript;
    private ListView listMp3;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private Mp3play mp3play;
    private String rootPath;
    private SpeechTranscriber speechTranscriber;
    private final ArrayDeque<String> lastTokens = new ArrayDeque<>();
    private final ArrayDeque<String> last3Go = new ArrayDeque<>();
    private boolean isRecording = false, isSpeechManualActive = false;
    private long recordingStartUs = -1L;
    private String recordingAudioPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_freqmul);

        textRootPath = findViewById(R.id.text_root_path);
        textTranscript = findViewById(R.id.text_transcript);
        buttonListMp3 = findViewById(R.id.button_list_mp3);
        buttonRootRep = findViewById(R.id.button_root_rep);
        buttonPlayRandom = findViewById(R.id.button_play_random);
        buttonStop = findViewById(R.id.button_stop);
        buttonToggleSpeech = findViewById(R.id.button_toggle_speech);
        listMp3 = findViewById(R.id.list_mp3);

        UiLog.init(this, textTranscript, findViewById(R.id.scroll_transcript));
        UiLog.log("FreqMul prêt.");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3List);
        listMp3.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootPath = prefs.getString(KEY_ROOT_PATH, getDefaultMusicDirPath());
        updateRootPathLabel();

        mp3play = new Mp3play(this, rootPath);

        buttonListMp3.setOnClickListener(v -> { if (ensureStoragePermission()) { mp3play.reloadList(rootPath); displayListFromMp3play(); } });
        buttonRootRep.setOnClickListener(v -> openDirectoryPicker());
        buttonPlayRandom.setOnClickListener(v -> { if (ensureStoragePermission()) mp3play.playRandom(); });
        buttonStop.setOnClickListener(v -> mp3play.stop());

        buttonToggleSpeech.setOnClickListener(v -> {
            if (ensureAudioPermission()) {
                isSpeechManualActive = !isSpeechManualActive;
                if (isSpeechManualActive) { speechTranscriber.start(); buttonToggleSpeech.setText("Speech: ON"); }
                else { speechTranscriber.stop(); buttonToggleSpeech.setText("Speech: OFF"); }
            }
        });

        speechTranscriber = new SpeechTranscriber(this);
        speechTranscriber.setListener(new SpeechTranscriber.Listener() {
            @Override public void onPartial(String text) { processIncomingText(text); }
            @Override public void onFinal(String text) { processIncomingText(text); }
            @Override public void onErrorText(String errorText) { }
        });
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_ROOT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ROOT && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            // On tente de résoudre le chemin
            String path = getPathFromTreeUri(treeUri);
            
            if (path != null) {
                rootPath = path;
                UiLog.log("Nouveau dossier : " + rootPath);
                
                // Sauvegarde
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_ROOT_PATH, rootPath).apply();
                
                // Mise à jour UI
                updateRootPathLabel();
                
                // Forcer le rechargement immédiat
                mp3play.reloadList(rootPath);
                displayListFromMp3play();
            } else {
                UiLog.log("Erreur : Impossible de résoudre le chemin de " + treeUri.toString());
                Toast.makeText(this, "Utilise le dossier Musique interne !", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getPathFromTreeUri(Uri uri) {
        if (uri == null) return null;
        String docId = DocumentsContract.getTreeDocumentId(uri);
        String[] split = docId.split(":");
        if ("primary".equalsIgnoreCase(split[0])) {
            return Environment.getExternalStorageDirectory() + "/" + (split.length > 1 ? split[1] : "");
        }
        // Fallback pour les chemins de type Download ou autres
        if (docId.contains("msf:")) {
             return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getParent() + "/" + split[1];
        }
        return null;
    }

    private void updateRootPathLabel() { textRootPath.setText("Dossier : " + rootPath); }

    private void displayListFromMp3play() {
        mp3List.clear();
        ArrayList<String> list = mp3play.getList();
        if (list != null && !list.isEmpty()) {
            mp3List.addAll(list);
            UiLog.log(list.size() + " morceaux trouvés.");
        } else {
            UiLog.log("Aucun MP3 trouvé dans ce répertoire.");
        }
        adapter.notifyDataSetChanged();
    }

    private String getDefaultMusicDirPath() { 
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC); 
        return d != null ? d.getAbsolutePath() : "/sdcard/Music"; 
    }

    private boolean ensureStoragePermission() { 
        String p = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{p}, REQ_PERMISSION_STORAGE); return false; }
        return true;
    }

    private boolean ensureAudioPermission() { if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSION_AUDIO); return false; } return true; }
    private void processIncomingText(String text) { /* ta logique existante */ }
    private void updateTranscriptView() { /* ta logique existante */ }
}
