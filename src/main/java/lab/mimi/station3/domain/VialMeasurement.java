package lab.mimi.station3.domain;

public record VialMeasurement(
        int vial,
        LabColor appearance,
        double imageQuality,
        String sourceReference,
        String configHash,
        String extractorVersion,
        String sourceHash) {
    public VialMeasurement {
        if (vial < 1) throw new IllegalArgumentException("vial must be positive");
        if (appearance == null) throw new IllegalArgumentException("CIELAB appearance is required");
        if (!Double.isFinite(imageQuality) || imageQuality < 0 || imageQuality > 1) {
            throw new IllegalArgumentException("image quality must be in [0,1]");
        }
        if (sourceReference == null || sourceReference.isBlank()) throw new IllegalArgumentException("source reference is required");
        if (configHash == null || configHash.isBlank()) throw new IllegalArgumentException("config hash is required");
        if (extractorVersion == null || extractorVersion.isBlank()) throw new IllegalArgumentException("extractor version is required");
        if (sourceHash == null || sourceHash.isBlank()) throw new IllegalArgumentException("source hash is required");
    }
}
