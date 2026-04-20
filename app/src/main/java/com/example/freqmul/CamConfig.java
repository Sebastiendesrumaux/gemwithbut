package com.example.freqmul;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CamConfig {

    public String cameraId;
    public int width;
    public int height;
    public int fps;              // fps “fixe”
    public boolean stabilization;

    public static final String SYNG_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/freqmul";
    public static final String CAMCONF_FILE = SYNG_DIR + "/camconfig.json";

    public CamConfig() {}

    public static CamConfig defaultFromCaps(CameraCaps caps) {
        CamConfig c = new CamConfig();
        c.cameraId = caps.cameraId;

        // résolution la plus basse
        c.width = caps.videoSizesSorted.get(0).getWidth();
        c.height = caps.videoSizesSorted.get(0).getHeight();

        // fps le plus bas
        c.fps = caps.fpsValuesSorted.get(0);

        // stab off par défaut
        c.stabilization = false;
        return c;
    }

    public static CamConfig loadOrDefault(Context ctx, CameraCaps caps) {
        File f = new File(CAMCONF_FILE);
        if (!f.exists()) return defaultFromCaps(caps);

        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            String text = new String(data, StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(text);

            CamConfig c = new CamConfig();
            c.cameraId = o.optString("cameraId", caps.cameraId);
            c.width = o.optInt("width", caps.videoSizesSorted.get(0).getWidth());
            c.height = o.optInt("height", caps.videoSizesSorted.get(0).getHeight());
            c.fps = o.optInt("fps", caps.fpsValuesSorted.get(0));
            c.stabilization = o.optBoolean("stabilization", false);

            // normaliser sur les valeurs supportées
            normalizeToCaps(c, caps);
            return c;

        } catch (Exception e) {
            Toast.makeText(ctx, "camconfig.json illisible → défaut", Toast.LENGTH_LONG).show();
            return defaultFromCaps(caps);
        }
    }

    public static void save(Context ctx, CamConfig c) {
        try {
            File dir = new File(SYNG_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(ctx, "Impossible de créer " + SYNG_DIR, Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject o = new JSONObject();
            o.put("cameraId", c.cameraId);
            o.put("width", c.width);
            o.put("height", c.height);
            o.put("fps", c.fps);
            o.put("stabilization", c.stabilization);

            java.nio.file.Files.write(new File(CAMCONF_FILE).toPath(),
                    o.toString().getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            Toast.makeText(ctx, "Erreur écriture camconfig.json", Toast.LENGTH_LONG).show();
        }
    }

    public static void normalizeToCaps(CamConfig c, CameraCaps caps) {
        // cameraId imposé: objectif focale min
        c.cameraId = caps.cameraId;

        // taille: choisir la plus proche (exact si possible)
        boolean exact = false;
        for (android.util.Size s : caps.videoSizesSorted) {
            if (s.getWidth() == c.width && s.getHeight() == c.height) {
                exact = true;
                break;
            }
        }
        if (!exact) {
            c.width = caps.videoSizesSorted.get(0).getWidth();
            c.height = caps.videoSizesSorted.get(0).getHeight();
        }

        // fps: si non supporté, prendre le plus bas
        if (!caps.fpsValuesSorted.contains(c.fps)) {
            c.fps = caps.fpsValuesSorted.get(0);
        }

        // stab: si non supportée, forcer off
        if (!caps.stabilizationSupported) c.stabilization = false;
    }

    public String resLabel() {
        return width + "x" + height;
    }

    public static int findSizeIndex(List<android.util.Size> sizes, int w, int h) {
        for (int i = 0; i < sizes.size(); i++) {
            android.util.Size s = sizes.get(i);
            if (s.getWidth() == w && s.getHeight() == h) return i;
        }
        return 0;
    }

    public static int findFpsIndex(List<Integer> fpsList, int fps) {
        for (int i = 0; i < fpsList.size(); i++) {
            if (fpsList.get(i) == fps) return i;
        }
        return 0;
    }
}
