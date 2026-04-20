package com.example.freqmul;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechTranscriber implements RecognitionListener {

    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onErrorText(String errorText);
    }

    private final Context context;
    private final SpeechRecognizer recognizer;
    private final Intent intent;
    private Listener listener;
    private boolean active = false;

    public SpeechTranscriber(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        this.recognizer.setRecognitionListener(this);

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (!active) {
            active = true;
            recognizer.startListening(intent);
        }
    }

    public void stop() {
        active = false;
        try {
            recognizer.stopListening();
        } catch (Exception ignored) {}
    }

    public void destroy() {
        active = false;
        recognizer.destroy();
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {
        if (active) {
            try { recognizer.startListening(intent); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onError(int error) {
        String msg;
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: msg = "NETWORK_TIMEOUT"; break;
            case SpeechRecognizer.ERROR_NETWORK: msg = "NETWORK"; break;
            case SpeechRecognizer.ERROR_AUDIO: msg = "AUDIO"; break;
            case SpeechRecognizer.ERROR_SERVER: msg = "SERVER"; break;
            case SpeechRecognizer.ERROR_CLIENT: msg = "CLIENT"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "TIMEOUT"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: msg = "NO_MATCH"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: msg = "BUSY"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: msg = "NO_PERMISSION"; break;
            default: msg = "UNKNOWN_" + error; break;
        }

        if (listener != null) listener.onErrorText(msg);

        if (active) {
            try { recognizer.startListening(intent); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onResults(Bundle results) {
        if (listener != null) {
            String t = extractText(results);
            if (t != null) listener.onFinal(t);
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (listener != null) {
            String t = extractText(partialResults);
            if (t != null) listener.onPartial(t);
        }
    }

    @Override public void onEvent(int eventType, Bundle params) {}

    private String extractText(Bundle bundle) {
        if (bundle == null) return null;
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }
}
