package lab.mimi.station3.analysis;

import lab.mimi.station3.domain.*;
import lab.mimi.station3.validation.RecipeDemandValidator;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Associates recipe metadata and observations with the verified source contract. */
public final class InvestigationEvidence {
    private InvestigationEvidence() {}

    public static Map<String, Object> build(CanonicalReplayService.Replay replay,
            List<RecipeAssessment> assessments, double minimumQuality) throws IOException {
        byte[] bytes;
        try (var in = new ClassPathResource("source/video5_vial_recipes.csv").getInputStream()) {
            bytes = in.readAllBytes();
        }
        try {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .equals(CanonicalReplayService.MANIFEST_HASH)) {
                throw new IOException("recipe context manifest hash mismatch");
            }
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        // Fixed, hash-verified source schema. Never use this parser for arbitrary uploads.
        Map<Integer, Map<String, Object>> recipes = new LinkedHashMap<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).lines().skip(1).toList()) {
            if (line.isBlank()) continue;
            String[] cells = line.split(",", -1);
            int id = Integer.parseInt(cells[1]);
            int t100 = volume(cells[3]);
            int t200 = volume(cells[4]);
            int t400 = volume(cells[5]);
            int total = volume(cells[6]);
            RecipeDemandValidator.requireSafe(t100, t200, 0, t400, total, cells[7]);
            recipes.putIfAbsent(id, Map.of("recipeId", id,
                    "requestedMl", Map.of("T100", t100, "T200", t200, "T400", t400),
                    "requestedTotalMl", total, "dispenseOrder", cells[7],
                    "source", "source/video5_vial_recipes.csv",
                    "manifestSha256", CanonicalReplayService.MANIFEST_HASH));
        }
        Map<Integer, RecipeAssessment> byRecipe = new HashMap<>();
        assessments.forEach(a -> byRecipe.put(a.recipeId(), a));
        List<Map<String, Object>> transitions = new ArrayList<>();
        Integer previous = null;
        for (var entry : recipes.entrySet()) {
            RecipeAssessment a = byRecipe.get(entry.getKey());
            if (a == null) throw new IOException("missing assessment for recipe " + entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recipeId", entry.getKey());
            row.put("recipe", entry.getValue());
            row.put("predecessorRecipe", previous == null ? null : recipes.get(previous));
            row.put("processContextConfidence", a.investigationHypothesis().name());
            row.put("causeStatus", "UNVERIFIED");
            row.put("fillConformity", "UNVERIFIED");
            row.put("hypothesis", a.investigationHypothesis() == InvestigationHypothesis.PREDECESSOR_DIRECTION_SUPPORTED
                    ? "Appearance is compatible with the immediate predecessor. Carryover requires process evidence."
                    : a.investigationHypothesis() == InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE
                    ? "The immediate predecessor appearance is unavailable, so the predecessor direction cannot be evaluated. No predecessor-specific inference is made."
                    : "Appearance does not support the predecessor-direction test. Cause remains unknown.");
            row.put("actionRecommendation", a.status() == EvidenceStatus.INSUFFICIENT_REFERENCE_EVIDENCE
                    ? "Review the rejected frames and obtain a stable reference before making a determination."
                    : a.batchConsistency() == BatchConsistency.CONSISTENT
                    ? "No within-batch difference flagged. Verify the intended recipe against an independent reference."
                    : "Review the indicated vials, tank colors, recipe queue, flush logs, dispense history and process alarms.");
            transitions.add(row);
            previous = entry.getKey();
        }
        Map<Integer, VialMeasurement> measurements = new HashMap<>();
        replay.observations().measurements().forEach(m -> measurements.put(m.vial(), m));
        List<Map<String, Object>> vials = new ArrayList<>();
        for (VialRecord v : replay.manifest()) {
            VialMeasurement m = measurements.get(v.vial());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vial", v.vial());
            row.put("recipeId", v.recipeId());
            row.put("startSeconds", v.startSeconds());
            row.put("endSeconds", v.endSeconds());
            row.put("appearance", m == null ? null : m.appearance());
            row.put("imageQuality", m == null ? null : m.imageQuality());
            row.put("observationQuality", m == null ? "MISSING" : m.imageQuality() >= minimumQuality ? "ACCEPTED" : "REJECTED");
            row.put("sourceReference", m == null ? null : m.sourceReference());
            vials.add(row);
        }
        return Map.of("scopeVersion", "station3-canonical-v1.0",
                "recipeContexts", transitions, "vials", vials,
                "capabilities", Map.of(
                    "analysis", "PRECOMPUTED_VIDEO_5_REPLAY",
                    "multiCameraAnalysis", "DESCRIPTIVE_EXACT_TIMESTAMP_CONTEXT",
                    "fillCalibration", "NOT_VERIFIED",
                    "independentRecipeReference", "NOT_VERIFIED",
                    "heldOutAccuracy", "NOT_VERIFIED",
                    "hardwareDemonstration", "NOT_VERIFIED",
                    "measuredBaselineComparison", "SAME_RUN_DESCRIPTIVE_ONLY"),
                "tankColorContext", "One exact-timestamp Run 5 tank frame provides visible color and gross-state context. This application does not measure tank color, per-vial dose, or chemical identity.",
                "volumeInterpretation", "Requested volume values are recipe metadata, not measured dispensed volumes.");
    }

    private static int volume(String value) { return value.isBlank() ? 0 : Integer.parseInt(value); }
}

