/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.util;

import de.spoolmaker.android.model.MaterialProfile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class CuraMaterialParser {
    public static final long MAX_INPUT_BYTES = 50L * 1024L;

    private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;
    private static final int MAX_XML_DEPTH = 64;
    private static final int MAX_START_TAGS = 20_000;
    private static final int MAX_FIELD_CHARS = 4096;

    public MaterialProfile parse(InputStream inputStream) throws IOException, XmlPullParserException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Kein XML-Datenstrom vorhanden.");
        }

        // Do not rely on optional XmlPullParser feature flags here. Android devices
        // can ship different XmlPull implementations and some of them reject calls
        // such as FEATURE_PROCESS_DOCDECL/FEATURE_VALIDATION as "unsupported feature".
        // Instead, read one bounded file, reject DTD/entity declarations ourselves,
        // and then use the parser with its normal, widely supported configuration.
        byte[] xml = readLimited(inputStream, MAX_INPUT_BYTES);
        rejectForbiddenDeclarations(xml);

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");

        String brand = "";
        String material = "";
        String description = "";
        String color = "";
        String guid = "";
        long spoolWeightMg = 0L;
        int startTags = 0;
        boolean rootSeen = false;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                startTags++;
                if (startTags > MAX_START_TAGS) {
                    throw new XmlPullParserException(
                            "Materialdatei enthält zu viele XML-Elemente.", parser, null);
                }
                if (parser.getDepth() > MAX_XML_DEPTH) {
                    throw new XmlPullParserException(
                            "Materialdatei ist zu tief verschachtelt.", parser, null);
                }

                String name = parser.getName();
                if (!rootSeen) {
                    if (parser.getDepth() != 1 || name == null
                            || !"fdmmaterial".equalsIgnoreCase(name)) {
                        throw new XmlPullParserException(
                                "Die Datei ist kein Cura/UltiMaker-fdmmaterial-Dokument.",
                                parser, null);
                    }
                    rootSeen = true;
                }
                if (name != null) {
                    String key = name.toLowerCase(Locale.US);
                    if ("brand".equals(key) && brand.isEmpty()) {
                        brand = safeNextText(parser);
                    } else if ("material".equals(key) && material.isEmpty()) {
                        material = safeNextText(parser);
                    } else if ("description".equals(key) && description.isEmpty()) {
                        description = safeNextText(parser);
                    } else if (("color".equals(key) || "colour".equals(key)
                            || "color_name".equals(key)) && color.isEmpty()) {
                        color = safeNextText(parser);
                    } else if ("guid".equals(key) && guid.isEmpty()) {
                        guid = safeNextText(parser);
                    } else if (spoolWeightMg == 0L && isDirectWeightTag(key)) {
                        spoolWeightMg = parseWeightMgOrZero(safeNextText(parser));
                    } else if (spoolWeightMg == 0L && "setting".equals(key)) {
                        String settingKey = parser.getAttributeValue(null, "key");
                        if (settingKey != null && settingKey.length() > MAX_FIELD_CHARS) {
                            throw new XmlPullParserException(
                                    "Ein XML-Attribut der Materialdatei ist ungewöhnlich lang.",
                                    parser, null);
                        }
                        if (isWeightSetting(settingKey)) {
                            spoolWeightMg = parseWeightMgOrZero(safeNextText(parser));
                        }
                    }
                }
            }
            event = parser.next();
        }

        if (!rootSeen) {
            throw new XmlPullParserException("Die Materialdatei enthält kein XML-Wurzelelement.");
        }

        if (material.isEmpty()) {
            material = description;
        }
        if (guid.isEmpty()) {
            throw new IllegalArgumentException("Die Datei enthält keine Material-GUID.");
        }
        if (brand.isEmpty() && material.isEmpty() && color.isEmpty()) {
            material = "Importiertes Material";
        }
        return new MaterialProfile(brand, material, color, guid, spoolWeightMg);
    }

    private static byte[] readLimited(InputStream inputStream, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException("Materialdatei ist größer als " + limit + " Byte.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void rejectForbiddenDeclarations(byte[] xml) throws XmlPullParserException {
        if (containsAsciiIgnoreCase(xml, "<!DOCTYPE")
                || containsAsciiIgnoreCase(xml, "<!ENTITY")) {
            throw new XmlPullParserException(
                    "DOCTYPE/DTD/ENTITY ist in Materialdateien nicht zugelassen.");
        }
    }

    private static boolean containsAsciiIgnoreCase(byte[] data, String needle) {
        if (data == null || needle == null || needle.isEmpty() || data.length < needle.length()) {
            return false;
        }
        for (int start = 0; start <= data.length - needle.length(); start++) {
            boolean match = true;
            for (int offset = 0; offset < needle.length(); offset++) {
                int actual = data[start + offset] & 0xFF;
                char expected = needle.charAt(offset);
                if (actual >= 'a' && actual <= 'z') {
                    actual -= ('a' - 'A');
                }
                if (expected >= 'a' && expected <= 'z') {
                    expected = (char) (expected - ('a' - 'A'));
                }
                if (actual != expected) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectWeightTag(String key) {
        return "weight".equals(key)
                || "spool_weight".equals(key)
                || "spoolweight".equals(key)
                || "material_spool_weight".equals(key)
                || "materialspoolweight".equals(key);
    }

    private static boolean isWeightSetting(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.US)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
        return "weight".equals(normalized)
                || "spool weight".equals(normalized)
                || "material spool weight".equals(normalized);
    }

    private static long parseWeightMgOrZero(String raw) {
        if (raw == null) {
            return 0L;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.endsWith("grams")) {
            value = value.substring(0, value.length() - 5).trim();
        } else if (value.endsWith("gram")) {
            value = value.substring(0, value.length() - 4).trim();
        } else if (value.endsWith("g")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            BigDecimal grams = new BigDecimal(value.replace(',', '.'));
            BigDecimal milligrams = grams.multiply(BigDecimal.valueOf(1000L))
                    .setScale(0, RoundingMode.HALF_UP);
            long result = milligrams.longValueExact();
            if (result <= 0 || result > MAX_UNSIGNED_INT) {
                return 0L;
            }
            return result;
        } catch (NumberFormatException | ArithmeticException exception) {
            return 0L;
        }
    }

    private static String safeNextText(XmlPullParser parser) throws IOException, XmlPullParserException {
        try {
            String text = parser.nextText();
            String cleaned = text == null ? "" : text.trim();
            if (cleaned.length() > MAX_FIELD_CHARS) {
                throw new XmlPullParserException(
                        "Ein Feld der Materialdatei ist ungewöhnlich lang.", parser, null);
            }
            return cleaned;
        } catch (IllegalStateException exception) {
            return "";
        }
    }
}
