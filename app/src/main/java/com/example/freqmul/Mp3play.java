package com.example.freqmul;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class Mp3play {

    public interface Mp3Listener { void onTrackCompletion(String path); }

    private final Context context;
    private ArrayList<String> mp3;
    private final Random rng = new Random();
    private MediaPlayer mediaPlayer = null;
    private String currentPath = null;
    private Mp3Listener listener;
    
    private float mulMin = 0.9438f;
    private float mulMax = 1.0594f;

    public Mp3play(Context context, String rootPath) {
        this.context = context;
        mp3 = FileUtilsMp3.loadJson();
        if (mp3 == null || mp3.isEmpty()) reloadList(rootPath);
    }

    public void setFrequencyBounds(float min, float max) {
        this.mulMin = min;
        this.mulMax = max;
    }

    public void setListener(Mp3Listener listener) { this.listener = listener; }
    public ArrayList<String> getList() { return mp3; }

    public void reloadList(String rootPath) {
        File root = new File(rootPath);
        mp3 = FileUtilsMp3.scanMp3(root);
        FileUtilsMp3.saveJson(context, mp3);
    }

    public void stop() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void playRandom() {
        if (mp3 == null || mp3.isEmpty()) return;
        playFile(mp3.get(rng.nextInt(mp3.size())));
    }

    public void playFile(String path) {
        stop();
        currentPath = path;
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnCompletionListener(mp -> {
                if (listener != null) listener.onTrackCompletion(currentPath);
                playRandom(); 
            });
            mediaPlayer.prepare();

            // --- LA MAGIE FREQMUL ---
            float k = mulMin + rng.nextFloat() * (mulMax - mulMin);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(k);
                params.setPitch(k); // Synchronisation vitesse/hauteur pour effet analogique
                mediaPlayer.setPlaybackParams(params);
            }
            // ------------------------

            mediaPlayer.start();
            UiLog.log("Playing (x" + String.format("%.4f", k) + ") : " + path);
        } catch (Exception e) {
            UiLog.log("Player Error: " + e.getMessage());
        }
    }

    public String getCurrentPath() { return currentPath; }
    public void restartCurrent() { if (currentPath != null) playFile(currentPath); }
}
