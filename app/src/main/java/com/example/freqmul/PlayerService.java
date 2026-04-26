package com.example.freqmul;

import android.app.*;
import android.content.Intent;
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
    
    private boolean sequentialMode = false;
    private int currentTrackIndex = 0;
    private ArrayList<String> trackList = new ArrayList<>();

    public class LocalBinder extends Binder {
        PlayerService getService() { return PlayerService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mp3play = new Mp3play(this, "/sdcard/Music");
        
        mp3play.setListener(path -> {
            if (sequentialMode) {
                playNext(); // Utilise la nouvelle logique
            } else {
                showNotification("Playing (Random): " + new File(path).getName());
            }
        });
    }

    public Mp3play getMp3play() { return mp3play; }
    public void setSequentialMode(boolean mode) { this.sequentialMode = mode; }
    public boolean isSequentialMode() { return sequentialMode; }
    public void setTrackList(ArrayList<String> list) { this.trackList = list; }
    
    public void playTrackAtIndex(int index) {
        if (trackList == null || trackList.isEmpty() || index >= trackList.size()) return;
        this.currentTrackIndex = index;
        String path = trackList.get(index);
        mp3play.playFile(path);
        showNotification("Playing: " + new File(path).getName());
    }

    // LA NOUVELLE MÉTHODE CLÉ
    public void playNext() {
        if (trackList == null || trackList.isEmpty()) return;
        
        if (sequentialMode) {
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
            playTrackAtIndex(currentTrackIndex);
        } else {
            mp3play.playRandom();
            // On met à jour la notif pour le random car playRandom n'appelle pas playTrackAtIndex
            if (mp3play.getCurrentPath() != null) {
                showNotification("Playing (Random): " + new File(mp3play.getCurrentPath()).getName());
            }
        }
    }

    public void showNotification(String trackName) {
        Intent notificationIntent = new Intent(this, freqmul.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bouddha Player")
                .setContentText(trackName)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Bouddha Player Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        if (mp3play != null) mp3play.stop();
        super.onDestroy();
    }
}
