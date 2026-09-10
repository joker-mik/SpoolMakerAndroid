/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

final class LocaleHelper {
    static final String LANGUAGE_SYSTEM = "system";
    static final String LANGUAGE_GERMAN = "de";
    static final String LANGUAGE_ENGLISH = "en";

    private static final String PREFS_NAME = "spool_maker_ui";
    private static final String KEY_LANGUAGE = "language";
    private static final String DEFAULT_LANGUAGE = LANGUAGE_SYSTEM;

    private LocaleHelper() {
    }

    static Context wrap(Context base) {
        String selection = getLanguage(base);
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

    static String getLanguage(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String language = preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        return isSupported(language) ? language : DEFAULT_LANGUAGE;
    }

    static void setLanguage(Context context, String language) {
        String safeLanguage = isSupported(language) ? language : DEFAULT_LANGUAGE;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, safeLanguage)
                .apply();
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
