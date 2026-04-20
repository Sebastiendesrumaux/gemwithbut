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
import android.view.TextureView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Size;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

public class freqmul extends AppCompatActivity {

    private static final int REQ_PICK_ROOT = 1001;
    private static final int REQ_PERMISSION_STORAGE = 2001;
    private static final int REQ_PERMISSION_AUDIO = 2002;
    private static final int REQ_PERMISSION_CAMERA = 2003;

    private static final String PREFS_NAME = "syng_prefs";
    private static final String KEY_ROOT_PATH = "root_path";

    private Button buttonListMp3, buttonRootRep, buttonPlayRandom, buttonStop, buttonRecord, buttonStopRec;
    private TextView textRootPath, textTranscript;
    private ListView listMp3;
    private FrameLayout camContainer;
    private Spinner spinnerResolution, spinnerFps;
    private CheckBox checkboxStab;

    private ArrayAdapter<String> adapter;
    private final ArrayList<String> mp3List = new ArrayList<>();
    private Mp3play mp3play;
    private String rootPath;
    private SpeechTranscriber speechTranscriber;
    private RecVideo recVideo;
    private CameraCaps cameraCaps;
    private CamConfig camConfig;
    private final ArrayDeque<String> lastTokens = new ArrayDeque<>();
    private final ArrayDeque<String> last3Go = new ArrayDeque<>();
    private boolean isRecording = false;
    private long recordingStartUs = -1L;
    private String recordingAudioPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_syng);

        // 1. D'ABORD on lie TOUTES les vues
        textRootPath = findViewById(R.id.text_root_path);
        textTranscript = findViewById(R.id.text_transcript);
        buttonListMp3 = findViewById(R.id.button_list_mp3);
        buttonRootRep = findViewById(R.id.button_root_rep);
        buttonPlayRandom = findViewById(R.id.button_play_random);
        buttonStop = findViewById(R.id.button_stop);
        buttonRecord = findViewById(R.id.button_record);
        buttonStopRec = findViewById(R.id.button_stop_rec);
        listMp3 = findViewById(R.id.list_mp3);
        camContainer = findViewById(R.id.cam_container);
        spinnerResolution = findViewById(R.id.spinner_resolution);
        spinnerFps = findViewById(R.id.spinner_fps);
        checkboxStab = findViewById(R.id.checkbox_stab);

        // 2. ENSUITE on initialise le log (maintenant que textTranscript n'est plus null)
        UiLog.init(this, textTranscript, findViewById(R.id.scroll_transcript));
        UiLog.log("Système FreqMul initialisé.");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3List);
        listMp3.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        rootPath = prefs.getString(KEY_ROOT_PATH, getDefaultMusicDirPath());
        updateRootPathLabel();

        mp3play = new Mp3play(this, rootPath);
        mp3play.setListener(path -> {
            if (recVideo != null && recVideo.isRecording()) stopRecordingAndMux(-1L);
        });

        buttonListMp3.setOnClickListener(v -> { if (ensureStoragePermission()) displayListFromMp3play(); });
        buttonPlayRandom.setOnClickListener(v -> { if (ensureStoragePermission()) mp3play.playRandom(); });
        buttonStop.setOnClickListener(v -> mp3play.stop());
        buttonRootRep.setOnClickListener(v -> openDirectoryPicker());
        buttonRecord.setOnClickListener(v -> startRecordingFromUI());
        buttonStopRec.setOnClickListener(v -> stopRecordingFromUI());

        speechTranscriber = new SpeechTranscriber(this);
        speechTranscriber.setListener(new SpeechTranscriber.Listener() {
            @Override public void onPartial(String text) { processIncomingText(text); }
            @Override public void onFinal(String text) { processIncomingText(text); }
            @Override public void onErrorText(String errorText) { }
        });
        
        if (ensureAudioPermission()) speechTranscriber.start();
        if (ensureCameraPermission()) initCameraUiAndPreview();
    }

    private String getDefaultMusicDirPath() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return (musicDir != null) ? musicDir.getAbsolutePath() : Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
    }

    private void updateRootPathLabel() { textRootPath.setText("Racine : " + rootPath); }

    private void displayListFromMp3play() {
        mp3List.clear();
        if (mp3play.getList() != null) mp3List.addAll(mp3play.getList());
        adapter.notifyDataSetChanged();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_ROOT);
    }

    private void processIncomingText(String text) {
        if (text == null || text.isEmpty()) return;
        String[] parts = text.split("\\s+");
        for (String w : parts) {
            String lw = w.toLowerCase(Locale.ROOT);
            lastTokens.addLast(lw);
            while (lastTokens.size() > 50) lastTokens.removeFirst();
            if (!isRecording && lw.equals("go")) {
                last3Go.addLast("go");
                if (last3Go.size() == 3) {
                    startRecordingFromUI();
                    last3Go.clear();
                }
            } else if (!lw.equals("go")) last3Go.clear();
        }
        updateTranscriptView();
    }

    private void updateTranscriptView() {
        StringBuilder sb = new StringBuilder();
        for (String t : lastTokens) sb.append(t).append(' ');
        textTranscript.setText(sb.toString().trim());
    }

    private boolean ensureStoragePermission() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_PERMISSION_STORAGE);
            return false;
        }
        return true;
    }

    private boolean ensureAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSION_AUDIO);
            return false;
        }
        return true;
    }

    private boolean ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMISSION_CAMERA);
            return false;
        }
        return true;
    }

    private void initCameraUiAndPreview() { /* Garde ton code existant ici */ }
    private void startRecordingFromUI() { /* Garde ton code existant ici */ }
    private void stopRecordingFromUI() { /* Garde ton code existant ici */ }
    private void stopRecordingAndMux(long maxAudioUs) { /* Garde ton code existant ici */ }
}
