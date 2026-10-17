import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ZipVerify {
    public static void main(String[] args) throws Exception {
        int totalEntries = 0, bad = 0;
        for (String p : args) {
            System.out.println("### " + p);
            try (ZipFile zf = new ZipFile(p)) {
                byte[] buf = new byte[65536];
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    totalEntries++;
                    try (InputStream in = zf.getInputStream(e)) {
                        long n = 0; int r;
                        while ((r = in.read(buf)) > 0) n += r;
                        if (n != e.getSize()) {
                            System.out.println("  SIZE MISMATCH " + e.getName() + " got " + n + " want " + e.getSize());
                            bad++;
                        }
                    } catch (Exception ex) {
                        System.out.println("  READ FAIL " + e.getName() + " -> " + ex);
                        bad++;
                    }
                }
            }
            System.out.println("  entries read OK, running total " + totalEntries + ", failures " + bad);
        }
        System.out.println("TOTAL entries=" + totalEntries + " failures=" + bad);
    }
}
