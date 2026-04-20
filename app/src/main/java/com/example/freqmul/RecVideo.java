package com.example.freqmul;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class RecVideo {

    private static final String TAG = "RecVideo";

    private final Context context;
    private final TextureView textureView;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;

    private CamConfig config;

    private boolean recording = false;
    private String lastOutputPath;

    public RecVideo(Context ctx, TextureView tv, CamConfig cfg) {
        this.context = ctx;
        this.textureView = tv;
        this.config = cfg;
    }

    public boolean isRecording() { return recording; }
    public String getLastOutputPath() { return lastOutputPath; }

    public void applyConfig(CamConfig cfg) {
        // ne pas reconfigurer en plein enregistrement
        if (recording) return;
        this.config = cfg;
        // redémarrer preview
        restartPreview();
    }

    // ============================================================
    // PREVIEW
    // ============================================================

    public void startPreview() {
        if (textureView.isAvailable()) openCamera();
        else textureView.setSurfaceTextureListener(surfaceListener);
    }

    private void restartPreview() {
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {}
        session = null;

        try {
            if (camera != null) {
                camera.close();
            }
        } catch (Exception ignored) {}
        camera = null;

        startPreview();
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    openCamera();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            };

    private void openCamera() {
        try {
            CameraManager mgr = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            mgr.openCamera(config.cameraId, stateCallback, null);
        } catch (Exception e) {
            Log.e(TAG, "openCamera failed", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice cd) {
                    camera = cd;
                    createPreviewSession();
                }
                @Override public void onDisconnected(CameraDevice cd) {
                    cd.close();
                    camera = null;
                }
                @Override public void onError(CameraDevice cd, int err) {
                    cd.close();
                    camera = null;
                }
            };

    private void createPreviewSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            // preview buffer = config résolution (simple et robuste)
            st.setDefaultBufferSize(config.width, config.height);
            Surface previewSurface = new Surface(st);

            CaptureRequest.Builder b =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(previewSurface);
            applyFpsAndStab(b);

            camera.createCaptureSession(
                    Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                session.setRepeatingRequest(b.build(), null, null);
                            } catch (Exception e) {
                                Log.e(TAG, "preview start failed", e);
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {}
                    },
                    null
            );
        } catch (Exception e) {
            Log.e(TAG, "createPreviewSession failed", e);
        }
    }

    private void applyFpsAndStab(CaptureRequest.Builder b) {
        // FPS: on force un range fixe (fps,fps) si possible, sinon Android fera au mieux.
        try {
            b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(config.fps, config.fps));
        } catch (Exception ignored) {}

        // Stabilisation vidéo si supportée + demandée
        try {
            int mode = config.stabilization
                    ? CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    : CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
            b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, mode);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // RECORDING
    // ============================================================

    public void startRecording() {
        if (camera == null || recording) return;

        try {
            setupRecorder();

            SurfaceTexture st = textureView.getSurfaceTexture();
            st.setDefaultBufferSize(config.width, config.height);
            Surface previewSurface = new Surface(st);
            Surface recordSurface = recorder.getSurface();

            CaptureRequest.Builder b =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            b.addTarget(previewSurface);
            b.addTarget(recordSurface);
            applyFpsAndStab(b);

            camera.createCaptureSession(
                    Arrays.asList(previewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                session.setRepeatingRequest(b.build(), null, null);
                                recorder.start();
                                recording = true;
                            } catch (Exception e) {
                                Log.e(TAG, "startRecording failed", e);
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {}
                    },
                    null
            );

        } catch (Exception e) {
            Log.e(TAG, "startRecording exception", e);
        }
    }

    public void stopRecording() {
        if (!recording) return;
        try { recorder.stop(); } catch (Exception ignored) {}
        try { recorder.reset(); } catch (Exception ignored) {}
        recording = false;
        createPreviewSession();
    }

    public void stop() {
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (camera != null) camera.close(); } catch (Exception ignored) {}
    }

    // ============================================================
    // MEDIARECORDER
    // ============================================================

    private void setupRecorder() throws Exception {
        if (recorder == null) recorder = new MediaRecorder();
        recorder.reset();

        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        recorder.setVideoSize(config.width, config.height);
        recorder.setVideoFrameRate(config.fps);

        // bitrate: échelle simple selon pixels * fps (plutôt conservateur)
        long px = (long) config.width * (long) config.height;
        int br = (int) Math.max(250_000, Math.min(6_000_000, (px * config.fps) / 5)); // clamp
        recorder.setVideoEncodingBitRate(br);

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "freqmul");
        dir.mkdirs();

        lastOutputPath = new File(dir,
                "VID_" + System.currentTimeMillis() + ".mp4").getAbsolutePath();

        recorder.setOutputFile(lastOutputPath);
        recorder.prepare();
    }
}
