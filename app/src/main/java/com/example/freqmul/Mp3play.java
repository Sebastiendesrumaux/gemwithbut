package com.example.freqmul;

import android.content.Context;
import android.media.MediaPlayer;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class Mp3play {

    public interface Mp3Listener {
        void onTrackCompletion(String path);
    }

    private final Context context;
    private ArrayList<String> mp3;
    private final Random rng = new Random();
    private MediaPlayer mediaPlayer = null;

    private String currentPath = null;

    private Mp3Listener listener;
    private boolean inhibitAutoNext = false;

    public Mp3play(Context context, String rootPath) {
        this.context = context;

        mp3 = FileUtilsMp3.loadJson();
        if (mp3 == null || mp3.isEmpty()) {
            File root = new File(rootPath);
            mp3 = FileUtilsMp3.scanMp3(root);
            FileUtilsMp3.saveJson(context, mp3);
        }
    }

    public void setListener(Mp3Listener listener) {
        this.listener = listener;
    }

    public void setInhibitAutoNext(boolean inhibit) {
        this.inhibitAutoNext = inhibit;
    }

    public ArrayList<String> getList() {
        return mp3;
    }

    public String getRandom() {
        if (mp3 == null || mp3.isEmpty()) return null;
        return mp3.get(rng.nextInt(mp3.size()));
    }

    public void reloadList(String rootPath) {
        File root = new File(rootPath);
        mp3 = FileUtilsMp3.scanMp3(root);
        FileUtilsMp3.saveJson(context, mp3);
        currentPath = null;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    // --- Lecture audio ---
    public void stop() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void playRandom() {
        String path = getRandom();
        if (path == null) {
            Toast.makeText(context, "Aucun mp3", Toast.LENGTH_SHORT).show();
            return;
        }
        playFile(path);
    }

    public void playFile(String path) {
        stop();
        File f = new File(path);
        if (!f.exists()) {
            Toast.makeText(context, "Fichier introuvable : " + path, Toast.LENGTH_SHORT).show();
            return;
        }
        currentPath = path;

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnCompletionListener(mp -> {
                String finished = currentPath;
                if (listener != null) listener.onTrackCompletion(finished);
                if (!inhibitAutoNext) {
                    playRandom();
                }
            });
            mediaPlayer.prepare();
            mediaPlayer.start();

            Toast.makeText(context, "Lecture : " + f.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Erreur MediaPlayer", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // Relancer le morceau courant depuis le début
    public void restartCurrent() {
        if (currentPath == null) return;
        playFile(currentPath);
    }
}
