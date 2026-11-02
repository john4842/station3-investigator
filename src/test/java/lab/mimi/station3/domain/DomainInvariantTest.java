package lab.mimi.station3.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DomainInvariantTest {
    private static final WithinBatchMetrics AVAILABLE = new WithinBatchMetrics(
            1, 2, 3, 1.0, 2.0, 1.0, "policy", "source", "comparison");

    @Test
    void labColorRejectsNonfiniteComponentsAndOutOfRangeLightness() {
        assertThrows(IllegalArgumentException.class, () -> new LabColor(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LabColor(-0.01, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LabColor(100.01, 0, 0));
    }

    @Test
    void vialRecordRejectsInvalidIdentityIntervalsAndSource() {
        assertThrows(IllegalArgumentException.class, () -> new VialRecord(0, 1, 0, 1, 0, 1, "source"));
        assertThrows(IllegalArgumentException.class, () -> new VialRecord(1, 1, 1, 1, 0, 1, "source"));
        assertThrows(IllegalArgumentException.class, () -> new VialRecord(1, 1, 0, 1, 1, 1, "source"));
        assertThrows(IllegalArgumentException.class, () -> new VialRecord(1, 1, 0, 1, 0, 1, " "));
    }

    @Test
    void vialMeasurementRejectsInvalidQualityAndMissingProvenance() {
        LabColor color = new LabColor(50, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new VialMeasurement(1, color, 1.01, "source", "config", "extractor", "hash"));
        assertThrows(IllegalArgumentException.class,
                () -> new VialMeasurement(1, color, 0.5, "source", " ", "extractor", "hash"));
    }

    @Test
    void withinBatchMetricsRequireOneCoherentAvailabilityState() {
        assertThrows(IllegalArgumentException.class,
                () -> new WithinBatchMetrics(1, 0, 3, 1, 2, 1, "p", "s", "c"));
        assertThrows(IllegalArgumentException.class,
                () -> new WithinBatchMetrics(1, 3, 2, 1, 2, 1, "p", "s", "c"));
        assertThrows(IllegalArgumentException.class,
                () -> new WithinBatchMetrics(1, 2, 3, 1, Double.NaN, 1, "p", "s", "c"));
        assertThrows(IllegalArgumentException.class,
                () -> new WithinBatchMetrics(1, 2, 3, 1, -0.1, 1, "p", "s", "c"));
        assertEquals(0, WithinBatchMetrics.unavailable("missing").firstVial());
    }

    @Test
    void assessmentRejectsImpossibleCountsAndMetricStates() {
        assertThrows(IllegalArgumentException.class, () -> assessment(3, 4,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, Double.NaN, AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> assessment(3, 2,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, Double.NaN, AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> assessment(3, 3,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, 1.0, AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> assessment(3, 3,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_SUPPORTED, Double.NaN, AVAILABLE));
    }

    @Test
    void assessmentDefensivelyCopiesTextLists() {
        List<String> references = new ArrayList<>(List.of("frame"));
        RecipeAssessment assessment = new RecipeAssessment(7, EvidenceStatus.WITHIN_RUN_STABLE,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, 3, 3,
                1.0, 1.0, Double.NaN, AVAILABLE, BatchConsistency.CONSISTENT,
                RecipeConformity.UNVERIFIED_REFERENCE, "NONE", "valid", references,
                List.of("config"), List.of("extractor"), List.of("source"), List.of());
        references.add("mutated");
        assertEquals(List.of("frame"), assessment.sourceReferences());
        assertThrows(UnsupportedOperationException.class, () -> assessment.sourceReferences().add("x"));
    }

    @Test
    void assessmentRejectsNullOrBlankRequiredContent() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeAssessment(7, null,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, 3, 3,
                1, 1, Double.NaN, AVAILABLE, BatchConsistency.CONSISTENT,
                RecipeConformity.UNVERIFIED_REFERENCE, "NONE", "valid", List.of(),
                List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RecipeAssessment(7, EvidenceStatus.WITHIN_RUN_STABLE,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, 3, 3,
                1, 1, Double.NaN, AVAILABLE, BatchConsistency.CONSISTENT,
                RecipeConformity.UNVERIFIED_REFERENCE, "NONE", "valid", List.of(" "),
                List.of(), List.of(), List.of(), List.of()));
    }

    private static RecipeAssessment assessment(
            int expected,
            int accepted,
            InvestigationHypothesis hypothesis,
            double predecessor,
            WithinBatchMetrics metrics) {
        return new RecipeAssessment(7, EvidenceStatus.WITHIN_RUN_STABLE, hypothesis, expected, accepted,
                1.0, 1.0, predecessor, metrics, BatchConsistency.CONSISTENT,
                RecipeConformity.UNVERIFIED_REFERENCE, "NONE", "valid", List.of("frame"),
                List.of("config"), List.of("extractor"), List.of("source"), List.of());
    }
}

