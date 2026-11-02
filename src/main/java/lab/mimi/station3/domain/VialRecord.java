package lab.mimi.station3.domain;

public record VialRecord(
        int vial,
        int recipeId,
        double startSeconds,
        double endSeconds,
        long startFrame,
        long endFrame,
        String sourceReference) {

    public VialRecord {
        if (vial < 1 || recipeId < 1) throw new IllegalArgumentException("vial and recipe must be positive");
        if (!Double.isFinite(startSeconds) || !Double.isFinite(endSeconds) || startSeconds < 0 || endSeconds <= startSeconds) {
            throw new IllegalArgumentException("invalid time interval for vial " + vial);
        }
        if (startFrame < 0 || endFrame <= startFrame) throw new IllegalArgumentException("invalid frame interval for vial " + vial);
        if (sourceReference == null || sourceReference.isBlank()) throw new IllegalArgumentException("source reference is required");
    }
}
