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
    private enum State { IDLE, WAITING_FOR_PROMPT, WAITING_FOR_LISTEN, WAITING_FOR_COPY }
    private State currentState = State.IDLE;
    private String targetId = "";
    private static final String TARGET_PKG = "com.google.android.googlequicksearchbox";
    
    private ToneGenerator toneGen;
    private Vibrator vibrator;
    private static final String CID = "gem_watcher_zen";

    private final BroadcastReceiver watcherReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.gemwithbut.START_WATCHING".equals(action)) {
                Object idExtra = intent.getExtras().get("target_id");
                targetId = (idExtra != null) ? String.valueOf(idExtra) : "";
                currentState = State.WAITING_FOR_PROMPT;
                feedback(ToneGenerator.TONE_PROP_ACK, 60);
                FileLogger.log(context, "🎯 Sniper GoogleSearchBox activé pour ID : " + targetId);
                updateNotification("Sniper Actif", "Traque de l'ID [" + targetId + "]");
            } else if ("com.example.gemwithbut.STOP_WATCHING".equals(action)) {
                resetWatcher();
                feedback(ToneGenerator.TONE_PROP_NACK, 150);
                FileLogger.log(context, "🛑 Sniper désactivé manuellement.");
            }
        }
    };

    private void resetWatcher() {
        currentState = State.IDLE;
        targetId = "";
        updateNotification("Zen Watcher", "Prêt (Repos)");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (currentState == State.IDLE) return;

        // Filtrage strict sur l'application Google
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !TARGET_PKG.equals(pkg.toString())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        switch (currentState) {
            case WAITING_FOR_PROMPT:
                // Basé sur ton DOM : le prompt est dans un EditText ou TextView
                if (containsText(root, "[#" + targetId + "#]")) {
                    currentState = State.WAITING_FOR_LISTEN;
                    FileLogger.log(getApplicationContext(), "✅ Prompt identifié. En attente de l'audio...");
                }
                break;

            case WAITING_FOR_LISTEN:
                // Basé sur ton DOM : Button avec desc "Écouter"
                AccessibilityNodeInfo listenBtn = findNode(root, "android.widget.Button", "Écouter");
                if (listenBtn != null) {
                    if (performClick(listenBtn)) {
                        currentState = State.WAITING_FOR_COPY;
                        feedback(ToneGenerator.TONE_PROP_BEEP, 80);
                        FileLogger.log(getApplicationContext(), "🔊 Clic Écouter réussi.");
                    }
                    listenBtn.recycle();
                }
                break;

            case WAITING_FOR_COPY:
                // Basé sur ton DOM : Button "Copier" dans un HorizontalScrollView
                AccessibilityNodeInfo copyBtn = findCopyButton(root);
                if (copyBtn != null) {
                    if (performClick(copyBtn)) {
                        feedback(ToneGenerator.TONE_CDMA_PIP, 120);
                        FileLogger.log(getApplicationContext(), "📋 Réponse copiée. Mission terminée.");
                        resetWatcher();
                    }
                    copyBtn.recycle();
                }
                break;
        }
        root.recycle();
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String className, String desc) {
        if (node == null) return null;
        if (className.equals(node.getClassName()) && 
            node.getContentDescription() != null && 
            node.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), className, desc);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findCopyButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        // On cherche le bouton Copier
        if ("android.widget.Button".equals(node.getClassName()) && 
            node.getContentDescription() != null && 
            node.getContentDescription().toString().equalsIgnoreCase("Copier")) {
            
            // Vérification du parent : doit être dans la zone de défilement horizontale des actions
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && "android.widget.HorizontalScrollView".equals(parent.getClassName())) {
                return node;
            }
            if (parent != null) parent.recycle();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findCopyButton(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().contains(text)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsText(node.getChild(i), text)) return true;
        }
        return false;
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        AccessibilityNodeInfo p = node.getParent();
        return (p != null && p.isClickable()) && p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private void feedback(int tone, int ms) {
        if (toneGen != null) toneGen.startTone(tone, ms);
        if (vibrator != null) try { vibrator.vibrate(ms); } catch (Exception e) {}
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title).setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        nm.notify(1, builder.build());
    }

    @Override
    protected void onServiceConnected() {
        toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createChannel();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.gemwithbut.START_WATCHING");
        filter.addAction("com.example.gemwithbut.STOP_WATCHING");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(watcherReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(watcherReceiver, filter);
        }
        FileLogger.log(this, "🚀 Sniper Gemini (com.google.android.googlequicksearchbox) prêt.");
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
        try { unregisterReceiver(watcherReceiver); } catch (Exception e) {}
        super.onDestroy();
    }
}
