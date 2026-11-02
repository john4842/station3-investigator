package lab.mimi.station3.web;

import lab.mimi.station3.analysis.CanonicalReplayService;
import lab.mimi.station3.analysis.ProcessContextEvidence;
import lab.mimi.station3.analysis.SourceFrameArchiveExtractor;
import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.EvidenceStatus;
import lab.mimi.station3.domain.AnalysisPolicy;
import lab.mimi.station3.domain.BatchConsistency;
import lab.mimi.station3.domain.InvestigationHypothesis;
import lab.mimi.station3.domain.RecipeAssessment;
import lab.mimi.station3.domain.RecipeConformity;
import lab.mimi.station3.ingest.CsvIngestor;
import lab.mimi.station3.validation.ManifestValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);
    private static final long MAX_UPLOAD_BYTES = 128L * 1024 * 1024;
    private static final int MAX_LABEL_CHARACTERS = 100;
    private static final AnalysisPolicy POLICY = new AnalysisPolicy(
            "station3-prototype-threshold-band-v1", 0.80, 2.0, 7.0, 10.0);

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "station3-investigator", "capabilities", DataAvailability.inspect());
    }

    @GetMapping("/runs")
    public Map<String, Object> suppliedRuns() {
        var runs = java.util.List.of(
                suppliedRun(1, 1122, 19, false),
                suppliedRun(2, 160, 3, false),
                suppliedRun(3, 193, 6, false),
                suppliedRun(4, 316, 6, false),
                suppliedRun(5, 1698, 30, true));
        return Map.of(
                "runCount", 5,
                "cameraStreamCount", 15,
                "totalVials", 64,
                "labeledVials", 30,
                "unlabeledVials", 34,
                "runs", runs);
    }

    private static Map<String, Object> suppliedRun(int videoId, int durationSeconds, int vialCount,
            boolean recipeLabeled) {
        return Map.of(
                "videoId", videoId,
                "durationSeconds", durationSeconds,
                "vialCount", vialCount,
                "recipeLabeled", recipeLabeled,
                "cameraStreams", java.util.List.of("cap_seal", "tank_level", "vial_fill"),
                "analysisStatus", recipeLabeled
                        ? DataAvailability.inspect().get("canonicalStatus")
                        : "INVENTORY_ONLY_NO_RECIPE_LABELS");
    }

    @PostMapping("/analyze/canonical")
    public Map<String, Object> analyzeCanonical() throws IOException {
        DataAvailability.requireReplay();
        var replay = CanonicalReplayService.run();
        var records = replay.manifest();
        ManifestValidator.requireWellFormed(records);
        var assessments = Station3Analyzer.analyze(records, replay.observations().measurements(), POLICY);

        Map<String, Object> response = baseResponse("Canonical Video 5 replay", "CANONICAL_VIDEO_5_REPLAY",
                records.size(), replay.observations().measurements().size(), assessments.size(), assessments);
        response.put("measurementOrigin", "PRECOMPUTED_CANONICAL_EXTRACTION_REPLAY");
        response.put("observationWindow", "PREREGISTERED_PRE_END_WINDOW");
        response.put("referenceMode", "SERVER_OWNED_MANIFEST");
        response.put("provenanceStatus", "VERIFIED_CANONICAL_SOURCE");
        response.put("extractionReceipt", replay.receipt());
        response.put("processContext", ProcessContextEvidence.load());
        response.put("investigation", lab.mimi.station3.analysis.InvestigationEvidence.build(replay, assessments, POLICY.minimumImageQuality()));
        return response;
    }

    @PostMapping("/analyze/synthetic")
    public Map<String, Object> analyzeSynthetic() throws IOException {
        var replay = lab.mimi.station3.analysis.SyntheticReplayService.run();
        var assessments = Station3Analyzer.analyze(replay.manifest(), replay.measurements(), POLICY);
        var response = baseResponse("Synthetic example, not plant evidence", "SYNTHETIC_FIXTURE",
                replay.manifest().size(), replay.measurements().size(), assessments.size(), assessments);
        response.put("synthetic", true);
        response.put("provenanceStatus", "SYNTHETIC_NOT_PLANT_EVIDENCE");
        response.put("measurementOrigin", "TEAM_AUTHORED_SYNTHETIC_FIXTURE");
        response.put("referenceMode", "ORDERED_VIAL_COMPARISON");
        return response;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> analyzeUpload(
            @RequestParam MultipartFile sourceBundle,
            @RequestParam(defaultValue = "Uploaded source run") String label) throws IOException {
        requireZip(sourceBundle);
        String datasetLabel = normalizeLabel(label);
        DataAvailability.requireUpload();
        Path workspace = createPrivateWorkspace();
        try {
            Path manifestPath = workspace.resolve("video5_vial_recipes.csv");
            Path sourcePath = workspace.resolve("source-run.zip");
            try (var input = new ClassPathResource("source/video5_vial_recipes.csv").getInputStream()) {
                Files.copy(input, manifestPath);
            }
            sourceBundle.transferTo(sourcePath);
            var records = CsvIngestor.readManifest(manifestPath);
            ManifestValidator.requireWellFormed(records);
            var extraction = SourceFrameArchiveExtractor.extract(sourcePath, records);
            var exploratory = Station3Analyzer.analyze(records, extraction.measurements(), POLICY);
            var assessments = exploratory.stream().map(AnalysisController::provenanceAbstention).toList();

            Map<String, Object> response = baseResponse(datasetLabel, "UNVERIFIED_UPLOAD", records.size(),
                    extraction.measurements().size(), assessments.size(), assessments);
            response.put("measurementOrigin", "SERVER_DERIVED_SOURCE_FRAMES");
            response.put("observationWindow", "PROVISIONAL_PRE_END_FRAME_WINDOW");
            response.put("referenceMode", "PROVENANCE_ABSTENTION");
            response.put("provenanceStatus", "INSUFFICIENT_PROVENANCE");
            response.put("archiveImageCount", extraction.archiveImageCount());
            response.put("extractor", Map.of("version", extraction.extractorVersion(),
                    "configHash", extraction.configHash(), "sourceHash", extraction.sourceHash()));
            return response;
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static Map<String, Object> baseResponse(String label, String evidenceType, int manifestRows,
            int measurementRows, int assessmentCount, java.util.List<RecipeAssessment> assessments) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetLabel", label);
        response.put("evidenceType", evidenceType);
        response.put("generatedAt", Instant.now());
        response.put("manifestRows", manifestRows);
        response.put("measurementRows", measurementRows);
        response.put("assessmentCount", assessmentCount);
        response.put("analysisPolicyVersion", POLICY.version());
        response.put("thresholds", Map.of("minimumImageQuality", POLICY.minimumImageQuality(),
                "maximumLaterPairDeltaE00", POLICY.maximumLaterPairDeltaE00(),
                "lowerFirstVialDeltaE00", POLICY.lowerFirstVialDeltaE00(),
                "upperFirstVialDeltaE00", POLICY.upperFirstVialDeltaE00()));
        response.put("thresholdInterpretation",
                "values from 7.0 inclusive to 10.0 exclusive are explicitly threshold-sensitive; neither cutoff is production-calibrated");
        response.put("assessments", assessments.stream().map(AnalysisController::judgeFacingAssessment).toList());
        return response;
    }

    private static Map<String, Object> judgeFacingAssessment(RecipeAssessment assessment) {
        var metrics = assessment.withinBatchMetrics();
        Map<String, Object> withinBatch = new LinkedHashMap<>();
        withinBatch.put("firstVial", metrics.firstVial());
        withinBatch.put("secondVial", metrics.secondVial());
        withinBatch.put("thirdVial", metrics.thirdVial());
        withinBatch.put("vial1ToVial2DeltaE00", finiteOrNull(metrics.vial1ToVial2DeltaE00()));
        withinBatch.put("vial1ToVial3DeltaE00", finiteOrNull(metrics.vial1ToVial3DeltaE00()));
        withinBatch.put("vial2ToVial3DeltaE00", finiteOrNull(metrics.vial2ToVial3DeltaE00()));
        withinBatch.put("referencePolicyVersion", metrics.referencePolicyVersion());
        withinBatch.put("referenceSource", metrics.referenceSource());
        withinBatch.put("comparisonDescription", metrics.comparisonDescription());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipeId", assessment.recipeId());
        result.put("status", assessment.status());
        result.put("investigationHypothesis", assessment.investigationHypothesis());
        result.put("expectedVials", assessment.expectedVials());
        result.put("acceptedMeasurements", assessment.acceptedMeasurements());
        result.put("firstToLaterMeanDeltaE00", finiteOrNull(assessment.firstToLaterMeanDeltaE00()));
        result.put("laterPairDeltaE00", finiteOrNull(assessment.laterPairDeltaE00()));
        result.put("predecessorDeltaE00", finiteOrNull(assessment.predecessorDeltaE00()));
        result.put("withinBatchMetrics", withinBatch);
        result.put("batchConsistency", assessment.batchConsistency());
        result.put("recipeConformity", assessment.recipeConformity());
        result.put("recipeConformityReference", assessment.recipeConformityReference());
        result.put("explanation", assessment.explanation());
        result.put("sourceReferences", assessment.sourceReferences());
        result.put("configHashes", assessment.configHashes());
        result.put("extractorVersions", assessment.extractorVersions());
        result.put("sourceHashes", assessment.sourceHashes());
        result.put("rejectionReasons", assessment.rejectionReasons());
        return result;
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static RecipeAssessment provenanceAbstention(RecipeAssessment assessment) {
        var reasons = new java.util.ArrayList<>(assessment.rejectionReasons());
        reasons.add("uploaded source identity has not passed the canonical Video 5 contract");
        return new RecipeAssessment(assessment.recipeId(), EvidenceStatus.INSUFFICIENT_PROVENANCE,
                InvestigationHypothesis.PREDECESSOR_DIRECTION_UNAVAILABLE, assessment.expectedVials(),
                assessment.acceptedMeasurements(), assessment.firstToLaterMeanDeltaE00(),
                assessment.laterPairDeltaE00(), Double.NaN,
                assessment.withinBatchMetrics(), BatchConsistency.INSUFFICIENT_EVIDENCE,
                RecipeConformity.INSUFFICIENT_EVIDENCE, "NONE",
                "measurements were derived, but recipe classification is withheld because source provenance is unverified",
                assessment.sourceReferences(), assessment.configHashes(), assessment.extractorVersions(),
                assessment.sourceHashes(), reasons);
    }

    private static void requireZip(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("source run ZIP is required");
        if (file.getSize() > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("source run ZIP exceeds 128 MiB");
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("source run must be a .zip file");
        }
    }

    private static String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.strip();
        if (normalized.isEmpty()) return "Uploaded source run";
        if (normalized.codePointCount(0, normalized.length()) > MAX_LABEL_CHARACTERS) {
            throw new IllegalArgumentException("run name must be at most 100 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("run name contains a control character");
        }
        return normalized;
    }

    private static Path createPrivateWorkspace() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempDirectory("station3-source-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        return Files.createTempDirectory("station3-source-");
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException failure) {
                    LOGGER.warn("could not delete temporary upload path {}", path, failure);
                }
            });
        } catch (IOException failure) {
            LOGGER.warn("could not traverse temporary upload workspace {}", directory, failure);
        }
    }
}
