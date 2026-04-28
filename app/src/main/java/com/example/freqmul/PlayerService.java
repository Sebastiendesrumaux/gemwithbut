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
import android.os.PowerManager;
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
            } else if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                int newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                FileLogger.log(context, "🔊 Vol: " + newVol);
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                FileLogger.log(context, "🔋 Batt: " + level + "%");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                FileLogger.log(context, "📱 Écran: ON");
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                FileLogger.log(context, "🕶️ Écran: OFF");
            }
        }
    };

    public class LocalBinder extends Binder {
        PlayerService getService() { return PlayerService.this; }
    }

    private final AudioManager.OnAudioFocusChangeListener mFocusListener = focusChange -> {
        String eventLabel;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN: eventLabel = "🟢 GAIN (Autorisé)"; break;
            case AudioManager.AUDIOFOCUS_LOSS: eventLabel = "🔴 LOSS (Définitif)"; break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: eventLabel = "🟡 LOSS_TRANS (Appel/IA)"; break;
            default: eventLabel = "❓ EVENT_" + focusChange;
        }
        
        FileLogger.log(this, "🤖 SYSTÈME : Focus -> " + eventLabel);

        if (mp3play == null) return;

        if (focusChange != AudioManager.AUDIOFOCUS_GAIN) {
            FileLogger.log(this, "🧠 LOGIQUE : Interruption immédiate du moteur.");
            mp3play.stop();
        } else {
            if (userActivePlayback) {
                FileLogger.log(this, "🧠 LOGIQUE : L'utilisateur voulait écouter (Intention=Active). Reprise !");
                mp3play.restartCurrent();
            } else {
                FileLogger.log(this, "🧠 LOGIQUE : Focus obtenu mais l'utilisateur est en STOP. On reste muet.");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        FileLogger.log(this, "🚀 SERVICE : Initialisation...");
        createNotificationChannel();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mp3play = new Mp3play(this, "/sdcard/Music");
        mp3play.setListener(path -> {
            if (sequentialMode) playNext();
            else showNotification("Évasion : " + new File(path).getName());
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mSystemReceiver, filter);
    }

    public void stopPlayback() {
        FileLogger.log(this, "👤 USAGER : Clic sur [STOP]");
        userActivePlayback = false;
        mp3play.stop();
        abandonFocus();
    }

    private void requestFocus() {
        FileLogger.log(this, "🧠 LOGIQUE : Demande de canal audio au système...");
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
        FileLogger.log(this, "🧠 LOGIQUE : Abandon du canal audio.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        else mAudioManager.abandonAudioFocus(mFocusListener);
    }

    public void playTrackAtIndex(int index) {
        if (trackList == null || trackList.isEmpty() || index >= trackList.size()) return;
        FileLogger.log(this, "👤 USAGER : Clic sur [PLAY/NEXT]");
        userActivePlayback = true;
        requestFocus();
        this.currentTrackIndex = index;
        mp3play.playFile(trackList.get(index));
    }

    public void playNext() {
        if (trackList == null || trackList.isEmpty()) return;
        if (sequentialMode) {
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
            playTrackAtIndex(currentTrackIndex);
        } else {
            FileLogger.log(this, "👤 USAGER : Clic sur [RANDOM]");
            userActivePlayback = true;
            requestFocus();
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

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        FileLogger.log(this, "🛑 SERVICE : Destruction");
        unregisterReceiver(mSystemReceiver);
        if (mp3play != null) mp3play.stop();
        abandonFocus();
        super.onDestroy();
    }
}
