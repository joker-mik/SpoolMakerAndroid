# Selbst kompilieren

## Android Studio

1. Projektordner oeffnen.
2. JDK 17 auswaehlen.
3. SDK Platform 36 installieren lassen.
4. Gradle Sync ausfuehren.
5. `app` auf einem NFC-faehigen Telefon starten.

## Kommandozeile

Debug:

```bash
./gradlew assembleDebug
```

Tests und Lint wie in CI:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release mit bestehendem Update-Schluessel:

1. `signing.properties.example` nach `signing.properties` kopieren.
2. Pfad, Alias und Kennwoerter des separat aufbewahrten PKCS12-Schluessels
   eintragen.
3. `./gradlew assembleRelease` ausfuehren.

Der private Signierschluessel gehoert nicht in Git und ist deshalb im
Quellarchiv nicht enthalten.
