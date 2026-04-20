package com.example.freqmul;

import android.app.Activity;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;

public class UiLog {

    private static Activity activity;
    private static TextView textView;
    private static ScrollView scrollView;

    public static void init(Activity act, TextView tv, ScrollView sv) {
        activity = act;
        textView = tv;
        scrollView = sv;
    }

    public static void log(String msg) {
        if (activity == null || textView == null) return;

        activity.runOnUiThread(() -> {
            textView.append(msg + "\n");
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    // Compatibilité avec anciens appels UiToast.show(this, msg)
    public static void log(Context ctx, String msg) {
        log(msg);
    }

    public static void log(Activity act, String msg) {
        log(msg);
    }
}
