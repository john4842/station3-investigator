package lab.mimi.station3.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Verifies the immutable ledger for the 845 indexed Run 5 fill-camera stills. */
public final class SourceContractEvidence {
    public static final String RESOURCE = "source/video5_source_contract.json";
    public static final String FILE_SHA256 = "136475c7be838ab9465a40b147aef3239bdebf76bef44aba1bf79a9669809e82";
    public static final String CANONICAL_SHA256 = "cf69344f5ba24bd10f05ab7a4ebcc19affe1affbf3a568d2305e5aedd4fe31fe";
    private static final ObjectMapper JSON = new ObjectMapper();

    private SourceContractEvidence() {}

    public static Map<String, Object> verify(String expectedVideoHash, String expectedManifestHash) throws IOException {
        byte[] bytes;
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            bytes = input.readAllBytes();
        }
        if (!FILE_SHA256.equals(sha256(bytes))) throw new IOException("source-frame contract file hash mismatch");
        JsonNode root = JSON.readTree(bytes);
        validate(root, expectedVideoHash, expectedManifestHash);
        JsonNode contract = root.path("contract");
        JsonNode labels = contract.path("frame_labels");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", contract.path("schema").asText());
        result.put("fileSha256", FILE_SHA256);
        result.put("canonicalSha256", CANONICAL_SHA256);
        result.put("frameCount", labels.path("rows").asInt());
        result.put("inFillFrames", labels.path("in_fill_rows").asInt());
        result.put("betweenFillFrames", labels.path("between_fill_rows").asInt());
        result.put("semanticOrder", contract.path("join_rules").path("semantic_order").asText());
        result.put("claimBoundary", contract.path("purpose").asText());
        return Map.copyOf(result);
    }

    static void validate(JsonNode root, String expectedVideoHash, String expectedManifestHash) {
        if (!CANONICAL_SHA256.equals(root.path("canonical_contract_sha256").asText())) {
            throw new IllegalArgumentException("source-frame canonical contract identity mismatch");
        }
        JsonNode contract = root.path("contract");
        if (!"station3-video5-source-contract/v1".equals(contract.path("schema").asText())) {
            throw new IllegalArgumentException("unsupported source-frame contract schema");
        }
        if (contract.path("video_id").asInt(-1) != 5 || !"vial_fill".equals(contract.path("camera").asText())) {
            throw new IllegalArgumentException("source-frame contract identifies the wrong run or camera");
        }
        if (!expectedVideoHash.equals(contract.path("video").path("sha256").asText())) {
            throw new IllegalArgumentException("source-frame contract video identity mismatch");
        }
        if (!expectedManifestHash.equals(contract.path("vial_manifest").path("sha256").asText())) {
            throw new IllegalArgumentException("source-frame contract manifest identity mismatch");
        }
        JsonNode labels = contract.path("frame_labels");
        JsonNode frames = contract.path("frames");
        int expectedRows = labels.path("rows").asInt(-1);
        if (expectedRows != 845 || !frames.isArray() || frames.size() != expectedRows) {
            throw new IllegalArgumentException("source-frame contract must contain 845 indexed frames");
        }
        if (labels.path("in_fill_rows").asInt(-1) + labels.path("between_fill_rows").asInt(-1) != expectedRows) {
            throw new IllegalArgumentException("source-frame contract frame denominators do not balance");
        }
        Set<String> names = new HashSet<>();
        Set<Double> times = new HashSet<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (JsonNode frame : frames) {
            String name = frame.path("filename").asText();
            String hash = frame.path("sha256").asText();
            double time = frame.path("video_s").asDouble(Double.NaN);
            if (name.isBlank() || !names.add(name)) throw new IllegalArgumentException("source-frame contract repeats a filename");
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("source-frame contract contains an invalid frame hash");
            if (!Double.isFinite(time) || !times.add(time) || time <= previous) {
                throw new IllegalArgumentException("source-frame contract video_s must be unique and strictly increasing");
            }
            previous = time;
        }
        JsonNode rules = contract.path("join_rules");
        if (!"ascending validated video_s".equals(rules.path("semantic_order").asText())
                || rules.path("row_order_authoritative").asBoolean(true)) {
            throw new IllegalArgumentException("source-frame contract does not enforce semantic time order");
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
