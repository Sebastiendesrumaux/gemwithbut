package com.example.freqmul;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FileUtilsMp3 {
    public static final String SYNG_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/freqmul";
    public static final String LMP3_FILE = SYNG_DIR + "/lmp3.json";

    public static ArrayList<String> scanMp3(Context ctx, String treeUriStr) {
        ArrayList<String> list = new ArrayList<>();
        if (treeUriStr == null || treeUriStr.isEmpty()) return list;
        try {
            Uri treeUri = Uri.parse(treeUriStr);
            DocumentFile root = DocumentFile.fromTreeUri(ctx, treeUri);
            if (root != null) {
                walkSaf(root, list);
            }
        } catch (Exception e) {
            FileLogger.log(ctx, "⚠️ Erreur Scan SAF : " + e.getMessage());
        }
        return list;
    }

    private static void walkSaf(DocumentFile dir, ArrayList<String> out) {
        DocumentFile[] files = dir.listFiles();
        for (DocumentFile f : files) {
            if (f.isDirectory()) {
                walkSaf(f, out);
            } else {
                String n = f.getName();
                if (n != null) {
                    n = n.toLowerCase();
                    if (n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".m4a")) {
                        out.add(f.getUri().toString()); // On stocke l'URI sécurisée
                    }
                }
            }
        }
    }

    public static boolean saveJson(Context ctx, ArrayList<String> list) {
        File f = new File(SYNG_DIR);
        if (!f.exists()) f.mkdirs();
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LMP3_FILE))) {
            bw.write(arr.toString());
            return true;
        } catch (IOException e) { return false; }
    }

    public static ArrayList<String> loadJson() {
        File f = new File(LMP3_FILE);
        if (!f.exists()) return null;
        try (FileInputStream fis = new FileInputStream(f);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return jsonToArrayList(sb.toString());
        } catch (Exception e) { return null; }
    }

    private static ArrayList<String> jsonToArrayList(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        return list;
    }
}
