/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.util;

import de.spoolmaker.android.model.MaterialProfile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class CuraMaterialParser {
    public static final long MAX_INPUT_BYTES = 2L * 1024L * 1024L;

    private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;
    private static final int MAX_XML_DEPTH = 64;
    private static final int MAX_START_TAGS = 20_000;
    private static final int MAX_FIELD_CHARS = 4096;
    private static final String FEATURE_DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";

    public MaterialProfile parse(InputStream inputStream) throws IOException, XmlPullParserException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Kein XML-Datenstrom vorhanden.");
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        hardenFactory(factory);

        XmlPullParser parser = factory.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        parser.setFeature(XmlPullParser.FEATURE_VALIDATION, false);
        parser.setInput(new LimitedInputStream(inputStream, MAX_INPUT_BYTES), "UTF-8");

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
            if (event == XmlPullParser.DOCDECL) {
                throw new XmlPullParserException(
                        "DOCTYPE/DTD ist in Materialdateien nicht zugelassen.", parser, null);
            }
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
            event = parser.nextToken();
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

    private static void hardenFactory(XmlPullParserFactory factory) throws XmlPullParserException {
        // Android's security guidance recommends rejecting DTD declarations. Some
        // XmlPull implementations do not expose the Apache feature; in that case
        // FEATURE_PROCESS_DOCDECL=false plus the explicit DOCDECL check above is
        // the portable fallback.
        try {
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        } catch (XmlPullParserException unsupportedFeature) {
            factory.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        }
        factory.setFeature(XmlPullParser.FEATURE_VALIDATION, false);
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

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long consumed;

        LimitedInputStream(InputStream inputStream, long limit) {
            super(inputStream);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increment(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                increment(count);
            }
            return count;
        }

        private void increment(long count) throws IOException {
            consumed += count;
            if (consumed > limit) {
                throw new IOException("Materialdatei ist größer als " + limit + " Byte.");
            }
        }
    }
}
