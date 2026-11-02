package lab.mimi.station3.analysis;

import lab.mimi.station3.domain.LabColor;

public final class Ciede2000 {
    private Ciede2000() {}

    public static double between(LabColor first, LabColor second) {
        double l1 = first.lStar();
        double a1 = first.aStar();
        double b1 = first.bStar();
        double l2 = second.lStar();
        double a2 = second.aStar();
        double b2 = second.bStar();

        double c1 = Math.hypot(a1, b1);
        double c2 = Math.hypot(a2, b2);
        double meanC = (c1 + c2) / 2.0;
        double meanC7 = Math.pow(meanC, 7);
        double g = 0.5 * (1.0 - Math.sqrt(meanC7 / (meanC7 + Math.pow(25.0, 7))));

        double a1Prime = (1.0 + g) * a1;
        double a2Prime = (1.0 + g) * a2;
        double c1Prime = Math.hypot(a1Prime, b1);
        double c2Prime = Math.hypot(a2Prime, b2);
        double h1Prime = hueDegrees(b1, a1Prime);
        double h2Prime = hueDegrees(b2, a2Prime);

        double deltaLPrime = l2 - l1;
        double deltaCPrime = c2Prime - c1Prime;
        double deltaHPrimeDegrees = hueDeltaDegrees(h1Prime, h2Prime, c1Prime, c2Prime);
        double deltaHPrime = 2.0 * Math.sqrt(c1Prime * c2Prime)
                * Math.sin(Math.toRadians(deltaHPrimeDegrees / 2.0));

        double meanLPrime = (l1 + l2) / 2.0;
        double meanCPrime = (c1Prime + c2Prime) / 2.0;
        double meanHPrime = meanHueDegrees(h1Prime, h2Prime, c1Prime, c2Prime);

        double t = 1.0
                - 0.17 * Math.cos(Math.toRadians(meanHPrime - 30.0))
                + 0.24 * Math.cos(Math.toRadians(2.0 * meanHPrime))
                + 0.32 * Math.cos(Math.toRadians(3.0 * meanHPrime + 6.0))
                - 0.20 * Math.cos(Math.toRadians(4.0 * meanHPrime - 63.0));
        double deltaTheta = 30.0 * Math.exp(-Math.pow((meanHPrime - 275.0) / 25.0, 2));
        double meanCPrime7 = Math.pow(meanCPrime, 7);
        double rC = 2.0 * Math.sqrt(meanCPrime7 / (meanCPrime7 + Math.pow(25.0, 7)));
        double lOffset = meanLPrime - 50.0;
        double sL = 1.0 + (0.015 * lOffset * lOffset) / Math.sqrt(20.0 + lOffset * lOffset);
        double sC = 1.0 + 0.045 * meanCPrime;
        double sH = 1.0 + 0.015 * meanCPrime * t;
        double rT = -Math.sin(Math.toRadians(2.0 * deltaTheta)) * rC;

        double lTerm = deltaLPrime / sL;
        double cTerm = deltaCPrime / sC;
        double hTerm = deltaHPrime / sH;
        return Math.sqrt(lTerm * lTerm + cTerm * cTerm + hTerm * hTerm + rT * cTerm * hTerm);
    }

    private static double hueDegrees(double b, double aPrime) {
        double degrees = Math.toDegrees(Math.atan2(b, aPrime));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }

    private static double hueDeltaDegrees(double first, double second, double c1, double c2) {
        if (c1 * c2 == 0) return 0;
        double delta = second - first;
        if (delta > 180.0) return delta - 360.0;
        if (delta < -180.0) return delta + 360.0;
        return delta;
    }

    private static double meanHueDegrees(double first, double second, double c1, double c2) {
        if (c1 * c2 == 0) return first + second;
        if (Math.abs(first - second) <= 180.0) return (first + second) / 2.0;
        return first + second < 360.0 ? (first + second + 360.0) / 2.0 : (first + second - 360.0) / 2.0;
    }
}
