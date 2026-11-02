package lab.mimi.station3.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class EvaluationTest {
    @Test void baselineBoundaryAndMissingEvidence() {
        assertEquals("NO_ALERT", EvaluationController.baseline(9.999));
        assertEquals("ALERT", EvaluationController.baseline(10));
        assertEquals("ABSTAIN", EvaluationController.baseline(Double.NaN));
        assertEquals("ABSTAIN", EvaluationController.baseline(Double.POSITIVE_INFINITY));
    }
    @Tag("organizer-data")
    @Test void comparisonIsCompleteAndDoesNotInventAccuracy() throws Exception {
        var result = new EvaluationController().evaluate();
        assertEquals(10, result.get("denominator"));
        assertEquals(false, result.get("independentDefectLabels"));
        assertEquals("NOT_ESTIMABLE", result.get("accuracy"));
        var rows = (List<Map<String,Object>>) result.get("rows");
        assertEquals(10, rows.stream().map(r -> r.get("recipeId")).distinct().count());
        assertEquals(rows.stream().filter(r -> !r.get("baseline").equals(r.get("guarded"))).count(),
                result.get("changedDecisions"));
        var seven = rows.stream().filter(r -> r.get("recipeId").equals(7)).findFirst().orElseThrow();
        assertEquals("ALERT", seven.get("baseline"));
        assertEquals("ALERT", seven.get("guarded"));
        var thirteen = rows.stream().filter(r -> r.get("recipeId").equals(13)).findFirst().orElseThrow();
        assertEquals("NO_ALERT", thirteen.get("baseline"));
        assertEquals("REVIEW", thirteen.get("guarded"));
        var twelve = rows.stream().filter(r -> r.get("recipeId").equals(12)).findFirst().orElseThrow();
        assertEquals("ABSTAIN", twelve.get("guarded"));
    }
}


