/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MaterialProfile {
    private final String brand;
    private final String material;
    private final String color;
    private final String guid;
    private final long spoolWeightMg;

    public MaterialProfile(String brand, String material, String color, String guid) {
        this(brand, material, color, guid, 0L);
    }

    public MaterialProfile(String brand, String material, String color, String guid, long spoolWeightMg) {
        this.brand = clean(brand);
        this.material = clean(material);
        this.color = clean(color);
        this.guid = normalizeGuid(guid);
        if (spoolWeightMg < 0 || spoolWeightMg > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Spulengewicht liegt ausserhalb des Tagformats.");
        }
        this.spoolWeightMg = spoolWeightMg;
    }

    public String getBrand() {
        return brand;
    }

    public String getMaterial() {
        return material;
    }

    public String getColor() {
        return color;
    }

    public String getGuid() {
        return guid;
    }

    public long getSpoolWeightMg() {
        return spoolWeightMg;
    }

    public String getDisplayName() {
        List<String> parts = new ArrayList<>();
        if (!brand.isEmpty()) {
            parts.add(brand);
        }
        if (!material.isEmpty()) {
            parts.add(material);
        }
        if (!color.isEmpty()) {
            parts.add(color);
        }
        if (parts.isEmpty()) {
            return guid;
        }
        StringBuilder displayName = new StringBuilder();
        for (String part : parts) {
            if (displayName.length() > 0) {
                displayName.append(" - ");
            }
            displayName.append(part);
        }
        return displayName.toString();
    }

    public static String normalizeGuid(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Die Material-GUID fehlt.");
        }
        String normalized = value.trim();
        if (normalized.startsWith("{") && normalized.endsWith("}") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.regionMatches(true, 0, "urn:uuid:", 0, 9)) {
            normalized = normalized.substring(9);
        }
        try {
            return UUID.fromString(normalized).toString().toLowerCase(Locale.US);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Die Material-GUID ist ung\u00fcltig.", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
