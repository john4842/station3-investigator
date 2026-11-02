package lab.mimi.station3.ingest;

import lab.mimi.station3.domain.LabColor;
import lab.mimi.station3.domain.VialMeasurement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalObservationIngestor {
    private CanonicalObservationIngestor() {}

    public static Result readNominal(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IllegalArgumentException("canonical observations are empty: " + path);
        Map<String, Integer> header = header(lines.getFirst());
        require(header, "vial", "recipe_id", "timing_shift_s", "mask_morphology_px", "lab_l", "lab_a",
                "lab_b", "evidence_status", "abstention_reasons", "frame_receipts", "configuration_sha256",
                "code_version_sha256", "source_video_sha256");

        Map<Integer, VialMeasurement> byVial = new LinkedHashMap<>();
        Map<Integer, Integer> recipes = new LinkedHashMap<>();
        Map<Integer, List<Integer>> selectedFrames = new LinkedHashMap<>();
        Map<Integer, List<String>> rejectionReasons = new LinkedHashMap<>();
        String configHash = null;
        String extractorHash = null;
        String sourceHash = null;

        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> cells = split(lines.get(i));
            if (decimal(cells, header.get("timing_shift_s")) != 0.0
                    || integer(cells, header.get("mask_morphology_px")) != 0) continue;
            int vial = integer(cells, header.get("vial"));
            if (byVial.containsKey(vial)) throw new IllegalArgumentException("duplicate nominal observation for vial " + vial);
            int recipe = integer(cells, header.get("recipe_id"));
            String status = cell(cells, header.get("evidence_status"));
            String rowConfig = cell(cells, header.get("configuration_sha256"));
            String rowExtractor = cell(cells, header.get("code_version_sha256"));
            String rowSource = cell(cells, header.get("source_video_sha256"));
            configHash = same(configHash, rowConfig, "configuration hash");
            extractorHash = same(extractorHash, rowExtractor, "extractor hash");
            sourceHash = same(sourceHash, rowSource, "source hash");
            List<String> reasons = jsonStringArray(cell(cells, header.get("abstention_reasons")));
            List<Integer> frames = frameIds(cell(cells, header.get("frame_receipts")));
            double quality = "pass".equals(status) ? 1.0 : 0.0;
            byVial.put(vial, new VialMeasurement(vial,
                    new LabColor(decimal(cells, header.get("lab_l")), decimal(cells, header.get("lab_a")),
                            decimal(cells, header.get("lab_b"))),
                    quality, "canonical-video-5:vial-" + vial + ":frames-" + frames,
                    rowConfig, rowExtractor, rowSource));
            recipes.put(vial, recipe);
            selectedFrames.put(vial, frames);
            rejectionReasons.put(vial, reasons);
        }
        if (byVial.size() != 30) throw new IllegalArgumentException("expected 30 nominal observations, found " + byVial.size());
        for (int vial = 1; vial <= 30; vial++) {
            if (!byVial.containsKey(vial)) throw new IllegalArgumentException("missing nominal observation for vial " + vial);
        }
        return new Result(List.copyOf(byVial.values()), Map.copyOf(recipes), Map.copyOf(selectedFrames),
                Map.copyOf(rejectionReasons), configHash, extractorHash, sourceHash);
    }

    private static String same(String previous, String value, String name) {
        if (previous != null && !previous.equals(value)) throw new IllegalArgumentException("mixed " + name + " values");
        return value;
    }

    private static List<Integer> frameIds(String json) {
        List<Integer> result = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("\\\"frame_id\\\"\\s*:\\s*(\\d+)").matcher(json);
        while (matcher.find()) result.add(Integer.parseInt(matcher.group(1)));
        if (result.size() != 5) throw new IllegalArgumentException("expected five selected frames, found " + result.size());
        return List.copyOf(result);
    }

    private static List<String> jsonStringArray(String json) {
        if ("[]".equals(json.strip())) return List.of();
        List<String> result = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        while (matcher.find()) result.add(matcher.group(1).replace("\\\\\\\"", "\\\"").replace("\\\\\\\\", "\\\\"));
        return List.copyOf(result);
    }

    private static Map<String, Integer> header(String line) {
        List<String> names = split(line);
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < names.size(); i++) result.put(names.get(i).trim(), i);
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

    private static String cell(List<String> cells, int index) {
        if (index >= cells.size() || cells.get(index).isBlank()) throw new IllegalArgumentException("missing CSV value");
        return cells.get(index).trim();
    }

    private static int integer(List<String> cells, int index) { return Integer.parseInt(cell(cells, index)); }
    private static double decimal(List<String> cells, int index) { return Double.parseDouble(cell(cells, index)); }

    public record Result(List<VialMeasurement> measurements, Map<Integer, Integer> recipes,
            Map<Integer, List<Integer>> selectedFrames, Map<Integer, List<String>> rejectionReasons,
            String configurationHash, String extractorHash, String sourceHash) {}
}
