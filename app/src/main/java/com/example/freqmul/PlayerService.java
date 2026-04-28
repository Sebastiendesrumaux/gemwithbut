package com.example.freqmul;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.io.File;

public class PlayerService extends Service {
    private static final String CHANNEL_ID = "BouddhaPlayerChannel";
    private final IBinder binder = new LocalBinder();
    private Mp3play mp3play;
    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;
    private boolean sequentialMode = false;
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
        String eventLabel;
        boolean resumeAfter = false;
        boolean stopNow = false;

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                eventLabel = "🟢 GAIN (Reprise autorisée)";
                resumeAfter = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                eventLabel = "🔴 LOSS (Définitif)";
                stopNow = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                eventLabel = "🟡 LOSS_TRANS (Appel/IA)";
                stopNow = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                eventLabel = "🦆 DUCK (Notification/Mail)";
                stopNow = true;
                break;
            default:
                eventLabel = "❓ EVENT_" + focusChange;
                stopNow = true;
        }
        
        FileLogger.log(this, "🤖 SYSTÈME : Focus -> " + eventLabel);

        if (mp3play == null) return;

        if (stopNow) {
            FileLogger.log(this, "🧠 LOGIQUE : Pause temporaire.");
            mp3play.stop();
        } else if (resumeAfter && userActivePlayback) {
            FileLogger.log(this, "🧠 LOGIQUE : Reprise automatique demandée.");
            mp3play.restartCurrent();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        FileLogger.log(this, "🚀 SERVICE : Initialisation...");
        createNotificationChannel();
        
        // 🛡️ LE BOUCLIER EST ICI : Déclaration immédiate en Foreground
        showNotification("Connecté et en veille...");

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mp3play = new Mp3play(this, "/sdcard/Music");
        mp3play.setListener(path -> {
            if (sequentialMode) playNext();
            else showNotification("Évasion : " + new File(path).getName());
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 🛡️ RENFORCEMENT : On s'assure que la notification est maintenue
        showNotification("Moteur Audio Actif");
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        FileLogger.log(this, "🕶️ UI : Interface balayée (Swipe)");
        super.onTaskRemoved(rootIntent);
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
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(mFocusListener).build();
            mAudioManager.requestAudioFocus(mFocusRequest);
        } else {
            mAudioManager.requestAudioFocus(mFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null)
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        else mAudioManager.abandonAudioFocus(mFocusListener);
    }

    public void playTrackAtIndex(int index) {
        if (trackList == null || trackList.isEmpty() || index >= trackList.size()) return;
        userActivePlayback = true;
        requestFocus();
        this.currentTrackIndex = index;
        
        String currentPath = trackList.get(index);
        showNotification("Évasion : " + new File(currentPath).getName());
        mp3play.playFile(currentPath);
    }

    public void playNext() {
        if (trackList == null || trackList.isEmpty()) return;
        userActivePlayback = true;
        requestFocus();
        if (sequentialMode) {
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
            playTrackAtIndex(currentTrackIndex);
        } else {
            showNotification("Évasion : Mode Aléatoire...");
            mp3play.playRandom();
        }
    }

    public Mp3play getMp3play() { return mp3play; }
    public void setSequentialMode(boolean mode) { this.sequentialMode = mode; }
    public boolean isSequentialMode() { return sequentialMode; }
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
        FileLogger.log(this, "🛑 SERVICE : Destruction");
        unregisterReceiver(mSystemReceiver);
        if (mp3play != null) mp3play.stop();
        abandonFocus();
        super.onDestroy();
    }
}
