package de.tbhelfer.server;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class PreservedBtbContentTest {
    private File directory() throws IOException {
        File f = File.createTempFile("btb-unit-", ".dir");
        assertTrue(f.delete()); assertTrue(f.mkdir()); return f;
    }
    private String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024]; int n;
            while ((n=in.read(buf))!=-1) out.write(buf,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private void clean(File dir) {
        File[] files = dir.listFiles();
        if(files!=null) for(File f:files) f.delete();
        dir.delete();
    }
    @Test public void retriesKeepExactUnicodeAndUseSafeNames() throws Exception {
        File dir=directory();
        try {
            String payload="{\"text\":\"Änderung\\nkm 0+000\"}";
            File a=PreservedBtbContent.preserve(dir,"../../other",payload);
            assertEquals(payload,read(a));
            assertEquals(a,PreservedBtbContent.preserve(dir,"../../other",payload));
            assertEquals(dir.getCanonicalFile(),a.getParentFile().getCanonicalFile());
            assertEquals(1,dir.listFiles().length);
        } finally {clean(dir);}
    }
    @Test public void differentEditsRemainSeparate() throws Exception {
        File dir=directory();
        try {
            File a=PreservedBtbContent.preserve(dir,"btb","first");
            File b=PreservedBtbContent.preserve(dir,"btb","second");
            assertNotEquals(a,b); assertEquals("first",read(a)); assertEquals("second",read(b));
        } finally {clean(dir);}
    }
    @Test public void writeFailureKeepsExistingData() throws Exception {
        File file=File.createTempFile("btb-blocked-", ".json");
        try {
            try(FileOutputStream out=new FileOutputStream(file)){out.write("keep".getBytes(StandardCharsets.UTF_8));}
            try {PreservedBtbContent.preserve(file,"btb","edit"); fail("must fail");}
            catch(IOException expected){assertEquals("keep",read(file));}
        } finally {file.delete();}
    }
    @Test public void corruptExistingCopyIsNotSilentlyAcceptedOrReplaced() throws Exception {
        File dir=directory();
        try {
            File a=PreservedBtbContent.preserve(dir,"btb","first");
            try(FileOutputStream out=new FileOutputStream(a)){out.write("corrupt".getBytes(StandardCharsets.UTF_8));}
            try {PreservedBtbContent.preserve(dir,"btb","first"); fail("must fail");}
            catch(IOException expected){assertEquals("corrupt",read(a));}
        } finally {clean(dir);}
    }
    @Test public void concurrentEditsAreAllRetained() throws Exception {
        File dir=directory(); ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<File>> futures=new ArrayList<>();
            for(int i=0;i<20;i++){final String value="edit"+i; futures.add(pool.submit(()->PreservedBtbContent.preserve(dir,"btb",value)));}
            Set<String> values=new HashSet<>();
            for(Future<File> f:futures)values.add(read(f.get()));
            assertEquals(20,values.size());
            assertFalse(Arrays.stream(dir.listFiles()).anyMatch(f->f.getName().endsWith(".tmp")));
        } finally {pool.shutdownNow();clean(dir);}
    }
}

