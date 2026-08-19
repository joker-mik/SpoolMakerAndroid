/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.util;

import de.spoolmaker.android.model.MaterialProfile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class CuraMaterialParser {
    private static final long MAX_UNSIGNED_INT = 0xFFFF_FFFFL;

    public MaterialProfile parse(InputStream inputStream) throws IOException, XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(inputStream, "UTF-8");

        String brand = "";
        String material = "";
        String description = "";
        String color = "";
        String guid = "";
        long spoolWeightMg = 0L;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (name != null) {
                    String key = name.toLowerCase(Locale.US);
                    if ("brand".equals(key) && brand.isEmpty()) {
                        brand = safeNextText(parser);
                    } else if ("material".equals(key) && material.isEmpty()) {
                        material = safeNextText(parser);
                    } else if ("description".equals(key) && description.isEmpty()) {
                        description = safeNextText(parser);
                    } else if (("color".equals(key) || "colour".equals(key) || "color_name".equals(key)) && color.isEmpty()) {
                        color = safeNextText(parser);
                    } else if ("guid".equals(key) && guid.isEmpty()) {
                        guid = safeNextText(parser);
                    } else if (spoolWeightMg == 0L && isDirectWeightTag(key)) {
                        spoolWeightMg = parseWeightMgOrZero(safeNextText(parser));
                    } else if (spoolWeightMg == 0L && "setting".equals(key)) {
                        String settingKey = parser.getAttributeValue(null, "key");
                        if (isWeightSetting(settingKey)) {
                            spoolWeightMg = parseWeightMgOrZero(safeNextText(parser));
                        }
                    }
                }
            }
            event = parser.next();
        }

        if (material.isEmpty()) {
            material = description;
        }
        if (guid.isEmpty()) {
            throw new IllegalArgumentException("Die Datei enth\u00e4lt keine Material-GUID.");
        }
        if (brand.isEmpty() && material.isEmpty() && color.isEmpty()) {
            material = "Importiertes Material";
        }
        return new MaterialProfile(brand, material, color, guid, spoolWeightMg);
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
            return text == null ? "" : text.trim();
        } catch (IllegalStateException exception) {
            return "";
        }
    }
}
