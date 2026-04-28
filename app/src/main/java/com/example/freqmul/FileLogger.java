package com.example.freqmul;

import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {
    private static final String FILE_CURRENT = "log.txt";
    private static final String FILE_OLD = "logold.txt";
    private static final long MAX_SIZE = 50 * 1024;

    public static void log(android.content.Context context, String message) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "freqmul");
            if (!dir.exists()) dir.mkdirs();
            File currentFile = new File(dir, FILE_CURRENT);
            if (currentFile.exists() && currentFile.length() > MAX_SIZE) {
                File oldFile = new File(dir, FILE_OLD);
                if (oldFile.exists()) oldFile.delete();
                currentFile.renameTo(oldFile);
            }
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String fullMessage = timeStamp + " - " + message + "\n";
            FileOutputStream fos = new FileOutputStream(currentFile, true);
            fos.write(fullMessage.getBytes());
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
