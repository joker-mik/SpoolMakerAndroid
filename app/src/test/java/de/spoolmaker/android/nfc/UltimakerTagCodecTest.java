/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class UltimakerTagCodecTest {
    private static final String GUID = "01234567-89ab-cdef-0123-456789abcdef";
    private static final String UID = "04A1B2C3D4E5F6";
    private static final long DATE_SECONDS = 1_700_000_000L;

    @Test
    public void crcMatchesReferenceVector() {
        assertEquals(0xC0,
                UltimakerTagCodec.crc8("123".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void newWriterMatchesExpectedLayoutAndRoundTrips() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 750_000L, 750_000L,
                UltimakerTagCodec.DateMeaning.OPENED, DATE_SECONDS);

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
        assertEquals(0L, decoded.getTotalUsageDurationSeconds());
        assertEquals(0xAFFE, decoded.getStationId());
        assertEquals("123456789AB", decoded.getBatchCode());
        assertEquals("5303", decoded.getMaterialTrailingHex());
        assertEquals(DATE_SECONDS, (long) decoded.getTimeFieldDoubleSeconds());
        assertEquals(UltimakerTagCodec.DateMeaning.OPENED, decoded.getDateMeaning());
        assertTrue(decoded.isSpoolMakerTag());
        assertTrue(decoded.hasSpoolMakerDate());
        assertEquals(UltimakerTagCodec.UNIT_MILLIGRAMS, decoded.getUnit());
        assertTrue(decoded.isStatusCrcValid());
        assertTrue(decoded.isDuplicateStatusMatches());
        assertTrue(decoded.isSignaturePresent());
        assertTrue(decoded.hasExpectedSigMarker());
        assertEquals(0x2000, decoded.getSignatureValue());
        assertEquals(1, decoded.getActiveStatusRecordIndex());
        assertEquals(2, decoded.getStatusRecordCount());
        assertEquals(4, decoded.getNdefRecords().size());
        assertEquals(1, decoded.getMaterialRecordCount());
        assertEquals(1, decoded.getSignatureRecordCount());
        assertEquals(888, decoded.getReadMemoryLength());
        assertEquals(226, decoded.getNdefLength());
        assertTrue(UltimakerTagCodec.hasExpectedNdefLayout(decoded));
        assertTrue(UltimakerTagCodec.isIntegrityValid(UID, decoded));

        for (UltimakerTagCodec.DecodedStatusRecord status : decoded.getStatusRecords()) {
            assertTrue(status.isCrcValid());
            assertEquals(status.getStoredCrc(), status.getCalculatedCrc());
            assertEquals(40, status.getPayloadHex().length());
        }
    }

    @Test
    public void writerRoundTripsFromNtag215SizedMemory() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 750_000L, 650_000L,
                UltimakerTagCodec.DateMeaning.OPENED, DATE_SECONDS);

        byte[] ntag215Memory = Arrays.copyOf(encoded, NtagIo.NTAG215_USER_BYTES);
        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(ntag215Memory);

        assertEquals(NtagIo.NTAG215_USER_BYTES, decoded.getReadMemoryLength());
        assertEquals(650_000L, decoded.getRemainingAmount());
        assertTrue(UltimakerTagCodec.hasExpectedNdefLayout(decoded));
        assertTrue(UltimakerTagCodec.isIntegrityValid(UID, decoded));
    }

    @Test
    public void changedSignatureMarkerBreaksOverallIntegrity() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 750_000L, 750_000L,
                UltimakerTagCodec.DateMeaning.OPENED, DATE_SECONDS);
        encoded[141] ^= 0x01;

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertFalse(decoded.hasExpectedSigMarker());
        assertTrue(UltimakerTagCodec.hasExpectedNdefLayout(decoded));
        assertFalse(UltimakerTagCodec.isIntegrityValid(UID, decoded));
    }

    @Test
    public void noneDateStillWritesNewSpoolMakerFormat() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 1_000_000L, 425_000L,
                UltimakerTagCodec.DateMeaning.NONE, 0L);

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertTrue(decoded.isSpoolMakerTag());
        assertEquals(UltimakerTagCodec.DateMeaning.NONE, decoded.getDateMeaning());
        assertFalse(decoded.hasSpoolMakerDate());
        assertEquals("5300", decoded.getMaterialTrailingHex());
        assertEquals(0.0d, decoded.getTimeFieldDoubleSeconds(), 0.0d);
        assertEquals(1_000_000L, decoded.getTotalAmount());
        assertEquals(425_000L, decoded.getRemainingAmount());
        assertEquals(0L, decoded.getTotalUsageDurationSeconds());
    }

    @Test
    public void decoderAcceptsNdefTlvWrapper() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 1_000_000L, 1_000_000L,
                UltimakerTagCodec.DateMeaning.CREATED, DATE_SECONDS);
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
    public void corruptedFirstStatusIsReportedAndSecondIsSelected() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 500_000L, 500_000L,
                UltimakerTagCodec.DateMeaning.NONE, 0L);
        byte[] damaged = Arrays.copyOf(encoded, encoded.length);
        damaged[183] ^= 0x01;

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(damaged);

        assertFalse(decoded.isStatusCrcValid());
        assertFalse(decoded.isDuplicateStatusMatches());
        assertEquals(2, decoded.getActiveStatusRecordIndex());
    }

    @Test
    public void newerSecondStatusIsSelectedEvenWhenRemainingIncreases() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 750_000L, 750_000L,
                UltimakerTagCodec.DateMeaning.NONE, 0L);
        patchStatus(encoded, 0, 739_034L, 4_353L);
        patchStatus(encoded, 1, 739_411L, 4_354L);

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertTrue(decoded.isStatusCrcValid());
        assertFalse(decoded.isDuplicateStatusMatches());
        assertEquals(2, decoded.getActiveStatusRecordIndex());
        assertEquals(739_411L, decoded.getRemainingAmount());
        assertEquals(4_354L, decoded.getTotalUsageDurationSeconds());
    }

    @Test
    public void equalUsageKeepsFirstStatusLikePythonMax() {
        byte[] encoded = UltimakerTagCodec.encodeSpool(
                GUID, UID, 750_000L, 750_000L,
                UltimakerTagCodec.DateMeaning.NONE, 0L);
        patchStatus(encoded, 0, 822L, 200_364L);
        patchStatus(encoded, 1, 7_416L, 200_364L);

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(encoded);

        assertTrue(decoded.isStatusCrcValid());
        assertFalse(decoded.isDuplicateStatusMatches());
        assertEquals(1, decoded.getActiveStatusRecordIndex());
        assertEquals(822L, decoded.getRemainingAmount());
    }


    @Test
    public void originalFactoryTagFromRealDumpStillDecodes() {
        byte[] tag = fromPages(
                "9C156C01","756C7469","6D616B65","722E6E6C","3A6D6174","65726961","6C310000",
                "30344235","38303332","43373139","393041D9","86A0D10E","E06EE92B","1F0BA069",
                "496986B4","30127CFB","6F7B000C","41323430","34313030","32383300",
                "00000000","00000000","00000000","00000000","00000000","00000000","00000000",
                "00000000","00000000","00000000","00000000","00000000","00000000",
                "00001103","02536967","20001C11","1401756C","74696D61","6B65722E","6E6C3A73",
                "74617432","00000200","0B71B000","0B71B000","00000000","00000079","5C111401",
                "756C7469","6D616B65","722E6E6C","3A737461","74320000","02000B71","B0000B71",
                "B0000000","00000000","00790000");

        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(tag);

        assertEquals("04B58032C71990", decoded.getSerial());
        assertEquals("e92b1f0b-a069-4969-86b4-30127cfb6f7b", decoded.getMaterialGuid());
        assertEquals(12, decoded.getStationId());
        assertEquals("A2404100283", decoded.getBatchCode());
        assertEquals("41D986A0D10EE06E", decoded.getTimeFieldRawHex());
        assertEquals(1_713_013_572.232448d, decoded.getTimeFieldDoubleSeconds(), 0.000001d);
        assertEquals("0000", decoded.getMaterialTrailingHex());
        assertFalse(decoded.isSpoolMakerTag());
        assertEquals(750_000L, decoded.getTotalAmount());
        assertEquals(750_000L, decoded.getRemainingAmount());
        assertEquals(0L, decoded.getTotalUsageDurationSeconds());
        assertTrue(decoded.isStatusCrcValid());
        assertTrue(decoded.isDuplicateStatusMatches());
        assertTrue(decoded.hasExpectedSigMarker());
    }

    @Test
    public void uidSerialComparisonMatchesNtag216SerialField() {
        assertTrue(UltimakerTagCodec.uidMatchesSerial(
                "04:A1:B2:C3:D4:E5:F6", UID));
        assertFalse(UltimakerTagCodec.uidMatchesSerial(
                "04:A1:B2:C3:D4:E5:F7", UID));
    }


    private static byte[] fromPages(String... pages) {
        byte[] result = new byte[pages.length * 4];
        int cursor = 0;
        for (String page : pages) {
            if (page.length() != 8) {
                throw new IllegalArgumentException("page");
            }
            for (int index = 0; index < 8; index += 2) {
                result[cursor++] = (byte) Integer.parseInt(
                        page.substring(index, index + 2), 16);
            }
        }
        return result;
    }

    private static void patchStatus(byte[] encoded, int statusIndex,
                                    long remainingMg, long usageSeconds) {
        int payloadOffset;
        if (statusIndex == 0) {
            payloadOffset = 164;
        } else if (statusIndex == 1) {
            payloadOffset = 206;
        } else {
            throw new IllegalArgumentException("statusIndex");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(payloadOffset + 7, (int) remainingMg);
        buffer.putLong(payloadOffset + 11, usageSeconds);
        encoded[payloadOffset + 19] = (byte) UltimakerTagCodec.crc8(
                encoded, payloadOffset, 19);
    }
}
