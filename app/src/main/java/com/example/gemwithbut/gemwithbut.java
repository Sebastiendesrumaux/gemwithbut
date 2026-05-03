package com.example.gemwithbut;
import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class gemwithbut extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gemwithbut);
        
        // Demande de permission pour les notifications (indispensable Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
        
        // Lancement discret du service de lecture en fond
        startService(new Intent(this, PlayerService.class));
    }
}
