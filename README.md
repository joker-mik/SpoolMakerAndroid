# Spool Maker Android 1.2.2

Vollstaendiges Android-Studio-/Gradle-Projekt fuer eine Android-Portierung von
DA-Osbornes **Spool-Maker**. Die App liest und schreibt das von dem Upstream-
Projekt implementierte UltiMaker-kompatible NFC-Spulenformat auf NTAG215- und
NTAG216-Tags.

Upstream / technische Grundlage:
https://github.com/DA-Osborne/Spool-Maker

Aktuelles Release:
https://github.com/joker-mik/SpoolMakerAndroid/releases/tag/v1.2.2

Dieses Projekt ist Community-Software und kein offizielles Produkt von
UltiMaker oder DA-Osborne.

## Stand 1.2.2

Version 1.2.2 erweitert den gehaerteten 1.2-Stand um eine englische und deutsche
Benutzeroberflaeche. Die App verwendet standardmaessig die Systemsprache. Ist
die Systemsprache Deutsch, wird die deutsche Oberflaeche verwendet; bei
Englisch die englische. Fuer alle anderen Systemsprachen dient Englisch als
Fallback.

Die Sprache kann jederzeit ueber das Seitenmenue geaendert werden. Die
Sprachauswahl ist wie Materialbibliothek, Info und Lizenz als eigene Seite mit
Zurueck-Navigation umgesetzt. Zur Auswahl stehen **System default / Systemkonfiguration**,
**German / Deutsch** und **English / Englisch**.

Enthalten sind unter anderem:

- Englische und deutsche UI-Lokalisierung mit systemabhaengiger Vorauswahl und
  englischem Fallback fuer nicht unterstuetzte Systemsprachen.
- Vollbild-Sprachauswahl mit Zurueck-Pfeil und sofortiger Umschaltung der
  Oberflaechensprache.
- NFC-Lesen und -Schreiben ueber den NFC-A-Reader-Mode von Android.
- Explizite Tag-Erkennung per `GET_VERSION`; zugelassen werden NTAG215 und
  NTAG216.
- Vollstaendiges Lesen des erkannten Benutzerspeichers: 504 Byte bei NTAG215,
  888 Byte bei NTAG216.
- Vor dem Schreiben werden statische Lock-Bits, dynamische Lock-Bits und
  Passwortschutz fuer den Zielbereich geprueft.
- Der Schreibdialog bleibt waehrend Schreiben und Verifikation aktiv und warnt
  davor, den Tag zu entfernen.
- Nach dem Schreiben werden zuerst exakt die 228 geschriebenen Byte rueckgelesen
  und bytegenau sowie semantisch verifiziert.
- Ein abgebrochener Schreibvorgang meldet ausdruecklich, wenn der Tag bereits
  teilweise veraendert worden sein kann.
- Dekodierung und Konsistenzpruefung von Material-, Signatur- und beiden
  Statusrecords inklusive CRC-8, UID/Serienfeld, Signaturmarker und erwartetem
  Vier-Record-NDEF-Layout.
- Cura/UltiMaker-`.xml.fdm_material`-Import im Hintergrund mit Begrenzungen fuer
  Dateigroesse, Dateianzahl und XML-Komplexitaet sowie Ablehnung von DTD/DOCTYPE.
- Spulengewicht wird beim Import aus `weight` bzw. kompatiblen Gewichtsfeldern
  uebernommen und lokal gespeichert.
- Die Materialbibliothek behaelt beim Speichern eine letzte gueltige JSON-
  Sicherung und ueberschreibt eine beschaedigte Bibliothek nicht stillschweigend.
- Migration der separat gespeicherten Gewichte aus den App-Versionen 1.1.9 bis
  1.1.15 (`spool_maker_material_weights_v1`).
- Materialbibliothek als mehrzeilige Liste mit Symbolbuttons fuer Hinzufuegen,
  Bearbeiten und Loeschen.
- NFC-Bereitschaft und Schreibfortschritt werden sichtbar angezeigt.
- Materialbibliothek, Sprache, Info und Lizenz sind eigene Seiten mit
  Zurueck-Navigation.
- Die Lizenzseite zeigt Herkunft, Copyright, Aenderungsstand, Quellcode und den
  vollstaendigen GPL-Text.

Das NFC-Tag-Format und die Materialverarbeitung wurden durch die
Sprachunterstuetzung nicht veraendert.

## Projekt oeffnen

1. ZIP entpacken oder das Repository klonen.
2. Den Projektordner in Android Studio oeffnen.
3. Android SDK Platform 36 installieren lassen, falls sie fehlt.
4. Gradle-Synchronisierung ausfuehren.
5. Ein echtes Android-Geraet mit NFC verwenden.

