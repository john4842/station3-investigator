package lab.mimi.station3.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicReleaseTest {
    @Autowired private MockMvc mvc;

    @Test void presenceChecksNeverClaimVerification() {
        var missing = DataAvailability.inspect(path -> false);
        assertEquals(false, missing.get("canonicalAvailable"));
        assertEquals(false, missing.get("uploadAvailable"));
        assertEquals("DATA_UNAVAILABLE", missing.get("canonicalStatus"));
        var present = DataAvailability.inspect(path -> true);
        assertEquals(true, present.get("canonicalAvailable"));
        assertEquals("AVAILABLE_NOT_VERIFIED", present.get("canonicalStatus"));
    }

    @Test void aPartialBundleCannotEnableReplay() {
        var partial = DataAvailability.inspect(path -> path.equals("source/video5_vial_recipes.csv"));
        assertEquals(false, partial.get("canonicalAvailable"));
        assertEquals(true, partial.get("uploadAvailable"));
        assertEquals(false, partial.get("cameraEvidenceAvailable"));
    }

    @Test void syntheticEndpointWorksWithoutOrganizerDataAndKeepsItsIdentity() throws Exception {
        mvc.perform(post("/api/analyze/synthetic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.provenanceStatus").value("SYNTHETIC_NOT_PLANT_EVIDENCE"))
                .andExpect(jsonPath("$.measurementRows").value(6))
                .andExpect(jsonPath("$.assessmentCount").value(2))
                .andExpect(jsonPath("$.assessments[0].status").value("WITHIN_RUN_STABLE"))
                .andExpect(jsonPath("$.assessments[1].status").value("RETROSPECTIVE_FIRST_VIAL_DEVIATION"))
                .andExpect(jsonPath("$.assessments[0].withinBatchMetrics.referenceSource").value("ORDERED_VIAL_COMPARISON"))
                .andExpect(jsonPath("$.extractionReceipt").doesNotExist());
    }

    @Test void missingDataIsUnavailableNotInvalidUserInput() throws Exception {
        var missing = DataAvailability.inspect(path -> false);
        try (var availability = org.mockito.Mockito.mockStatic(
                DataAvailability.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            availability.when(DataAvailability::inspect).thenReturn(missing);
            mvc.perform(post("/api/analyze/canonical"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("data_unavailable"));
            mvc.perform(get("/api/evaluation"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("data_unavailable"));
        }
    }

    @Test void missingUploadManifestHasAnExplicitResponse() throws Exception {
        var missing = DataAvailability.inspect(path -> false);
        try (var availability = org.mockito.Mockito.mockStatic(
                DataAvailability.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            availability.when(DataAvailability::inspect).thenReturn(missing);
            mvc.perform(multipart("/api/analyze").file(
                    new MockMultipartFile("sourceBundle", "frames.zip", "application/zip", new byte[] {1})))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("data_unavailable"));
        }
    }

    @Test void publicPageDoesNotLoadRestrictedImagesBeforeVerification() throws Exception {
        var page = mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("id=\"canonical-brief\" hidden"));
        assertTrue(page.contains("id=\"canonical-evidence\" hidden"));
        assertFalse(page.contains(" src=\"/recipe7-evidence.svg\""));
        assertFalse(page.contains(" src=\"/run5-tank-context.svg\""));
        assertTrue(page.contains("Run synthetic example"));
    }
}
