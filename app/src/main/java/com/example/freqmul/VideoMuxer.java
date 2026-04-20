package com.example.freqmul;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoMuxer {

    public interface ProgressListener {
        void onStep(String msg);
    }

    public static String mux(Context ctx,
                             String videoPath,
                             String audioPath,
                             long maxAudioUs) {
        return mux(ctx, videoPath, audioPath, maxAudioUs, null);
    }

    public static String mux(Context ctx,
                             String videoPath,
                             String audioPath,
                             long maxAudioUs,
                             ProgressListener pl) {
        try {
            return doMux(ctx, videoPath, audioPath, maxAudioUs, pl);
        } catch (Exception e) {
            UiLog.log(ctx, "MUX ERROR:\n" + e.getMessage());
            return null;
        }
    }

    private static String doMux(Context ctx,
                                String videoPath,
                                String audioPath,
                                long maxAudioUs,
                                ProgressListener pl) throws Exception {

        if (pl != null) pl.onStep("Analyse des fichiers…");

        File outDir = new File(FileUtilsMp3.SYNG_DIR);
        outDir.mkdirs();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File outFile = new File(outDir, "FINAL_" + ts + ".mp4");

        File pcmFile = new File(outDir, "tmp_pcm.raw");

        if (pl != null) pl.onStep("Décodage MP3 → PCM…");
        PcmInfo pcm = decodeMp3ToPcm(audioPath, pcmFile, pl);

        if (pl != null) pl.onStep("Préparation du muxer…");
        MediaMuxer muxer = new MediaMuxer(outFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        if (pl != null) pl.onStep("Ajout piste vidéo…");
        int videoTrack = addVideoTrack(videoPath, muxer);

        if (pl != null) pl.onStep("Encodage PCM → AAC…");
        int audioTrack = encodePcmToAacAndMux(
                pcmFile, pcm.sampleRate, pcm.channels, muxer, maxAudioUs, pl
        );

        if (pl != null) pl.onStep("Assemblage final…");
        copyVideoSamples(videoPath, muxer, videoTrack);

        muxer.stop();
        muxer.release();

        if (pl != null) pl.onStep("Terminé");

        return outFile.getAbsolutePath();
    }

    // ===================== AUDIO =====================

    private static class PcmInfo {
        final int sampleRate;
        final int channels;
        PcmInfo(int sr, int ch) { sampleRate = sr; channels = ch; }
    }

    private static PcmInfo decodeMp3ToPcm(String path, File out, ProgressListener pl) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(path);

        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                track = i;
                fmt = f;
                break;
            }
        }
        if (track < 0) throw new Exception("Pas de piste audio");

        ex.selectTrack(track);
        MediaCodec dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        dec.configure(fmt, null, null, 0);
        dec.start();

        FileOutputStream fos = new FileOutputStream(out);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        boolean done = false;
        while (!done) {
            int in = dec.dequeueInputBuffer(10_000);
            if (in >= 0) {
                ByteBuffer b = dec.getInputBuffer(in);
                int r = ex.readSampleData(b, 0);
                if (r < 0) {
                    dec.queueInputBuffer(in, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    dec.queueInputBuffer(in, 0, r, ex.getSampleTime(), 0);
                    ex.advance();
                }
            }

            int outIndex = dec.dequeueOutputBuffer(info, 10_000);
            if (outIndex >= 0) {
                ByteBuffer b = dec.getOutputBuffer(outIndex);
                byte[] data = new byte[info.size];
                b.get(data);
                fos.write(data);
                dec.releaseOutputBuffer(outIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    done = true;
            }
        }

        fos.close();
        dec.stop();
        dec.release();
        ex.release();

        return new PcmInfo(fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
    }

    private static int encodePcmToAacAndMux(File pcm,
                                            int sr,
                                            int ch,
                                            MediaMuxer muxer,
                                            long maxUs,
                                            ProgressListener pl) throws Exception {

        MediaFormat f = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);

        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        enc.start();

        FileInputStream fis = new FileInputStream(pcm);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int trackIndex = -1;
        boolean muxStarted = false;
        boolean done = false;
        byte[] buf = new byte[4096];

        long pts = 0;
        long maxSamples = maxUs > 0 ? (maxUs * sr) / 1_000_000L : Long.MAX_VALUE;
        long writtenSamples = 0;

        while (!done) {
            int in = enc.dequeueInputBuffer(10_000);
            if (in >= 0) {
                ByteBuffer b = enc.getInputBuffer(in);
                int r = fis.read(buf);
                if (r < 0 || writtenSamples >= maxSamples) {
                    enc.queueInputBuffer(in, 0, 0, pts,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    b.put(buf, 0, r);
                    long samples = r / (2 * ch);
                    writtenSamples += samples;
                    enc.queueInputBuffer(in, 0, r, pts, 0);
                    pts += (samples * 1_000_000L) / sr;
                }
            }

            int out = enc.dequeueOutputBuffer(info, 10_000);
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(enc.getOutputFormat());
                muxer.start();
                muxStarted = true;
            } else if (out >= 0) {
                if (muxStarted && info.size > 0) {
                    ByteBuffer b = enc.getOutputBuffer(out);
                    muxer.writeSampleData(trackIndex, b, info);
                }
                enc.releaseOutputBuffer(out, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    done = true;
            }
        }

        fis.close();
        enc.stop();
        enc.release();
        return trackIndex;
    }

    // ===================== VIDEO =====================

    private static int addVideoTrack(String path, MediaMuxer muxer) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(path);

        int track = -1;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            if (f.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                track = muxer.addTrack(f);
                break;
            }
        }
        ex.release();
        return track;
    }

    private static void copyVideoSamples(String path,
                                         MediaMuxer muxer,
                                         int track) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(path);

        int t = -1;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                t = i;
                break;
            }
        }
        ex.selectTrack(t);

        ByteBuffer buf = ByteBuffer.allocate(512 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long firstPts = -1;

        while (true) {
            int r = ex.readSampleData(buf, 0);
            if (r < 0) break;
            if (firstPts < 0) firstPts = ex.getSampleTime();

            info.size = r;
            info.presentationTimeUs = ex.getSampleTime() - firstPts;
            info.flags = ex.getSampleFlags();
            info.offset = 0;

            muxer.writeSampleData(track, buf, info);
            ex.advance();
        }
        ex.release();
    }
}
