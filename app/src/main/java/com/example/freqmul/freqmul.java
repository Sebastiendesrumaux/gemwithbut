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
import java.util.List;
import java.util.Locale;

public class freqmul extends AppCompatActivity {

    private static final int REQ_PICK_ROOT = 1001;
    private static final int REQ_PERMISSION_STORAGE = 2001;
    private static final int REQ_PERMISSION_AUDIO = 2002;
    private static final int REQ_PERMISSION_CAMERA = 2003;

    private static final String PREFS_NAME = "syng_prefs";
    private static final String KEY_ROOT_PATH = "root_path";

    private Button buttonListMp3;
    private Button buttonRootRep;
    private Button buttonPlayRandom;
    private Button buttonStop;
    private Button buttonRecord;
    private Button buttonStopRec;

    private TextView textRootPath;
    private TextView textTranscript;
    private ListView listMp3;
    private FrameLayout camContainer;

    // UI cam
    private Spinner spinnerResolution;
    private Spinner spinnerFps;
    private CheckBox checkboxStab;

    private ArrayAdapter<String> adapter;
    private final ArrayList<String> mp3List = new ArrayList<>();

    private Mp3play mp3play;
    private String rootPath;

    private SpeechTranscriber speechTranscriber;
    private RecVideo recVideo;

    // caps/config cam
    private CameraCaps cameraCaps;
    private CamConfig camConfig;
    private ArrayAdapter<String> resAdapter;
    private ArrayAdapter<String> fpsAdapter;

    // transcription (50 tokens)
    private final ArrayDeque<String> lastTokens = new ArrayDeque<>();
    // gogogo (hors tournage)
    private final ArrayDeque<String> last3Go = new ArrayDeque<>();

    private boolean isRecording = false;

