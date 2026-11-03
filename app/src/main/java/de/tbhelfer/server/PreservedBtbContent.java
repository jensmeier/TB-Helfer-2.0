package de.tbhelfer.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Content-addressed recovery copies. No Android dependency; compatible with API 23. */
public final class PreservedBtbContent {
    private PreservedBtbContent() {}

    public static synchronized File preserve(File directory, String identity, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("BTB-Sicherung nicht verfügbar");
        File target = new File(directory, digest(identity.getBytes(StandardCharsets.UTF_8)) + "-" + digest(bytes) + ".json");
        if (target.exists()) {
            if (!Arrays.equals(read(target), bytes)) throw new IOException("BTB-Sicherung beschädigt");
            return target;
        }
        File temporary = File.createTempFile("btb-", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            if (!temporary.renameTo(target)) throw new IOException("BTB-Sicherung konnte nicht übernommen werden");
            return target;
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static byte[] read(File file) throws IOException {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static String digest(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
