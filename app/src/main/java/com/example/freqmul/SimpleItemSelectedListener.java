package com.example.freqmul;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {

    public interface Callback { void onSelected(int position); }
    private final Callback cb;

    public SimpleItemSelectedListener(Callback cb) {
        this.cb = cb;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (cb != null) cb.onSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
