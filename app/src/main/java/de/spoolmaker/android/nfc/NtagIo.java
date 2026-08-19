/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import android.nfc.Tag;
import android.nfc.tech.NfcA;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public final class NtagIo {
    public static final int FIRST_USER_PAGE = 4;
    public static final int NTAG216_USER_BYTES = 888;

    private static final int READ_COMMAND = 0x30;
    private static final int WRITE_COMMAND = 0xA2;
    private static final int ACK = 0x0A;

    private NtagIo() {
    }

    public static byte[] readUserMemory(Tag tag, int byteCount) throws IOException {
        if (byteCount <= 0 || byteCount > NTAG216_USER_BYTES) {
            throw new IllegalArgumentException("Ung\u00fcltige Leselaenge.");
        }
        NfcA technology = NfcA.get(tag);
        if (technology == null) {
            throw new IOException("Der Tag stellt keine NFC-A-Technologie bereit.");
        }

        try {
            technology.connect();
            technology.setTimeout(1500);
            ByteArrayOutputStream output = new ByteArrayOutputStream(byteCount);
            int page = FIRST_USER_PAGE;
            while (output.size() < byteCount) {
                byte[] response = technology.transceive(new byte[]{(byte) READ_COMMAND, (byte) page});
                if (response == null || response.length < 16) {
                    throw new IOException("Unvollstaendige Antwort beim Lesen von Seite " + page + ".");
                }
                int remaining = byteCount - output.size();
                output.write(response, 0, Math.min(response.length, remaining));
                page += 4;
            }
            return output.toByteArray();
        } finally {
            try {
                technology.close();
            } catch (IOException ignored) {
                // Preserve the original exception, if any.
            }
        }
    }

    public static void writeUserMemory(Tag tag, byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length % 4 != 0) {
            throw new IllegalArgumentException("Schreibdaten muessen ein nichtleeres Vielfaches von vier Bytes sein.");
        }
        if (data.length > NTAG216_USER_BYTES) {
            throw new IllegalArgumentException("Die Daten sind groesser als der NTAG216-Benutzerspeicher.");
        }

        NfcA technology = NfcA.get(tag);
        if (technology == null) {
            throw new IOException("Der Tag stellt keine NFC-A-Technologie bereit.");
        }

        try {
            technology.connect();
            technology.setTimeout(1500);

            int finalPage = FIRST_USER_PAGE + data.length / 4 - 1;
            int probePage = Math.max(FIRST_USER_PAGE, finalPage - 3);
            byte[] capacityProbe = technology.transceive(
                    new byte[]{(byte) READ_COMMAND, (byte) probePage});
            if (capacityProbe == null || capacityProbe.length < 16) {
                throw new IOException("Der Tag-Speicher ist f\u00fcr die Spulendaten zu klein.");
            }

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
                if (response == null || response.length == 0 || (response[0] & 0x0F) != ACK) {
                    throw new IOException("Tag verweigert Schreibzugriff auf Seite " + page + ".");
                }
            }
        } finally {
            try {
                technology.close();
            } catch (IOException ignored) {
                // Preserve the original exception, if any.
            }
        }
    }

    public static String formatUid(Tag tag) {
        byte[] id = tag.getId();
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
