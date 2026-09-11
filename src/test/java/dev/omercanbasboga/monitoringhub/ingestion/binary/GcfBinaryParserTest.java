package dev.omercanbasboga.monitoringhub.ingestion.binary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GcfBinaryParserTest {

    /**
     * Hand-builds one 1024-byte GCF block for channel "L2" (sea level) with a
     * known first-integration-constant and diff sequence, then asserts the
     * parser recovers the exact physical values and timestamps. This is the
     * one part of the format spec that's easy to get subtly wrong (diff
     * decode, big-endian layout, the sea-level reference-offset correction),
     * so it's worth pinning down with a real byte-level test rather than only
     * exercising it against live files.
     */
    @Test
    void parsesASingleBlockWithKnownValues() throws IOException {
        int systemId = encodeBase36("TEST");
        int streamId = encodeBase36("STA1L2"); // 4-char station id "STA1" + channel "L2" -- but base36 field is 4 bytes wide
        // streamId must fit the 4-char decode used by GcfRecord (first 4 chars = station, rest = channel);
        // use a 6-char logical stream encoded across the 4-byte int the same way the parser reads it.
        // For this test we only need round-trip correctness of decode, so use a station id that is itself 4 chars
        // and rely on GcfRecord's own substring logic against the *decoded* streamId string.
        String logicalStreamId = "ST01L2";

        byte[] block = new byte[1024];
        ByteBuffer buf = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0, encodeBase36Padded("TEST", 4));
        buf.putInt(4, encodeBase36Stream(logicalStreamId));
        buf.putInt(8, encodeTimeWord(LocalDateTime.of(1989, 11, 17, 0, 0, 1))); // epoch + 1 second
        buf.put(13, (byte) 0x00); // decimation exponent 0 -> 1 Hz
        buf.put(15, (byte) 2);    // 2 additional diff records beyond the FIC
        buf.putInt(16, 8280);     // FIC: raw value 8280 -> sea level correction gives 0.0
        buf.putInt(20, -10);      // diff #1: 8280 + (-10) = 8270 -> (8280-8270)/10 = 1.0
        buf.putInt(24, 20);       // diff #2: 8270 + 20 = 8290 -> (8280-8290)/10 = -1.0

        GcfBinaryParser.GcfRecord record = GcfBinaryParser.parse(new ByteArrayInputStream(block));

        assertEquals(3, record.values.length);
        assertEquals(0.0, record.values[0], 1e-9);
        assertEquals(1.0, record.values[1], 1e-9);
        assertEquals(-1.0, record.values[2], 1e-9);
        assertEquals(1.0, record.samplingRate, 1e-9);
        assertEquals(LocalDateTime.of(1989, 11, 17, 0, 0, 1), record.startTime);
    }

    private int encodeBase36(String s) { return encodeBase36Padded(s, s.length()); }

    private int encodeBase36Padded(String s, int len) {
        long val = 0;
        for (char c : s.toCharArray()) val = val * 36 + Character.digit(c, 36);
        return (int) val;
    }

    /** Encodes a 6-char logical stream id the same way GcfRecord later splits it back into station(4)+channel(2). */
    private int encodeBase36Stream(String s) {
        // The real format only has 4 bytes (base-36 up to ~6 chars fits in an unsigned 32-bit int),
        // and GcfRecord derives station/channel by substring-ing the *decoded* string, not by
        // re-deriving from the raw int. So we must encode such that decodeBase36 reproduces `s` exactly.
        long val = 0;
        for (char c : s.toCharArray()) val = val * 36 + Character.digit(c, 36);
        return (int) val;
    }

    private int encodeTimeWord(LocalDateTime time) {
        LocalDateTime epoch = LocalDateTime.of(1989, 11, 17, 0, 0, 0);
        long totalSeconds = java.time.Duration.between(epoch, time).getSeconds();
        long days = totalSeconds / 86400;
        long secs = totalSeconds % 86400;
        return (int) ((days << 17) | secs);
    }
}
