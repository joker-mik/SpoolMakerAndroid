/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class UltimakerTagCodec {
    public static final int UNIT_UNUSED = 0;
    public static final int UNIT_MILLIMETRES = 1;
    public static final int UNIT_MILLIGRAMS = 2;
    public static final int UNIT_CUBIC_CENTIMETRES = 3;
    public static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;

    private static final int TNF_WELL_KNOWN = 0x01;
    private static final int TNF_EXTERNAL_TYPE = 0x04;
    private static final int FLAG_MB = 0x80;
    private static final int FLAG_ME = 0x40;
    private static final int FLAG_CF = 0x20;
    private static final int FLAG_SR = 0x10;
    private static final int FLAG_IL = 0x08;

    private static final byte[] TYPE_MATERIAL =
            "ultimaker.nl:material".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TYPE_SIGNATURE =
            "Sig".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TYPE_STATUS =
            "ultimaker.nl:stat".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ID_MATERIAL = "1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ID_STATUS = "2".getBytes(StandardCharsets.US_ASCII);

    private static final int MATERIAL_PAYLOAD_LENGTH = 108;
    private static final int MATERIAL_INTERPRETED_LENGTH = 106;
    private static final int STATUS_PAYLOAD_LENGTH = 20;
    private static final int DEFAULT_STATION_ID = 0xAFFE;
    private static final String DEFAULT_BATCH_CODE = "123456789AB";

    // SpoolMaker extension inside the two bytes that S5/S8 firmware does not interpret.
    // Byte 106: 0x53 ('S'), byte 107: DateMeaning code.
    private static final int SPOOLMAKER_DATE_MARKER = 0x53;

    public enum DateMeaning {
        NONE(0),
        MANUFACTURED(1),
        PURCHASED(2),
        OPENED(3),
        CREATED(4);

        private final int code;

        DateMeaning(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        private static DateMeaning fromCode(int code) {
            for (DateMeaning value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return NONE;
        }
    }

    private UltimakerTagCodec() {
    }

    /**
     * Encodes a new SpoolMaker tag.
     *
     * <p>This is intentionally the only writer entry point. New tags always use
     * programming-station id 0xAFFE and SpoolMaker metadata in material bytes
     * 106/107. If {@code dateMeaning != NONE}, the 8-byte material time field is
     * written as a big-endian IEEE-754 double containing Unix seconds. For
     * {@code NONE}, the time field is 0.0.</p>
     */
    public static byte[] encodeSpool(String materialGuid, String uidHex,
                                     long totalWeightMg, long remainingWeightMg,
                                     DateMeaning dateMeaning, long dateEpochSeconds) {
        if (totalWeightMg <= 0 || totalWeightMg > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException(
                    "Gesamtgewicht liegt ausserhalb des 32-Bit-Tagformats.");
        }
        if (remainingWeightMg < 0 || remainingWeightMg > totalWeightMg
                || remainingWeightMg > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException(
                    "Restgewicht muss zwischen 0 und Gesamtgewicht liegen.");
        }
        if (dateMeaning == null) {
            throw new IllegalArgumentException("Datumsart fehlt.");
        }
        if (dateMeaning == DateMeaning.NONE) {
            dateEpochSeconds = 0L;
        } else if (dateEpochSeconds <= 0L) {
            throw new IllegalArgumentException("Datum muss nach dem Unix-Epoch liegen.");
        }

        UUID materialUuid = UUID.fromString(materialGuid);
        String serial = sanitizeSerial(uidHex);
        if (serial.length() != 14) {
            throw new IllegalArgumentException(
                    "NTAG216-UID muss als 7 Byte bzw. 14 Hex-Zeichen vorliegen.");
        }

        double dateSeconds = dateMeaning == DateMeaning.NONE
                ? 0.0d : (double) dateEpochSeconds;
        long dateBits = Double.doubleToRawLongBits(dateSeconds);

        byte[] materialPayload = encodeMaterialPayload(
                materialUuid,
                serial,
                dateBits,
                new byte[]{(byte) SPOOLMAKER_DATE_MARKER, (byte) dateMeaning.getCode()});

        // A newly created tag starts with two identical valid status records.
        // The printer will alternate them later. Usage starts at zero.
        byte[] statusPayload = encodeStatusPayload(
                totalWeightMg, remainingWeightMg, BigInteger.ZERO);
        byte[] signaturePayload = new byte[]{0x20, 0x00};

        ByteArrayOutputStream output = new ByteArrayOutputStream(228);
        append(output, encodeRecord(true, false, TNF_EXTERNAL_TYPE,
                TYPE_MATERIAL, ID_MATERIAL, materialPayload));
        append(output, encodeRecord(false, false, TNF_WELL_KNOWN,
                TYPE_SIGNATURE, null, signaturePayload));
        append(output, encodeRecord(false, false, TNF_EXTERNAL_TYPE,
                TYPE_STATUS, ID_STATUS, statusPayload));
        append(output, encodeRecord(false, true, TNF_EXTERNAL_TYPE,
                TYPE_STATUS, ID_STATUS, statusPayload));

        byte[] raw = output.toByteArray();
        int paddedLength = (raw.length + 3) & ~3;
        return Arrays.copyOf(raw, paddedLength);
    }

    public static DecodedSpool decode(byte[] tagUserMemory) {
        if (tagUserMemory == null || tagUserMemory.length == 0) {
            throw new IllegalArgumentException("Keine Tagdaten vorhanden.");
        }

        ExtractedMessage extracted = extractNdefBytes(tagUserMemory);
        ParsedRecords parsed = parseRecords(extracted.bytes);

        MaterialData material = null;
        int materialRecordCount = 0;
        List<DecodedStatusRecord> statuses = new ArrayList<>();
        List<byte[]> statusPayloads = new ArrayList<>();
        List<DecodedNdefRecord> publicRecords = new ArrayList<>();

        boolean signaturePresent = false;
        boolean signatureMarkerMatches = false;
        int signatureValue = -1;
        byte[] signaturePayload = new byte[0];
        int signatureRecordCount = 0;

        int recordIndex = 0;
        for (NdefRecord record : parsed.records) {
            recordIndex++;
            String type = new String(record.type, StandardCharsets.US_ASCII);

            publicRecords.add(new DecodedNdefRecord(
                    recordIndex,
                    record.flags,
                    record.tnf,
                    type,
                    decodeDisplayId(record.id),
                    toHex(record.id),
                    record.payload.length,
                    toHex(record.payload),
                    record.offset,
                    record.length));

            if ("ultimaker.nl:material".equals(type)) {
                materialRecordCount++;
                if (material == null) {
                    material = decodeMaterialPayload(record.payload);
                }
            } else if ("ultimaker.nl:stat".equals(type)) {
                StatusData status = decodeStatusPayload(record.payload);
                statuses.add(new DecodedStatusRecord(
                        statuses.size() + 1,
                        status.version,
                        status.compatibility,
                        status.unit,
                        status.totalAmount,
                        status.remainingAmount,
                        status.totalUsageDurationSeconds,
                        status.storedCrc,
                        status.calculatedCrc,
                        status.crcValid,
                        toHex(status.payload)));
                statusPayloads.add(status.payload);
            } else if ("Sig".equals(type)) {
                signatureRecordCount++;
                if (!signaturePresent) {
                    signaturePresent = true;
                    signaturePayload = Arrays.copyOf(record.payload, record.payload.length);
                    if (record.payload.length == 2) {
                        signatureValue = ((record.payload[0] & 0xFF) << 8)
                                | (record.payload[1] & 0xFF);
                        signatureMarkerMatches = signatureValue == 0x2000;
                    }
                }
            }
        }

        if (material == null) {
            throw new IllegalArgumentException("Kein Ultimaker-Materialrecord gefunden.");
        }
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Kein Ultimaker-Statusrecord gefunden.");
        }

        boolean copiesMatch = statusPayloads.size() >= 2;
        if (copiesMatch) {
            byte[] first = statusPayloads.get(0);
            for (int index = 1; index < statusPayloads.size(); index++) {
                if (!Arrays.equals(first, statusPayloads.get(index))) {
                    copiesMatch = false;
                    break;
                }
            }
        }

        boolean allStatusCrcValid = true;
        for (DecodedStatusRecord status : statuses) {
            if (!status.isCrcValid()) {
                allStatusCrcValid = false;
                break;
            }
        }

        return new DecodedSpool(
                material.guid,
                material.serial,
                material.timeFieldUnsigned,
                material.timeFieldDoubleSeconds,
                toHex(material.timeFieldBytes),
                material.stationId,
                material.batchCode,
                material.version,
                material.compatibility,
                toHex(material.trailingBytes),
                Collections.unmodifiableList(new ArrayList<>(statuses)),
                allStatusCrcValid,
                copiesMatch,
                signaturePresent,
                signatureMarkerMatches,
                signatureValue,
                toHex(signaturePayload),
                materialRecordCount,
                signatureRecordCount,
                Collections.unmodifiableList(publicRecords),
                extracted.tlvWrapped,
                extracted.offset,
                parsed.consumedLength,
                tagUserMemory.length);
    }

    public static int crc8(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("CRC-Daten fehlen.");
        }
        return crc8(data, 0, data.length);
    }

    public static int crc8(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Ungueltiger CRC-Bereich.");
        }
        int crc = 0;
        for (int index = offset; index < offset + length; index++) {
            crc ^= data[index] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0x07) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
        }
        return crc;
    }

    public static String unitLabel(int unit) {
        switch (unit) {
            case UNIT_UNUSED:
                return "nicht verwendet";
            case UNIT_MILLIMETRES:
                return "mm";
            case UNIT_MILLIGRAMS:
                return "mg";
            case UNIT_CUBIC_CENTIMETRES:
                return "cm3";
            default:
                return "unbekannt";
        }
    }

    public static boolean uidMatchesSerial(String uidHex, String serial) {
        String uid = sanitizeSerial(uidHex);
        String stored = sanitizeSerial(serial);
        return uid.length() == 14 && stored.length() == 14 && uid.equals(stored);
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return builder.toString();
    }

    private static byte[] encodeMaterialPayload(UUID guid, String serial, long timeFieldBits,
                                                byte[] trailingBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(MATERIAL_PAYLOAD_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        putFixed(buffer, serial.getBytes(StandardCharsets.UTF_8), 14);
        buffer.putLong(timeFieldBits);
        buffer.putLong(guid.getMostSignificantBits());
        buffer.putLong(guid.getLeastSignificantBits());
        buffer.putShort((short) DEFAULT_STATION_ID);
        putFixed(buffer, DEFAULT_BATCH_CODE.getBytes(StandardCharsets.UTF_8), 64);
        buffer.put(trailingBytes[0]);
        buffer.put(trailingBytes[1]);
        return buffer.array();
    }

    private static byte[] encodeStatusPayload(long totalWeightMg, long remainingWeightMg,
                                              BigInteger totalUsageDurationSeconds) {
        if (totalUsageDurationSeconds.signum() < 0
                || totalUsageDurationSeconds.bitLength() > 64) {
            throw new IllegalArgumentException("Nutzungsdauer passt nicht in uint64.");
        }
        ByteBuffer buffer = ByteBuffer.allocate(STATUS_PAYLOAD_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put((byte) UNIT_MILLIGRAMS);
        buffer.putInt((int) totalWeightMg);
        buffer.putInt((int) remainingWeightMg);
        buffer.put(toUnsignedFixed(totalUsageDurationSeconds, 8));
        byte[] payload = buffer.array();
        payload[19] = (byte) crc8(payload, 0, 19);
        return payload;
    }

    private static byte[] encodeRecord(boolean messageBegin, boolean messageEnd, int tnf,
                                       byte[] type, byte[] id, byte[] payload) {
        if (type.length > 255 || payload.length > 255 || (id != null && id.length > 255)) {
            throw new IllegalArgumentException(
                    "Record passt nicht in das Short-Record-Format.");
        }

        boolean hasId = id != null && id.length > 0;
        int flags = FLAG_SR | tnf;
        if (messageBegin) {
            flags |= FLAG_MB;
        }
        if (messageEnd) {
            flags |= FLAG_ME;
        }
        if (hasId) {
            flags |= FLAG_IL;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(flags);
        output.write(type.length);
        output.write(payload.length);
        if (hasId) {
            output.write(id.length);
        }
        append(output, type);
        if (hasId) {
            append(output, id);
        }
        append(output, payload);
        return output.toByteArray();
    }

    private static ExtractedMessage extractNdefBytes(byte[] memory) {
        int cursor = 0;
        while (cursor < memory.length && memory[cursor] == 0x00) {
            cursor++;
        }
        if (cursor >= memory.length) {
            throw new IllegalArgumentException("Der Tag enthaelt keine NDEF-Daten.");
        }

        int firstType = memory[cursor] & 0xFF;
        if (firstType != 0x01 && firstType != 0x02 && firstType != 0x03) {
            return new ExtractedMessage(
                    Arrays.copyOfRange(memory, cursor, memory.length), false, cursor);
        }

        while (cursor < memory.length) {
            int type = memory[cursor++] & 0xFF;
            if (type == 0x00) {
                continue;
            }
            if (type == 0xFE) {
                break;
            }
            if (cursor >= memory.length) {
                break;
            }

            int length = memory[cursor++] & 0xFF;
            if (length == 0xFF) {
                if (cursor + 1 >= memory.length) {
                    throw new IllegalArgumentException("Beschaedigte NDEF-TLV-Laenge.");
                }
                length = ((memory[cursor] & 0xFF) << 8)
                        | (memory[cursor + 1] & 0xFF);
                cursor += 2;
            }
            if (cursor + length > memory.length) {
                throw new IllegalArgumentException("NDEF-TLV ist unvollstaendig.");
            }
            if (type == 0x03) {
                return new ExtractedMessage(
                        Arrays.copyOfRange(memory, cursor, cursor + length), true, cursor);
            }
            cursor += length;
        }

        throw new IllegalArgumentException("Keine NDEF-Nachricht im Tag gefunden.");
    }

    private static ParsedRecords parseRecords(byte[] message) {
        List<NdefRecord> records = new ArrayList<>();
        int cursor = 0;
        boolean foundEnd = false;

        while (cursor < message.length) {
            int recordOffset = cursor;
            int flags = readUnsignedByte(message, cursor++);
            boolean shortRecord = (flags & FLAG_SR) != 0;
            boolean hasId = (flags & FLAG_IL) != 0;
            boolean chunked = (flags & FLAG_CF) != 0;
            if (chunked) {
                throw new IllegalArgumentException(
                        "Chunked NDEF-Records werden nicht unterstuetzt.");
            }

            int typeLength = readUnsignedByte(message, cursor++);
            long payloadLength;
            if (shortRecord) {
                payloadLength = readUnsignedByte(message, cursor++);
            } else {
                payloadLength = readUnsignedInt(message, cursor);
                cursor += 4;
            }
            int idLength = hasId ? readUnsignedByte(message, cursor++) : 0;

            if (payloadLength > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("NDEF-Payload ist zu gross.");
            }
            int required = typeLength + idLength + (int) payloadLength;
            if (required < 0 || cursor + required > message.length) {
                throw new IllegalArgumentException("NDEF-Record ist unvollstaendig.");
            }

            byte[] type = Arrays.copyOfRange(message, cursor, cursor + typeLength);
            cursor += typeLength;
            byte[] id = Arrays.copyOfRange(message, cursor, cursor + idLength);
            cursor += idLength;
            byte[] payload = Arrays.copyOfRange(
                    message, cursor, cursor + (int) payloadLength);
            cursor += (int) payloadLength;

            records.add(new NdefRecord(
                    flags, flags & 0x07, type, id, payload,
                    recordOffset, cursor - recordOffset));

            if ((flags & FLAG_ME) != 0) {
                foundEnd = true;
                break;
            }
        }

        if (!foundEnd || records.isEmpty()) {
            throw new IllegalArgumentException(
                    "NDEF-Nachricht besitzt kein gueltiges Ende.");
        }
        return new ParsedRecords(records, cursor);
    }

    private static MaterialData decodeMaterialPayload(byte[] payload) {
        if (payload.length < MATERIAL_INTERPRETED_LENGTH) {
            throw new IllegalArgumentException("Material-Payload ist zu kurz.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int version = buffer.get() & 0xFF;
        int compatibility = buffer.get() & 0xFF;

        byte[] serialBytes = new byte[14];
        buffer.get(serialBytes);

        byte[] timeFieldBytes = new byte[8];
        buffer.get(timeFieldBytes);
        BigInteger timeFieldUnsigned = unsignedBigInteger(timeFieldBytes);
        double timeFieldDoubleSeconds = ByteBuffer.wrap(timeFieldBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getDouble();

        UUID guid = new UUID(buffer.getLong(), buffer.getLong());
        int stationId = buffer.getShort() & 0xFFFF;

        byte[] batchBytes = new byte[64];
        buffer.get(batchBytes);

        int trailingLength = Math.min(
                2, Math.max(0, payload.length - MATERIAL_INTERPRETED_LENGTH));
        byte[] trailingBytes = trailingLength == 0
                ? new byte[0]
                : Arrays.copyOfRange(
                        payload,
                        MATERIAL_INTERPRETED_LENGTH,
                        MATERIAL_INTERPRETED_LENGTH + trailingLength);

        String serial = decodeZeroTerminated(serialBytes);
        String batch = decodeZeroTerminated(batchBytes);
        return new MaterialData(
                version,
                compatibility,
                serial,
                timeFieldBytes,
                timeFieldUnsigned,
                timeFieldDoubleSeconds,
                guid.toString().toLowerCase(Locale.US),
                stationId,
                batch,
                trailingBytes);
    }

    private static StatusData decodeStatusPayload(byte[] payload) {
        if (payload.length < STATUS_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Status-Payload ist zu kurz.");
        }

        byte[] exactPayload = Arrays.copyOf(payload, STATUS_PAYLOAD_LENGTH);
        ByteBuffer buffer = ByteBuffer.wrap(exactPayload).order(ByteOrder.BIG_ENDIAN);
        int version = buffer.get() & 0xFF;
        int compatibility = buffer.get() & 0xFF;
        int unit = buffer.get() & 0xFF;
        long total = ((long) buffer.getInt()) & MAX_UNSIGNED_INT;
        long remaining = ((long) buffer.getInt()) & MAX_UNSIGNED_INT;

        byte[] usageBytes = new byte[8];
        buffer.get(usageBytes);
        BigInteger totalUsageDurationSeconds = unsignedBigInteger(usageBytes);

        int storedCrc = exactPayload[19] & 0xFF;
        int calculatedCrc = crc8(exactPayload, 0, 19);

        return new StatusData(
                version,
                compatibility,
                unit,
                total,
                remaining,
                totalUsageDurationSeconds,
                storedCrc,
                calculatedCrc,
                storedCrc == calculatedCrc,
                exactPayload);
    }

    private static BigInteger unsignedBigInteger(byte[] bytes) {
        return new BigInteger(1, bytes);
    }

    private static byte[] toUnsignedFixed(BigInteger value, int length) {
        byte[] source = value.toByteArray();
        byte[] result = new byte[length];
        int copyLength = Math.min(length, source.length);
        System.arraycopy(source, source.length - copyLength,
                result, length - copyLength, copyLength);
        return result;
    }

    private static String sanitizeSerial(String value) {
        if (value == null) {
            return "";
        }
        String serial = value.replaceAll("[^0-9A-Fa-f]", "")
                .toUpperCase(Locale.US);
        if (serial.length() > 14) {
            return serial.substring(0, 14);
        }
        return serial;
    }

    private static String decodeZeroTerminated(byte[] bytes) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8).trim();
    }

    private static String decodeDisplayId(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value < 0x20 || value > 0x7E) {
                return "";
            }
        }
        return text;
    }

    private static void putFixed(ByteBuffer buffer, byte[] bytes, int length) {
        int copyLength = Math.min(bytes.length, length);
        buffer.put(bytes, 0, copyLength);
        for (int index = copyLength; index < length; index++) {
            buffer.put((byte) 0);
        }
    }

    private static int readUnsignedByte(byte[] data, int offset) {
        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException(
                    "Unerwartetes Ende der NDEF-Nachricht.");
        }
        return data[offset] & 0xFF;
    }

    private static long readUnsignedInt(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) {
            throw new IllegalArgumentException(
                    "Unerwartetes Ende der NDEF-Nachricht.");
        }
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
    }

    private static void append(ByteArrayOutputStream output, byte[] bytes) {
        output.write(bytes, 0, bytes.length);
    }

    private static final class ExtractedMessage {
        private final byte[] bytes;
        private final boolean tlvWrapped;
        private final int offset;

        private ExtractedMessage(byte[] bytes, boolean tlvWrapped, int offset) {
            this.bytes = bytes;
            this.tlvWrapped = tlvWrapped;
            this.offset = offset;
        }
    }

    private static final class ParsedRecords {
        private final List<NdefRecord> records;
        private final int consumedLength;

        private ParsedRecords(List<NdefRecord> records, int consumedLength) {
            this.records = records;
            this.consumedLength = consumedLength;
        }
    }

    private static final class NdefRecord {
        private final int flags;
        private final int tnf;
        private final byte[] type;
        private final byte[] id;
        private final byte[] payload;
        private final int offset;
        private final int length;

        private NdefRecord(int flags, int tnf, byte[] type, byte[] id,
                           byte[] payload, int offset, int length) {
            this.flags = flags;
            this.tnf = tnf;
            this.type = type;
            this.id = id;
            this.payload = payload;
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class MaterialData {
        private final int version;
        private final int compatibility;
        private final String serial;
        private final byte[] timeFieldBytes;
        private final BigInteger timeFieldUnsigned;
        private final double timeFieldDoubleSeconds;
        private final String guid;
        private final int stationId;
        private final String batchCode;
        private final byte[] trailingBytes;

        private MaterialData(int version, int compatibility, String serial,
                             byte[] timeFieldBytes, BigInteger timeFieldUnsigned,
                             double timeFieldDoubleSeconds, String guid,
                             int stationId, String batchCode, byte[] trailingBytes) {
            this.version = version;
            this.compatibility = compatibility;
            this.serial = serial;
            this.timeFieldBytes = timeFieldBytes;
            this.timeFieldUnsigned = timeFieldUnsigned;
            this.timeFieldDoubleSeconds = timeFieldDoubleSeconds;
            this.guid = guid;
            this.stationId = stationId;
            this.batchCode = batchCode;
            this.trailingBytes = trailingBytes;
        }
    }

    private static final class StatusData {
        private final int version;
        private final int compatibility;
        private final int unit;
        private final long totalAmount;
        private final long remainingAmount;
        private final BigInteger totalUsageDurationSeconds;
        private final int storedCrc;
        private final int calculatedCrc;
        private final boolean crcValid;
        private final byte[] payload;

        private StatusData(int version, int compatibility, int unit,
                           long totalAmount, long remainingAmount,
                           BigInteger totalUsageDurationSeconds,
                           int storedCrc, int calculatedCrc,
                           boolean crcValid, byte[] payload) {
            this.version = version;
            this.compatibility = compatibility;
            this.unit = unit;
            this.totalAmount = totalAmount;
            this.remainingAmount = remainingAmount;
            this.totalUsageDurationSeconds = totalUsageDurationSeconds;
            this.storedCrc = storedCrc;
            this.calculatedCrc = calculatedCrc;
            this.crcValid = crcValid;
            this.payload = payload;
        }
    }

    public static final class DecodedNdefRecord {
        private final int index;
        private final int flags;
        private final int tnf;
        private final String type;
        private final String idText;
        private final String idHex;
        private final int payloadLength;
        private final String payloadHex;
        private final int offset;
        private final int recordLength;

        private DecodedNdefRecord(int index, int flags, int tnf, String type,
                                  String idText, String idHex, int payloadLength,
                                  String payloadHex, int offset, int recordLength) {
            this.index = index;
            this.flags = flags;
            this.tnf = tnf;
            this.type = type;
            this.idText = idText;
            this.idHex = idHex;
            this.payloadLength = payloadLength;
            this.payloadHex = payloadHex;
            this.offset = offset;
            this.recordLength = recordLength;
        }

        public int getIndex() { return index; }
        public int getFlags() { return flags; }
        public int getTnf() { return tnf; }
        public String getType() { return type; }
        public String getIdText() { return idText; }
        public String getIdHex() { return idHex; }
        public int getPayloadLength() { return payloadLength; }
        public String getPayloadHex() { return payloadHex; }
        public int getOffset() { return offset; }
        public int getRecordLength() { return recordLength; }
        public boolean isMessageBegin() { return (flags & FLAG_MB) != 0; }
        public boolean isMessageEnd() { return (flags & FLAG_ME) != 0; }
        public boolean isShortRecord() { return (flags & FLAG_SR) != 0; }
        public boolean hasId() { return (flags & FLAG_IL) != 0; }
    }

    public static final class DecodedStatusRecord {
        private final int index;
        private final int version;
        private final int compatibility;
        private final int unit;
        private final long totalAmount;
        private final long remainingAmount;
        private final BigInteger totalUsageDurationSeconds;
        private final int storedCrc;
        private final int calculatedCrc;
        private final boolean crcValid;
        private final String payloadHex;

        private DecodedStatusRecord(int index, int version, int compatibility, int unit,
                                    long totalAmount, long remainingAmount,
                                    BigInteger totalUsageDurationSeconds,
                                    int storedCrc, int calculatedCrc,
                                    boolean crcValid, String payloadHex) {
            this.index = index;
            this.version = version;
            this.compatibility = compatibility;
            this.unit = unit;
            this.totalAmount = totalAmount;
            this.remainingAmount = remainingAmount;
            this.totalUsageDurationSeconds = totalUsageDurationSeconds;
            this.storedCrc = storedCrc;
            this.calculatedCrc = calculatedCrc;
            this.crcValid = crcValid;
            this.payloadHex = payloadHex;
        }

        public int getIndex() { return index; }
        public int getVersion() { return version; }
        public int getCompatibility() { return compatibility; }
        public int getUnit() { return unit; }
        public long getTotalAmount() { return totalAmount; }
        public long getRemainingAmount() { return remainingAmount; }
        public BigInteger getTotalUsageDurationSecondsUnsigned() {
            return totalUsageDurationSeconds;
        }
        public long getTotalUsageDurationSeconds() {
            return totalUsageDurationSeconds.longValue();
        }
        public int getStoredCrc() { return storedCrc; }
        public int getCalculatedCrc() { return calculatedCrc; }
        public boolean isCrcValid() { return crcValid; }
        public String getPayloadHex() { return payloadHex; }
    }

    public static final class DecodedSpool {
        private final String materialGuid;
        private final String serial;
        private final BigInteger timeFieldUnsigned;
        private final double timeFieldDoubleSeconds;
        private final String timeFieldRawHex;
        private final int stationId;
        private final String batchCode;
        private final int materialVersion;
        private final int materialCompatibility;
        private final String materialTrailingHex;
        private final List<DecodedStatusRecord> statusRecords;
        private final boolean statusCrcValid;
        private final boolean duplicateStatusMatches;
        private final boolean signaturePresent;
        private final boolean signatureMarkerMatches;
        private final int signatureValue;
        private final String signaturePayloadHex;
        private final int materialRecordCount;
        private final int signatureRecordCount;
        private final List<DecodedNdefRecord> ndefRecords;
        private final boolean tlvWrapped;
        private final int ndefOffset;
        private final int ndefLength;
        private final int readMemoryLength;

        private DecodedSpool(String materialGuid, String serial,
                             BigInteger timeFieldUnsigned,
                             double timeFieldDoubleSeconds,
                             String timeFieldRawHex,
                             int stationId, String batchCode,
                             int materialVersion, int materialCompatibility,
                             String materialTrailingHex,
                             List<DecodedStatusRecord> statusRecords,
                             boolean statusCrcValid,
                             boolean duplicateStatusMatches,
                             boolean signaturePresent,
                             boolean signatureMarkerMatches,
                             int signatureValue,
                             String signaturePayloadHex,
                             int materialRecordCount,
                             int signatureRecordCount,
                             List<DecodedNdefRecord> ndefRecords,
                             boolean tlvWrapped, int ndefOffset,
                             int ndefLength, int readMemoryLength) {
            this.materialGuid = materialGuid;
            this.serial = serial;
            this.timeFieldUnsigned = timeFieldUnsigned;
            this.timeFieldDoubleSeconds = timeFieldDoubleSeconds;
            this.timeFieldRawHex = timeFieldRawHex;
            this.stationId = stationId;
            this.batchCode = batchCode;
            this.materialVersion = materialVersion;
            this.materialCompatibility = materialCompatibility;
            this.materialTrailingHex = materialTrailingHex;
            this.statusRecords = statusRecords;
            this.statusCrcValid = statusCrcValid;
            this.duplicateStatusMatches = duplicateStatusMatches;
            this.signaturePresent = signaturePresent;
            this.signatureMarkerMatches = signatureMarkerMatches;
            this.signatureValue = signatureValue;
            this.signaturePayloadHex = signaturePayloadHex;
            this.materialRecordCount = materialRecordCount;
            this.signatureRecordCount = signatureRecordCount;
            this.ndefRecords = ndefRecords;
            this.tlvWrapped = tlvWrapped;
            this.ndefOffset = ndefOffset;
            this.ndefLength = ndefLength;
            this.readMemoryLength = readMemoryLength;
        }

        private DecodedStatusRecord activeStatus() {
            DecodedStatusRecord active = null;

            // Prefer valid records. Highest usage wins; equal usage keeps the
            // earlier record, matching Python max() over the original order.
            for (DecodedStatusRecord status : statusRecords) {
                if (!status.isCrcValid()) {
                    continue;
                }
                if (active == null || status.getTotalUsageDurationSecondsUnsigned()
                        .compareTo(active.getTotalUsageDurationSecondsUnsigned()) > 0) {
                    active = status;
                }
            }

            if (active != null) {
                return active;
            }

            // Diagnostic fallback if all CRCs are invalid.
            active = statusRecords.get(0);
            for (int index = 1; index < statusRecords.size(); index++) {
                DecodedStatusRecord candidate = statusRecords.get(index);
                if (candidate.getTotalUsageDurationSecondsUnsigned()
                        .compareTo(active.getTotalUsageDurationSecondsUnsigned()) > 0) {
                    active = candidate;
                }
            }
            return active;
        }

        public String getMaterialGuid() { return materialGuid; }
        public String getSerial() { return serial; }
        public BigInteger getTimeFieldUnsigned() { return timeFieldUnsigned; }
        public double getTimeFieldDoubleSeconds() { return timeFieldDoubleSeconds; }
        public String getTimeFieldRawHex() { return timeFieldRawHex; }
        public int getStationId() { return stationId; }
        public String getBatchCode() { return batchCode; }
        public int getMaterialVersion() { return materialVersion; }
        public int getMaterialCompatibility() { return materialCompatibility; }
        public String getMaterialTrailingHex() { return materialTrailingHex; }

        public boolean isSpoolMakerTag() {
            if (stationId != DEFAULT_STATION_ID || materialTrailingHex == null
                    || materialTrailingHex.length() < 4) {
                return false;
            }
            try {
                int marker = Integer.parseInt(materialTrailingHex.substring(0, 2), 16);
                return marker == SPOOLMAKER_DATE_MARKER;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        public DateMeaning getDateMeaning() {
            if (!isSpoolMakerTag()) {
                return DateMeaning.NONE;
            }
            try {
                int code = Integer.parseInt(materialTrailingHex.substring(2, 4), 16);
                return DateMeaning.fromCode(code);
            } catch (NumberFormatException ignored) {
                return DateMeaning.NONE;
            }
        }

        public boolean hasSpoolMakerDate() {
            return isSpoolMakerTag()
                    && getDateMeaning() != DateMeaning.NONE
                    && Double.isFinite(timeFieldDoubleSeconds)
                    && timeFieldDoubleSeconds > 0.0d;
        }

        public int getActiveStatusRecordIndex() { return activeStatus().getIndex(); }
        public int getStatusVersion() { return activeStatus().getVersion(); }
        public int getStatusCompatibility() { return activeStatus().getCompatibility(); }
        public int getUnit() { return activeStatus().getUnit(); }
        public long getTotalAmount() { return activeStatus().getTotalAmount(); }
        public long getRemainingAmount() { return activeStatus().getRemainingAmount(); }
        public long getTotalUsageDurationSeconds() {
            return activeStatus().getTotalUsageDurationSeconds();
        }
        public BigInteger getTotalUsageDurationSecondsUnsigned() {
            return activeStatus().getTotalUsageDurationSecondsUnsigned();
        }

        public boolean isStatusCrcValid() { return statusCrcValid; }
        public boolean isDuplicateStatusMatches() { return duplicateStatusMatches; }
        public boolean isSignaturePresent() { return signaturePresent; }
        public boolean hasExpectedSigMarker() { return signatureMarkerMatches; }
        public int getSignatureValue() { return signatureValue; }
        public String getSignaturePayloadHex() { return signaturePayloadHex; }
        public int getStatusRecordCount() { return statusRecords.size(); }
        public int getMaterialRecordCount() { return materialRecordCount; }
        public int getSignatureRecordCount() { return signatureRecordCount; }
        public List<DecodedStatusRecord> getStatusRecords() { return statusRecords; }
        public List<DecodedNdefRecord> getNdefRecords() { return ndefRecords; }
        public boolean isTlvWrapped() { return tlvWrapped; }
        public int getNdefOffset() { return ndefOffset; }
        public int getNdefLength() { return ndefLength; }
        public int getReadMemoryLength() { return readMemoryLength; }
    }
}
