package lab.mimi.station3.analysis;

import lab.mimi.station3.ingest.CanonicalObservationIngestor;
import lab.mimi.station3.ingest.CsvIngestor;
import lab.mimi.station3.domain.VialRecord;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalReplayService {
    public static final String VIDEO_HASH = "6f87cec1aeec3b2bae6dac639a317c077a3f175f27abd5fc8430d6953d9d05b9";
    public static final String MANIFEST_HASH = "4baf9246f8b34b803f8e3de4ef9915375311be83f5de91edf8cb249a78254f6d";
    public static final String EXTRACTOR_HASH = "a99193d32bfcd3d8dea26757621b36ba820a2298a15b27c86d4285bfb4730005";
    public static final String CONFIG_BYTE_HASH = "b1c26ff5957ddee38d6547662e17635a06655d470e594f7860f38b98ec83b823";
    public static final String CONFIG_CANONICAL_HASH = "0566b389c1f4118c4fa6cc215e4319ab850dbc8cc95134efd6241f5d3511772b";
    public static final String ORIGINAL_OBSERVATION_HASH = "310fdbafbb25e13f2e73fbb6cb4360100b7ec97fb4088f22a650a0c6aa8af901";
    public static final String BUNDLED_OBSERVATION_HASH = "5a6f09c6cdab61b13362168d715197bd508457b744d36fb2a52bd78629bd3920";

    private CanonicalReplayService() {}

    public static Replay run() throws IOException {
        Path workspace = Files.createTempDirectory("station3-canonical-replay-");
        try {
            Path manifest = copyResource("source/video5_vial_recipes.csv", workspace.resolve("manifest.csv"));
            Path observations = copyResource("canonical/observations.csv", workspace.resolve("observations.csv"));
            requireHash(manifest, MANIFEST_HASH, "bundled canonical manifest");
            requireHash(observations, BUNDLED_OBSERVATION_HASH, "bundled canonical observation table");
            var extracted = CanonicalObservationIngestor.readNominal(observations);
            if (!CONFIG_CANONICAL_HASH.equals(extracted.configurationHash())) throw new IOException("canonical observation config hash mismatch");
            if (!EXTRACTOR_HASH.equals(extracted.extractorHash())) throw new IOException("canonical observation extractor hash mismatch");
            if (!VIDEO_HASH.equals(extracted.sourceHash())) throw new IOException("canonical observation source hash mismatch");

            int accepted = (int) extracted.measurements().stream().filter(item -> item.imageQuality() == 1.0).count();
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("replayMode", "PRECOMPUTED_CANONICAL_EXTRACTION_REPLAY");
            receipt.put("sourceVideoSha256", VIDEO_HASH);
            receipt.put("manifestSha256", MANIFEST_HASH);
            receipt.put("extractorSha256", EXTRACTOR_HASH);
            receipt.put("configurationByteSha256", CONFIG_BYTE_HASH);
            receipt.put("configurationCanonicalSha256", CONFIG_CANONICAL_HASH);
            receipt.put("originalObservationCsvSha256", ORIGINAL_OBSERVATION_HASH);
            receipt.put("bundledObservationCsvSha256", BUNDLED_OBSERVATION_HASH);
            receipt.put("lineEndingNote", "bundled UTF-8 table uses Eclipse-normalized LF; cell content and 270 rows are unchanged");
            receipt.put("expectedVials", 30);
            receipt.put("acceptedVials", accepted);
            receipt.put("rejectedVials", 30 - accepted);
            receipt.put("selectedFramesByVial", extracted.selectedFrames());
            receipt.put("rejectionReasonsByVial", extracted.rejectionReasons());
            receipt.put("sourceFrameContract", SourceContractEvidence.verify(VIDEO_HASH, MANIFEST_HASH));
            receipt.put("provenance", "VERIFIED_CANONICAL_VIDEO_5");
            return new Replay(CsvIngestor.readManifest(manifest), extracted, Map.copyOf(receipt));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static Path copyResource(String resource, Path target) throws IOException {
        try (var input = new ClassPathResource(resource).getInputStream()) { Files.copy(input, target); }
        return target;
    }

    private static void requireHash(Path path, String expected, String label) throws IOException {
        String actual = sha256(path);
        if (!expected.equals(actual)) throw new IOException(label + " hash mismatch: " + actual);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    public record Replay(List<VialRecord> manifest, CanonicalObservationIngestor.Result observations,
            Map<String, Object> receipt) {}
}
