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

public class GeminiWatcher extends AccessibilityService {
    private long lastMacroTimestamp = 0;
    private boolean isWaiting = false;
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private static final String CID = "gem_watcher_zen";

    private BroadcastReceiver macroReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastMacroTimestamp = intent.getLongExtra("timestamp", 0);
            isWaiting = true;
            feedback(ToneGenerator.TONE_PROP_ACK, 60);
            updateNotification("L'IA médite...", "Le Watcher cherche le dernier message.");
            FileLogger.log(context, "📡 Attente du bouton TTS le plus récent...");
        }
    };

    @Override
    protected void onServiceConnected() {
        toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createChannel();
        IntentFilter filter = new IntentFilter("com.example.gemwithbut.START_WATCHING");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(macroReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(macroReceiver, filter);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isWaiting) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // On lance la recherche inversée
        if (findLastAndClick(root)) {
            isWaiting = false;
            feedback(ToneGenerator.TONE_CDMA_PIP, 120);
            updateNotification("✨ Instant de clarté", "Dernier bouton cliqué !");
            
            Intent ready = new Intent("com.example.gemwithbut.GEMINI_READY");
            ready.putExtra("original_timestamp", lastMacroTimestamp);
            sendBroadcast(ready);
        }
        root.recycle();
    }

    private boolean findLastAndClick(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // PARCOURS INVERSÉ : On commence par le dernier enfant (le bas de l'écran)
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findLastAndClick(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        // Si aucun enfant n'a matché, on vérifie le noeud lui-même
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (isMatch(text) || isMatch(desc)) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            // Remontée vers le parent cliquable
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    parent.recycle();
                    return true;
                }
                AccessibilityNodeInfo old = parent;
                parent = parent.getParent();
                old.recycle();
            }
        }
        return false;
    }

    private boolean isMatch(CharSequence cs) {
        if (cs == null) return false;
        String t = cs.toString().toLowerCase();
        return t.contains("écouter") || t.contains("listen") || t.contains("tts") || t.contains("vocal");
    }

    private void feedback(int tone, int ms) {
        if (toneGen != null) toneGen.startTone(tone, ms);
        try { if (vibrator != null) vibrator.vibrate(ms); } catch (Exception e) {}
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isWaiting);
        nm.notify(1, builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CID, "Zen Watcher", NotificationManager.IMPORTANCE_LOW);
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
