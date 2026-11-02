package lab.mimi.station3.domain;

public record LabColor(double lStar, double aStar, double bStar) {
    public LabColor {
        if (!Double.isFinite(lStar) || !Double.isFinite(aStar) || !Double.isFinite(bStar)) {
            throw new IllegalArgumentException("CIELAB components must be finite");
        }
        if (lStar < 0 || lStar > 100) throw new IllegalArgumentException("CIELAB L* must be in [0,100]");
    }

    public static LabColor mean(LabColor left, LabColor right) {
        return new LabColor(
                (left.lStar + right.lStar) / 2.0,
                (left.aStar + right.aStar) / 2.0,
                (left.bStar + right.bStar) / 2.0);
    }
}
