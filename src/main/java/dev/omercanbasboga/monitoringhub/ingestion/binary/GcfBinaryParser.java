package dev.omercanbasboga.monitoringhub.ingestion.binary;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Parser for GCF (Guralp Compressed Format), a compact binary time-series
 * format widely used by environmental and seismic sensor hardware. A file is
 * a sequence of fixed-size (1024-byte) blocks; each block carries a header
 * (station/stream id, block start time, compression/decimation info, sample
 * count) followed by diff-encoded 32-bit sample deltas that must be
 * accumulated against a "first integration constant" to recover the actual
 * signal.
 *
 * <p>This is a from-scratch reimplementation of the format written for a
 * university sea-level/weather monitoring project; the format spec itself is
 * public and used across many organizations' sensor hardware, so nothing
 * here is specific to any one deployment.
 */
public final class GcfBinaryParser {

    private GcfBinaryParser() {}

    private static final LocalDateTime GCF_EPOCH = LocalDateTime.of(1989, 11, 17, 0, 0, 0);
    private static final int BLOCK_SIZE = 1024;
    private static final double SEA_LEVEL_REFERENCE = 8280.0;

    /** Channel codes this reimplementation recognizes and their physical unit. */
    public static final Map<String, String> CHANNEL_NAMES = new LinkedHashMap<>();
    static {
        CHANNEL_NAMES.put("H2", "Humidity (%)");
        CHANNEL_NAMES.put("T2", "Air temperature (C)");
        CHANNEL_NAMES.put("W2", "Wind speed (m/s)");
        CHANNEL_NAMES.put("L2", "Sea level (cm)");
        CHANNEL_NAMES.put("D2", "Wind direction (deg)");
        CHANNEL_NAMES.put("S2", "Sea water temperature (C)");
        CHANNEL_NAMES.put("P2", "Barometric pressure (hPa)");
    }

    public static class GcfRecord {
        public final String systemId;
        public final String streamId;
        public final String stationId;
        public final String channelId;
        public final LocalDateTime startTime;
        public final double samplingRate;
        public final double[] values;

        public GcfRecord(String systemId, String streamId, LocalDateTime startTime, double samplingRate, double[] values) {
            this.systemId = systemId;
            this.streamId = streamId;
            this.stationId = streamId.length() >= 4 ? streamId.substring(0, 4) : streamId;
            this.channelId = streamId.length() > 4 ? streamId.substring(4) : "";
            this.startTime = startTime;
            this.samplingRate = samplingRate;
            this.values = values;
        }

        public String getChannelDescription() {
            return CHANNEL_NAMES.getOrDefault(channelId, channelId);
        }

        /** Timestamp of the i-th sample in this block, derived from samplingRate. */
        public LocalDateTime timeAt(int idx) {
            long nanos = (long) (idx * 1_000_000_000L / samplingRate);
            return startTime.plusNanos(nanos);
        }

        @Override
        public String toString() {
            return String.format("GcfRecord[station=%s channel=%s start=%s samples=%d rate=%.4f]",
                    stationId, channelId, startTime, values.length, samplingRate);
        }
    }

    /**
     * Parses a full GCF file from a stream: reads every block, diff-decodes
     * the samples, and converts them to physical units.
     *
     * <p>Header layout (big-endian), repeated at the start of every block:
     * <pre>
     *   [0..3]   systemId (base-36)
     *   [4..7]   streamId (base-36) -- first 4 chars = station id, rest = channel id
     *   [8..11]  block start time (days-since-epoch : seconds-in-day, packed)
     *   [12]     reserved
     *   [13]     compression type (high nibble) / decimation exponent (low nibble)
     *   [14]     gain
     *   [15]     sample count in this block (0-255)
     *   [16..19] first integration constant (FIC)
     *   [20..]   diff-encoded sample deltas, 4 bytes each
     * </pre>
     */
    public static GcfRecord parse(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        int blockCount = bytes.length / BLOCK_SIZE;
        if (blockCount == 0) throw new IOException("GCF file is empty or too small.");

        ByteBuffer first = block(bytes, 0);
        String systemId = decodeBase36(first.getInt(0));
        String streamId = decodeBase36(first.getInt(4));
        LocalDateTime startTime = decodeTime(first.getInt(8));
        double samplingRate = decodeSamplingRate(first.get(13) & 0xFF);
        String channelId = streamId.length() > 4 ? streamId.substring(4) : "";

        List<Integer> raw = new ArrayList<>();
        for (int b = 0; b < blockCount; b++) {
            ByteBuffer buf = block(bytes, b);
            int sampleCount = buf.get(15) & 0xFF;
            int current = buf.getInt(16);
            raw.add(current);
            for (int r = 0; r < sampleCount; r++) {
                current += buf.getInt(20 + r * 4);
                raw.add(current);
            }
        }

        return new GcfRecord(systemId, streamId, startTime, samplingRate, applyCorrection(channelId, raw));
    }

    public static GcfRecord parse(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return parse(fis);
        }
    }

    /**
     * Sea level channel: (reference - raw) / 10 -- raw counts down as the
     * water rises relative to a fixed reference point. Every other channel
     * is a plain raw/10 fixed-point value.
     */
    private static double[] applyCorrection(String channel, List<Integer> raw) {
        double[] result = new double[raw.size()];
        boolean isSeaLevel = "L2".equals(channel);
        for (int i = 0; i < raw.size(); i++) {
            result[i] = isSeaLevel ? (SEA_LEVEL_REFERENCE - raw.get(i)) / 10.0 : raw.get(i) / 10.0;
        }
        return result;
    }

    /**
     * Byte 13: high nibble = compression type, low nibble D = decimation exponent.
     * Environmental sensors are typically base 1 Hz, so rate = 1 / 2^D.
     */
    private static double decodeSamplingRate(int headerByte13) {
        int decimationExp = headerByte13 & 0x0F;
        double rate = 1.0 / Math.pow(2, decimationExp);
        return (rate > 0 && Double.isFinite(rate)) ? rate : 1.0;
    }

    private static ByteBuffer block(byte[] data, int index) {
        ByteBuffer buf = ByteBuffer.wrap(data, index * BLOCK_SIZE, BLOCK_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf;
    }

    private static String decodeBase36(int n) {
        if (n == 0) return "0";
        final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        long val = Integer.toUnsignedLong(n);
        StringBuilder sb = new StringBuilder();
        while (val > 0) {
            sb.insert(0, CHARS.charAt((int) (val % 36)));
            val /= 36;
        }
        return sb.toString();
    }

    /** [31:17] = days since epoch, [16:0] = seconds within the day. Epoch: 1989-11-17 00:00:00. */
    private static LocalDateTime decodeTime(int timeWord) {
        long t = Integer.toUnsignedLong(timeWord);
        long days = (t >> 17) & 0x7FFFL;
        long secs = t & 0x1FFFFL;
        return GCF_EPOCH.plusDays(days).plusSeconds(secs);
    }
}
