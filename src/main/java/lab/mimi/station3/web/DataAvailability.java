package lab.mimi.station3.web;

import org.springframework.core.io.ClassPathResource;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Resource presence is a capability check, never proof of verified evidence. */
public final class DataAvailability {
    private static final List<String> REPLAY = List.of(
            "source/video5_vial_recipes.csv", "canonical/observations.csv",
            "source/video5_source_contract.json", "source/run5_process_context.json");
    private DataAvailability() {}

    public static Map<String, Object> inspect() {
        return inspect(path -> new ClassPathResource(path).exists());
    }

    static Map<String, Object> inspect(Predicate<String> exists) {
        var missing = REPLAY.stream().filter(path -> !exists.test(path)).toList();
        return Map.of("canonicalAvailable", missing.isEmpty(),
                "uploadAvailable", exists.test(REPLAY.getFirst()),
                "cameraEvidenceAvailable", exists.test("static/recipe7-evidence.svg")
                        && exists.test("static/run5-tank-context.svg"),
                "missingResources", missing,
                "canonicalStatus", missing.isEmpty() ? "AVAILABLE_NOT_VERIFIED" : "DATA_UNAVAILABLE");
    }

    public static void requireReplay() {
        if (!Boolean.TRUE.equals(inspect().get("canonicalAvailable"))) {
            throw new Unavailable("Run 5 replay is unavailable because restricted data is not installed. Use the synthetic example or see SOURCE_ASSETS.md.");
        }
    }

    public static void requireUpload() {
        if (!Boolean.TRUE.equals(inspect().get("uploadAvailable"))) {
            throw new Unavailable("Upload analysis requires the restricted Run 5 manifest. A ZIP alone does not supply it. See SOURCE_ASSETS.md.");
        }
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }
}
