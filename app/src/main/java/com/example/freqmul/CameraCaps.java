package com.example.freqmul;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class CameraCaps {

    public final String cameraId;
    public final List<Size> videoSizesSorted;   // triées croissant (plus petite d’abord)
    public final List<Integer> fpsValuesSorted; // valeurs “fixes” proposées (15/24/30/60...)
    public final boolean stabilizationSupported;

    private CameraCaps(String cameraId, List<Size> sizes, List<Integer> fps, boolean stab) {
        this.cameraId = cameraId;
        this.videoSizesSorted = sizes;
        this.fpsValuesSorted = fps;
        this.stabilizationSupported = stab;
    }

    public static CameraCaps queryMinFocal(Context ctx) throws Exception {
        CameraManager mgr = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);

        String bestId = null;
        float bestFocal = Float.MAX_VALUE;
        CameraCharacteristics bestCc = null;

        for (String id : mgr.getCameraIdList()) {
            CameraCharacteristics cc = mgr.getCameraCharacteristics(id);

            Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
            // on privilégie la caméra arrière si possible
            if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) {
                continue;
            }

            float[] focals = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focals == null || focals.length == 0) continue;

            float minF = focals[0];
            for (float f : focals) if (f < minF) minF = f;

            if (minF < bestFocal) {
                bestFocal = minF;
                bestId = id;
                bestCc = cc;
            }
        }

        // si aucune back cam, on prend n’importe laquelle avec une focale
        if (bestId == null) {
            for (String id : mgr.getCameraIdList()) {
                CameraCharacteristics cc = mgr.getCameraCharacteristics(id);
                float[] focals = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (focals == null || focals.length == 0) continue;
                float minF = focals[0];
                for (float f : focals) if (f < minF) minF = f;
                bestFocal = minF;
                bestId = id;
                bestCc = cc;
                break;
            }
        }

        if (bestId == null || bestCc == null) {
            throw new Exception("Aucune caméra utilisable (focale introuvable)");
        }

        StreamConfigurationMap map = bestCc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) throw new Exception("SCALER_STREAM_CONFIGURATION_MAP null");

        Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
        if (videoSizes == null || videoSizes.length == 0) throw new Exception("Aucune taille vidéo MediaRecorder");

        ArrayList<Size> sizes = new ArrayList<>(Arrays.asList(videoSizes));
        Collections.sort(sizes, Comparator.comparingInt(s -> s.getWidth() * s.getHeight()));

        // fps ranges
        Range<Integer>[] ranges = bestCc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        LinkedHashSet<Integer> fpsSet = new LinkedHashSet<>();
        if (ranges != null) {
            // On propose des fps “fixes” en prenant les bornes de ranges
            // et quelques valeurs standard si elles tombent dedans.
            int[] standard = new int[]{10, 12, 15, 20, 24, 25, 30, 48, 50, 60};
            for (Range<Integer> r : ranges) {
                fpsSet.add(r.getLower());
                fpsSet.add(r.getUpper());
                for (int s : standard) {
                    if (s >= r.getLower() && s <= r.getUpper()) fpsSet.add(s);
                }
            }
        }
        if (fpsSet.isEmpty()) {
            // fallback raisonnable
            fpsSet.add(15);
            fpsSet.add(30);
        }

        ArrayList<Integer> fps = new ArrayList<>(fpsSet);
        Collections.sort(fps);

        // stabilisation vidéo
        int[] stabModes = bestCc.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        boolean stabSupported = false;
        if (stabModes != null) {
            for (int m : stabModes) {
                if (m == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                    stabSupported = true;
                    break;
                }
            }
        }

        return new CameraCaps(bestId, sizes, fps, stabSupported);
    }
}
