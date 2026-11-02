package lab.mimi.station3;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.mimi.station3.ingest.CsvIngestor;
import lab.mimi.station3.report.ReportWriter;
import lab.mimi.station3.analysis.Station3Analyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublicInputRegressionTest {
    @TempDir Path temp;

    @Test void duplicateManifestHeadersAreRejectedBeforeRows() throws Exception {
        var file = temp.resolve("manifest.csv");
        Files.writeString(file, "vial,recipe_id,start_s,end_s,start_frame,end_frame,vial\n99,6,0,10,0,300,1\n");
        var error = assertThrows(IllegalArgumentException.class, () -> CsvIngestor.readManifest(file));
        assertEquals("duplicate CSV column: vial", error.getMessage());
    }

    @Test void duplicateMeasurementHeadersAreRejected() throws Exception {
        var file = temp.resolve("measurements.csv");
        Files.writeString(file, "vial,l_star,a_star,b_star,image_quality,source_ref,config_hash,extractor_version,source_hash, vial\n");
        assertThrows(IllegalArgumentException.class, () -> CsvIngestor.readMeasurements(file));
    }

    @Test void blankHeadersAndMismatchedRowsAreRejected() throws Exception {
        var file = temp.resolve("manifest.csv");
        for (String content : List.of(
                "vial,recipe_id,start_s,end_s,start_frame,end_frame,\n",
                "vial,recipe_id,start_s,end_s,start_frame,end_frame\n1,6,0,10,0,300,extra\n",
                "vial,recipe_id,start_s,end_s,start_frame,end_frame\n1,6,0,10,0\n")) {
            Files.writeString(file, content);
            assertThrows(IllegalArgumentException.class, () -> CsvIngestor.readManifest(file));
        }
    }

    @Test void jsonRoundTripsEveryControlCharacterQuotesBackslashesAndUnicode() throws Exception {
        StringBuilder label = new StringBuilder("quoted \"value\" \\ café 🌱");
        for (char c = 0; c < 32; c++) label.append(c);
        var json = ReportWriter.json(label.toString(), true, List.of());
        assertEquals(label.toString(), new ObjectMapper().readTree(json).get("dataset").asText());
    }

    @Test void syntheticAnalysisDoesNotClaimRunFiveProvenance() throws Exception {
        var result = Station3Analyzer.analyze(
                CsvIngestor.readManifest(Path.of("fixtures/synthetic_manifest.csv")),
                CsvIngestor.readMeasurements(Path.of("fixtures/synthetic_measurements.csv")), .8, 2, 10);
        var json = ReportWriter.json("Synthetic", true, result);
        assertFalse(json.contains("VIDEO_5"));
        assertFalse(json.contains("video5"));
        assertTrue(json.contains("ORDERED_VIAL_COMPARISON"));
    }
}
