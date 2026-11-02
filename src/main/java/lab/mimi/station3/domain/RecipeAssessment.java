package lab.mimi.station3.domain;

import java.util.List;

public record RecipeAssessment(
        int recipeId,
        EvidenceStatus status,
        InvestigationHypothesis investigationHypothesis,
        int expectedVials,
        int acceptedMeasurements,
        double firstToLaterMeanDeltaE00,
        double laterPairDeltaE00,
        double predecessorDeltaE00,
        WithinBatchMetrics withinBatchMetrics,
        BatchConsistency batchConsistency,
        RecipeConformity recipeConformity,
        String recipeConformityReference,
        String explanation,
        List<String> sourceReferences,
        List<String> configHashes,
        List<String> extractorVersions,
        List<String> sourceHashes,
        List<String> rejectionReasons) {

    public RecipeAssessment {
        requireWellFormed(recipeId, status, investigationHypothesis, expectedVials, acceptedMeasurements,
                firstToLaterMeanDeltaE00, laterPairDeltaE00, predecessorDeltaE00, withinBatchMetrics,
                batchConsistency, recipeConformity);
        recipeConformityReference = required(recipeConformityReference, "recipe conformity reference");
        explanation = required(explanation, "explanation");
        sourceReferences = copyText(sourceReferences, "source references");
        configHashes = copyText(configHashes, "config hashes");
        extractorVersions = copyText(extractorVersions, "extractor versions");
        sourceHashes = copyText(sourceHashes, "source hashes");
        rejectionReasons = copyText(rejectionReasons, "rejection reasons");
    }

    private static void requireWellFormed(
            int recipeId,
            EvidenceStatus status,
            InvestigationHypothesis hypothesis,
            int expectedVials,
            int acceptedMeasurements,
            double firstToLater,
            double laterPair,
            double predecessor,
            WithinBatchMetrics metrics,
            BatchConsistency batchConsistency,
            RecipeConformity recipeConformity) {
        if (recipeId < 1) throw new IllegalArgumentException("recipe ID must be positive");
        required(status, "evidence status");
        required(hypothesis, "investigation hypothesis");
        required(metrics, "within-batch metrics");
        required(batchConsistency, "batch consistency");
        required(recipeConformity, "recipe conformity");
        if (expectedVials < 0 || acceptedMeasurements < 0 || acceptedMeasurements > expectedVials) {
            throw new IllegalArgumentException("accepted measurements must be between zero and expected vials");
        }
        requireDeltaOrUnavailable(firstToLater, "first-to-later delta E00");
        requireDeltaOrUnavailable(laterPair, "later-pair delta E00");
        requireDeltaOrUnavailable(predecessor, "predecessor delta E00");

        if (acceptedMeasurements < expectedVials) {
            if (!Double.isNaN(firstToLater) || !Double.isNaN(laterPair)
                    || !Double.isNaN(predecessor) || metrics.firstVial() != 0) {
                throw new IllegalArgumentException("incomplete evidence cannot carry computed comparison metrics");
            }
        } else if (acceptedMeasurements == 3 && metrics.firstVial() == 0) {
            throw new IllegalArgumentException("three accepted measurements require available pairwise metrics");
        }

        if (hypothesis == InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE) {
            if (!Double.isNaN(predecessor)) {
                throw new IllegalArgumentException("unavailable predecessor direction requires an unavailable distance");
            }
        } else if (!Double.isFinite(predecessor)) {
            throw new IllegalArgumentException("a predecessor-direction result requires a finite distance");
        }
    }

    private static void requireDeltaOrUnavailable(double value, String label) {
        if (Double.isInfinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be nonnegative or unavailable");
        }
    }

    private static <T> T required(T value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    private static List<String> copyText(List<String> values, String label) {
        if (values == null) throw new IllegalArgumentException(label + " are required");
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " cannot contain blank values");
            }
        }
        return List.copyOf(values);
    }
}

