package com.example.freqmul;

import android.app.Dialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressOverlay {

    private final Dialog dialog;
    private final TextView text;

    public ProgressOverlay(Context ctx) {
        dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(0xCC000000);

        ProgressBar pb = new ProgressBar(ctx);
        pb.setIndeterminate(true);

        text = new TextView(ctx);
        text.setTextSize(15f);
        text.setPadding(0, 24, 0, 0);
        text.setTextColor(0xFFFFFFFF);

        root.addView(pb);
        root.addView(text);

        dialog.setContentView(root);
        dialog.setCancelable(false);
    }

    public void show(String msg) {
        text.setText(msg);
        dialog.show();
    }

    public void update(String msg) {
        text.setText(msg);
    }

    public void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }
}
