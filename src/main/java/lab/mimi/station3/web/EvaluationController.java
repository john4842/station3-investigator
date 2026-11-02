package lab.mimi.station3.web;

import lab.mimi.station3.analysis.*;
import lab.mimi.station3.domain.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
public class EvaluationController {
    @GetMapping("/api/evaluation")
    public Map<String, Object> evaluate() throws IOException {
        DataAvailability.requireReplay();
        var replay = CanonicalReplayService.run();
        var policy = new AnalysisPolicy("station3-prototype-threshold-band-v1", 0.8, 2, 7, 10);
        var assessments = Station3Analyzer.analyze(replay.manifest(), replay.observations().measurements(), policy);
        var rows = assessments.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recipeId", a.recipeId());
            row.put("firstToLaterMeanDeltaE00", finite(a.firstToLaterMeanDeltaE00()));
            row.put("laterPairDeltaE00", finite(a.laterPairDeltaE00()));
            row.put("baseline", baseline(a.firstToLaterMeanDeltaE00()));
            row.put("guarded", decision(a.status()));
            row.put("reason", a.explanation());
            return row;
        }).toList();
        long changed = rows.stream().filter(r -> !r.get("baseline").equals(r.get("guarded"))).count();
        return Map.ofEntries(
            Map.entry("protocol", "run5-threshold-ablation-v1"),
            Map.entry("evidence", "DESCRIPTIVE_SAME_RUN_COMPARISON_NOT_ACCURACY"),
            Map.entry("baselineRule", "Alert when first-to-later-mean delta E00 >= 10; otherwise no alert. Missing values abstain. No later-reference stability gate or 7-10 review band."),
            Map.entry("guardedRule", "Same metric and upper cutoff; add later-pair stability <= 2, image quality >= 0.8 and review band [7,10)."),
            Map.entry("denominator", rows.size()),
            Map.entry("changedDecisions", changed),
            Map.entry("rows", rows),
            Map.entry("sourceSha256", CanonicalReplayService.VIDEO_HASH),
            Map.entry("observationsSha256", CanonicalReplayService.BUNDLED_OBSERVATION_HASH),
            Map.entry("independentDefectLabels", false),
            Map.entry("accuracy", "NOT_ESTIMABLE"),
            Map.entry("limitations", "Same Run 5 observations, not a held-out trial. This ablation measures decision changes, not correct decisions, savings, or performance against Teledyne. Thresholds are provisional and were not independently calibrated."));
    }

    static String baseline(double distance) {
        return !Double.isFinite(distance) ? "ABSTAIN" : distance >= 10 ? "ALERT" : "NO_ALERT";
    }
    static String decision(EvidenceStatus status) {
        return switch (status) {
            case RETROSPECTIVE_FIRST_VIAL_DEVIATION -> "ALERT";
            case WITHIN_RUN_STABLE -> "NO_ALERT";
            case THRESHOLD_SENSITIVE -> "REVIEW";
            case INSUFFICIENT_REFERENCE_EVIDENCE, INSUFFICIENT_PROVENANCE -> "ABSTAIN";
        };
    }
    private static Double finite(double value) { return Double.isFinite(value) ? value : null; }
}
