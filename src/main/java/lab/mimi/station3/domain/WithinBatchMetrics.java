package lab.mimi.station3.domain;

public record WithinBatchMetrics(
        int firstVial,
        int secondVial,
        int thirdVial,
        double vial1ToVial2DeltaE00,
        double vial1ToVial3DeltaE00,
        double vial2ToVial3DeltaE00,
        String referencePolicyVersion,
        String referenceSource,
        String comparisonDescription) {

    public WithinBatchMetrics {
        requireWellFormed(firstVial, secondVial, thirdVial,
                vial1ToVial2DeltaE00, vial1ToVial3DeltaE00, vial2ToVial3DeltaE00);
        referencePolicyVersion = required(referencePolicyVersion, "reference policy version");
        referenceSource = required(referenceSource, "reference source");
        comparisonDescription = required(comparisonDescription, "comparison description");
    }

    public static WithinBatchMetrics unavailable(String reason) {
        return new WithinBatchMetrics(0, 0, 0, Double.NaN, Double.NaN, Double.NaN,
                "station3-video5-third-vial-provisional-v1", "NONE", reason);
    }

    private static void requireWellFormed(
            int firstVial,
            int secondVial,
            int thirdVial,
            double firstToSecond,
            double firstToThird,
            double secondToThird) {
        boolean unavailableIdentity = firstVial == 0 && secondVial == 0 && thirdVial == 0;
        boolean availableIdentity = firstVial > 0 && firstVial < secondVial && secondVial < thirdVial;
        if (!unavailableIdentity && !availableIdentity) {
            throw new IllegalArgumentException(
                    "vial identities must be all zero or positive and strictly increasing");
        }

        boolean unavailableMetrics = Double.isNaN(firstToSecond)
                && Double.isNaN(firstToThird)
                && Double.isNaN(secondToThird);
        boolean availableMetrics = validDelta(firstToSecond)
                && validDelta(firstToThird)
                && validDelta(secondToThird);
        if (unavailableIdentity != unavailableMetrics || availableIdentity != availableMetrics) {
            throw new IllegalArgumentException(
                    "vial identities and pairwise metrics must be available or unavailable together");
        }
    }

    private static boolean validDelta(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value;
    }
}

