package lab.mimi.station3.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("organizer-data")
class EvidenceContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void completeSourceContractVerifiesAllIndexedFillFrames() throws Exception {
        var result = SourceContractEvidence.verify(CanonicalReplayService.VIDEO_HASH, CanonicalReplayService.MANIFEST_HASH);
        assertEquals(845, result.get("frameCount"));
        assertEquals(509, result.get("inFillFrames"));
        assertEquals(336, result.get("betweenFillFrames"));
        assertEquals("ascending validated video_s", result.get("semanticOrder"));
    }

    @Test void sourceContractRejectsDuplicateSemanticTime() throws Exception {
        var root = JSON.readTree(new ClassPathResource(SourceContractEvidence.RESOURCE).getInputStream());
        var frames = root.path("contract").withArray("frames");
        ((com.fasterxml.jackson.databind.node.ObjectNode) frames.get(1)).put("video_s", frames.get(0).path("video_s").asDouble());
        assertThrows(IllegalArgumentException.class, () -> SourceContractEvidence.validate(
                root, CanonicalReplayService.VIDEO_HASH, CanonicalReplayService.MANIFEST_HASH));
    }

    @Test void synchronizedContextRejectsCameraTimestampMismatch() throws Exception {
        var root = JSON.readTree(new ClassPathResource(ProcessContextEvidence.RESOURCE).getInputStream());
        var views = root.path("crossCamera").withArray("views");
        ((com.fasterxml.jackson.databind.node.ObjectNode) views.get(2)).put("timestamp", "mismatched");
        assertThrows(IllegalArgumentException.class, () -> ProcessContextEvidence.validate(root));
    }
}

