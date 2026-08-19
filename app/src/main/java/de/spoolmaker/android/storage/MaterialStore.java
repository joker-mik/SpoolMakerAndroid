/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.storage;

import android.content.Context;
import android.content.SharedPreferences;

import de.spoolmaker.android.model.MaterialProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MaterialStore {
    private static final String PREFS_NAME = "spool_maker_materials";
    private static final String KEY_MATERIALS = "materials_v1";

    private final SharedPreferences preferences;
    private final SharedPreferences legacyWeightPreferences;

    public MaterialStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        legacyWeightPreferences = context.getSharedPreferences(
                "spool_maker_material_weights_v1", Context.MODE_PRIVATE);
    }

    public synchronized List<MaterialProfile> getAll() {
        List<MaterialProfile> profiles = load();
        Collections.sort(profiles, new Comparator<MaterialProfile>() {
            @Override
            public int compare(MaterialProfile left, MaterialProfile right) {
                return String.CASE_INSENSITIVE_ORDER.compare(
                        left.getDisplayName(), right.getDisplayName());
            }
        });
        return profiles;
    }

    public synchronized MaterialProfile findByGuid(String guid) {
        if (guid == null) {
            return null;
        }
        String normalized;
        try {
            normalized = MaterialProfile.normalizeGuid(guid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        for (MaterialProfile profile : load()) {
            if (profile.getGuid().equals(normalized)) {
                return profile;
            }
        }
        return null;
    }

    public synchronized void upsert(MaterialProfile profile) {
        List<MaterialProfile> profiles = load();
        for (int index = profiles.size() - 1; index >= 0; index--) {
            if (profiles.get(index).getGuid().equals(profile.getGuid())) {
                profiles.remove(index);
            }
        }
        profiles.add(profile);
        save(profiles);
    }

    public synchronized void remove(String guid) {
        String normalized = MaterialProfile.normalizeGuid(guid);
        List<MaterialProfile> profiles = load();
        for (int index = profiles.size() - 1; index >= 0; index--) {
            if (profiles.get(index).getGuid().equals(normalized)) {
                profiles.remove(index);
            }
        }
        save(profiles);
        legacyWeightPreferences.edit().remove(normalized).apply();
    }

    private List<MaterialProfile> load() {
        List<MaterialProfile> result = new ArrayList<>();
        String raw = preferences.getString(KEY_MATERIALS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                try {
                    String guid = item.optString("guid", "");
                    long storedWeight = Math.max(0L, item.optLong("spoolWeightMg", 0L));
                    if (storedWeight == 0L) {
                        try {
                            String normalized = MaterialProfile.normalizeGuid(guid);
                            storedWeight = Math.max(0L, legacyWeightPreferences.getLong(normalized, 0L));
                        } catch (IllegalArgumentException ignored) {
                            // The constructor below will reject an invalid GUID.
                        }
                    }
                    result.add(new MaterialProfile(
                            item.optString("brand", ""),
                            item.optString("material", ""),
                            item.optString("color", ""),
                            guid,
                            storedWeight
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Ignore damaged entries instead of making the whole library unusable.
                }
            }
        } catch (JSONException ignored) {
            // A damaged preference is treated as an empty library.
        }
        return result;
    }

    private void save(List<MaterialProfile> profiles) {
        JSONArray array = new JSONArray();
        for (MaterialProfile profile : profiles) {
            JSONObject item = new JSONObject();
            try {
                item.put("brand", profile.getBrand());
                item.put("material", profile.getMaterial());
                item.put("color", profile.getColor());
                item.put("guid", profile.getGuid());
                item.put("spoolWeightMg", profile.getSpoolWeightMg());
                array.put(item);
            } catch (JSONException exception) {
                throw new IllegalStateException("Materialbibliothek konnte nicht serialisiert werden.", exception);
            }
        }
        preferences.edit().putString(KEY_MATERIALS, array.toString()).apply();

        SharedPreferences.Editor legacyEditor = legacyWeightPreferences.edit();
        for (MaterialProfile profile : profiles) {
            if (profile.getSpoolWeightMg() > 0) {
                legacyEditor.putLong(profile.getGuid(), profile.getSpoolWeightMg());
            } else {
                legacyEditor.remove(profile.getGuid());
            }
        }
        legacyEditor.apply();
    }
}
