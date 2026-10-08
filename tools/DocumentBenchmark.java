/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
import bluej.editor.flow.HoleDocument;
import java.util.Arrays;
import java.util.Locale;

public class DocumentBenchmark {
    public static void main(String[] args) {
        System.out.println("lines,median_us,p95_us");
        for (int lines : new int[] {1000, 5000, 20000}) {
            HoleDocument doc = new HoleDocument();
            String text = "    int value = 12345; // benchmark\n".repeat(lines);
            doc.replaceText(0, 0, text);
            int pos = text.length() / 2 + 3;
            for (int i = 0; i < 3000; i++) edit(doc, pos);
            long[] times = new long[5000];
            for (int i = 0; i < times.length; i++) {
                long start = System.nanoTime();
                edit(doc, pos);
                times[i] = System.nanoTime() - start;
            }
            if (!doc.getFullContent().equals(text)) throw new AssertionError("Content changed");
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "%d,%.3f,%.3f%n", lines,
                times[times.length / 2] / 1000.0, times[(times.length * 95) / 100] / 1000.0);
        }
    }
    private static void edit(HoleDocument doc, int pos) {
        doc.replaceText(pos, pos, "x");
        doc.replaceText(pos, pos + 1, "");
        doc.getLineFromPosition(pos);
    }
}
