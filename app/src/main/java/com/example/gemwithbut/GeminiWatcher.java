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
    private enum State { IDLE, WAITING_FOR_ID, WAITING_FOR_LISTEN, WAITING_FOR_COPY }
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
                currentState = State.WAITING_FOR_ID;
                feedback(ToneGenerator.TONE_PROP_ACK, 60);
                FileLogger.log(context, "🎯 Sniper activé pour [#" + targetId + "]");
                updateNotification("Sniper Actif", "Cible : [#" + targetId + "]");
            } else if ("com.example.gemwithbut.STOP_WATCHING".equals(action)) {
                resetWatcher();
                feedback(ToneGenerator.TONE_PROP_NACK, 150);
            }
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (currentState == State.IDLE) return;
        
        CharSequence pkg = event.getPackageName();
        if (pkg != null && !TARGET_PKG.equals(pkg.toString())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            AccessibilityNodeInfo anchor = findNodeWithText(root, "[#" + targetId);
            if (anchor == null) return;

            AccessibilityNodeInfo rowNode = findChildOfRecyclerView(anchor);
            if (rowNode != null) {
                AccessibilityNodeInfo recyclerView = rowNode.getParent();
                if (recyclerView != null) {
                    processStateLogic(recyclerView, rowNode);
                    recyclerView.recycle();
                }
                rowNode.recycle();
            }
            anchor.recycle();
            
        } finally {
            root.recycle();
        }
    }

    private void processStateLogic(AccessibilityNodeInfo list, AccessibilityNodeInfo anchorRow) {
        boolean foundAnchor = false;
        
        for (int i = 0; i < list.getChildCount(); i++) {
            AccessibilityNodeInfo child = list.getChild(i);
            if (child == null) continue;

            if (!foundAnchor) {
                // Utilisation de equals() pour la comparaison de nodes
                if (child.equals(anchorRow)) {
                    foundAnchor = true;
                }
            }

            if (foundAnchor) {
                if (currentState == State.WAITING_FOR_ID || currentState == State.WAITING_FOR_LISTEN) {
                    AccessibilityNodeInfo btn = findButtonByKeyword(child, "écouter", "listen", "lire");
                    if (btn != null) {
                        if (performClick(btn)) {
                            currentState = State.WAITING_FOR_COPY;
                            feedback(ToneGenerator.TONE_PROP_BEEP, 80);
                            FileLogger.log(getApplicationContext(), "🔊 Audio lancé.");
                        }
                        btn.recycle();
                        child.recycle();
                        return;
                    }
                }

                if (currentState == State.WAITING_FOR_COPY) {
                    AccessibilityNodeInfo btn = findButtonByKeyword(child, "copier", "copy");
                    if (btn != null) {
                        if (performClick(btn)) {
                            feedback(ToneGenerator.TONE_CDMA_PIP, 120);
                            FileLogger.log(getApplicationContext(), "📋 Copie réussie !");
                            resetWatcher();
                        }
                        btn.recycle();
                        child.recycle();
                        return;
                    }
                }
            }
            child.recycle();
        }
    }

    private AccessibilityNodeInfo findChildOfRecyclerView(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        while (current != null) {
            AccessibilityNodeInfo parent = current.getParent();
            if (parent != null) {
                CharSequence className = parent.getClassName();
                if (className != null && className.toString().contains("RecyclerView")) {
                    parent.recycle();
                    return current;
                }
                current.recycle();
                current = parent;
            } else {
                if (current != null) current.recycle();
                return null;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeWithText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.getText() != null && node.getText().toString().contains(text)) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findNodeWithText(child, text);
            if (child != null) child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findButtonByKeyword(AccessibilityNodeInfo node, String... keys) {
        if (node == null) return null;
        String desc = (node.getContentDescription() != null) ? node.getContentDescription().toString().toLowerCase() : "";
        String text = (node.getText() != null) ? node.getText().toString().toLowerCase() : "";
        
        for (String key : keys) {
            if (desc.contains(key) || text.contains(key)) return AccessibilityNodeInfo.obtain(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findButtonByKeyword(child, keys);
            if (child != null) child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        AccessibilityNodeInfo p = node.getParent();
        if (p != null) {
            boolean res = p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            p.recycle();
            return res;
        }
        return false;
    }

    private void resetWatcher() {
        currentState = State.IDLE;
        targetId = "";
        updateNotification("Zen Watcher", "Prêt (Repos)");
    }

    private void feedback(int tone, int ms) {
        if (toneGen != null) toneGen.startTone(tone, ms);
        if (vibrator != null) try { vibrator.vibrate(ms); } catch(Exception e) {}
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
        FileLogger.log(this, "🚀 Sniper Gemini corrigé prêt.");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CID, "Zen Watcher", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    @Override public void onInterrupt() {}
}
