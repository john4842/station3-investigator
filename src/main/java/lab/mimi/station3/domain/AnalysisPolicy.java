package lab.mimi.station3.domain;

public record AnalysisPolicy(
        String version,
        double minimumImageQuality,
        double maximumLaterPairDeltaE00,
        double lowerFirstVialDeltaE00,
        double upperFirstVialDeltaE00) {

    public AnalysisPolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("analysis policy version is required");
        }
        requireFinite(minimumImageQuality, "minimum image quality");
        requireFinite(maximumLaterPairDeltaE00, "maximum later-pair delta E00");
        requireFinite(lowerFirstVialDeltaE00, "lower first-vial delta E00");
        requireFinite(upperFirstVialDeltaE00, "upper first-vial delta E00");
        if (minimumImageQuality < 0.0 || minimumImageQuality > 1.0) {
            throw new IllegalArgumentException("minimum image quality must be in [0,1]");
        }
        if (maximumLaterPairDeltaE00 < 0.0 || lowerFirstVialDeltaE00 < 0.0) {
            throw new IllegalArgumentException("delta E00 thresholds must be nonnegative");
        }
        if (upperFirstVialDeltaE00 < lowerFirstVialDeltaE00) {
            throw new IllegalArgumentException("upper first-vial threshold must be at least the lower threshold");
        }
    }

    public static AnalysisPolicy singleThreshold(
            double minimumImageQuality,
            double maximumLaterPairDeltaE00,
            double firstVialDeltaE00) {
        return new AnalysisPolicy("single-threshold-compatibility", minimumImageQuality,
                maximumLaterPairDeltaE00, firstVialDeltaE00, firstVialDeltaE00);
    }

    public boolean hasSensitivityBand() {
        return lowerFirstVialDeltaE00 < upperFirstVialDeltaE00;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
