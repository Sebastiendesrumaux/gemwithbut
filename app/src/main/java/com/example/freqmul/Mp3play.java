package com.example.freqmul;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.Random;
import java.util.Locale;

public class Mp3play {
    public interface Mp3Listener { void onTrackCompletion(String path); }
    private final Context context;
    private ArrayList<String> mp3;
    private final Random rng = new Random();
    private MediaPlayer mediaPlayer = null;
    private String currentUriStr = null;
    private Mp3Listener listener;
    private float mulMin = 0.9438f, mulMax = 1.0594f;

    public Mp3play(Context context) {
        this.context = context;
        this.mp3 = FileUtilsMp3.loadJson();
        if (this.mp3 == null) this.mp3 = new ArrayList<>();
    }

    private String getFileName(String uriStr) {
        try {
            DocumentFile df = DocumentFile.fromSingleUri(context, Uri.parse(uriStr));
            if (df != null && df.getName() != null) return df.getName();
        } catch (Exception ignored) {}
        try { return Uri.parse(uriStr).getLastPathSegment(); } catch (Exception e) { return "Inconnu"; }
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

    public void reloadList(String treeUriStr) {
        long startTime = System.currentTimeMillis();
        mp3 = FileUtilsMp3.scanMp3(context, treeUriStr);
        FileUtilsMp3.saveJson(context, mp3);
        long duration = System.currentTimeMillis() - startTime;
        FileLogger.log(context, "📂 Scan SAF : " + mp3.size() + " morceaux indexés en " + duration + "ms");
    }

    public void stop() {
        if (mediaPlayer != null) {
            try { 
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop(); 
                }
            } catch (Exception ignored) {}
            finally {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    public void playRandom() {
        if (mp3 != null && !mp3.isEmpty()) playFile(mp3.get(rng.nextInt(mp3.size())));
    }

    public void playFile(String uriStr) {
        stop(); // 🛡️ On s'assure que tout est purgé avant de lancer la suite
        currentUriStr = uriStr;
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, Uri.parse(uriStr));
            mediaPlayer.setOnCompletionListener(mp -> { if (listener != null) listener.onTrackCompletion(currentUriStr); });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                FileLogger.log(context, "⚠️ Erreur Audio SAF : " + getFileName(uriStr));
                return false; 
            });

            mediaPlayer.prepare();
            float k = mulMin + rng.nextFloat() * (mulMax - mulMin);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                PlaybackParams p = new PlaybackParams();
                p.setSpeed(k); p.setPitch(k);
                mediaPlayer.setPlaybackParams(p);
            }
            mediaPlayer.start();
            String musicInfo = getMusicalInfo(k);
            String fileName = getFileName(uriStr);
            UiLog.log("Playing (x" + String.format("%.4f", k) + ") : " + fileName);
            FileLogger.log(context, "🎶 Joue : " + fileName + " | x" + String.format("%.4f", k) + " | " + musicInfo);
        } catch (Exception e) {
            FileLogger.log(context, "⚠️ Échec Ouverture SAF : " + getFileName(uriStr));
        }
    }
    
    private String getMusicalInfo(float k) {
        double semitones = 12.0 * Math.log(k) / Math.log(2.0);
        String sign = semitones >= 0 ? "+" : "";
        String label = " (Microtonal)";
        double absSt = Math.abs(semitones);
        if (absSt < 0.05) label = " (Unisson)";
        else if (Math.abs(absSt - 0.5) < 0.1) label = " (1/4 de ton)";
        else if (Math.abs(absSt - 1.0) < 0.1) label = " (1/2 ton)";
        else if (Math.abs(absSt - 2.0) < 0.1) label = " (1 ton)";
        return String.format(Locale.US, "%s%.2f st%s", sign, semitones, label);
    }

    public String getCurrentPath() { return currentUriStr; }
    public void restartCurrent() { if (currentUriStr != null) playFile(currentUriStr); }
}
