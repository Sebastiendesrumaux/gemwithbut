package com.example.gemwithbut;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Vibrator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class GeminiWatcher extends AccessibilityService {
    private long lastMacroTimestamp = 0;
    private boolean isWaiting = false;
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private static final String CID = "gem_watcher_channel";

    private BroadcastReceiver macroReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastMacroTimestamp = intent.getLongExtra("timestamp", 0);
            isWaiting = true;
            feedback(ToneGenerator.TONE_PROP_ACK, 100);
            updateNotification("Monitoring Gemini...");
        }
    };

    @Override
    protected void onServiceConnected() {
        toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createChannel();
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(macroReceiver, new IntentFilter("com.example.gemwithbut.START_WATCHING"), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(macroReceiver, new IntentFilter("com.example.gemwithbut.START_WATCHING"));
        }
        updateNotification("Watcher prêt");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isWaiting) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (findText(root, "Écouter la réponse") || findText(root, "Listen")) {
            isWaiting = false;
            feedback(ToneGenerator.TONE_CDMA_PIP, 200);
            updateNotification("✨ Réponse Gemini prête !");
            
            Intent intent = new Intent("com.example.gemwithbut.GEMINI_READY");
            intent.putExtra("original_timestamp", lastMacroTimestamp);
            sendBroadcast(intent);
        }
        root.recycle();
    }

    private boolean findText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(text);
        return list != null && !list.isEmpty();
    }

    private void feedback(int tone, int ms) {
        if (toneGen != null) toneGen.startTone(tone, ms);
        if (vibrator != null) vibrator.vibrate(ms);
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Gemini Watcher")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(isWaiting);
        nm.notify(1, builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CID, "Watcher", NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        if (toneGen != null) toneGen.release();
        try { unregisterReceiver(macroReceiver); } catch (Exception e) {}
        super.onDestroy();
    }
}
