package lab.mimi.station3.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SuppliedRunsWebTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void suppliedRunManifestExposesAllFiveSynchronizedRuns() throws Exception {
        mvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runCount").value(5))
                .andExpect(jsonPath("$.cameraStreamCount").value(15))
                .andExpect(jsonPath("$.totalVials").value(64))
                .andExpect(jsonPath("$.labeledVials").value(30))
                .andExpect(jsonPath("$.unlabeledVials").value(34))
                .andExpect(jsonPath("$.runs.length()").value(5))
                .andExpect(jsonPath("$.runs[0].vialCount").value(19))
                .andExpect(jsonPath("$.runs[4].recipeLabeled").value(true))
                .andExpect(jsonPath("$.runs[4].analysisStatus").value("VERIFIED_RECIPE_ANALYSIS"));
    }

    @Test
    void dashboardNamesCompleteCoverageAndItsLabelBoundary() throws Exception {
        String page = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String script = mvc.perform(get("/runs.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Recipe changeover investigation"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Only Run 5 has recipe labels"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("id=\"run-rows\""));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("fetch('/api/runs')"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("Source available for labeling"));
    }

    @Test
    void dashboardPresentsARecordableOperatorDecisionBrief() throws Exception {
        String page = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String script = mvc.perform(get("/executive.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Find first-vial changes after a recipe switch"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Review the Recipe 6 to 7 switch"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Do not reject product or claim contamination from this result alone"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("VERIFIED_CANONICAL_SOURCE"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("recipeId === 7"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("recipe7.withinBatchMetrics?.vial1ToVial3DeltaE00"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Vial 7 vs. vial 9"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Vial 8 vs. vial 9"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("How it works"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("It does not process a live camera feed"));
        org.junit.jupiter.api.Assertions.assertFalse(page.contains("The demonstration, explained"));
        org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("id=\"decision-title\"") < page.indexOf("id=\"dataset-title\""));
    }
}
