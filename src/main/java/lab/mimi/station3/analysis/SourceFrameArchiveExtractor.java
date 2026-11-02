package lab.mimi.station3.analysis;

import lab.mimi.station3.domain.LabColor;
import lab.mimi.station3.domain.VialMeasurement;
import lab.mimi.station3.domain.VialRecord;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SourceFrameArchiveExtractor {
    public static final String EXTRACTOR_VERSION = "station3-source-roi-v4";
    private static final String CONFIG = "roi=x:0.45..0.55,y:0.60..0.84;candidate_window=0.45..0.90;selection=lowest_max_pair_de00;order=video_s;count=3;rgb=d65;max_pixels=8000000;unique_labels=true";
    private static final String CONFIG_HASH = sha256(CONFIG.getBytes(StandardCharsets.UTF_8));
    private static final long MAX_ENTRY_BYTES = 12L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private static final int MAX_LABEL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LABEL_ROWS = 20_000;
    private static final int MAX_CSV_LINE_CHARS = 16_384;
    private static final long MAX_IMAGE_PIXELS = 8_000_000L;
    private static final int MAX_IMAGE_DIMENSION = 8192;
    private static final int MAX_ENTRIES = 6000;

    private SourceFrameArchiveExtractor() {}

    public static ExtractionResult extract(Path archive, List<VialRecord> manifest) throws IOException {
        if (archive == null || manifest == null) throw new IllegalArgumentException("archive and manifest are required");
        String sourceHash = sha256(archive);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ArchiveIndex index = index(zip);
            Set<Integer> expectedVials = manifest.stream().map(VialRecord::vial).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Map<Integer, List<TimedFrame>> framesByVial = readLabels(zip, index.labels(), expectedVials);
            List<VialMeasurement> measurements = new ArrayList<>();
            for (VialRecord record : manifest) {
                List<TimedFrame> candidates = framesByVial.getOrDefault(record.vial(), List.of());
                if (candidates.size() < 3) {
                    continue;
                }
                Selection selection = selectStableWindow(zip, index.imagesByName(), candidates);
                List<String> selectedNames = selection.names();
                List<LabColor> colors = selection.colors();
                double quality = consistencyQuality(colors);
                measurements.add(new VialMeasurement(
                        record.vial(),
                        mean(colors),
                        quality,
                        String.join(" | ", selectedNames),
                        CONFIG_HASH,
                        EXTRACTOR_VERSION,
                        sourceHash));
            }
            return new ExtractionResult(List.copyOf(measurements), index.imagesByName().size(),
                    EXTRACTOR_VERSION, CONFIG_HASH, sourceHash);
        }
    }

    private static ArchiveIndex index(ZipFile zip) {
        Map<String, ZipEntry> images = new HashMap<>();
        ZipEntry labels = null;
        long totalBytes = 0;
        int entries = 0;
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entry.isDirectory()) continue;
            entries++;
            if (entries > MAX_ENTRIES) throw new IllegalArgumentException("source archive has too many files");
            long size = entry.getSize();
            if (size < 0 || size > MAX_ENTRY_BYTES) throw new IllegalArgumentException("source archive contains an oversized or unknown-size file");
            totalBytes += size;
            if (totalBytes > MAX_TOTAL_BYTES) throw new IllegalArgumentException("source archive expands beyond the allowed size");
            String name = entry.getName().replace('\\', '/');
            if (name.startsWith("/") || name.contains("../")) throw new IllegalArgumentException("source archive contains an unsafe path");
            String base = basename(name);
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.equals("video5_frame_labels.csv") || lower.equals("frame_labels.csv")) {
                if (labels != null) throw new IllegalArgumentException("source archive contains more than one frame-label csv");
                labels = entry;
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                if (images.putIfAbsent(base, entry) != null) throw new IllegalArgumentException("duplicate image filename " + base);
            }
        }
        if (labels == null) throw new IllegalArgumentException("source archive must contain video5_frame_labels.csv or frame_labels.csv");
        if (images.isEmpty()) throw new IllegalArgumentException("source archive contains no JPEG or PNG frames");
        return new ArchiveIndex(labels, Map.copyOf(images));
    }

    private static Map<Integer, List<TimedFrame>> readLabels(
            ZipFile zip,
            ZipEntry labels,
            Set<Integer> expectedVials) throws IOException {
        Map<Integer, List<TimedFrame>> result = new LinkedHashMap<>();
        byte[] labelBytes = readBounded(zip, labels, MAX_LABEL_BYTES, "frame-label csv");
        Set<String> labelledImages = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(labelBytes), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) throw new IllegalArgumentException("frame-label csv is empty");
            requireCsvLineLength(first);
            String[] headers = first.split(",", -1);
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < headers.length; i++) positions.put(headers[i].trim(), i);
            int filename = required(positions, "filename");
            int inFill = required(positions, "in_fill");
            int vial = required(positions, "vial");
            int videoSeconds = required(positions, "video_s");
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (++rows > MAX_LABEL_ROWS) throw new IllegalArgumentException("frame-label csv has too many rows");
                requireCsvLineLength(line);
                String[] cells = line.split(",", -1);
                String frameName = safeBasename(cell(cells, filename));
                if (!labelledImages.add(frameName)) {
                    throw new IllegalArgumentException("frame-label csv repeats image identity " + frameName);
                }
                String fillValue = cell(cells, inFill);
                if (!fillValue.equals("0") && !fillValue.equals("1")) {
                    throw new IllegalArgumentException("frame-label csv in_fill must be 0 or 1");
                }
                if (fillValue.equals("1")) {
                    String vialValue = cell(cells, vial);
                    if (vialValue.isBlank()) throw new IllegalArgumentException("in-fill frame is missing a vial identity");
                    int vialNumber;
                    try {
                        vialNumber = Integer.parseInt(vialValue);
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException("frame-label csv contains an invalid vial identity", error);
                    }
                    if (!expectedVials.contains(vialNumber)) {
                        throw new IllegalArgumentException("frame-label csv references unknown vial " + vialNumber);
                    }
                    double seconds;
                    try {
                        seconds = Double.parseDouble(cell(cells, videoSeconds));
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException("frame-label csv contains an invalid video_s", error);
                    }
                    if (!Double.isFinite(seconds)) {
                        throw new IllegalArgumentException("frame-label csv contains a non-finite video_s");
                    }
                    result.computeIfAbsent(vialNumber, ignored -> new ArrayList<>())
                            .add(new TimedFrame(frameName, seconds));
                }
            }
        }
        result.forEach((vialNumber, frames) -> {
            frames.sort(Comparator.comparingDouble(TimedFrame::videoSeconds)
                    .thenComparing(TimedFrame::name));
            for (int index = 1; index < frames.size(); index++) {
                if (Double.compare(frames.get(index - 1).videoSeconds(), frames.get(index).videoSeconds()) == 0) {
                    throw new IllegalArgumentException(
                            "frame-label csv repeats video_s within vial " + vialNumber);
                }
            }
        });
        return result;
    }

    private static Selection selectStableWindow(
            ZipFile zip,
            Map<String, ZipEntry> images,
            List<TimedFrame> frames) throws IOException {
        int first = Math.min(frames.size() - 3, Math.max(0, (int) Math.floor(frames.size() * 0.45)));
        int limit = Math.max(first + 3, Math.min(frames.size(), (int) Math.ceil(frames.size() * 0.90)));
        Map<String, LabColor> colors = new HashMap<>();
        Selection best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int start = first; start + 2 < limit; start++) {
            List<String> names = List.of(frames.get(start).name(), frames.get(start + 1).name(),
                    frames.get(start + 2).name());
            List<LabColor> window = new ArrayList<>(3);
            for (String name : names) {
                ZipEntry entry = images.get(name);
                if (entry == null) throw new IllegalArgumentException("source archive is missing labelled image " + name);
                LabColor color = colors.get(name);
                if (color == null) {
                    color = readRoiColor(zip, entry);
                    colors.put(name, color);
                }
                window.add(color);
            }
            double score = maximumPairDelta(window);
            if (score < bestScore) {
                bestScore = score;
                best = new Selection(names, List.copyOf(window));
            }
        }
        if (best == null) throw new IllegalArgumentException("no provisional pre-end three-frame window is available");
        return best;
    }

    private static LabColor readRoiColor(ZipFile zip, ZipEntry entry) throws IOException {
        byte[] encoded = readBounded(zip, entry, MAX_ENTRY_BYTES, "image " + entry.getName());
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if (input == null) throw new IllegalArgumentException("unsupported image " + entry.getName());
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("unsupported image " + entry.getName());
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 40 || height < 40) throw new IllegalArgumentException("image is too small " + entry.getName());
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || Math.multiplyExact((long) width, (long) height) > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("image dimensions exceed the allowed pixel budget " + entry.getName());
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (image == null) throw new IllegalArgumentException("unsupported image " + entry.getName());
        int x0 = (int) Math.floor(image.getWidth() * 0.45);
        int x1 = Math.max(x0 + 1, (int) Math.ceil(image.getWidth() * 0.55));
        int y0 = (int) Math.floor(image.getHeight() * 0.60);
        int y1 = Math.max(y0 + 1, (int) Math.ceil(image.getHeight() * 0.84));
        double red = 0;
        double green = 0;
        double blue = 0;
        long count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                red += srgbToLinear((rgb >>> 16) & 0xff);
                green += srgbToLinear((rgb >>> 8) & 0xff);
                blue += srgbToLinear(rgb & 0xff);
                count++;
            }
        }
        return linearRgbToLab(red / count, green / count, blue / count);
    }

    private static LabColor linearRgbToLab(double red, double green, double blue) {
        double x = (0.4124564 * red + 0.3575761 * green + 0.1804375 * blue) / 0.95047;
        double y = (0.2126729 * red + 0.7151522 * green + 0.0721750 * blue);
        double z = (0.0193339 * red + 0.1191920 * green + 0.9503041 * blue) / 1.08883;
        double fx = labCurve(x);
        double fy = labCurve(y);
        double fz = labCurve(z);
        return new LabColor(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
    }

    private static double srgbToLinear(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double labCurve(double value) {
        double pivot = 216.0 / 24389.0;
        return value > pivot ? Math.cbrt(value) : (24389.0 / 27.0 * value + 16.0) / 116.0;
    }

    private static double consistencyQuality(List<LabColor> colors) {
        return Math.max(0.0, Math.min(1.0, 1.0 - maximumPairDelta(colors) / 25.0));
    }

    private static double maximumPairDelta(List<LabColor> colors) {
        double maxDelta = 0.0;
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                maxDelta = Math.max(maxDelta, Ciede2000.between(colors.get(i), colors.get(j)));
            }
        }
        return maxDelta;
    }

    private static LabColor mean(List<LabColor> colors) {
        return new LabColor(
                colors.stream().mapToDouble(LabColor::lStar).average().orElseThrow(),
                colors.stream().mapToDouble(LabColor::aStar).average().orElseThrow(),
                colors.stream().mapToDouble(LabColor::bStar).average().orElseThrow());
    }

    private static int required(Map<String, Integer> positions, String name) {
        Integer value = positions.get(name);
        if (value == null) throw new IllegalArgumentException("frame-label csv is missing column " + name);
        return value;
    }

    private static String cell(String[] cells, int index) {
        return index < cells.length ? cells[index].trim() : "";
    }

    private static String basename(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private static String safeBasename(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("frame-label csv contains an invalid image identity");
        }
        String base = basename(name.strip());
        if (base.isBlank() || base.equals(".") || base.equals("..")) {
            throw new IllegalArgumentException("frame-label csv contains an invalid image identity");
        }
        return base;
    }

    private static void requireCsvLineLength(String line) {
        if (line.length() > MAX_CSV_LINE_CHARS) {
            throw new IllegalArgumentException("frame-label csv contains an oversized row");
        }
    }

    private static byte[] readBounded(ZipFile zip, ZipEntry entry, long limit, String label) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(limit + 1));
            if (bytes.length > limit) throw new IllegalArgumentException(label + " exceeds the allowed size");
            return bytes;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record ArchiveIndex(ZipEntry labels, Map<String, ZipEntry> imagesByName) {}

    private record TimedFrame(String name, double videoSeconds) {}

    private record Selection(List<String> names, List<LabColor> colors) {}

    public record ExtractionResult(
            List<VialMeasurement> measurements,
            int archiveImageCount,
            String extractorVersion,
            String configHash,
            String sourceHash) {}
}
