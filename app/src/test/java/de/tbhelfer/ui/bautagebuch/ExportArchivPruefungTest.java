package de.tbhelfer.ui.bautagebuch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportArchivPruefungTest {
    private File archive(String workbook, String target, String image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream xlsx = new ZipOutputStream(bytes)) {
            xlsx.putNextEntry(new ZipEntry("xl/worksheets/_rels/sheet1.xml.rels"));
            xlsx.write(("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" " +
                    "TargetMode=\"External\" Target=\"" + target + "\"/></Relationships>").getBytes(StandardCharsets.UTF_8));
            xlsx.closeEntry();
        }
        File file = File.createTempFile("export-test", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(workbook)); zip.write(bytes.toByteArray()); zip.closeEntry();
            if (image != null) { zip.putNextEntry(new ZipEntry(image)); zip.write(1); zip.closeEntry(); }
        }
        return file;
    }
    private void valid(String workbook, String target, String image, int count) throws Exception {
        File file = archive(workbook, target, image);
        try { assertEquals(count, ExportArchivPruefung.pruefen(file)); } finally { file.delete(); }
    }
    private void invalid(String workbook, String target, String image) throws Exception {
        File file = archive(workbook, target, image);
        try {
            try { ExportArchivPruefung.pruefen(file); fail("Broken link must prevent sharing"); }
            catch (IOException expected) { assertNotNull(expected.getMessage()); }
        } finally { file.delete(); }
    }
    @Test public void dailyLinkResolvesTwoParentDirectories() throws Exception {
        valid("Site/BTB/2026-11-03/day.xlsx", "../../Bilder/2026-11-03/B001.jpg", "Site/Bilder/2026-11-03/B001.jpg", 1);
    }
    @Test public void totalListMustReferenceActualManifestName() throws Exception {
        invalid("Site/gesamt.xlsx", "Bilder/2026-11-03/B066.jpg", "Site/Bilder/2026-11-03/B001.jpg");
    }
    @Test public void missingOutsideRangeImageFails() throws Exception {
        invalid("Site/gesamt.xlsx", "Bilder/2026-10-26/B001.jpg", null);
    }
    @Test public void encodedUmlautAndSpaceResolve() throws Exception {
        valid("Site/list.xlsx", "Bilder/M%C3%A4ngel%20Foto.jpg", "Site/Bilder/Mängel Foto.jpg", 1);
    }
    @Test public void literalPlusAndPercentRemainIntact() throws Exception {
        valid("Site/list.xlsx", "Bilder/1+2_100%.jpg", "Site/Bilder/1+2_100%.jpg", 1);
    }
    @Test public void externalGpsLinkIsNotAnArchiveImage() throws Exception {
        valid("Site/list.xlsx", "https://maps.google.com/", null, 0);
    }
    @Test public void pathCannotEscapeArchive() throws Exception {
        invalid("Site/list.xlsx", "../../outside.jpg", null);
    }
    @Test public void sameFileCanAppearInMultipleLists() throws Exception {
        valid("Site/BTB/day/night.xlsx", "../../Bilder/shared.jpg", "Site/Bilder/shared.jpg", 1);
    }
}
