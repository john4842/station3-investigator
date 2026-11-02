package lab.mimi.station3.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Station3WebApplicationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void browserDashboardIsServedFromTheApplication() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void healthEndpointIsReady() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Tag("organizer-data")
    @Test
    void servedDashboardAndHtmlReportExposePredecessorDistance() throws Exception {
        String page = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String script = mvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Predecessor distance"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("formatNumber(assessment.predecessorDeltaE00)"));
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("formatNumber(item.predecessorDeltaE00)"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Run 5 analysis"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Camera evidence"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("/recipe7-evidence.svg"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("Synchronized process context"));
        org.junit.jupiter.api.Assertions.assertTrue(page.contains("/run5-tank-context.svg"));
        mvc.perform(get("/recipe7-evidence.svg")).andExpect(status().isOk());
        mvc.perform(get("/run5-tank-context.svg")).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(script.contains("canonicalSubmit.click();"));
    }

    @Tag("organizer-data")
    @Test
    void canonicalResponseExposesThresholdPolicyAndSensitiveRecipe() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/analyze/canonical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisPolicyVersion").value("station3-prototype-threshold-band-v1"))
                .andExpect(jsonPath("$.thresholds.lowerFirstVialDeltaE00").value(7.0))
                .andExpect(jsonPath("$.thresholds.upperFirstVialDeltaE00").value(10.0))
                .andExpect(jsonPath("$.assessments[?(@.recipeId == 13)].status").value("THRESHOLD_SENSITIVE"))
                .andExpect(jsonPath("$.assessments[?(@.recipeId == 13)].batchConsistency").value("POLICY_SENSITIVE"))
                .andExpect(jsonPath("$.assessments[?(@.recipeId == 7)].withinBatchMetrics.vial1ToVial2DeltaE00").exists())
                .andExpect(jsonPath("$.assessments[?(@.recipeId == 7)].withinBatchMetrics.vial1ToVial3DeltaE00").exists())
                .andExpect(jsonPath("$.assessments[?(@.recipeId == 7)].withinBatchMetrics.vial2ToVial3DeltaE00").exists())
                .andExpect(jsonPath("$.extractionReceipt.sourceFrameContract.frameCount").value(845))
                .andExpect(jsonPath("$.extractionReceipt.sourceFrameContract.inFillFrames").value(509))
                .andExpect(jsonPath("$.processContext.crossCamera.joinStatus").value("EXACT_INDEX_TIMESTAMP"))
                .andExpect(jsonPath("$.processContext.crossCamera.views.length()").value(3))
                .andExpect(jsonPath("$.processContext.timingModel.perTankVolumesAndActiveComponentCountR2").value(0.925))
                .andExpect(jsonPath("$.assessments[?(@.recipeConformity != 'UNVERIFIED_REFERENCE')]").isEmpty());
    }

    @Tag("organizer-data")
    @Test
    void canonicalResponseUsesJsonNullForUnavailableNumbers() throws Exception {
        String json = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/analyze/canonical"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(json.contains("NaN"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"predecessorDeltaE00\":null"));
    }

    @Tag("organizer-data")
    @Test
    void unverifiedSourceBundleDerivesColorsButAbstainsFromRecipeDispositions() throws Exception {
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "video5-source.zip", "application/zip", sourceBundle());

        mvc.perform(multipart("/api/analyze")
                        .file(source)
                        .param("label", "source test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetLabel").value("source test"))
                .andExpect(jsonPath("$.evidenceType").value("UNVERIFIED_UPLOAD"))
                .andExpect(jsonPath("$.measurementOrigin").value("SERVER_DERIVED_SOURCE_FRAMES"))
                .andExpect(jsonPath("$.referenceMode").value("PROVENANCE_ABSTENTION"))
                .andExpect(jsonPath("$.provenanceStatus").value("INSUFFICIENT_PROVENANCE"))
                .andExpect(jsonPath("$.assessments.length()").value(10))
                .andExpect(jsonPath("$.assessments[?(@.status != 'INSUFFICIENT_PROVENANCE')]").isEmpty())
                .andExpect(jsonPath("$.manifestRows").value(30))
                .andExpect(jsonPath("$.measurementRows").value(30))
                .andExpect(jsonPath("$.archiveImageCount").value(120))
                .andExpect(jsonPath("$.assessmentCount").value(10))
                .andExpect(jsonPath("$.assessments[?(@.batchConsistency != 'INSUFFICIENT_EVIDENCE')]").isEmpty())
                .andExpect(jsonPath("$.assessments[?(@.recipeConformity != 'INSUFFICIENT_EVIDENCE')]").isEmpty())
                .andExpect(jsonPath("$.extractor.version").value("station3-source-roi-v4"));
    }

    @Tag("organizer-data")
    @Test
    void uploadFrameSelectionUsesVideoTimeInsteadOfCsvRowOrder() throws Exception {
        String orderedJson = mvc.perform(multipart("/api/analyze")
                        .file(new MockMultipartFile("sourceBundle", "ordered.zip", "application/zip",
                                sourceBundle(false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reversedJson = mvc.perform(multipart("/api/analyze")
                        .file(new MockMultipartFile("sourceBundle", "reversed.zip", "application/zip",
                                sourceBundle(true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode ordered = objectMapper.readTree(orderedJson).at("/assessments/0/sourceReferences");
        JsonNode reversed = objectMapper.readTree(reversedJson).at("/assessments/0/sourceReferences");
        org.junit.jupiter.api.Assertions.assertEquals(ordered, reversed);
    }

    @Test
    void emptySourceBundleIsRejectedAsBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "sourceBundle", "video5-source.zip", "application/zip", new byte[0]);

        mvc.perform(multipart("/api/analyze").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_input"))
                .andExpect(jsonPath("$.message").value("source run ZIP is required"));
    }

    @Tag("organizer-data")
    @Test
    void duplicateFrameIdentityInLabelLedgerIsRejected() throws Exception {
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "duplicate-label.zip", "application/zip", duplicateLabelBundle());

        mvc.perform(multipart("/api/analyze").file(source))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_input"))
                .andExpect(jsonPath("$.message").value("frame-label csv repeats image identity repeated.png"));
    }

    @Tag("organizer-data")
    @Test
    void unknownVialInLabelLedgerIsRejected() throws Exception {
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "unknown-vial.zip", "application/zip", unknownVialBundle());

        mvc.perform(multipart("/api/analyze").file(source))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("frame-label csv references unknown vial 31"));
    }

    @Tag("organizer-data")
    @Test
    void oversizedImageDimensionsAreRejectedBeforePixelDecode() throws Exception {
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "wide-image.zip", "application/zip", wideImageBundle());

        mvc.perform(multipart("/api/analyze").file(source))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("image dimensions exceed the allowed pixel budget images/wide.png"));
    }

    @Test
    void apiEnforcesRunNameLimitInsteadOfTrustingBrowserMarkup() throws Exception {
        // Invalid input must be rejected before archive parsing or dataset access.
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "source.zip", "application/zip", new byte[] {1});

        mvc.perform(multipart("/api/analyze").file(source).param("label", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("run name must be at most 100 characters"));
    }

    @Test
    void apiRejectsControlCharactersBeforeReadingSourceData() throws Exception {
        MockMultipartFile source = new MockMultipartFile(
                "sourceBundle", "source.zip", "application/zip", new byte[] {1});

        mvc.perform(multipart("/api/analyze").file(source).param("label", "run\tname"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("run name contains a control character"));
    }

    private static byte[] sourceBundle() throws Exception {
        return sourceBundle(false);
    }

    private static byte[] sourceBundle(boolean reverseLabelRows) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<String> labelRows = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int vial = 1; vial <= 30; vial++) {
                int recipeIndex = (vial - 1) / 3;
                Color color = new Color(
                        40 + recipeIndex * 12,
                        75 + recipeIndex * 7,
                        100 + recipeIndex * 5);
                for (int frame = 1; frame <= 4; frame++) {
                    String name = "vial-" + vial + "-frame-" + frame + ".png";
                    labelRows.add(name + ",1," + vial + "," + (vial * 10 + frame));
                    zip.putNextEntry(new ZipEntry("images/" + name));
                    zip.write(image(color));
                    zip.closeEntry();
                }
            }
            if (reverseLabelRows) Collections.reverse(labelRows);
            String labels = "filename,in_fill,vial,video_s\n" + String.join("\n", labelRows) + "\n";
            zip.putNextEntry(new ZipEntry("video5_frame_labels.csv"));
            zip.write(labels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] duplicateLabelBundle() throws Exception {
        return bundle("filename,in_fill,vial,video_s\nrepeated.png,1,1,1\nrepeated.png,1,1,2\n",
                "repeated.png", image(Color.BLUE));
    }

    private static byte[] unknownVialBundle() throws Exception {
        return bundle("filename,in_fill,vial,video_s\nunknown.png,1,31,1\n", "unknown.png", image(Color.BLUE));
    }

    private static byte[] wideImageBundle() throws Exception {
        BufferedImage image = new BufferedImage(9000, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bundle("filename,in_fill,vial,video_s\nwide.png,1,1,1\nwide2.png,1,1,2\nwide3.png,1,1,3\n",
                new String[] {"wide.png", "wide2.png", "wide3.png"}, bytes.toByteArray());
    }

    private static byte[] bundle(String labels, String imageName, byte[] image) throws Exception {
        return bundle(labels, new String[] {imageName}, image);
    }

    private static byte[] bundle(String labels, String[] imageNames, byte[] image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String imageName : imageNames) {
                zip.putNextEntry(new ZipEntry("images/" + imageName));
                zip.write(image);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("video5_frame_labels.csv"));
            zip.write(labels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
