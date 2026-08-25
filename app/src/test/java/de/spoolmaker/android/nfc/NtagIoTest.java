/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.nfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

public final class NtagIoTest {
    private static final byte[] VERSION_215 = new byte[]{
            0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x11, 0x03
    };
    private static final byte[] VERSION_216 = new byte[]{
            0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x13, 0x03
    };
    private static final byte[] VERSION_216F = new byte[]{
            0x00, 0x04, 0x04, 0x04, 0x01, 0x00, 0x13, 0x03
    };
    private static final byte[] VERSION_213 = new byte[]{
            0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x0F, 0x03
    };

    @Test
    public void getVersionAcceptsNtag215AndNtag216() throws Exception {
        NtagIo.TagInfo ntag215 = NtagIo.parseVersionResponse(VERSION_215);
        NtagIo.TagInfo ntag216 = NtagIo.parseVersionResponse(VERSION_216);
        NtagIo.TagInfo ntag216f = NtagIo.parseVersionResponse(VERSION_216F);

        assertEquals(NtagIo.TagModel.NTAG215, ntag215.getModel());
        assertEquals(NtagIo.NTAG215_USER_BYTES, ntag215.getUserBytes());
        assertEquals(NtagIo.TagModel.NTAG216, ntag216.getModel());
        assertEquals(NtagIo.NTAG216_USER_BYTES, ntag216.getUserBytes());
        assertEquals(NtagIo.TagModel.NTAG216F, ntag216f.getModel());
        assertEquals(NtagIo.NTAG216_USER_BYTES, ntag216f.getUserBytes());
    }

    @Test
    public void getVersionRejectsOtherNtag21xSizes() {
        IOException exception = assertThrows(IOException.class,
                () -> NtagIo.parseVersionResponse(VERSION_213));
        assertTrue(exception.getMessage().contains("NTAG215 und NTAG216"));
    }


    @Test
    public void capabilityContainerFallbackRecognizesNtag215And216() throws Exception {
        byte[] cc215 = new byte[]{(byte) 0xE1, 0x10, 0x3E, 0x00};
        byte[] cc216 = new byte[]{(byte) 0xE1, 0x10, 0x6D, 0x00};

        NtagIo.TagInfo ntag215 = NtagIo.parseCapabilityContainer(cc215, null);
        NtagIo.TagInfo ntag216 = NtagIo.parseCapabilityContainer(cc216, null);

        assertEquals(NtagIo.TagModel.NTAG215, ntag215.getModel());
        assertEquals(NtagIo.NTAG215_USER_BYTES, ntag215.getUserBytes());
        assertEquals(NtagIo.TagModel.NTAG216, ntag216.getModel());
        assertEquals(NtagIo.NTAG216_USER_BYTES, ntag216.getUserBytes());
    }

    @Test
    public void capabilityContainerFallbackRejectsUnsupportedSize() {
        byte[] cc213 = new byte[]{(byte) 0xE1, 0x10, 0x12, 0x00};
        IOException exception = assertThrows(IOException.class,
                () -> NtagIo.parseCapabilityContainer(cc213, null));
        assertTrue(exception.getMessage().contains("Speichergröße"));
    }

    @Test
    public void unlockedTargetRangePassesPreflightModel() throws Exception {
        NtagIo.TagInfo info = NtagIo.parseVersionResponse(VERSION_215);
        byte[] page2 = new byte[16];
        byte[] dynamic = new byte[16];
        byte[] config = new byte[16];
        config[3] = (byte) 0xFF;

        assertNull(NtagIo.findWriteProtectionReason(info, 4, 60,
                page2, dynamic, config));
    }

    @Test
    public void staticLockForPageFourIsDetected() throws Exception {
        NtagIo.TagInfo info = NtagIo.parseVersionResponse(VERSION_215);
        byte[] page2 = new byte[16];
        page2[2] = 0x10;
        byte[] dynamic = new byte[16];
        byte[] config = new byte[16];
        config[3] = (byte) 0xFF;

        String reason = NtagIo.findWriteProtectionReason(info, 4, 60,
                page2, dynamic, config);
        assertTrue(reason.contains("Seite 4"));
    }

    @Test
    public void dynamicLockForPages48To63IsDetected() throws Exception {
        NtagIo.TagInfo info = NtagIo.parseVersionResponse(VERSION_216);
        byte[] page2 = new byte[16];
        byte[] dynamic = new byte[16];
        dynamic[0] = 0x04;
        byte[] config = new byte[16];
        config[3] = (byte) 0xFF;

        String reason = NtagIo.findWriteProtectionReason(info, 4, 60,
                page2, dynamic, config);
        assertTrue(reason.contains("Seite 48 bis 63"));
    }

    @Test
    public void passwordProtectionStartingInsideTargetIsDetected() throws Exception {
        NtagIo.TagInfo info = NtagIo.parseVersionResponse(VERSION_215);
        byte[] page2 = new byte[16];
        byte[] dynamic = new byte[16];
        byte[] config = new byte[16];
        config[3] = 60;

        String reason = NtagIo.findWriteProtectionReason(info, 4, 60,
                page2, dynamic, config);
        assertTrue(reason.contains("ab Seite 60"));
    }
}
