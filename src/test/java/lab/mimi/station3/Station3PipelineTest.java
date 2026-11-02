package lab.mimi.station3;

import lab.mimi.station3.analysis.CanonicalReplayService;
import lab.mimi.station3.analysis.Ciede2000;
import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.EvidenceStatus;
import lab.mimi.station3.domain.AnalysisPolicy;
import lab.mimi.station3.domain.BatchConsistency;
import lab.mimi.station3.domain.InvestigationHypothesis;
import lab.mimi.station3.domain.LabColor;
import lab.mimi.station3.domain.RecipeConformity;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.ingest.CanonicalObservationIngestor;
import lab.mimi.station3.ingest.CsvIngestor;
import lab.mimi.station3.validation.ManifestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Station3PipelineTest {
    @Test
    void ciede2000MatchesPublishedReferencePair() {
        double value = Ciede2000.between(
                new LabColor(50.0000, 2.6772, -79.7751),
                new LabColor(50.0000, 0.0000, -82.7485));

        assertEquals(2.0425, value, 0.0001);
    }

    @Test
    void stableIntendedRecipeShiftDoesNotBecomeFirstVialDeviation() throws Exception {
        var manifest = twoRecipeManifest();
        var measurements = CsvIngestor.readMeasurements(Path.of("fixtures/falsifiers/stable_shift_measurements.csv"));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.WITHIN_RUN_STABLE, result.get(0).status());
        assertEquals(EvidenceStatus.WITHIN_RUN_STABLE, result.get(1).status());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_NOT_SUPPORTED,
                result.get(1).investigationHypothesis());
        assertEquals(0.0, result.get(1).firstToLaterMeanDeltaE00(), 0.000001);
    }

    @Test
    void duplicateMeasurementsCannotCounterfeitDistinctVials() throws Exception {
        var manifest = twoRecipeManifest();
        var measurements = CsvIngestor.readMeasurements(Path.of("fixtures/falsifiers/duplicate_vial_measurements.csv"));

        assertThrows(IllegalArgumentException.class,
                () -> Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0));
    }

    @Test
    void missingExpectedVialProducesInsufficientEvidence() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(measurement(1, lab(50, 0, 0)), measurement(2, lab(50, 0, 0)));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, result.getFirst().status());
        assertEquals(2, result.getFirst().acceptedMeasurements());
        assertEquals(List.of("missing measurement for expected vial 3"), result.getFirst().rejectionReasons());
    }

    @Test
    void trueFirstVialDeviationUsesStableLaterPairAndKeepsHypothesisSeparate() {
        var manifest = twoRecipeManifest();
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)), measurement(2, lab(50, 0, 0)), measurement(3, lab(50, 0, 0)),
                measurement(4, lab(50, 0, 0)), measurement(5, lab(70, 30, 20)), measurement(6, lab(70, 30, 20)));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.RETROSPECTIVE_FIRST_VIAL_DEVIATION, result.get(1).status());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_SUPPORTED,
                result.get(1).investigationHypothesis());
        assertEquals(0.0, result.get(1).laterPairDeltaE00(), 0.000001);
    }

    @Test
    void unstableLaterPairForcesAbstention() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0)),
                measurement(3, lab(80, 40, 20)));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, result.getFirst().status());
        assertEquals(BatchConsistency.INCONSISTENT, result.getFirst().batchConsistency());
        assertEquals(RecipeConformity.UNVERIFIED_REFERENCE, result.getFirst().recipeConformity());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                result.getFirst().investigationHypothesis());
    }

    @Test
    void firstVialDiffersWhileSecondAndThirdMatchIsExplicitlyInconsistent() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(70, 30, 20)),
                measurement(3, lab(70, 30, 20)));

        var assessment = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).getFirst();

        assertTrue(assessment.withinBatchMetrics().vial1ToVial2DeltaE00() > 10.0);
        assertTrue(assessment.withinBatchMetrics().vial1ToVial3DeltaE00() > 10.0);
        assertEquals(0.0, assessment.withinBatchMetrics().vial2ToVial3DeltaE00(), 0.000001);
        assertEquals(BatchConsistency.INCONSISTENT, assessment.batchConsistency());
        assertEquals(RecipeConformity.UNVERIFIED_REFERENCE, assessment.recipeConformity());
    }

    @Test
    void secondAndThirdVialDisagreementIsExplicitAndAbstains() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0)),
                measurement(3, lab(80, 40, 20)));

        var assessment = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).getFirst();

        assertTrue(assessment.withinBatchMetrics().vial2ToVial3DeltaE00() > 2.0);
        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, assessment.status());
        assertEquals(BatchConsistency.INCONSISTENT, assessment.batchConsistency());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                assessment.investigationHypothesis());
    }

    @Test
    void bothEarlierVialsCanDifferFromThirdWithoutHidingAnyPair() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(45, -30, -20)),
                measurement(2, lab(60, 10, 10)),
                measurement(3, lab(80, 40, 20)));

        var assessment = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).getFirst();

        assertTrue(assessment.withinBatchMetrics().vial1ToVial3DeltaE00() > 0.0);
        assertTrue(assessment.withinBatchMetrics().vial2ToVial3DeltaE00() > 0.0);
        assertEquals(BatchConsistency.INCONSISTENT, assessment.batchConsistency());
    }

    @Test
    void allThreeMatchingVialsAreConsistentButNotRecipeConformityProof() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0)),
                measurement(3, lab(50, 0, 0)));

        var assessment = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).getFirst();

        assertEquals(BatchConsistency.CONSISTENT, assessment.batchConsistency());
        assertEquals(RecipeConformity.UNVERIFIED_REFERENCE, assessment.recipeConformity());
        assertEquals("NONE", assessment.recipeConformityReference());
    }

    @Test
    void missingOrRejectedThirdVialMakesPairwiseAxesUnavailable() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var missing = List.of(measurement(1, lab(50, 0, 0)), measurement(2, lab(50, 0, 0)));
        var rejected = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0)),
                measurement(3, lab(50, 0, 0), 0.2));

        for (var measurements : List.of(missing, rejected)) {
            var assessment = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0).getFirst();
            assertTrue(Double.isNaN(assessment.withinBatchMetrics().vial1ToVial3DeltaE00()));
            assertEquals(BatchConsistency.INSUFFICIENT_EVIDENCE, assessment.batchConsistency());
            assertEquals(RecipeConformity.INSUFFICIENT_EVIDENCE, assessment.recipeConformity());
        }
    }

    @Test
    void nontransitiveChainComputesEveryPairIndependently() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(56, 0, 0)),
                measurement(3, lab(62, 0, 0)));

        var metrics = Station3Analyzer.analyze(manifest, measurements, 0.8, 20.0, 30.0)
                .getFirst().withinBatchMetrics();

        assertTrue(metrics.vial1ToVial2DeltaE00() > 0.0);
        assertTrue(metrics.vial2ToVial3DeltaE00() > 0.0);
        assertTrue(metrics.vial1ToVial3DeltaE00() > metrics.vial1ToVial2DeltaE00());
        assertTrue(metrics.vial1ToVial3DeltaE00() > metrics.vial2ToVial3DeltaE00());
    }

    @Test
    void predecessorDirectionCannotCrossAnIncompleteRecipeGap() {
        var manifest = threeRecipeManifest();
        var recipeSixColor = lab(50, 0, 0);
        var recipeEightLaterColor = lab(70, 30, 20);
        var measurements = List.of(
                measurement(1, recipeSixColor), measurement(2, recipeSixColor), measurement(3, recipeSixColor),
                measurement(4, lab(60, 10, 10)), measurement(5, lab(60, 10, 10)),
                measurement(7, recipeSixColor), measurement(8, recipeEightLaterColor), measurement(9, recipeEightLaterColor));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, result.get(1).status());
        assertEquals(EvidenceStatus.RETROSPECTIVE_FIRST_VIAL_DEVIATION, result.get(2).status());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                result.get(2).investigationHypothesis());
    }

    @Test
    void predecessorDirectionUsesTheEligibleImmediateRecipe() {
        var manifest = threeRecipeManifest();
        var recipeSixColor = lab(50, 0, 0);
        var recipeSevenColor = lab(60, 10, 10);
        var recipeEightLaterColor = lab(70, 30, 20);
        var measurements = List.of(
                measurement(1, recipeSixColor), measurement(2, recipeSixColor), measurement(3, recipeSixColor),
                measurement(4, recipeSevenColor), measurement(5, recipeSevenColor), measurement(6, recipeSevenColor),
                measurement(7, recipeSevenColor), measurement(8, recipeEightLaterColor), measurement(9, recipeEightLaterColor));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_SUPPORTED,
                result.get(2).investigationHypothesis());
        assertEquals(0.0, result.get(2).predecessorDeltaE00(), 0.000001);
    }

    @Test
    void unstableCurrentReferenceCannotClaimPredecessorDirection() {
        var manifest = threeRecipeManifest();
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)), measurement(2, lab(50, 0, 0)), measurement(3, lab(50, 0, 0)),
                measurement(4, lab(50, 0, 0)), measurement(5, lab(80, 40, 20)), measurement(6, lab(60, 10, 10)),
                measurement(7, lab(50, 0, 0)), measurement(8, lab(70, 30, 20)), measurement(9, lab(70, 30, 20)));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, result.get(1).status());
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                result.get(1).investigationHypothesis());
        assertEquals(true, Double.isNaN(result.get(1).predecessorDeltaE00()));
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE,
                result.get(2).investigationHypothesis());
    }

    @Test
    void imageQualityGateNamesRejectedVialAndForcesInsufficientEvidence() {
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, lab(50, 0, 0)),
                measurement(2, lab(50, 0, 0), 0.2),
                measurement(3, lab(50, 0, 0)));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, 10.0);

        assertEquals(EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE, result.getFirst().status());
        assertEquals("vial 2 image quality 0.200 is below 0.800", result.getFirst().rejectionReasons().getFirst());
    }

    @Test
    void firstVialThresholdEqualityCountsAsDeviation() {
        LabColor first = lab(50, 0, 0);
        LabColor later = lab(70, 30, 20);
        double exactThreshold = Ciede2000.between(first, later);
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(
                measurement(1, first), measurement(2, later), measurement(3, later));

        var result = Station3Analyzer.analyze(manifest, measurements, 0.8, 2.0, exactThreshold);

        assertEquals(EvidenceStatus.RETROSPECTIVE_FIRST_VIAL_DEVIATION, result.getFirst().status());
    }

    @Test
    void thresholdBandKeepsBothUncalibratedInterpretationsVisible() {
        LabColor first = lab(50, 0, 0);
        LabColor later = lab(70, 30, 20);
        double measured = Ciede2000.between(first, later);
        var policy = new AnalysisPolicy("test-band", 0.8, 2.0, measured - 1.0, measured + 1.0);
        var manifest = List.of(vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));
        var measurements = List.of(measurement(1, first), measurement(2, later), measurement(3, later));

        var result = Station3Analyzer.analyze(manifest, measurements, policy);

        assertEquals(EvidenceStatus.THRESHOLD_SENSITIVE, result.getFirst().status());
    }

    @Test
    void invalidOrNonfinitePolicyIsRejectedAtRuntime() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisPolicy("bad", 0.8, 2.0, 10.0, 7.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisPolicy("bad", 0.8, Double.NaN, 7.0, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisPolicy("bad", 0.8, 2.0, 7.0, Double.POSITIVE_INFINITY));
    }

    @Tag("organizer-data")
    @Test
    void actualVideo5ManifestParsesThirtyRowsAsTenGroupsOfThree() throws Exception {
        var records = CsvIngestor.readManifest(Path.of("evidence/run5/source/video5_vial_recipes.csv"));
        ManifestValidator.requireWellFormed(records);

        assertEquals(30, records.size());
        var groupSizes = records.stream().collect(Collectors.groupingBy(VialRecord::recipeId, Collectors.counting()));
        assertEquals(10, groupSizes.size());
        assertEquals(List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), groupSizes.keySet().stream().sorted().toList());
        groupSizes.values().forEach(size -> assertEquals(3L, size));
    }

    @Test
    void wellFormedRejectsDuplicateOrNoncontiguousVials() {
        assertThrows(IllegalArgumentException.class,
                () -> ManifestValidator.requireWellFormed(List.of(vial(1, 6, 0), vial(3, 6, 20))));
    }

    @Test
    void manifestRejectsIncompleteOrRecurringRecipeGroups() {
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.requireWellFormed(List.of(
                vial(1, 6, 0), vial(2, 6, 20),
                vial(3, 7, 40), vial(4, 7, 60), vial(5, 7, 80))));

        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.requireWellFormed(List.of(
                vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40),
                vial(4, 7, 60), vial(5, 7, 80), vial(6, 7, 100),
                vial(7, 6, 120), vial(8, 6, 140), vial(9, 6, 160))));
    }

    @Test
    void analyzerEnforcesManifestSequenceBeforePredecessorDirection() {
        var outOfOrder = List.of(
                vial(4, 7, 60), vial(5, 7, 80), vial(6, 7, 100),
                vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40));

        assertThrows(IllegalArgumentException.class,
                () -> Station3Analyzer.analyze(outOfOrder, List.of(), 0.8, 2.0, 10.0));
    }

    @Test
    void runtimeConstructorRejectsImpossibleIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new VialRecord(1, 6, 4, 3, 0, 10, "test"));
    }

    @Tag("organizer-data")
    @Test
    void canonicalReplayRunsQualityExtractorAndMatchesRecipeSeven() throws Exception {
        var replay = CanonicalReplayService.run();
        var manifest = replay.manifest();
        var result = Station3Analyzer.analyze(manifest, replay.observations().measurements(), 0.8, 2.0, 10.0);
        var recipeSeven = result.stream().filter(item -> item.recipeId() == 7).findFirst().orElseThrow();

        assertEquals(30, replay.observations().measurements().size());
        assertEquals(13.152135, recipeSeven.firstToLaterMeanDeltaE00(), 0.000001);
        assertEquals(0.797700, recipeSeven.laterPairDeltaE00(), 0.000001);
        assertEquals("VERIFIED_CANONICAL_VIDEO_5", replay.receipt().get("provenance"));
    }

    @Tag("organizer-data")
    @Test
    void canonicalRecipeThirteenIsExplicitlyThresholdSensitive() throws Exception {
        var replay = CanonicalReplayService.run();
        var policy = new AnalysisPolicy("station3-prototype-threshold-band-v1", 0.8, 2.0, 7.0, 10.0);

        var result = Station3Analyzer.analyze(replay.manifest(), replay.observations().measurements(), policy);
        var recipeThirteen = result.stream().filter(item -> item.recipeId() == 13).findFirst().orElseThrow();

        assertEquals(7.165498, recipeThirteen.firstToLaterMeanDeltaE00(), 0.000001);
        assertEquals(EvidenceStatus.THRESHOLD_SENSITIVE, recipeThirteen.status());
    }

    @Tag("organizer-data")
    @Test
    void canonicalObservationJoinIsInvariantToCsvRowOrder() throws Exception {
        Path source = Files.createTempFile("station3-observations-source-", ".csv");
        try (var input = new ClassPathResource("canonical/observations.csv").getInputStream()) {
            Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var original = CanonicalObservationIngestor.readNominal(source);
        List<String> lines = Files.readAllLines(source);
        List<String> reversed = new ArrayList<>();
        reversed.add(lines.getFirst());
        for (int i = lines.size() - 1; i >= 1; i--) reversed.add(lines.get(i));
        Path temporary = Files.createTempFile("station3-observations-reversed-", ".csv");
        try {
            Files.write(temporary, reversed);
            var reordered = CanonicalObservationIngestor.readNominal(temporary);
            var originalByVial = original.measurements().stream().collect(Collectors.toMap(VialMeasurement::vial, item -> item));
            var reorderedByVial = reordered.measurements().stream().collect(Collectors.toMap(VialMeasurement::vial, item -> item));
            assertEquals(originalByVial, reorderedByVial);
        } finally {
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(source);
        }
    }

    private static List<VialRecord> twoRecipeManifest() {
        return List.of(
                vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40),
                vial(4, 7, 60), vial(5, 7, 80), vial(6, 7, 100));
    }

    private static List<VialRecord> threeRecipeManifest() {
        return List.of(
                vial(1, 6, 0), vial(2, 6, 20), vial(3, 6, 40),
                vial(4, 7, 60), vial(5, 7, 80), vial(6, 7, 100),
                vial(7, 8, 120), vial(8, 8, 140), vial(9, 8, 160));
    }

    private static VialRecord vial(int vial, int recipe, int offset) {
        return new VialRecord(vial, recipe, offset, offset + 10, offset * 30L, offset * 30L + 299, "test:" + vial);
    }

    private static LabColor lab(double l, double a, double b) {
        return new LabColor(l, a, b);
    }

    private static VialMeasurement measurement(int vial, LabColor color) {
        return measurement(vial, color, 0.95);
    }

    private static VialMeasurement measurement(int vial, LabColor color, double quality) {
        return new VialMeasurement(vial, color, quality, "test:frame-window:" + vial,
                "config-sha256", "synthetic-lab-extractor-v1", "source-sha256");
    }
}
