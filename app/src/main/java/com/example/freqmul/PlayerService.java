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
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;

public class PlayerService extends Service {
    private static final String CHANNEL_ID = "BouddhaPlayerChannel";
    private final IBinder binder = new LocalBinder();
    private Mp3play mp3play;
    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;
    
    private boolean sequentialMode = false;
    private boolean infiniteMode = false;
    private boolean singleTrackMode = false;
    private int currentTrackIndex = 0;
    private ArrayList<String> trackList = new ArrayList<>();
    private boolean userActivePlayback = false;

    private final BroadcastReceiver mSystemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                int state = intent.getIntExtra("state", -1);
                FileLogger.log(context, (state == 1 ? "🎧 Casque: Connecté" : "🎧 Casque: Déconnecté"));
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                FileLogger.log(context, "🔋 Batt: " + level + "%");
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener mFocusListener = focusChange -> {
        boolean resumeAfter = false;
        boolean stopNow = false;

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                resumeAfter = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                stopNow = true;
                break;
        }

        if (mp3play == null) return;

        if (stopNow) {
            FileLogger.log(this, "🧠 LOGIQUE : Pause due au focus audio.");
            mp3play.stop();
        } else if (resumeAfter && userActivePlayback) {
            FileLogger.log(this, "🧠 LOGIQUE : Reprise automatique.");
            mp3play.restartCurrent();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        FileLogger.log(this, "🚀 SERVICE : Initialisation SAF...");
        createNotificationChannel();
        showNotification("Connecté et en veille...");

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        // CORRECTION ICI : Initialisation propre
        mp3play = new Mp3play(this);
        
        mp3play.setListener(path -> {
            FileLogger.log(this, "🧠 LOGIQUE : Fin du morceau détectée.");
            if (singleTrackMode) {
                if (infiniteMode) {
                    FileLogger.log(this, "🔄 INFINI : Rejoue le même morceau.");
                    mp3play.restartCurrent();
                } else {
                    FileLogger.log(this, "⏹️ SINGLE : Fin du morceau, arrêt.");
                    stopPlayback();
                }
            } else {
                if (sequentialMode) {
                    if (currentTrackIndex >= trackList.size() - 1) {
                        if (infiniteMode) {
                            FileLogger.log(this, "🔄 INFINI : Fin de l'arborescence, on boucle au début.");
                            playTrackAtIndex(0);
                        } else {
                            FileLogger.log(this, "⏹️ ALL : Fin de liste, on s'arrête.");
                            stopPlayback();
                        }
                    } else {
                        playNextSequential();
                    }
                } else {
                    FileLogger.log(this, "🔀 RAND : Morceau suivant aléatoire.");
                    mp3play.playRandom();
                }
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void stopPlayback() {
        FileLogger.log(this, "👤 USAGER : Clic sur [STOP]");
        userActivePlayback = false;
        mp3play.stop();
        abandonFocus();
    }

    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAcceptsDelayedFocusGain(true)
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
    
    private String getSafeName(String uriStr) {
        try { return Uri.parse(uriStr).getLastPathSegment(); } catch (Exception e) { return "Audio"; }
    }

    public void playTrackAtIndex(int index) {
        if (trackList == null || trackList.isEmpty() || index >= trackList.size()) return;
        userActivePlayback = true;
        requestFocus();
        this.currentTrackIndex = index;
        String currentUriStr = trackList.get(index);
        showNotification("Évasion : " + getSafeName(currentUriStr));
        mp3play.playFile(currentUriStr);
    }

    private void playNextSequential() {
        if (trackList == null || trackList.isEmpty()) return;
        userActivePlayback = true;
        requestFocus();
        currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
        String currentUriStr = trackList.get(currentTrackIndex);
        showNotification("Évasion : " + getSafeName(currentUriStr));
        mp3play.playFile(currentUriStr);
    }

    public void playNext() {
        singleTrackMode = false;
        if (trackList == null || trackList.isEmpty()) return;
        userActivePlayback = true;
        requestFocus();
        if (sequentialMode) {
            playNextSequential();
        } else {
            showNotification("Évasion : Mode Aléatoire...");
            mp3play.playRandom();
        }
    }

    public Mp3play getMp3play() { return mp3play; }
    public void setSequentialMode(boolean mode) { this.sequentialMode = mode; }
    public boolean isSequentialMode() { return sequentialMode; }
    public void setInfiniteMode(boolean mode) { this.infiniteMode = mode; }
    public boolean isInfiniteMode() { return infiniteMode; }
    public void setSingleTrackMode(boolean mode) { this.singleTrackMode = mode; }
    public boolean isSingleTrackMode() { return singleTrackMode; }
    public void setTrackList(ArrayList<String> list) { this.trackList = list; }

    public void showNotification(String message) {
        Intent notificationIntent = new Intent(this, freqmul.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bouddha Player")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true).build();
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bouddha Player", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    public class LocalBinder extends Binder { PlayerService getService() { return PlayerService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        unregisterReceiver(mSystemReceiver);
        if (mp3play != null) mp3play.stop();
        abandonFocus();
        super.onDestroy();
    }
}
