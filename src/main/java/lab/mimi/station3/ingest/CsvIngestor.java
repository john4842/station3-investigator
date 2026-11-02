package lab.mimi.station3.ingest;

import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.domain.LabColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CsvIngestor {
    private CsvIngestor() {}

    public static List<VialRecord> readManifest(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IllegalArgumentException("manifest is empty: " + path);
        Map<String, Integer> header = header(lines.getFirst());
        require(header, "vial", "recipe_id", "start_s", "end_s", "start_frame", "end_frame");
        List<VialRecord> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> cells = split(lines.get(i));
            if (cells.size() != header.size()) {
                throw new IllegalArgumentException("CSV column count differs from header at " + path + ":" + (i + 1));
            }
            String ref = path + ":" + (i + 1);
            result.add(new VialRecord(
                    integer(cells, header.get("vial"), ref),
                    integer(cells, header.get("recipe_id"), ref),
                    decimal(cells, header.get("start_s"), ref),
                    decimal(cells, header.get("end_s"), ref),
                    whole(cells, header.get("start_frame"), ref),
                    whole(cells, header.get("end_frame"), ref),
                    ref));
        }
        return result;
    }

    public static List<VialMeasurement> readMeasurements(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IllegalArgumentException("measurements are empty: " + path);
        Map<String, Integer> header = header(lines.getFirst());
        require(header, "vial", "l_star", "a_star", "b_star", "image_quality", "source_ref",
                "config_hash", "extractor_version", "source_hash");
        List<VialMeasurement> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> cells = split(lines.get(i));
            if (cells.size() != header.size()) {
                throw new IllegalArgumentException("CSV column count differs from header at " + path + ":" + (i + 1));
            }
            String ref = path + ":" + (i + 1);
            result.add(new VialMeasurement(
                    integer(cells, header.get("vial"), ref),
                    new LabColor(
                            decimal(cells, header.get("l_star"), ref),
                            decimal(cells, header.get("a_star"), ref),
                            decimal(cells, header.get("b_star"), ref)),
                    decimal(cells, header.get("image_quality"), ref),
                    cell(cells, header.get("source_ref"), ref),
                    cell(cells, header.get("config_hash"), ref),
                    cell(cells, header.get("extractor_version"), ref),
                    cell(cells, header.get("source_hash"), ref)));
        }
        return result;
    }

    private static Map<String, Integer> header(String line) {
        List<String> names = split(line);
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).trim();
            if (name.isEmpty()) throw new IllegalArgumentException("CSV column name is blank");
            if (result.putIfAbsent(name, i) != null) {
                throw new IllegalArgumentException("duplicate CSV column: " + name);
            }
        }
        return result;
    }

    private static void require(Map<String, Integer> header, String... names) {
        for (String name : names) if (!header.containsKey(name)) throw new IllegalArgumentException("missing CSV column: " + name);
    }

    private static List<String> split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString()); cell.setLength(0);
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("unterminated quoted CSV field");
        cells.add(cell.toString());
        return cells;
    }

    private static String cell(List<String> cells, int index, String ref) {
        if (index >= cells.size() || cells.get(index).isBlank()) throw new IllegalArgumentException("missing value at " + ref);
        return cells.get(index).trim();
    }
    private static int integer(List<String> cells, int index, String ref) { return Integer.parseInt(cell(cells, index, ref)); }
    private static long whole(List<String> cells, int index, String ref) { return Long.parseLong(cell(cells, index, ref)); }
    private static double decimal(List<String> cells, int index, String ref) { return Double.parseDouble(cell(cells, index, ref)); }
}
