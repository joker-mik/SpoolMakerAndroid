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

        byte[] encoded = UltimakerTagCodec.encodeSpool(
                guid, uid, 750_000L, 750_000L,
                UltimakerTagCodec.DateMeaning.OPENED, timestamp);
        require(encoded.length == 228, "Expected 228-byte padded message");
        require((encoded[0] & 0xFF) == 0x9C, "Material record header mismatch");
        require((encoded[134] & 0xFF) == 0x11, "Signature record header mismatch");
        require((encoded[142] & 0xFF) == 0x1C, "First status record header mismatch");
        require((encoded[184] & 0xFF) == 0x5C, "Second status record header mismatch");

        byte[] ntag216Memory = Arrays.copyOf(encoded, 888);
        ntag216Memory[887] = (byte) 0xA5;
        UltimakerTagCodec.DecodedSpool decoded216 = UltimakerTagCodec.decode(ntag216Memory);
        require(guid.equals(decoded216.getMaterialGuid()), "GUID round trip failed");
        require(uid.equals(decoded216.getSerial()), "UID serial round trip failed");
        require(decoded216.getTotalAmount() == 750_000L, "Total weight mismatch");
        require(decoded216.getRemainingAmount() == 750_000L, "Remaining weight mismatch");
        require(decoded216.isStatusCrcValid(), "CRC should be valid");
        require(decoded216.isDuplicateStatusMatches(), "Status copies should match");
        require(decoded216.isSignaturePresent(), "Signature record missing");
        require(decoded216.hasExpectedSigMarker(), "Signature marker should be 0x2000");
        require(decoded216.getSignatureValue() == 0x2000, "Signature value mismatch");
        require(decoded216.getStatusRecordCount() == 2, "Expected two status records");
        require(decoded216.getNdefRecords().size() == 4, "Expected four NDEF records");
        require(decoded216.getMaterialRecordCount() == 1, "Expected one material record");
        require(decoded216.getSignatureRecordCount() == 1, "Expected one signature record");
        require(decoded216.getReadMemoryLength() == 888,
                "NTAG216 memory length was not retained");
        require(decoded216.getNdefLength() == 226, "Consumed NDEF length mismatch");
        require(decoded216.isSpoolMakerTag(), "SpoolMaker marker missing");
        require(decoded216.getDateMeaning() == UltimakerTagCodec.DateMeaning.OPENED,
                "Date meaning mismatch");
        require(Math.round(decoded216.getTimeFieldDoubleSeconds()) == timestamp,
                "Date field mismatch");
        require(UltimakerTagCodec.hasExpectedNdefLayout(decoded216),
                "Expected NDEF layout missing");
        require(UltimakerTagCodec.isIntegrityValid(uid, decoded216),
                "Overall integrity should be valid");

        byte[] ntag215Memory = Arrays.copyOf(encoded, 504);
        UltimakerTagCodec.DecodedSpool decoded215 = UltimakerTagCodec.decode(ntag215Memory);
        require(decoded215.getReadMemoryLength() == 504,
                "NTAG215 memory length was not retained");
        require(UltimakerTagCodec.isIntegrityValid(uid, decoded215),
                "NTAG215-sized memory should decode with valid integrity");

        byte[] noDate = UltimakerTagCodec.encodeSpool(
                guid, uid, 1_000_000L, 425_000L,
                UltimakerTagCodec.DateMeaning.NONE, 0L);
        UltimakerTagCodec.DecodedSpool noDateDecoded = UltimakerTagCodec.decode(noDate);
        require(noDateDecoded.getTotalAmount() == 1_000_000L, "Custom total weight mismatch");
        require(noDateDecoded.getRemainingAmount() == 425_000L, "Custom remaining weight mismatch");
        require(noDateDecoded.getTotalUsageDurationSeconds() == 0L,
                "New tags must start with zero usage duration");
        require("5300".equals(noDateDecoded.getMaterialTrailingHex()),
                "SpoolMaker marker/date code mismatch");

        byte[] tlv = new byte[encoded.length + 3];
        tlv[0] = 0x03;
        tlv[1] = (byte) encoded.length;
        System.arraycopy(encoded, 0, tlv, 2, encoded.length);
        tlv[tlv.length - 1] = (byte) 0xFE;
        UltimakerTagCodec.DecodedSpool tlvDecoded = UltimakerTagCodec.decode(tlv);
        require(tlvDecoded.isTlvWrapped(), "TLV wrapper not detected");
        require(tlvDecoded.getNdefOffset() == 2, "TLV NDEF offset mismatch");

        byte[] damagedStatus = Arrays.copyOf(encoded, encoded.length);
        damagedStatus[183] ^= 0x01;
        UltimakerTagCodec.DecodedSpool damagedStatusDecoded =
                UltimakerTagCodec.decode(damagedStatus);
        require(!damagedStatusDecoded.isStatusCrcValid(), "Damaged CRC was not detected");
        require(!damagedStatusDecoded.isDuplicateStatusMatches(),
                "Different status copies were not detected");

        byte[] damagedSignature = Arrays.copyOf(encoded, encoded.length);
        damagedSignature[141] ^= 0x01;
        UltimakerTagCodec.DecodedSpool damagedSignatureDecoded =
                UltimakerTagCodec.decode(damagedSignature);
        require(!damagedSignatureDecoded.hasExpectedSigMarker(),
                "Damaged signature marker was not detected");
        require(!UltimakerTagCodec.isIntegrityValid(uid, damagedSignatureDecoded),
                "Damaged signature marker must fail overall integrity");

        System.out.println("CodecSelfTest OK");
        System.out.println("Encoded bytes: " + encoded.length);
        System.out.println("NTAG215 decoded user bytes: " + decoded215.getReadMemoryLength());
        System.out.println("NTAG216 decoded user bytes: " + decoded216.getReadMemoryLength());
        System.out.println("NDEF records: " + decoded216.getNdefRecords().size());
    }
}
