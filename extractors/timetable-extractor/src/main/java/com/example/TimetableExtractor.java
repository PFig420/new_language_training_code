/*
 * Bus Timetable PDF → CSV Extractor  (Java / Apache PDFBox 3.x)
 * ==============================================================
 * Maven dependency
 *   <dependency>
 *     <groupId>org.apache.pdfbox</groupId>
 *     <artifactId>pdfbox</artifactId>
 *     <version>3.0.1</version>
 *   </dependency>
 *
 * Gradle dependency
 *   implementation 'org.apache.pdfbox:pdfbox:3.0.1'
 *
 * Compile with standalone jar (no build tool)
 *   javac -cp pdfbox-app-3.0.1.jar TimetableExtractor.java
 *
 * Run
 *   java -cp ".:pdfbox-app-3.0.1.jar" TimetableExtractor input.pdf [output.csv]
 *   java -cp ".;pdfbox-app-3.0.1.jar" TimetableExtractor input.pdf [output.csv]  (Windows)
 */

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

public class TimetableExtractor {

    /** Matches time tokens such as 7:40 or 10:00. */
    private static final Pattern TIME_RE = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: TimetableExtractor <input.pdf> [output.csv]");
            System.exit(1);
        }

        String pdfPath = args[0];
        String csvPath = args.length > 1
                ? args[1]
                : Path.of(pdfPath).toString().replaceAll("\\.pdf$", ".csv");

        List<String[]> rows = extractRows(pdfPath);

        if (rows.isEmpty()) {
            System.err.println("No timetable data found.");
            System.exit(1);
        }

        writeCsv(rows, csvPath);
        System.out.printf("✓ %d stops written to '%s'%n", rows.size(), csvPath);
    }

    /**
     * Extract all timetable rows from the PDF.
     *
     * <p>Strategy: strip the full text with position-aware sorting, then for each
     * line match all time tokens. Everything before the first token is the stop name;
     * everything that matches is a departure time. Lines with no tokens are skipped
     * (headers, footers, legend lines, etc.).
     */
    private static List<String[]> extractRows(String pdfPath) throws IOException {
        List<String[]> rows = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // preserve reading order
            String text = stripper.getText(doc);

            for (String rawLine : text.split("\\r?\\n")) {
                String line = rawLine.strip();
                Matcher matcher = TIME_RE.matcher(line);

                List<String> times = new ArrayList<>();
                int firstStart = -1;

                while (matcher.find()) {
                    if (firstStart < 0) firstStart = matcher.start();
                    times.add(matcher.group());
                }

                if (times.isEmpty() || firstStart < 0) continue;

                String stopName = line.substring(0, firstStart).strip();
                if (stopName.isEmpty()) continue;

                String[] row = new String[1 + times.size()];
                row[0] = stopName;
                for (int i = 0; i < times.size(); i++) row[i + 1] = times.get(i);
                rows.add(row);
            }
        }

        return rows;
    }

    /** Write extracted rows to a UTF-8 CSV file with a generated header. */
    private static void writeCsv(List<String[]> rows, String csvPath) throws IOException {
        int maxServices = rows.stream().mapToInt(r -> r.length - 1).max().orElse(0);

        String[] header = new String[1 + maxServices];
        header[0] = "Paragem";
        for (int i = 1; i <= maxServices; i++) header[i] = "Serviço_" + i;

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(csvPath), StandardCharsets.UTF_8))) {
            pw.println(toCsvLine(header));
            for (String[] row : rows) pw.println(toCsvLine(row));
        }
    }

    /** Minimal RFC 4180 CSV serialisation for a single row. */
    private static String toCsvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String f = fields[i] != null ? fields[i] : "";
            if (f.contains(",") || f.contains("\"") || f.contains("\n")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }
}
