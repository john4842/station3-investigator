package lab.mimi.station3.validation;

import java.util.ArrayList;
import java.util.List;

/** Scope-locked input constraints for the supplied Station 3 recipe metadata. */
public final class RecipeDemandValidator {
    private RecipeDemandValidator() {}

    public static void requireSafe(int t100, int t200, int t300, int t400, int total, String dispenseOrder) {
        if (t100 < 0 || t200 < 0 || t300 < 0 || t400 < 0 || total < 0) {
            throw new IllegalArgumentException("recipe volumes must be nonnegative");
        }
        if (t300 != 0) throw new IllegalArgumentException("T300 was unavailable and must not be selected");
        if (total > 80) throw new IllegalArgumentException("recipe total exceeds 80 mL");
        if (t200 > 50) throw new IllegalArgumentException("T200 exceeds 50 mL");
        for (int value : new int[] {t100, t200, t400}) {
            if (value > 0 && value < 10) throw new IllegalArgumentException("selected components must request at least 10 mL");
        }
        if (t100 + t200 + t300 + t400 != total) throw new IllegalArgumentException("recipe component volumes do not equal total");
        List<String> expected = new ArrayList<>();
        if (t200 > 0) expected.add("T200");
        if (t400 > 0) expected.add("T400");
        if (t100 > 0) expected.add("T100");
        if (!String.join(">", expected).equals(dispenseOrder)) {
            throw new IllegalArgumentException("recipe dispense order does not match selected components");
        }
    }
}
