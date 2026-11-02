package lab.mimi.station3.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Descriptive process context. It is never promoted to a defect or cause verdict. */
public final class ProcessContextEvidence {
    public static final String RESOURCE = "source/run5_process_context.json";
    public static final String FILE_SHA256 = "ccfcf24bdbbba01c8e759bca6811b5c572666933c7cc3f343b421a54fc9df45d";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProcessContextEvidence() {}

    public static Map<String, Object> load() throws IOException {
        byte[] bytes;
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            bytes = input.readAllBytes();
        }
        if (!FILE_SHA256.equals(sha256(bytes))) throw new IOException("Run 5 process-context file hash mismatch");
        JsonNode root = JSON.readTree(bytes);
        validate(root);
        return JSON.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    static void validate(JsonNode root) {
        if (!"station3-run5-process-context/v1".equals(root.path("schema").asText())) {
            throw new IllegalArgumentException("unsupported process-context schema");
        }
        JsonNode crossCamera = root.path("crossCamera");
        int videoId = crossCamera.path("videoId").asInt(-1);
        String timestamp = crossCamera.path("timestamp").asText();
        JsonNode views = crossCamera.path("views");
        if (videoId != 5 || timestamp.isBlank() || !views.isArray() || views.size() != 3) {
            throw new IllegalArgumentException("process context requires three Run 5 camera views");
        }
        Set<String> cameras = new HashSet<>();
        for (JsonNode view : views) {
            if (view.path("videoId").asInt(-1) != videoId || !timestamp.equals(view.path("timestamp").asText())) {
                throw new IllegalArgumentException("cross-camera timestamp or run mismatch");
            }
            if (!cameras.add(view.path("camera").asText())) throw new IllegalArgumentException("duplicate camera context");
            if (!view.path("imageSha256").asText().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid process-context image hash");
            }
        }
        if (!cameras.equals(Set.of("vial_fill", "tank_level", "cap_seal"))) {
            throw new IllegalArgumentException("process context camera set mismatch");
        }
        JsonNode timing = root.path("timingModel");
        double simple = timing.path("totalVolumeOnlyR2").asDouble(Double.NaN);
        double contextual = timing.path("perTankVolumesAndActiveComponentCountR2").asDouble(Double.NaN);
        if (!Double.isFinite(simple) || !Double.isFinite(contextual) || simple < 0 || contextual > 1 || contextual <= simple) {
            throw new IllegalArgumentException("invalid Run 5 timing-model evidence");
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }
}
