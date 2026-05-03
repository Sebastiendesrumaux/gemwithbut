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
            updateNotification("L'IA médite...", "Le Watcher cherche le signal vocal.");
            FileLogger.log(context, "📡 Intent START reçu : En attente du bouton...");
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
        updateNotification("Sentinelle active", "À l'écoute des murmures de la machine.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // On écoute tout : changement de fenêtre ou de contenu
        if (!isWaiting) return;
        
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (findAndClick(root)) {
            isWaiting = false;
            feedback(ToneGenerator.TONE_CDMA_PIP, 120);
            updateNotification("✨ Instant de clarté", "Bouton détecté et cliqué !");
            FileLogger.log(this, "✅ Bouton trouvé et cliqué automatiquement.");
            
            Intent ready = new Intent("com.example.gemwithbut.GEMINI_READY");
            ready.putExtra("original_timestamp", lastMacroTimestamp);
            sendBroadcast(ready);
        }
        root.recycle();
    }

    private boolean findAndClick(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        // On élargit les critères de détection (plus de synonymes)
        if (isMatch(text) || isMatch(desc)) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null) {
                if (parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    parent.recycle();
                    return true;
                }
                AccessibilityNodeInfo oldParent = parent;
                parent = parent.getParent();
                oldParent.recycle();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (findAndClick(child)) {
                if (child != null) child.recycle();
                return true;
            }
            if (child != null) child.recycle();
        }
        return false;
    }

    private boolean isMatch(CharSequence cs) {
        if (cs == null) return false;
        String t = cs.toString().toLowerCase();
        return t.contains("écouter") || t.contains("listen") || 
               t.contains("tts") || t.contains("vocal") || 
               t.contains("haut-parleur") || t.contains("speaker");
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
                .setSubText("Écologie d'endroit")
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
