package lab.mimi.station3.analysis;

import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.ingest.CsvIngestor;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Loads only the team's public, explicitly synthetic fixtures. */
public final class SyntheticReplayService {
    private SyntheticReplayService() {}

    public static Replay run() throws IOException {
        var workspace = Files.createTempDirectory("station3-synthetic-");
        var manifest = workspace.resolve("manifest.csv");
        var measurements = workspace.resolve("measurements.csv");
        try {
            try (var input = new ClassPathResource("synthetic/synthetic_manifest.csv").getInputStream()) {
                Files.copy(input, manifest);
            }
            try (var input = new ClassPathResource("synthetic/synthetic_measurements.csv").getInputStream()) {
                Files.copy(input, measurements);
            }
            return new Replay(CsvIngestor.readManifest(manifest), CsvIngestor.readMeasurements(measurements));
        } finally {
            Files.deleteIfExists(measurements);
            Files.deleteIfExists(manifest);
            Files.deleteIfExists(workspace);
        }
    }

    public record Replay(List<VialRecord> manifest, List<VialMeasurement> measurements) {}
}
