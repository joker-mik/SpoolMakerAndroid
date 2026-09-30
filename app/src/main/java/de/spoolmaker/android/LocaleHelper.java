/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class LocaleHelper {
    static final String LANGUAGE_SYSTEM = "system";
    static final String LANGUAGE_GERMAN = "de";
    static final String LANGUAGE_ENGLISH = "en";

    private static final String PREFS_NAME = "spool_maker_ui";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FRAMEWORK_LOCALE_MIGRATED = "framework_locale_migrated";
    private static final String DEFAULT_LANGUAGE = LANGUAGE_SYSTEM;

    private LocaleHelper() {
    }

    static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = base.getSystemService(LocaleManager.class);
            SharedPreferences preferences = preferences(base);
            if (localeManager != null
                    && (!localeManager.getApplicationLocales().isEmpty()
                    || preferences.getBoolean(KEY_FRAMEWORK_LOCALE_MIGRATED, false))) {
                // Android 13+ applies the per-app locale to the Context itself.
                return base;
            }
        }

        // Legacy path for Android 12L and older. It is also used once on the first
        // Android 13+ launch after upgrading from a version that stored only our
        // SharedPreferences language setting, so the old choice is not lost.
        String selection = storedLanguage(base);
        Locale locale;
        if (LANGUAGE_SYSTEM.equals(selection)) {
            locale = supportedSystemLocale();
        } else if (LANGUAGE_GERMAN.equals(selection)) {
            locale = Locale.GERMAN;
        } else {
            locale = Locale.ENGLISH;
        }

        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }

    static void migrateLegacySelectionToFramework(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        SharedPreferences preferences = preferences(context);
        if (preferences.getBoolean(KEY_FRAMEWORK_LOCALE_MIGRATED, false)) {
            return;
        }

        LocaleManager localeManager = context.getSystemService(LocaleManager.class);
        if (localeManager == null) {
            return;
        }

        LocaleList currentLocales = localeManager.getApplicationLocales();
        String stored = storedLanguage(context);

        // Mark the one-time migration before calling setApplicationLocales(), as
        // that call can recreate the Activity immediately.
        preferences.edit()
                .putBoolean(KEY_FRAMEWORK_LOCALE_MIGRATED, true)
                .apply();

        if (currentLocales.isEmpty()) {
            if (!LANGUAGE_SYSTEM.equals(stored)) {
                localeManager.setApplicationLocales(LocaleList.forLanguageTags(stored));
            }
            return;
        }

        String frameworkLanguage = explicitLanguage(currentLocales.get(0).getLanguage());
        preferences.edit()
                .putString(KEY_LANGUAGE,
                        frameworkLanguage == null ? LANGUAGE_SYSTEM : frameworkLanguage)
                .apply();
    }

    static String getLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                LocaleList locales = localeManager.getApplicationLocales();
                if (!locales.isEmpty()) {
                    String language = explicitLanguage(locales.get(0).getLanguage());
                    return language == null ? LANGUAGE_SYSTEM : language;
                }
                if (preferences(context).getBoolean(KEY_FRAMEWORK_LOCALE_MIGRATED, false)) {
                    return LANGUAGE_SYSTEM;
                }
            }
        }
        return storedLanguage(context);
    }

    static void setLanguage(Context context, String language) {
        String safeLanguage = isSupported(language) ? language : DEFAULT_LANGUAGE;
        SharedPreferences.Editor editor = preferences(context)
                .edit()
                .putString(KEY_LANGUAGE, safeLanguage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            editor.putBoolean(KEY_FRAMEWORK_LOCALE_MIGRATED, true);
        }
        editor.apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                LocaleList locales = LANGUAGE_SYSTEM.equals(safeLanguage)
                        ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(safeLanguage);
                localeManager.setApplicationLocales(locales);
            }
        }
    }

    static int choiceIndex(String language) {
        if (LANGUAGE_SYSTEM.equals(language)) {
            return 0;
        }
        if (LANGUAGE_GERMAN.equals(language)) {
            return 1;
        }
        return 2;
    }

    static String languageForChoice(int choice) {
        if (choice == 0) {
            return LANGUAGE_SYSTEM;
        }
        if (choice == 1) {
            return LANGUAGE_GERMAN;
        }
        return LANGUAGE_ENGLISH;
    }

    static boolean isGerman(Context context) {
        return LANGUAGE_GERMAN.equals(primaryLocale(
                context.getResources().getConfiguration()).getLanguage());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String storedLanguage(Context context) {
        String language = preferences(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        return isSupported(language) ? language : DEFAULT_LANGUAGE;
    }

    private static String explicitLanguage(String language) {
        if (LANGUAGE_GERMAN.equals(language)) {
            return LANGUAGE_GERMAN;
        }
        if (LANGUAGE_ENGLISH.equals(language)) {
            return LANGUAGE_ENGLISH;
        }
        return null;
    }

    private static Locale supportedSystemLocale() {
        Locale systemLocale = primaryLocale(Resources.getSystem().getConfiguration());
        String language = systemLocale.getLanguage();
        if (LANGUAGE_GERMAN.equals(language) || LANGUAGE_ENGLISH.equals(language)) {
            return systemLocale;
        }
        return Locale.ENGLISH;
    }

    private static boolean isSupported(String language) {
        return LANGUAGE_SYSTEM.equals(language)
                || LANGUAGE_GERMAN.equals(language)
                || LANGUAGE_ENGLISH.equals(language);
    }

    @SuppressWarnings("deprecation")
    private static Locale primaryLocale(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return configuration.getLocales().isEmpty()
                    ? Locale.ENGLISH
                    : configuration.getLocales().get(0);
        }
        return configuration.locale == null ? Locale.ENGLISH : configuration.locale;
    }
}
