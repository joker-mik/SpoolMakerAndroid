/* SPDX-License-Identifier: GPL-3.0-or-later */
import de.spoolmaker.android.nfc.UltimakerTagCodec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class CodecSelfTest {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        String guid = "01234567-89ab-cdef-0123-456789abcdef";
        String uid = "04A1B2C3D4E5F6";
        long timestamp = 1_700_000_000L;

        require(UltimakerTagCodec.crc8("123".getBytes(StandardCharsets.US_ASCII)) == 0xC0,
                "CRC-8 reference vector failed");

        byte[] encoded = UltimakerTagCodec.encodeSpool(guid, uid, 750_000L, timestamp, 12_345L);
        require(encoded.length == 228, "Expected 228-byte padded message");
        require((encoded[0] & 0xFF) == 0x9C, "Material record header mismatch");
        require((encoded[134] & 0xFF) == 0x11, "Signature record header mismatch");
        require((encoded[142] & 0xFF) == 0x1C, "First status record header mismatch");
        require((encoded[184] & 0xFF) == 0x5C, "Second status record header mismatch");

        byte[] fullUserMemory = Arrays.copyOf(encoded, 888);
        fullUserMemory[887] = (byte) 0xA5;
        UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(fullUserMemory);
        require(guid.equals(decoded.getMaterialGuid()), "GUID round trip failed");
        require(uid.equals(decoded.getSerial()), "UID serial round trip failed");
        require(decoded.getTotalAmount() == 750_000L, "Total weight mismatch");
        require(decoded.getRemainingAmount() == 750_000L, "Remaining weight mismatch");
        require(decoded.isStatusCrcValid(), "CRC should be valid");

        UltimakerTagCodec.DecodedSpool partial = UltimakerTagCodec.decode(
                UltimakerTagCodec.encodeSpool(guid, uid, 1_000_000L, 425_000L));
        require(partial.getTotalAmount() == 1_000_000L, "Custom total weight mismatch");
        require(partial.getRemainingAmount() == 425_000L, "Custom remaining weight mismatch");
        require(decoded.isDuplicateStatusMatches(), "Status copies should match");
        require(decoded.isSignaturePresent(), "Signature record missing");
        require(decoded.isSignatureValid(), "Signature value should be valid");
        require(decoded.getSignatureValue() == 0x2000, "Signature value mismatch");
        require(decoded.getTotalUsageDurationSeconds() == 12_345L,
                "Usage duration round trip failed");
        require(decoded.getStatusRecordCount() == 2, "Expected two status records");
        require(decoded.getNdefRecords().size() == 4, "Expected four NDEF records");
        require(decoded.getMaterialRecordCount() == 1, "Expected one material record");
        require(decoded.getSignatureRecordCount() == 1, "Expected one signature record");
        require(decoded.getReadMemoryLength() == 888, "Full user memory length was not retained");
        require(decoded.getNdefLength() == 226, "Consumed NDEF length mismatch");
        require("0000".equals(decoded.getMaterialReservedHex()), "Reserved bytes mismatch");
        for (UltimakerTagCodec.DecodedStatusRecord status : decoded.getStatusRecords()) {
            require(status.isCrcValid(), "Individual status CRC should be valid");
            require(status.getStoredCrc() == status.getCalculatedCrc(),
                    "Stored and calculated CRC differ");
            require(status.getPayloadHex().length() == 40, "Status payload hex length mismatch");
        }

        UltimakerTagCodec.DecodedSpool defaults = UltimakerTagCodec.decode(
                UltimakerTagCodec.encodeSpool(guid, uid, 1_000_000L));
        require(defaults.getMaterialVersion() == 0, "Material version default mismatch");
        require(defaults.getStatusVersion() == 0, "Status version default mismatch");
        require(defaults.getManufacturingTimestamp() == 0L,
                "Manufacturing timestamp default mismatch");
        require(defaults.getTotalUsageDurationSeconds() == 0L,
                "Usage duration default mismatch");

        byte[] tlv = new byte[encoded.length + 3];
        tlv[0] = 0x03;
        tlv[1] = (byte) encoded.length;
        System.arraycopy(encoded, 0, tlv, 2, encoded.length);
        tlv[tlv.length - 1] = (byte) 0xFE;
        UltimakerTagCodec.DecodedSpool tlvDecoded = UltimakerTagCodec.decode(tlv);
        require(tlvDecoded.isTlvWrapped(), "TLV wrapper not detected");
        require(tlvDecoded.getNdefOffset() == 2, "TLV NDEF offset mismatch");

        byte[] damaged = Arrays.copyOf(encoded, encoded.length);
        damaged[183] ^= 0x01;
        UltimakerTagCodec.DecodedSpool damagedDecoded = UltimakerTagCodec.decode(damaged);
        require(!damagedDecoded.isStatusCrcValid(), "Damaged CRC was not detected");
        require(!damagedDecoded.isDuplicateStatusMatches(), "Different status copies were not detected");

        System.out.println("CodecSelfTest OK");
        System.out.println("Encoded bytes: " + encoded.length);
        System.out.println("Decoded user bytes: " + decoded.getReadMemoryLength());
        System.out.println("NDEF records: " + decoded.getNdefRecords().size());
        System.out.println("GUID: " + decoded.getMaterialGuid());
        System.out.println("Weight mg: " + decoded.getRemainingAmount());
    }
}
