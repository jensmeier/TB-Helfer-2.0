import de.tbhelfer.server.PreservedBtbContent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class SyncPreservationRegression {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static String read(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("btb-preservation-test").toFile();
        try {
            String original = "{\"text\":\"Änderung\\nkm 0+000\",\"fotos\":[\"lokal\"]}";
            File a = PreservedBtbContent.preserve(root, "../../BTB", original);
            check(read(a).equals(original), "bytes changed");
            check(a.equals(PreservedBtbContent.preserve(root, "../../BTB", original)), "retry duplicated content");
            check(a.getParentFile().getCanonicalFile().equals(root.getCanonicalFile()), "unsafe path");
            File b = PreservedBtbContent.preserve(root, "../../BTB", original + " ");
            check(!a.equals(b) && read(a).equals(original), "new version overwrote old");
            File blocker = new File(root, "blocked"); Files.writeString(blocker.toPath(), "keep");
            try { PreservedBtbContent.preserve(blocker, "id", "payload"); throw new AssertionError("write should fail"); }
            catch (IOException expected) { check(read(blocker).equals("keep"), "failure damaged original"); }
            Files.writeString(a.toPath(), "corrupt");
            try { PreservedBtbContent.preserve(root, "../../BTB", original); throw new AssertionError("corruption should fail"); }
            catch (IOException expected) { check(read(a).equals("corrupt"), "corrupt target overwritten"); }
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                List<Future<File>> copies = new ArrayList<>();
                for (int i=0; i<20; i++) {
                    final String text = "{\"offline\":" + i + "}";
                    copies.add(pool.submit(() -> PreservedBtbContent.preserve(root, "parallel", text)));
                }
                Set<String> texts = new HashSet<>();
                for (Future<File> copy : copies) texts.add(read(copy.get()));
                check(texts.size()==20, "concurrent version lost");
            } finally { pool.shutdownNow(); }
            check(Arrays.stream(root.listFiles()).noneMatch(f -> f.getName().endsWith(".tmp")), "temporary leak");
            System.out.println("PASS: Unicode, idempotent retry, safe paths, immutable revisions, disk error, corrupt copy, 20 concurrent revisions, no temp leak.");
        } finally {
            for (File f : root.listFiles()) f.delete();
            root.delete();
        }
    }
}

