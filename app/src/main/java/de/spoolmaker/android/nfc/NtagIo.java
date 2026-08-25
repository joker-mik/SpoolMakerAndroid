/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import android.nfc.Tag;
import android.nfc.tech.NfcA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public final class NtagIo {
    public static final int FIRST_USER_PAGE = 4;
    public static final int NTAG215_USER_BYTES = 504;
    public static final int NTAG216_USER_BYTES = 888;

    private static final int READ_COMMAND = 0x30;
    private static final int WRITE_COMMAND = 0xA2;
    private static final int GET_VERSION_COMMAND = 0x60;
    private static final int ACK = 0x0A;
    private static final int IO_TIMEOUT_MS = 2000;

    private NtagIo() {
    }

    public enum TagModel {
        NTAG215("NTAG215", NTAG215_USER_BYTES, 130, 131),
        NTAG216("NTAG216", NTAG216_USER_BYTES, 226, 227);

        private final String displayName;
        private final int userBytes;
        private final int dynamicLockPage;
        private final int configPage;

        TagModel(String displayName, int userBytes, int dynamicLockPage, int configPage) {
            this.displayName = displayName;
            this.userBytes = userBytes;
            this.dynamicLockPage = dynamicLockPage;
            this.configPage = configPage;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getUserBytes() {
            return userBytes;
        }

        int getDynamicLockPage() {
            return dynamicLockPage;
        }

        int getConfigPage() {
            return configPage;
        }
    }

    public static final class TagInfo {
        private final TagModel model;
        private final byte[] versionResponse;

        private TagInfo(TagModel model, byte[] versionResponse) {
            this.model = model;
            this.versionResponse = Arrays.copyOf(versionResponse, versionResponse.length);
        }

        public TagModel getModel() {
            return model;
        }

        public int getUserBytes() {
            return model.getUserBytes();
        }

        public String getDisplayName() {
            return model.getDisplayName();
        }

        public byte[] getVersionResponse() {
            return Arrays.copyOf(versionResponse, versionResponse.length);
        }
    }

    public static final class ReadResult {
        private final TagInfo tagInfo;
        private final byte[] userMemory;

        private ReadResult(TagInfo tagInfo, byte[] userMemory) {
            this.tagInfo = tagInfo;
            this.userMemory = userMemory;
        }

        public TagInfo getTagInfo() {
            return tagInfo;
        }

        public byte[] getUserMemory() {
            return Arrays.copyOf(userMemory, userMemory.length);
        }
    }

    public static final class WriteResult {
        private final TagInfo tagInfo;
        private final byte[] verification;

        private WriteResult(TagInfo tagInfo, byte[] verification) {
            this.tagInfo = tagInfo;
            this.verification = verification;
        }

        public TagInfo getTagInfo() {
            return tagInfo;
        }

        public byte[] getVerification() {
            return Arrays.copyOf(verification, verification.length);
        }
    }

    public static final class PartialWriteException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int completedPages;

        private PartialWriteException(String message, int completedPages, Throwable cause) {
            super(message, cause);
            this.completedPages = completedPages;
        }

        public int getCompletedPages() {
            return completedPages;
        }

        public boolean tagMayBePartiallyChanged() {
            return completedPages > 0;
        }
    }

    /**
     * Detects a supported NTAG and reads exactly its user-memory area.
     */
    public static ReadResult readSupportedUserMemory(Tag tag) throws IOException {
        NfcA technology = requireTechnology(tag);
        try {
            connect(technology);
            TagInfo tagInfo = inspectConnectedTag(technology);
            byte[] memory = readBytes(technology, FIRST_USER_PAGE, tagInfo.getUserBytes());
            return new ReadResult(tagInfo, memory);
        } finally {
            closeQuietly(technology);
        }
    }

    /**
     * Performs all safe preflight checks, writes complete pages, and verifies only
     * the bytes that were actually written. The connection remains open for the
     * complete preflight/write/verify sequence to minimize the critical NFC window.
     */
    public static WriteResult writeAndVerifyUserMemory(Tag tag, byte[] data) throws IOException {
        validateWriteData(data);

        NfcA technology = requireTechnology(tag);
        int completedPages = 0;
        try {
            connect(technology);
            TagInfo tagInfo = inspectConnectedTag(technology);
            if (data.length > tagInfo.getUserBytes()) {
                throw new IOException("Die Spulendaten passen nicht in "
                        + tagInfo.getDisplayName() + ".");
            }

            int finalPage = FIRST_USER_PAGE + data.length / 4 - 1;
            preflightWritableRange(technology, tagInfo, FIRST_USER_PAGE, finalPage);

            // Verify that the complete target range can be read while the tag is still
            // unchanged. This catches removal/read protection before the first write.
            readBytes(technology, FIRST_USER_PAGE, data.length);

            for (int offset = 0; offset < data.length; offset += 4) {
                int page = FIRST_USER_PAGE + offset / 4;
                byte[] command = new byte[]{
                        (byte) WRITE_COMMAND,
                        (byte) page,
                        data[offset],
                        data[offset + 1],
                        data[offset + 2],
                        data[offset + 3]
                };
                byte[] response = technology.transceive(command);
                if (!isAck(response)) {
                    throw new IOException("Tag verweigert Schreibzugriff auf Seite " + page + ".");
                }
                completedPages++;
            }

            byte[] verification = readBytes(technology, FIRST_USER_PAGE, data.length);
            if (!Arrays.equals(data, verification)) {
                throw new IOException("Die Rückleseprüfung stimmt nicht mit den geschriebenen Daten überein.");
            }
            return new WriteResult(tagInfo, verification);
        } catch (IOException exception) {
            if (completedPages > 0) {
                throw new PartialWriteException(
                        "Der Schreibvorgang wurde nach " + completedPages
                                + " von " + (data.length / 4)
                                + " Seiten unterbrochen. Der Tag kann teilweise verändert sein. "
                                + safeMessage(exception),
                        completedPages,
                        exception);
            }
            throw exception;
        } finally {
            closeQuietly(technology);
        }
    }

    private static void preflightWritableRange(NfcA technology, TagInfo tagInfo,
                                                int firstPage, int lastPage) throws IOException {
        byte[] page2Block = readFourPages(technology, 2);
        byte[] dynamicLockBlock = readFourPages(technology,
                tagInfo.getModel().getDynamicLockPage());
        byte[] configBlock = readFourPages(technology,
                tagInfo.getModel().getConfigPage());

        String reason = findWriteProtectionReason(
                tagInfo, firstPage, lastPage, page2Block, dynamicLockBlock, configBlock);
        if (reason != null) {
            throw new IOException(reason);
        }
    }

    static String findWriteProtectionReason(TagInfo tagInfo, int firstPage, int lastPage,
                                            byte[] page2Block, byte[] dynamicLockBlock,
                                            byte[] configBlock) {
        if (tagInfo == null) {
            return "Tag-Typ konnte nicht bestimmt werden.";
        }
        if (firstPage < FIRST_USER_PAGE || lastPage < firstPage) {
            return "Ungültiger Schreibbereich.";
        }
        if (page2Block == null || page2Block.length < 4
                || dynamicLockBlock == null || dynamicLockBlock.length < 3
                || configBlock == null || configBlock.length < 8) {
            return "Lock- oder Konfigurationsdaten des Tags konnten nicht vollständig gelesen werden.";
        }

        int lockByte0 = page2Block[2] & 0xFF;
        int lockByte1 = page2Block[3] & 0xFF;
        for (int page = Math.max(firstPage, 4); page <= Math.min(lastPage, 15); page++) {
            boolean locked;
            if (page <= 7) {
                locked = (lockByte0 & (1 << page)) != 0;
            } else {
                locked = (lockByte1 & (1 << (page - 8))) != 0;
            }
            if (locked) {
                return "Seite " + page + " ist durch die statischen Lock-Bits schreibgeschützt.";
            }
        }

        for (int page = Math.max(firstPage, 16); page <= lastPage; page++) {
            int group = (page - 16) / 16;
            boolean locked;
            if (group < 8) {
                locked = (dynamicLockBlock[0] & (1 << group)) != 0;
            } else {
                int bit = group - 8;
                locked = bit < 6 && (dynamicLockBlock[1] & (1 << bit)) != 0;
            }
            if (locked) {
                int groupStart = 16 + group * 16;
                int groupEnd = Math.min(groupStart + 15,
                        FIRST_USER_PAGE + tagInfo.getUserBytes() / 4 - 1);
                return "Der Bereich Seite " + groupStart + " bis " + groupEnd
                        + " ist durch dynamische Lock-Bits schreibgeschützt.";
            }
        }

        // Configuration page byte 3 is AUTH0. Password verification is required
        // for writes starting at this page, independent of the PROT read setting.
        int auth0 = configBlock[3] & 0xFF;
        if (auth0 <= lastPage) {
            return "Der Schreibbereich ist ab Seite " + auth0
                    + " durch Passwortschutz geschützt.";
        }

        return null;
    }

    private static TagInfo inspectConnectedTag(NfcA technology) throws IOException {
        byte[] response = technology.transceive(new byte[]{(byte) GET_VERSION_COMMAND});
        return parseVersionResponse(response);
    }

    static TagInfo parseVersionResponse(byte[] response) throws IOException {
        if (response == null || response.length < 8) {
            throw new IOException("Tag-Typ konnte nicht über GET_VERSION bestimmt werden. "
                    + "Unterstützt werden NTAG215 und NTAG216.");
        }

        int header = response[0] & 0xFF;
        int vendorId = response[1] & 0xFF;
        int productType = response[2] & 0xFF;
        int productSubtype = response[3] & 0xFF;
        int majorVersion = response[4] & 0xFF;
        int minorVersion = response[5] & 0xFF;
        int storageSize = response[6] & 0xFF;
        int protocolType = response[7] & 0xFF;

        boolean ntag21xSignature = header == 0x00
                && vendorId == 0x04
                && productType == 0x04
                && productSubtype == 0x02
                && majorVersion == 0x01
                && minorVersion == 0x00
                && protocolType == 0x03;
        if (!ntag21xSignature) {
            throw new IOException("Der erkannte NFC-A-Tag ist kein unterstützter NTAG215/NTAG216.");
        }

        TagModel model;
        if (storageSize == 0x11) {
            model = TagModel.NTAG215;
        } else if (storageSize == 0x13) {
            model = TagModel.NTAG216;
        } else {
            throw new IOException("Nicht unterstützte NTAG21x-Speichergröße 0x"
                    + String.format(Locale.US, "%02X", storageSize)
                    + ". Unterstützt werden NTAG215 und NTAG216.");
        }
        return new TagInfo(model, Arrays.copyOf(response, 8));
    }

    private static byte[] readBytes(NfcA technology, int startPage, int byteCount)
            throws IOException {
        if (byteCount <= 0 || byteCount % 4 != 0) {
            throw new IllegalArgumentException("Leselänge muss ein positives Vielfaches von vier Bytes sein.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(byteCount);
        int page = startPage;
        while (output.size() < byteCount) {
            byte[] response = readFourPages(technology, page);
            int remaining = byteCount - output.size();
            output.write(response, 0, Math.min(response.length, remaining));
            page += 4;
        }
        return output.toByteArray();
    }

    private static byte[] readFourPages(NfcA technology, int page) throws IOException {
        byte[] response = technology.transceive(
                new byte[]{(byte) READ_COMMAND, (byte) page});
        if (response == null || response.length < 16) {
            throw new IOException("Unvollständige Antwort beim Lesen ab Seite " + page + ".");
        }
        return response;
    }

    private static void validateWriteData(byte[] data) {
        if (data == null || data.length == 0 || data.length % 4 != 0) {
            throw new IllegalArgumentException(
                    "Schreibdaten müssen ein nichtleeres Vielfaches von vier Bytes sein.");
        }
        if (data.length > NTAG216_USER_BYTES) {
            throw new IllegalArgumentException(
                    "Die Daten sind größer als der unterstützte NTAG216-Benutzerspeicher.");
        }
    }

    private static NfcA requireTechnology(Tag tag) throws IOException {
        if (tag == null) {
            throw new IOException("Kein NFC-Tag vorhanden.");
        }
        NfcA technology = NfcA.get(tag);
        if (technology == null) {
            throw new IOException("Der Tag stellt keine NFC-A-Technologie bereit.");
        }
        return technology;
    }

    private static void connect(NfcA technology) throws IOException {
        technology.connect();
        technology.setTimeout(IO_TIMEOUT_MS);
    }

    private static boolean isAck(byte[] response) {
        return response != null && response.length > 0 && (response[0] & 0x0F) == ACK;
    }

    private static void closeQuietly(NfcA technology) {
        try {
            technology.close();
        } catch (IOException ignored) {
            // Preserve the original exception, if any.
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable == null ? "Unbekannter NFC-Fehler." : throwable.getClass().getSimpleName();
        }
        return message;
    }

    public static String formatUid(Tag tag) {
        byte[] id = tag == null ? null : tag.getId();
        if (id == null || id.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(id.length * 2);
        for (byte value : id) {
            builder.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return builder.toString();
    }
}
