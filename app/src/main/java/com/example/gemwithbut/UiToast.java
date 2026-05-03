package com.example.gemwithbut;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class UiToast {
    public static void show(Context ctx, String msg) {
        if (ctx == null || msg == null) return;
        TextView tv = new TextView(ctx);
        tv.setText(msg); tv.setTextSize(14f); tv.setPadding(24, 16, 24, 16); tv.setMaxLines(30);
        tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Toast t = new Toast(ctx);
        t.setView(tv); t.setDuration(Toast.LENGTH_LONG); t.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 120);
        t.show();
    }
}
