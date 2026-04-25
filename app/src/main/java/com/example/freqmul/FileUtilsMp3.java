package com.example.freqmul;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FileUtilsMp3 {

    public static final String SYNG_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    + "/freqmul";

    public static final String LMP3_FILE = SYNG_DIR + "/lmp3.json";

    // Crée le répertoire si besoin
    public static boolean ensurefreqmulDir(Context ctx) {
        File f = new File(SYNG_DIR);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                Toast.makeText(ctx, "Impossible de créer " + SYNG_DIR, Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    // Récursion
    public static ArrayList<String> scanMp3(File root) {
        ArrayList<String> list = new ArrayList<>();
        walk(root, list);
        return list;
    }

    private static void walk(File dir, ArrayList<String> out) {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) walk(f, out);
            } else {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".m4a")) out.add(f.getAbsolutePath());
            }
        }
    }

    // Sauvegarde JSON
    public static boolean saveJson(Context ctx, ArrayList<String> list) {
        if (!ensurefreqmulDir(ctx)) return false;

        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LMP3_FILE))) {
            bw.write(arr.toString());
            return true;
        } catch (IOException e) {
            Toast.makeText(ctx, "Erreur écriture lmp3.json", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // Chargement JSON (100% compatible Termux + Android 11+)
    public static ArrayList<String> loadJson() {
        File f = new File(LMP3_FILE);
        if (!f.exists()) return null;

        try (FileInputStream fis = new FileInputStream(f);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            JSONArray arr = new JSONArray(sb.toString());

            ArrayList<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
            return list;

        } catch (IOException | JSONException e) {
            // Si le fichier est corrompu ou illisible → on le considère inexistant
            return null;
        }
    }
}
