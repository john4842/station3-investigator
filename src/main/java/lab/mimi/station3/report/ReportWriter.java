package lab.mimi.station3.report;

import lab.mimi.station3.domain.RecipeAssessment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ReportWriter {
    private ReportWriter() {}

    public static void write(Path outputDirectory, String datasetLabel, boolean synthetic, List<RecipeAssessment> assessments)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("report.json"), json(datasetLabel, synthetic, assessments));
        Files.writeString(outputDirectory.resolve("report.html"), html(datasetLabel, synthetic, assessments));
    }

    public static String json(String datasetLabel, boolean synthetic, List<RecipeAssessment> assessments) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"dataset\": \"").append(escape(datasetLabel)).append("\",\n")
                .append("  \"synthetic\": ").append(synthetic).append(",\n")
                .append("  \"colorDifferenceMethod\": \"CIEDE2000 computed in Java from per-vial CIELAB appearance vectors\",\n")
                .append("  \"assessments\": [\n");
        for (int i = 0; i < assessments.size(); i++) {
            RecipeAssessment a = assessments.get(i);
            out.append("    {\"recipeId\": ").append(a.recipeId())
                    .append(", \"status\": \"").append(a.status().label()).append("\"")
                    .append(", \"investigationHypothesis\": \"").append(a.investigationHypothesis()).append("\"")
                    .append(", \"expectedVials\": ").append(a.expectedVials())
                    .append(", \"acceptedMeasurements\": ").append(a.acceptedMeasurements())
                    .append(", \"firstToLaterMeanDeltaE00\": ").append(number(a.firstToLaterMeanDeltaE00()))
                    .append(", \"laterPairDeltaE00\": ").append(number(a.laterPairDeltaE00()))
                    .append(", \"predecessorDeltaE00\": ").append(number(a.predecessorDeltaE00()))
                    .append(", \"batchConsistency\": \"").append(a.batchConsistency()).append("\"")
                    .append(", \"recipeConformity\": \"").append(a.recipeConformity()).append("\"")
                    .append(", \"recipeConformityReference\": \"").append(escape(a.recipeConformityReference())).append("\"")
                    .append(", \"withinBatchMetrics\": {")
                    .append("\"firstVial\": ").append(a.withinBatchMetrics().firstVial())
                    .append(", \"secondVial\": ").append(a.withinBatchMetrics().secondVial())
                    .append(", \"thirdVial\": ").append(a.withinBatchMetrics().thirdVial())
                    .append(", \"vial1ToVial2DeltaE00\": ").append(number(a.withinBatchMetrics().vial1ToVial2DeltaE00()))
                    .append(", \"vial1ToVial3DeltaE00\": ").append(number(a.withinBatchMetrics().vial1ToVial3DeltaE00()))
                    .append(", \"vial2ToVial3DeltaE00\": ").append(number(a.withinBatchMetrics().vial2ToVial3DeltaE00()))
                    .append(", \"referencePolicyVersion\": \"").append(escape(a.withinBatchMetrics().referencePolicyVersion())).append("\"")
                    .append(", \"referenceSource\": \"").append(escape(a.withinBatchMetrics().referenceSource())).append("\"")
                    .append(", \"comparisonDescription\": \"").append(escape(a.withinBatchMetrics().comparisonDescription())).append("\"}")
                    .append(", \"explanation\": \"").append(escape(a.explanation())).append("\"")
                    .append(", \"sourceReferences\": [");
            for (int j = 0; j < a.sourceReferences().size(); j++) {
                if (j > 0) out.append(", ");
                out.append('"').append(escape(a.sourceReferences().get(j))).append('"');
            }
            out.append("], \"configHashes\": ");
            appendStringArray(out, a.configHashes());
            out.append(", \"extractorVersions\": ");
            appendStringArray(out, a.extractorVersions());
            out.append(", \"sourceHashes\": ");
            appendStringArray(out, a.sourceHashes());
            out.append(", \"rejectionReasons\": ");
            appendStringArray(out, a.rejectionReasons());
            out.append("}").append(i + 1 == assessments.size() ? "\n" : ",\n");
        }
        return out.append("  ]\n}\n").toString();
    }

    public static String html(String datasetLabel, boolean synthetic, List<RecipeAssessment> assessments) {
        StringBuilder rows = new StringBuilder();
        for (RecipeAssessment a : assessments) {
            rows.append("<tr><td>").append(a.recipeId()).append("</td><td>").append(a.status().label())
                    .append("</td><td>").append(a.investigationHypothesis()).append("</td><td>")
                    .append(a.batchConsistency()).append("</td><td>").append(a.recipeConformity()).append("</td><td>")
                    .append(a.acceptedMeasurements()).append("/").append(a.expectedVials()).append("</td><td>")
                    .append(displayNumber(a.firstToLaterMeanDeltaE00())).append("</td><td>")
                    .append(displayNumber(a.withinBatchMetrics().vial1ToVial2DeltaE00())).append("</td><td>")
                    .append(displayNumber(a.withinBatchMetrics().vial1ToVial3DeltaE00())).append("</td><td>")
                    .append(displayNumber(a.withinBatchMetrics().vial2ToVial3DeltaE00())).append("</td><td>")
                    .append(displayNumber(a.laterPairDeltaE00())).append("</td><td>")
                    .append(displayNumber(a.predecessorDeltaE00()))
                    .append("</td><td>").append(escapeHtml(a.explanation())).append("</td><td>")
                    .append(escapeHtml("config=" + String.join(",", a.configHashes())
                            + "; extractor=" + String.join(",", a.extractorVersions())
                            + "; source=" + String.join(",", a.sourceHashes())))
                    .append("</td></tr>\n");
        }
        String warning = synthetic ? "<p class=warning>SYNTHETIC FIXTURE: not plant evidence.</p>" : "";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Station 3 Investigator</title>"
                + "<style>body{font-family:system-ui;max-width:1100px;margin:2rem auto}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.5rem;text-align:left}.warning{background:#ffe7a8;padding:1rem;font-weight:700}</style>"
                + "</head><body><h1>Station 3 Investigator</h1>" + warning + "<p>dataset: " + escapeHtml(datasetLabel) + "</p>"
                + "<p>color difference: CIEDE2000 computed in Java from per-vial CIELAB appearance vectors.</p>"
                + "<p>all three pairwise vial distances are explicit. vial 3 is a provisional dataset-specific reference, not independent recipe truth.</p>"
                + "<p>statuses are retrospective evidence classifications only. they do not establish prospective accuracy, root cause, or carryover certainty.</p>"
                + "<table><thead><tr><th>recipe</th><th>status</th><th>hypothesis</th><th>batch consistency</th><th>recipe conformity</th><th>accepted/expected</th><th>vial 1→later mean ΔE00</th><th>vial 1→2 ΔE00</th><th>vial 1→3 ΔE00</th><th>vial 2→3 ΔE00</th><th>legacy later pair ΔE00</th><th>predecessor ΔE00</th><th>explanation</th><th>provenance</th></tr></thead><tbody>"
                + rows + "</tbody></table></body></html>\n";
    }

    private static void appendStringArray(StringBuilder out, List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(escape(values.get(i))).append('"');
        }
        out.append(']');
    }

    private static String displayNumber(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "Unavailable"; }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "null"; }
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') escaped.append('\\').append(c);
            else if (c < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.toString();
    }
    private static String escapeHtml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
