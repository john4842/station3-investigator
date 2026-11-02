package lab.mimi.station3;

import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.EvidenceStatus;
import lab.mimi.station3.domain.InvestigationHypothesis;
import lab.mimi.station3.domain.LabColor;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentRecipeEligibilityTest {
    @Test
    void unstableCurrentRecipeCannotReportPredecessorDirection() {
        var manifest = List.of(
                vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40),
                vial(4, 7, 60), vial(5, 7, 80), vial(6, 7, 100));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0)),
                measurement(3, lab(50, 0, 0)),
                measurement(4, lab(50, 0, 0)),
                measurement(5, lab(80, 40, 20)),
                measurement(6, lab(60, 10, 10)));

        var current = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).get(1);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, current.status());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                current.investigationHypothesis());
        assertTrue(Double.isNaN(current.predecessorDeltaE00()));
    }

    private static VialRecord vial(int vial, int recipe, int offset) {
        return new VialRecord(vial, recipe, offset, offset + 10,
                offset * 30L, offset * 30L + 299, "test:" + vial);
    }

    private static LabColor lab(double l, double a, double b) {
        return new LabColor(l, a, b);
    }

    private static VialMeasurement measurement(int vial, LabColor color) {
        return new VialMeasurement(vial, color, 0.95, "test:frame-window:" + vial,
                "config-sha256", "synthetic-lab-extractor-v1", "source-sha256");
    }
}
