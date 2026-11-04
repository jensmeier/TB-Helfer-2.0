package de.tbhelfer.ui.bautagebuch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;

/** Checks the finished archive, not a second independently calculated filename list. */
public final class ExportArchivPruefung {
    private ExportArchivPruefung() {}

    public static int pruefen(File archive) throws IOException {
        int checked = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Set<String> files = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) files.add(entry.getName());
            }
            for (String workbook : files) {
                if (!workbook.endsWith(".xlsx")) continue;
                String folder = workbook.contains("/")
                        ? workbook.substring(0, workbook.lastIndexOf('/') + 1) : "";
                try (ZipInputStream inner = new ZipInputStream(zip.getInputStream(zip.getEntry(workbook)))) {
                    ZipEntry entry;
                    while ((entry = inner.getNextEntry()) != null) {
                        if (!entry.getName().endsWith(".rels")) continue;
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = inner.read(buffer)) != -1) bytes.write(buffer, 0, read);
                        String xml = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                        if (xml.contains("<!DOCTYPE")) throw new IOException("Unerwartete XML-Deklaration im Export.");
                        try {
                            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                            factory.setNamespaceAware(true);
                            factory.setExpandEntityReferences(false);
                            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
                            NodeList relations = builder.parse(new ByteArrayInputStream(bytes.toByteArray()))
                                    .getElementsByTagNameNS("*", "Relationship");
                            for (int i = 0; i < relations.getLength(); i++) {
                                Element relation = (Element) relations.item(i);
                                if (!relation.getAttribute("Type").endsWith("/hyperlink")) continue;
                                String target = relation.getAttribute("Target");
                                if (target.startsWith("https://") || target.startsWith("http://") || target.startsWith("mailto:")) continue;
                                String resolved = resolve(folder, target);
                                boolean found = files.contains(resolved);
                                if (!found && target.contains("%")) {
                                    found = files.contains(resolve(folder, URLDecoder.decode(target.replace("+", "%2B"), "UTF-8")));
                                }
                                if (!found) throw new IOException("Bildverweis fehlt im ZIP: " + workbook + " → " + target);
                                checked++;
                            }
                        } catch (IOException error) {
                            throw error;
                        } catch (Exception error) {
                            throw new IOException("Bilderliste konnte nicht geprüft werden: " + workbook, error);
                        }
                    }
                }
            }
        }
        return checked;
    }

    private static String resolve(String folder, String target) throws IOException {
        if (target.startsWith("/") || target.contains(":")) throw new IOException("Ungültiger Bildpfad im Export.");
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : (folder + target.replace('\\', '/')).split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (parts.isEmpty()) throw new IOException("Bildpfad verlässt das Exportarchiv.");
                parts.removeLast();
            } else parts.addLast(part);
        }
        return String.join("/", parts);
    }
}
