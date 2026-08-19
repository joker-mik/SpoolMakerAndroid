/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class UltimakerTagCodecTest {
    private static final String GUID = "01234567-89ab-cdef-0123-456789abcdef";
    private static final String UID = "04A1B2C3D4E5F6";
    private static final long TIMESTAMP = 1_700_000_000L;

    @Test
    public void crcMatchesReferenceVector() {
        assertEquals(0xC0, UltimakerTagCodec.crc8("123".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void encodedMessageMatchesExpectedLayoutAndRoundTrips() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(GUID, UID, 750_000L, TIMESTAMP, 12_345L);

        assertEquals(228, encoded.length);
        assertEquals(0x9C, encoded[0] & 0xFF);
        assertEquals(0x11, encoded[134] & 0xFF);
        assertEquals(0x1C, encoded[142] & 0xFF);
        assertEquals(0x5C, encoded[184] & 0xFF);
        assertEquals(0, encoded[226]);
        assertEquals(0, encoded[227]);

        byte[] fullUserMemory = Arrays.copyOf(encoded, 888);
        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(fullUserMemory);
        assertEquals(GUID, decoded.getMaterialGuid());
        assertEquals(UID, decoded.getSerial());
        assertEquals(750_000L, decoded.getTotalAmount());
        assertEquals(750_000L, decoded.getRemainingAmount());
        assertEquals(TIMESTAMP, decoded.getManufacturingTimestamp());
        assertEquals(12_345L, decoded.getTotalUsageDurationSeconds());
        assertEquals(0xAFFE, decoded.getStationId());
        assertEquals("123456789AB", decoded.getBatchCode());
        assertEquals("0000", decoded.getMaterialReservedHex());
        assertEquals(UltimakerTagCodec.UNIT_MILLIGRAMS, decoded.getUnit());
        assertTrue(decoded.isStatusCrcValid());
        assertTrue(decoded.isDuplicateStatusMatches());
        assertTrue(decoded.isSignaturePresent());
        assertTrue(decoded.isSignatureValid());
        assertEquals(0x2000, decoded.getSignatureValue());
        assertEquals(2, decoded.getStatusRecordCount());
        assertEquals(4, decoded.getNdefRecords().size());
        assertEquals(1, decoded.getMaterialRecordCount());
        assertEquals(1, decoded.getSignatureRecordCount());
        assertEquals(888, decoded.getReadMemoryLength());
        assertEquals(226, decoded.getNdefLength());

        for (UltimakerTagCodec.DecodedStatusRecord status : decoded.getStatusRecords()) {
            assertTrue(status.isCrcValid());
            assertEquals(status.getStoredCrc(), status.getCalculatedCrc());
            assertEquals(40, status.getPayloadHex().length());
        }
    }

    @Test
    public void defaultWriterMatchesUpstreamZeroDefaults() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(GUID, UID, 1_000_000L);
        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertEquals(0, decoded.getMaterialVersion());
        assertEquals(0, decoded.getMaterialCompatibility());
        assertEquals(0, decoded.getStatusVersion());
        assertEquals(0, decoded.getStatusCompatibility());
        assertEquals(0L, decoded.getManufacturingTimestamp());
        assertEquals(0L, decoded.getTotalUsageDurationSeconds());
    }

    @Test
    public void decoderAcceptsNdefTlvWrapper() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(GUID, UID, 1_000_000L, TIMESTAMP, 0L);
        byte[] tlv = new byte[encoded.length + 3];
        tlv[0] = 0x03;
        tlv[1] = (byte) encoded.length;
        System.arraycopy(encoded, 0, tlv, 2, encoded.length);
        tlv[tlv.length - 1] = (byte) 0xFE;

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(tlv);
        assertEquals(GUID, decoded.getMaterialGuid());
        assertEquals(1_000_000L, decoded.getRemainingAmount());
        assertTrue(decoded.isTlvWrapped());
        assertEquals(2, decoded.getNdefOffset());
    }

    @Test
    public void corruptedFirstStatusIsReported() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(GUID, UID, 500_000L, TIMESTAMP, 0L);
        byte[] damaged = Arrays.copyOf(encoded, encoded.length);
        damaged[183] ^= 0x01;

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(damaged);
        assertFalse(decoded.isStatusCrcValid());
        assertFalse(decoded.isDuplicateStatusMatches());
    }
    @Test
    public void writerSupportsIndependentRemainingWeight() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(GUID, UID, 1_000_000L, 425_000L);
        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertEquals(1_000_000L, decoded.getTotalAmount());
        assertEquals(425_000L, decoded.getRemainingAmount());
        assertTrue(decoded.isStatusCrcValid());
        assertTrue(decoded.isDuplicateStatusMatches());
    }

}
