package lab.mimi.station3.analysis;

import lab.mimi.station3.domain.EvidenceStatus;
import lab.mimi.station3.domain.AnalysisPolicy;
import lab.mimi.station3.domain.BatchConsistency;
import lab.mimi.station3.domain.InvestigationHypothesis;
import lab.mimi.station3.domain.LabColor;
import lab.mimi.station3.domain.RecipeAssessment;
import lab.mimi.station3.domain.RecipeConformity;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.domain.WithinBatchMetrics;
import lab.mimi.station3.validation.ManifestValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Station3Analyzer {
    private Station3Analyzer() {}

    public static List<RecipeAssessment> analyze(
            List<VialRecord> manifest,
            List<VialMeasurement> measurements,
            double minimumImageQuality,
            double laterPairStabilityThreshold,
            double firstVialDeviationThreshold) {

        return analyze(manifest, measurements, AnalysisPolicy.singleThreshold(
                minimumImageQuality, laterPairStabilityThreshold, firstVialDeviationThreshold));
    }

    public static List<RecipeAssessment> analyze(
            List<VialRecord> manifest,
            List<VialMeasurement> measurements,
            AnalysisPolicy policy) {

        if (manifest == null || measurements == null) {
            throw new IllegalArgumentException("manifest and measurements are required");
        }
        if (policy == null) throw new IllegalArgumentException("analysis policy is required");
        ManifestValidator.requireWellFormed(manifest);

        Map<Integer, VialRecord> byVial = new HashMap<>();
        Map<Integer, List<VialRecord>> byRecipe = new LinkedHashMap<>();
        for (VialRecord record : manifest) {
            byVial.put(record.vial(), record);
            byRecipe.computeIfAbsent(record.recipeId(), ignored -> new ArrayList<>()).add(record);
        }

        Map<Integer, VialMeasurement> measurementByVial = new HashMap<>();
        for (VialMeasurement measurement : measurements) {
            if (measurement == null) {
                throw new IllegalArgumentException("measurements cannot contain null entries");
            }
            if (!byVial.containsKey(measurement.vial())) {
                throw new IllegalArgumentException("measurement references unknown vial " + measurement.vial());
            }
            if (measurementByVial.putIfAbsent(measurement.vial(), measurement) != null) {
                throw new IllegalArgumentException("duplicate measurement for vial " + measurement.vial());
            }
        }

        List<RecipeAssessment> result = new ArrayList<>();
        LabColor previousLaterMean = null;
        for (Map.Entry<Integer, List<VialRecord>> entry : byRecipe.entrySet()) {
            // A predecessor claim is only eligible across one physical recipe boundary.
            // Consume the immediately preceding reference now, then invalidate it unless
            // this recipe establishes a new stable later-vial reference for its successor.
            LabColor immediatePredecessorLaterMean = previousLaterMean;
            previousLaterMean = null;
            List<VialRecord> expected = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparingInt(VialRecord::vial)).toList();
            List<VialMeasurement> accepted = new ArrayList<>();
            List<String> rejectionReasons = new ArrayList<>();
            for (VialRecord record : expected) {
                VialMeasurement measurement = measurementByVial.get(record.vial());
                if (measurement == null) {
                    rejectionReasons.add("missing measurement for expected vial " + record.vial());
                } else if (measurement.imageQuality() < policy.minimumImageQuality()) {
                    rejectionReasons.add("vial " + record.vial() + " image quality "
                            + format(measurement.imageQuality()) + " is below " + format(policy.minimumImageQuality()));
                } else {
                    accepted.add(measurement);
                }
            }
            List<String> refs = accepted.stream().map(VialMeasurement::sourceReference).distinct().sorted().toList();
            List<String> configHashes = accepted.stream().map(VialMeasurement::configHash).distinct().sorted().toList();
            List<String> extractorVersions = accepted.stream().map(VialMeasurement::extractorVersion).distinct().sorted().toList();
            List<String> sourceHashes = accepted.stream().map(VialMeasurement::sourceHash).distinct().sorted().toList();
            if (expected.size() != 3 || accepted.size() != expected.size()) {
                if (expected.size() != 3) rejectionReasons.add("recipe must declare exactly three expected vials");
                result.add(insufficient(entry.getKey(), expected.size(), accepted.size(), refs,
                        configHashes, extractorVersions, sourceHashes, rejectionReasons));
                continue;
            }

            VialMeasurement first = accepted.get(0);
            VialMeasurement laterOne = accepted.get(1);
            VialMeasurement laterTwo = accepted.get(2);
            LabColor laterMean = LabColor.mean(laterOne.appearance(), laterTwo.appearance());
            double firstToLater = Ciede2000.between(first.appearance(), laterMean);
            double vial1ToVial2 = Ciede2000.between(first.appearance(), laterOne.appearance());
            double vial1ToVial3 = Ciede2000.between(first.appearance(), laterTwo.appearance());
            double laterPair = Ciede2000.between(laterOne.appearance(), laterTwo.appearance());
            WithinBatchMetrics withinBatchMetrics = new WithinBatchMetrics(
                    first.vial(), laterOne.vial(), laterTwo.vial(), vial1ToVial2, vial1ToVial3, laterPair,
                    "station3-ordered-third-vial-provisional-v1",
                    "ORDERED_VIAL_COMPARISON",
                    "all three pairwise CIEDE2000 distances are reported; vial 3 is a provisional within-recipe reference, not independent recipe truth");
            double predecessorDistance = immediatePredecessorLaterMean == null
                    ? Double.NaN : Ciede2000.between(first.appearance(), immediatePredecessorLaterMean);
            InvestigationHypothesis hypothesis = immediatePredecessorLaterMean == null
                    ? InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE
                    : predecessorDistance < firstToLater
                            ? InvestigationHypothesis.PREDECESSOR_DIRECTION_SUPPORTED
                            : InvestigationHypothesis.PREDECESSOR_DIRECTION_NOT_SUPPORTED;

            EvidenceStatus status;
            String explanation;
            if (laterPair > policy.maximumLaterPairDeltaE00()) {
                status = EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE;
                predecessorDistance = Double.NaN;
                hypothesis = InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE;
                rejectionReasons.add("The later-vial pair has a ΔE00 of " + format(laterPair)
                        + ", above the stability threshold of " + format(policy.maximumLaterPairDeltaE00()) + ".");
                explanation = "The later-vial reference is unstable. The analyzer does not issue a classification.";
            } else if (firstToLater >= policy.upperFirstVialDeltaE00()) {
                status = EvidenceStatus.RETROSPECTIVE_FIRST_VIAL_DEVIATION;
                explanation = "first vial differs from the mean CIELAB appearance of the two stable later vials by delta E00 "
                        + format(firstToLater) + "; this is retrospective evidence, not prospective prediction or root-cause proof";
            } else if (policy.hasSensitivityBand() && firstToLater >= policy.lowerFirstVialDeltaE00()) {
                status = EvidenceStatus.THRESHOLD_SENSITIVE;
                explanation = "first-to-later delta E00 " + format(firstToLater)
                        + " is a retrospective deviation under the provisional lower threshold "
                        + format(policy.lowerFirstVialDeltaE00()) + " but within-run stable under the provisional upper threshold "
                        + format(policy.upperFirstVialDeltaE00()) + "; no calibrated production threshold resolves this policy sensitivity";
            } else {
                status = EvidenceStatus.WITHIN_RUN_STABLE;
                explanation = "first-to-later delta E00 " + format(firstToLater)
                        + " is below the configured lower deviation threshold and later-pair delta E00 "
                        + format(laterPair) + " is within the stability threshold";
            }
            BatchConsistency batchConsistency = batchConsistency(
                    vial1ToVial2, vial1ToVial3, laterPair, policy);
            result.add(new RecipeAssessment(entry.getKey(), status, hypothesis, expected.size(), accepted.size(),
                    firstToLater, laterPair, predecessorDistance, withinBatchMetrics, batchConsistency,
                    RecipeConformity.UNVERIFIED_REFERENCE, "NONE", explanation, refs,
                    configHashes, extractorVersions, sourceHashes, rejectionReasons));
            if (laterPair <= policy.maximumLaterPairDeltaE00()) previousLaterMean = laterMean;
        }
        return List.copyOf(result);
    }

    private static RecipeAssessment insufficient(
            int recipeId,
            int expectedVials,
            int acceptedMeasurements,
            List<String> refs,
            List<String> configHashes,
            List<String> extractorVersions,
            List<String> sourceHashes,
            List<String> rejectionReasons) {
        return new RecipeAssessment(recipeId, EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, expectedVials, acceptedMeasurements,
                Double.NaN, Double.NaN, Double.NaN, WithinBatchMetrics.unavailable(
                        "three accepted vial measurements are required for explicit pairwise comparison"),
                BatchConsistency.INSUFFICIENT_EVIDENCE, RecipeConformity.INSUFFICIENT_EVIDENCE, "NONE",
                "expected distinct-vial evidence is incomplete or rejected", refs,
                configHashes, extractorVersions, sourceHashes, rejectionReasons);
    }

    private static BatchConsistency batchConsistency(
            double vial1ToVial2,
            double vial1ToVial3,
            double vial2ToVial3,
            AnalysisPolicy policy) {
        if (vial2ToVial3 > policy.maximumLaterPairDeltaE00()) return BatchConsistency.INCONSISTENT;
        double largestEarlierDifference = Math.max(vial1ToVial2, vial1ToVial3);
        if (largestEarlierDifference >= policy.upperFirstVialDeltaE00()) return BatchConsistency.INCONSISTENT;
        if (policy.hasSensitivityBand() && largestEarlierDifference >= policy.lowerFirstVialDeltaE00()) {
            return BatchConsistency.POLICY_SENSITIVE;
        }
        return BatchConsistency.CONSISTENT;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
