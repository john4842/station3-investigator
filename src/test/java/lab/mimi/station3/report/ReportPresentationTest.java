package lab.mimi.station3.report;

import lab.mimi.station3.analysis.CanonicalReplayService;
import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.AnalysisPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("organizer-data")
class ReportPresentationTest {
    @Test
    void unavailableNumbersAreReadableInHtmlAndValidNullsInJson() throws Exception {
        var replay = CanonicalReplayService.run();
        var policy = new AnalysisPolicy("station3-prototype-threshold-band-v1", 0.8, 2.0, 7.0, 10.0);
        var assessments = Station3Analyzer.analyze(replay.manifest(), replay.observations().measurements(), policy);

        String html = ReportWriter.html("Canonical Video 5 replay", false, assessments);
        String json = ReportWriter.json("Canonical Video 5 replay", false, assessments);

        assertFalse(html.contains("NaN"));
        assertTrue(html.contains("Unavailable"));
        assertFalse(json.contains("NaN"));
        assertTrue(json.contains("\"predecessorDeltaE00\": null"));
    }
}

