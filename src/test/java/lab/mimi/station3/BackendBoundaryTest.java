package lab.mimi.station3;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.AnalysisPolicy;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.validation.ManifestValidator;

class BackendBoundaryTest {
    @Test
    void manifestWellFormedGateRejectsNullContainerAndElement() {
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.requireWellFormed(null));
        List<VialRecord> records = new ArrayList<>();
        records.add(null);
        assertThrows(IllegalArgumentException.class, () -> ManifestValidator.requireWellFormed(records));
    }

    @Test
    void analyzerRejectsNullMeasurementElementAtItsBoundary() {
        List<VialRecord> records = List.of(
                vial(1, 0), vial(2, 20), vial(3, 40));
        List<VialMeasurement> measurements = new ArrayList<>();
        measurements.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> Station3Analyzer.analyze(records, measurements,
                        new AnalysisPolicy("test", 0.8, 2.0, 7.0, 10.0)));
    }

    private static VialRecord vial(int id, int offset) {
        return new VialRecord(id, 7, offset, offset + 10, offset * 30L,
                offset * 30L + 299, "test:" + id);
    }
}