    // synchro audio
    private long recordingStartUs = -1L;
    private String recordingAudioPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_syng);
        UiLog.init(this, textTranscript, findViewById(R.id.scroll_transcript));

        UiLog.log(this, "ça log.");
        textTranscript.setText("ça log.");

        buttonListMp3 = findViewById(R.id.button_list_mp3);
        buttonRootRep = findViewById(R.id.button_root_rep);
        buttonPlayRandom = findViewById(R.id.button_play_random);
        buttonStop = findViewById(R.id.button_stop);
        buttonRecord = findViewById(R.id.button_record);
        buttonStopRec = findViewById(R.id.button_stop_rec);

        textRootPath = findViewById(R.id.text_root_path);
        textTranscript = findViewById(R.id.text_transcript);
        listMp3 = findViewById(R.id.list_mp3);
        camContainer = findViewById(R.id.cam_container);

        spinnerResolution = findViewById(R.id.spinner_resolution);
        spinnerFps = findViewById(R.id.spinner_fps);
        checkboxStab = findViewById(R.id.checkbox_stab);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3List);
        listMp3.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String defaultRoot = getDefaultMusicDirPath();
        rootPath = prefs.getString(KEY_ROOT_PATH, defaultRoot);
        updateRootPathLabel();

        mp3play = new Mp3play(this, rootPath);

        // fin mp3
        mp3play.setListener(path -> {
            if (recVideo != null && recVideo.isRecording()) {
                stopRecordingAndMux(-1L);
            }
        });

        buttonListMp3.setOnClickListener(v -> {
            if (ensureStoragePermission()) displayListFromMp3play();
        });

        buttonPlayRandom.setOnClickListener(v -> {
            if (ensureStoragePermission()) mp3play.playRandom();
        });

        buttonStop.setOnClickListener(v -> mp3play.stop());
        buttonRootRep.setOnClickListener(v -> openDirectoryPicker());

        buttonRecord.setOnClickListener(v -> startRecordingFromUI());
        buttonStopRec.setOnClickListener(v -> stopRecordingFromUI());

        // Transcription
        speechTranscriber = new SpeechTranscriber(this);
        speechTranscriber.setListener(new SpeechTranscriber.Listener() {
            @Override public void onPartial(String text) { processIncomingText(text); }
            @Override public void onFinal(String text) { processIncomingText(text); }
            @Override public void onErrorText(String errorText) { }
        });
        if (ensureAudioPermission()) speechTranscriber.start();

        // Camera config + preview
        if (ensureCameraPermission()) {
            initCameraUiAndPreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechTranscriber != null) {
            speechTranscriber.stop();
            speechTranscriber.destroy();
        }
        if (mp3play != null) mp3play.stop();
        if (recVideo != null) recVideo.stop();
    }

    // ============================================================
    // Camera UI init
    // ============================================================

    private void initCameraUiAndPreview() {
        try {
            cameraCaps = CameraCaps.queryMinFocal(this);
            camConfig = CamConfig.loadOrDefault(this, cameraCaps);

            // Résolutions
            ArrayList<String> resLabels = new ArrayList<>();
            for (Size s : cameraCaps.videoSizesSorted) {
                resLabels.add(s.getWidth() + "x" + s.getHeight());
            }
            resAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resLabels);
            spinnerResolution.setAdapter(resAdapter);
            spinnerResolution.setSelection(CamConfig.findSizeIndex(cameraCaps.videoSizesSorted, camConfig.width, camConfig.height), false);

            // FPS
            ArrayList<String> fpsLabels = new ArrayList<>();
            for (int f : cameraCaps.fpsValuesSorted) fpsLabels.add(String.valueOf(f));
            fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fpsLabels);
            spinnerFps.setAdapter(fpsAdapter);
            spinnerFps.setSelection(CamConfig.findFpsIndex(cameraCaps.fpsValuesSorted, camConfig.fps), false);

            // Stabilisation
            checkboxStab.setEnabled(cameraCaps.stabilizationSupported);
            checkboxStab.setChecked(camConfig.stabilization && cameraCaps.stabilizationSupported);

            // listeners UI
            spinnerResolution.setOnItemSelectedListener(new SimpleItemSelectedListener(pos -> {
                if (isRecording) return;
                Size s = cameraCaps.videoSizesSorted.get(pos);
                camConfig.width = s.getWidth();
                camConfig.height = s.getHeight();
                CamConfig.normalizeToCaps(camConfig, cameraCaps);
                CamConfig.save(this, camConfig);
                applyCamConfigToRecVideo();
            }));

            spinnerFps.setOnItemSelectedListener(new SimpleItemSelectedListener(pos -> {
                if (isRecording) return;
                camConfig.fps = cameraCaps.fpsValuesSorted.get(pos);
                CamConfig.normalizeToCaps(camConfig, cameraCaps);
                CamConfig.save(this, camConfig);
                applyCamConfigToRecVideo();
            }));

            checkboxStab.setOnCheckedChangeListener((btn, checked) -> {
                if (isRecording) {
                    btn.setChecked(camConfig.stabilization);
                    return;
                }
                camConfig.stabilization = checked && cameraCaps.stabilizationSupported;
                CamConfig.normalizeToCaps(camConfig, cameraCaps);
                CamConfig.save(this, camConfig);
                applyCamConfigToRecVideo();
            });

            // preview
            setupCameraPreview();

        } catch (Exception e) {
            Toast.makeText(this, "Cam init: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyCamConfigToRecVideo() {
        if (recVideo != null) {
            recVideo.applyConfig(camConfig);
        }
    }

    // ============================================================
    // Preview caméra
    // ============================================================

    private void setupCameraPreview() {
        if (recVideo != null) return;

        TextureView tv = new TextureView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        camContainer.removeAllViews();
        camContainer.addView(tv, lp);

        recVideo = new RecVideo(this, tv, camConfig);
        recVideo.startPreview();
    }

    // ============================================================
    // Recording (StopRec bouton) — speech off pendant tournage
    // ============================================================

    private long nowUs() { return System.nanoTime() / 1000L; }
    private long elapsedUs() {
        if (recordingStartUs <= 0) return -1L;
        return Math.max(0L, nowUs() - recordingStartUs);
    }

    private void startRecordingFromUI() {
        if (isRecording) return;
        if (!ensureCameraPermission()) return;

        String current = mp3play.getCurrentPath();
        if (current == null) {
            Toast.makeText(this, "Aucun mp3 en cours", Toast.LENGTH_SHORT).show();
            return;
        }

        // couper speech pendant tournage
        speechTranscriber.stop();

        isRecording = true;
        recordingStartUs = nowUs();
        recordingAudioPath = current;

        recVideo.startRecording();
        mp3play.restartCurrent();
    }

    private void stopRecordingFromUI() {
        if (!isRecording) return;
        stopRecordingAndMux(elapsedUs());
    }
private boolean waitForStableFile(String path,
                                  long checkDelayMs,
                                  int stableChecks,
                                  long timeoutMs) {
    File f = new File(path);
    long start = System.currentTimeMillis();
    long lastSize = -1;
    int sameCount = 0;

    while (System.currentTimeMillis() - start < timeoutMs) {
        if (!f.exists()) return false;

        long size = f.length();
        if (size > 0 && size == lastSize) {
            sameCount++;
            if (sameCount >= stableChecks) return true;
        } else {
            sameCount = 0;
            lastSize = size;
        }

        try { Thread.sleep(checkDelayMs); }
        catch (InterruptedException ignored) {}
    }
    return false;
}
    private void stopRecordingAndMux(long maxAudioUs) {
        //if (!isRecording) return;
runOnUiThread(() ->
    UiLog.log(this, "le bonheur serait presque de le faire debout")
);
        //UiLog.log(this, "le bonheur serait presque de le faire debout");

        String videoPath = null;
        if (recVideo != null && recVideo.isRecording()) {
            recVideo.stopRecording();
            videoPath = recVideo.getLastOutputPath();
        }
        isRecording = false;
        recordingStartUs = -1L;
        final String audioPath = recordingAudioPath;
        recordingAudioPath = null;

        // Réactiver la reconnaissance vocale immédiatement
        if (ensureAudioPermission()) speechTranscriber.start();

        // Vérifications STRICTES avant mux
        if (videoPath == null) {
            UiLog.log(this, "Aucune vidéo à muxer (chemin nul)");
            return;
        }

      

        if (audioPath == null) {
            UiLog.log(this, "Aucun audio à muxer");
            return;
        }
while (!waitForStableFile(videoPath, 10000, 3, 3000)) {
    UiLog.log(this, "Vidéo pas encore stabilisée :\n" + videoPath);
    return;
}

 
        // Mux réel → progress overlay
        final String finalVideoPath = videoPath;
        final String finalAudioPath = audioPath;
        final long finalMaxAudioUs = maxAudioUs;
        ProgressOverlay po = new ProgressOverlay(this);
        po.show("Préparation du mux…");

        new Thread(() -> {
            try {
                VideoMuxer.mux(
                        this,
                        finalVideoPath,
                        finalAudioPath,
                        finalMaxAudioUs,
                        step -> runOnUiThread(() -> po.update(step))
                );
            } catch (Exception e) {
                runOnUiThread(() -> UiLog.log(this, "Erreur mux:\n" + e.getMessage()));
            } finally {
                runOnUiThread(po::dismiss);
            }
        }).start();
    }

    // ============================================================
    // Transcription & gogogo (uniquement hors tournage)
    // ============================================================

    private void processIncomingText(String text) {
        if (text == null || text.isEmpty()) return;

        String[] parts = text.split("\\s+");
        for (String w : parts) {
            if (w.isEmpty()) continue;
            String lw = w.toLowerCase(Locale.ROOT);

            lastTokens.addLast(lw);
            while (lastTokens.size() > 50) lastTokens.removeFirst();

            if (!isRecording && lw.equals("go")) {
                last3Go.addLast("go");
                while (last3Go.size() > 3) last3Go.removeFirst();
                if (last3Go.size() == 3) {
                    lastTokens.addLast("[gogogo]");
                    while (lastTokens.size() > 50) lastTokens.removeFirst();
                    startRecordingFromUI();
                    last3Go.clear();
                }
            } else if (!lw.equals("go")) {
                last3Go.clear();
            }
        }
        updateTranscriptView();
    }

    private void updateTranscriptView() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String t : lastTokens) {
            if (!first) sb.append(' ');
            first = false;
            sb.append(t);
        }
        textTranscript.setText(sb.toString());
    }

    // ============================================================
    // MP3 / Root
    // ============================================================

    private String getDefaultMusicDirPath() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return (musicDir != null)
                ? musicDir.getAbsolutePath()
                : Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music";
    }

    private void updateRootPathLabel() {
        textRootPath.setText("Racine : " + rootPath);
    }

    private void displayListFromMp3play() {
        mp3List.clear();
        if (mp3play.getList() != null) mp3List.addAll(mp3play.getList());
        adapter.notifyDataSetChanged();
        Toast.makeText(this, mp3List.size() + " mp3", Toast.LENGTH_SHORT).show();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_ROOT);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ROOT && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            final int flags = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(treeUri, flags); }
            catch (Exception ignored) {}

            String path = getPathFromTreeUri(treeUri);
            if (path != null) {
                rootPath = path;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putString(KEY_ROOT_PATH, rootPath).apply();
                updateRootPathLabel();
                mp3play.reloadList(rootPath);
                displayListFromMp3play();
            }
        }
    }

    private String getPathFromTreeUri(Uri uri) {
        if (uri == null) return null;
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String docId = DocumentsContract.getTreeDocumentId(uri);
        if (docId == null) return null;
        String[] split = docId.split(":");
        if (split.length == 2 && "primary".equalsIgnoreCase(split[0])) {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
        }
        return null;
    }

    // ============================================================
    // Permissions
    // ============================================================

    private boolean ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_PERMISSION_STORAGE);
                return false;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMISSION_STORAGE);
                return false;
            }
        }
        return true;
    }

    private boolean ensureAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSION_AUDIO);
            return false;
        }
        return true;
    }

    private boolean ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMISSION_CAMERA);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speechTranscriber.start();
            }
        } else if (requestCode == REQ_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCameraUiAndPreview();
            }
        }
    }
}