Das Projekt ist auf Java 17 eingestellt und verwendet den mitgelieferten,
checksum-geprueften Gradle-Bootstrap. Auf einem Rechner mit Internetzugang laedt
dieser die in `gradle/wrapper/gradle-wrapper.properties` konfigurierte Version.

### Debug-APK

```bash
./gradlew assembleDebug
```

Ausgabe normalerweise unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release-APK / Update ueber eine vorhandene Installation

Android akzeptiert ein Update nur mit demselben Paketnamen, einem hoeheren
`versionCode` und demselben Signaturzertifikat. Dieser Quellstand verwendet:

```text
applicationId: de.spoolmaker.android
versionName:   1.2.2
versionCode:   31
```

Der `versionCode` wird ueber Releases hinweg fortlaufend erhoeht, damit Android
vorhandene Installationen als aktualisierbar erkennt.

Der private Update-Schluessel ist **nicht** im Repository enthalten. Fuer einen
signierten Release-Build `signing.properties.example` nach
`signing.properties` kopieren, Pfad und Kennwoerter eintragen und danach:

```bash
./gradlew assembleRelease
```

Das offizielle GitHub-Release wird ueber GitHub Actions mit dem dort hinterlegten
Release-Schluessel signiert.

## Codec-Selbsttest ohne Android SDK

Der reine NFC-Codec hat keine Android-Abhaengigkeit:

```bash
mkdir -p out
javac -d out \
  app/src/main/java/de/spoolmaker/android/nfc/UltimakerTagCodec.java \
  tools/CodecSelfTest.java
java -cp out CodecSelfTest
```

Der Selbsttest prueft sowohl NTAG215- als auch NTAG216-grosse Speicherabbilder,
Record-Layout, CRC und Signaturmarker.

## Ordnerstruktur

```text
app/src/main/java/de/spoolmaker/android/
  LocaleHelper.java
  MainActivity.java
  model/MaterialProfile.java
  nfc/NtagIo.java
  nfc/UltimakerTagCodec.java
  storage/MaterialStore.java
  util/CuraMaterialParser.java

app/src/main/res/
  layout/
  drawable/
  values/       # englische Standard-Ressourcen
  values-de/    # deutsche Ressourcen
  raw/gpl_3.txt
```

## Hinweise zum Tag-Schreiben

Zum ersten Test einen entbehrlichen, wiederbeschreibbaren NTAG215 oder NTAG216
verwenden. Die Implementierung unterstuetzt beide Tag-Groessen; der
Standalone-Selbsttest deckt beide Speicherabbild-Groessen ab. NTAG215 wurde vom
Projektbetreiber bisher nicht auf echter Hardware getestet.

Die App schreibt 228 Byte ab NFC-Seite 4. Hersteller-, Lock-, Passwort- und
Konfigurationsseiten werden nur gelesen, nicht beschrieben.

Mehrseitige NFC-EEPROM-Schreibvorgaenge sind nicht atomar. Wird ein Tag waehrend
des Schreibens aus dem Feld entfernt, kann er teilweise veraendert sein. Die App
haelt den Schreibdialog deshalb bis zum Ende der Ruecklesepruefung offen und
weist im Fehlerfall auf diesen Zustand hin.

## Datenschutz und Berechtigungen

Die App arbeitet lokal auf dem Geraet. Sie enthaelt keine Internet-Berechtigung,
keine Tracker, Analytics, Telemetrie, Werbung oder Benutzerkonten. Fuer die
Kernfunktion werden NFC sowie die im Manifest deklarierten, nicht gefaehrlichen
Systemfunktionen verwendet; gefaehrliche Android-Laufzeitberechtigungen werden
nicht angefordert.

## Lizenz

GPL-3.0-or-later. Siehe `LICENSE` und `NOTICE.md`.

## GitHub Actions und F-Droid

Dieses Repository enthaelt Workflows unter `.github/workflows/`:

- `ci.yml` fuehrt Standalone-Codec-Test, Android-Unit-Tests, Lint und Debug-Build
  aus.
- `release.yml` reagiert auf Versions-Tags (`v*`), prueft, dass der Tag zur
  `versionName` passt, und baut danach eine signierte Release-APK.

Die Signierschluessel werden ausschliesslich ueber GitHub Secrets bereitgestellt
und gehoeren niemals ins Repository.

F-Droid-Store-Metadaten liegen unter `fastlane/metadata/android/`. Eine Vorlage
fuers spaetere `fdroiddata`-Merge-Request liegt unter
`fdroid/de.spoolmaker.android.yml.template`.
