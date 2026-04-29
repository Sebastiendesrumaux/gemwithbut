package com.example.freqmul;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;

public class PlayerService extends Service {
    private static final String CHANNEL_ID = "BouddhaPlayerChannel";
    private final IBinder binder = new LocalBinder();
    private Mp3play mp3play;
    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;
    
    private boolean sequentialMode = false;
    private int loopMode = 0; // 0: OFF, 1: Single, 2: All
    private boolean singleTrackMode = false;
    private int currentTrackIndex = 0;
    private ArrayList<String> trackList = new ArrayList<>();
    private boolean userActivePlayback = false;
    private boolean isScanning = false;

    // Interface pour prévenir l'activité du progrès du scan
    public interface ScanListener {
        void onScanStarted();
        void onScanFinished(int count);
    }
    private ScanListener scanListener;

    private final AudioManager.OnAudioFocusChangeListener mFocusListener = focusChange -> {
        if (mp3play == null) return;
        if (focusChange != AudioManager.AUDIOFOCUS_GAIN) {
            mp3play.stop();
        } else if (userActivePlayback) {
            mp3play.restartCurrent();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        showNotification("Moteur SAF prêt");
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mp3play = new Mp3play(this);
        
        mp3play.setListener(path -> {
            if (loopMode == 1) mp3play.restartCurrent();
            else if (loopMode == 2) {
                if (singleTrackMode) mp3play.restartCurrent();
                else playNextLogic(true);
            } else {
                if (singleTrackMode) stopPlayback();
                else playNextLogic(false);
            }
        });
    }

    // --- LOGIQUE DE SCAN EN THREAD ---
    public void reloadListAsync(String treeUriStr) {
        if (isScanning) return;
        isScanning = true;
        if (scanListener != null) scanListener.onScanStarted();

        new Thread(() -> {
            mp3play.reloadList(treeUriStr);
            this.trackList = mp3play.getList();
            
            // On revient sur le thread principal pour mettre à jour l'UI
            new Handler(Looper.getMainLooper()).post(() -> {
                isScanning = false;
                if (scanListener != null) scanListener.onScanFinished(trackList.size());
            });
        }).start();
    }

    private void playNextLogic(boolean wrapAround) {
        if (trackList.isEmpty()) return;
        if (sequentialMode) {
            if (currentTrackIndex >= trackList.size() - 1) {
                if (wrapAround) playTrackAtIndex(0);
                else stopPlayback();
            } else {
                playTrackAtIndex(currentTrackIndex + 1);
            }
        } else {
            mp3play.playRandom();
        }
    }

    public void playTrackAtIndex(int index) {
        if (trackList == null || index >= trackList.size()) return;
        userActivePlayback = true;
        requestFocus();
        this.currentTrackIndex = index;
        mp3play.playFile(trackList.get(index));
    }

    public void playNext() {
        singleTrackMode = false;
        playNextLogic(loopMode == 2);
    }

    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(mFocusListener).build();
            mAudioManager.requestAudioFocus(mFocusRequest);
        } else {
            mAudioManager.requestAudioFocus(mFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        else mAudioManager.abandonAudioFocus(mFocusListener);
    }

    public void stopPlayback() {
        userActivePlayback = false;
        mp3play.stop();
        abandonFocus();
    }

    public class LocalBinder extends Binder { PlayerService getService() { return PlayerService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    public Mp3play getMp3play() { return mp3play; }
    public void setSequentialMode(boolean m) { this.sequentialMode = m; }
    public boolean isSequentialMode() { return sequentialMode; }
    public void setLoopMode(int m) { this.loopMode = m; }
    public int getLoopMode() { return loopMode; }
    public void setSingleTrackMode(boolean m) { this.singleTrackMode = m; }
    public boolean isSingleTrackMode() { return singleTrackMode; }
    public void setTrackList(ArrayList<String> l) { this.trackList = l; }
    public void setScanListener(ScanListener l) { this.scanListener = l; }

    public void showNotification(String message) {
        Intent notificationIntent = new Intent(this, freqmul.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bouddha Player")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bouddha Player", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
