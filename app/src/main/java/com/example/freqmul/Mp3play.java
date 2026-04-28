package com.example.freqmul;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
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
    private float mulMin = 0.9438f, mulMax = 1.0594f;

    public Mp3play(Context context, String rootPath) {
        this.context = context;
        this.mp3 = FileUtilsMp3.loadJson();
        if (mp3 == null || mp3.isEmpty()) reloadList(rootPath);
    }

    public void setFrequencyBounds(float min, float max) {
        this.mulMin = Math.max(0.1f, Math.min(min, 6.0f));
        this.mulMax = Math.max(0.1f, Math.min(max, 6.0f));
    }

    public boolean isPlaying() {
        try { return mediaPlayer != null && mediaPlayer.isPlaying(); }
        catch (Exception e) { return false; }
    }

    public float getMulMin() { return mulMin; }
    public float getMulMax() { return mulMax; }
    public void setListener(Mp3Listener listener) { this.listener = listener; }
    public ArrayList<String> getList() { return mp3; }

    public void reloadList(String rootPath) {
        File root = new File(rootPath);
        mp3 = FileUtilsMp3.scanMp3(root);
        FileUtilsMp3.saveJson(context, mp3);
        FileLogger.log(context, "📂 Scan dossier : " + rootPath + " (" + mp3.size() + " fichiers)");
    }

    public void stop() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void playRandom() {
        if (mp3 != null && !mp3.isEmpty()) playFile(mp3.get(rng.nextInt(mp3.size())));
    }

    public void playFile(String path) {
        stop();
        currentPath = path;
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnCompletionListener(mp -> { if (listener != null) listener.onTrackCompletion(currentPath); });
            mediaPlayer.prepare();
            float k = mulMin + rng.nextFloat() * (mulMax - mulMin);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                PlaybackParams p = new PlaybackParams();
                p.setSpeed(k); p.setPitch(k);
                mediaPlayer.setPlaybackParams(p);
            }
            mediaPlayer.start();
            UiLog.log("Playing (x" + String.format("%.4f", k) + ") : " + new File(path).getName());
            FileLogger.log(context, "🎶 FullPath : " + path + " | Factor: " + String.format("%.4f", k));
        } catch (Exception e) {
            FileLogger.log(context, "❌ Error : " + e.getMessage());
        }
    }

    public String getCurrentPath() { return currentPath; }
    public void restartCurrent() { if (currentPath != null) playFile(currentPath); }
}
