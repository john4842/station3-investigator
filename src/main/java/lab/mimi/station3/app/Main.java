package lab.mimi.station3.app;

import lab.mimi.station3.analysis.Station3Analyzer;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;
import lab.mimi.station3.ingest.CsvIngestor;
import lab.mimi.station3.report.ReportWriter;
import lab.mimi.station3.validation.ManifestValidator;

import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Arguments input = Arguments.parse(args);
        List<VialRecord> manifest = CsvIngestor.readManifest(input.manifest());
        ManifestValidator.requireWellFormed(manifest);
        List<VialMeasurement> measurements = CsvIngestor.readMeasurements(input.measurements());
        var assessments = Station3Analyzer.analyze(manifest, measurements, 0.80, 2.0, 10.0);
        ReportWriter.write(input.output(), input.datasetLabel(), input.synthetic(), assessments);
        System.out.printf("station3-investigator: wrote %d recipe assessments to %s%n", assessments.size(), input.output());
        if (input.synthetic()) System.out.println("SYNTHETIC FIXTURE: output is demonstration evidence, not plant evidence.");
    }

    private record Arguments(Path manifest, Path measurements, Path output, String datasetLabel, boolean synthetic) {
        static Arguments parse(String[] args) {
            Path manifest = null, measurements = null, output = Path.of("build/report");
            String label = "unspecified";
            boolean synthetic = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--manifest" -> manifest = Path.of(value(args, ++i));
                    case "--measurements" -> measurements = Path.of(value(args, ++i));
                    case "--out" -> output = Path.of(value(args, ++i));
                    case "--label" -> label = value(args, ++i);
                    case "--synthetic" -> synthetic = true;
                    default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
                }
            }
            if (manifest == null || measurements == null) {
                throw new IllegalArgumentException("required: --manifest PATH --measurements PATH [--out PATH] [--label TEXT] [--synthetic]");
            }
            return new Arguments(manifest, measurements, output, label, synthetic);
        }
        private static String value(String[] args, int index) {
            if (index >= args.length) throw new IllegalArgumentException("missing argument value");
            return args[index];
        }
    }
}
