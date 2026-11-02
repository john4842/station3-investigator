package lab.mimi.station3.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;
import lab.mimi.station3.analysis.*;
import lab.mimi.station3.domain.*;
import java.util.*;

@Tag("organizer-data")
class ScopeEvidenceTest {
    private final AnalysisPolicy policy = new AnalysisPolicy("test", 0.8, 2, 7, 10);

    @Test void exposesPhysicalVialsAndAuthoritativeRecipeDemands() throws Exception {
        var response = new AnalysisController().analyzeCanonical();
        var evidence = (Map<?, ?>) response.get("investigation");
        var contexts = (List<Map<String, Object>>) evidence.get("recipeContexts");
        assertEquals(30, ((List<?>) evidence.get("vials")).size());
        assertEquals(10, contexts.size());
        var seven = contexts.get(2);
        var recipe = (Map<?, ?>) seven.get("recipe");
        assertEquals(Map.of("T100", 20, "T200", 0, "T400", 50), recipe.get("requestedMl"));
        assertEquals("T400>T100", recipe.get("dispenseOrder"));
        assertEquals(6, ((Map<?, ?>) seven.get("predecessorRecipe")).get("recipeId"));
        assertEquals("UNVERIFIED", seven.get("causeStatus"));
        assertEquals("NOT_VERIFIED", ((Map<?, ?>) evidence.get("capabilities")).get("hardwareDemonstration"));
    }

    @Test void missingRecipeSevenMeasurementCannotReuseRecipeSixForEight() throws Exception {
        var replay = CanonicalReplayService.run();
        var reduced = replay.observations().measurements().stream().filter(m -> m.vial() != 7).toList();
        var assessments = Station3Analyzer.analyze(replay.manifest(), reduced, policy);
        var eight = assessments.stream().filter(a -> a.recipeId() == 8).findFirst().orElseThrow();
        assertEquals(InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, eight.investigationHypothesis());
        var evidence = InvestigationEvidence.build(replay, assessments, 0.8);
        var context = ((List<Map<String,Object>>) evidence.get("recipeContexts")).get(3);
        assertEquals(7, ((Map<?,?>) context.get("predecessorRecipe")).get("recipeId"));
        assertTrue(context.get("hypothesis").toString().contains("unavailable"));
    }

    @Test void numericalAnchorsAndUnknownRecipeConformityRemainIntact() throws Exception {
        var replay = CanonicalReplayService.run();
        var assessments = Station3Analyzer.analyze(replay.manifest(), replay.observations().measurements(), policy);
        var seven = assessments.stream().filter(a -> a.recipeId() == 7).findFirst().orElseThrow();
        assertEquals(13.15213485992713, seven.firstToLaterMeanDeltaE00(), 1e-9);
        assertEquals(0.7977004284666604, seven.laterPairDeltaE00(), 1e-9);
        assertEquals(9, seven.withinBatchMetrics().thirdVial());
        assertEquals(RecipeConformity.UNVERIFIED_REFERENCE, seven.recipeConformity());
        assertEquals(EvidenceStatus.THRESHOLD_SENSITIVE,
                assessments.stream().filter(a -> a.recipeId() == 13).findFirst().orElseThrow().status());
    }
}


