package lab.mimi.station3.domain;

public enum EvidenceStatus {
    WITHIN_RUN_STABLE("WITHIN-RUN STABLE"),
    RETROSPECTIVE_FIRST_VIAL_DEVIATION("RETROSPECTIVE FIRST-VIAL DEVIATION"),
    THRESHOLD_SENSITIVE("THRESHOLD SENSITIVE"),
    INSUFFICIENT_REFERENCE_EVIDENCE("INSUFFICIENT REFERENCE EVIDENCE"),
    INSUFFICIENT_PROVENANCE("INSUFFICIENT PROVENANCE");

    private final String label;

    EvidenceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
