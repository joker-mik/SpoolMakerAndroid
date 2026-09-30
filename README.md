# Spool Maker Android 1.2.4

Vollstaendiges Android-Studio-/Gradle-Projekt fuer eine Android-Portierung von
DA-Osbornes **Spool-Maker**. Die App liest und schreibt das von dem Upstream-
Projekt implementierte UltiMaker-kompatible NFC-Spulenformat auf NTAG215- und
NTAG216-Tags.

Upstream / technische Grundlage:
https://github.com/DA-Osborne/Spool-Maker

Aktuelles Release:
https://github.com/joker-mik/SpoolMakerAndroid/releases/tag/v1.2.4

Dieses Projekt ist Community-Software und kein offizielles Produkt von
UltiMaker oder DA-Osborne.

## Stand 1.2.4

Version 1.2.4 bringt die getestete Korrektur fuer die Statusleiste unter
Android 15+ (einschliesslich Motorola-Geraeten), modernisiert die
Sprachumschaltung auf Android-App-Sprachen und vereinfacht die Ergebnisansicht
beim Lesen eines Tags.

Ab Android 13 verwendet die App die Android-API fuer App-Sprachen
(`LocaleManager`); auf aelteren Android-Versionen bleibt die kompatible
Fallback-Implementierung erhalten. Zur Auswahl stehen
**System default / Systemkonfiguration**, **German / Deutsch** und
**English / Englisch**. Die Auswahl ist eindeutig, sodass immer nur genau eine
Sprache aktiv ist.

Die kompakte Ergebnisansicht zeigt jetzt Hersteller, Material, Farbe,
Spulen- und Restgewicht, Datum, Nutzungsdauer, Chip-UID und Material-GUID.
Unbekannte Material-GUIDs werden ausdruecklich als
**Material-GUID nicht in Datenbank** gekennzeichnet. Originale
UltiMaker-Tags zeigen ihren interpretierten Datumswert ebenfalls direkt an.

Enthalten sind unter anderem:

- Englische und deutsche UI-Lokalisierung mit Android-App-Sprachen ab
  Android 13 und kompatiblem Fallback auf aelteren Versionen.
- Eindeutige Sprachauswahl mit sofortiger Umschaltung der Oberflaechensprache.
- Android-15+-Statusleisten-Korrektur mit AndroidX `ProtectionLayout`, getestet
  auch auf Motorola-Hardware.
- Kompakte Ergebnisansicht mit Hersteller, Material, Farbe, Gewichten,
  Restprozent, Datum, Nutzungsdauer, Chip-UID und Material-GUID.
- Klarer Hinweis **Material-GUID nicht in Datenbank** fuer unbekannte
  Materialprofile.
- Datumsanzeige auch fuer originale UltiMaker-Tags.
- Aktualisierte F-Droid-Store-Beschreibungen und Screenshot-Galerien fuer
  Deutsch und Englisch.
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

Das NFC-Tag-Format und die Materialverarbeitung wurden durch diese
UI-, Sprach- und Darstellungsanpassungen nicht veraendert.

## Projekt oeffnen

1. ZIP entpacken oder das Repository klonen.
2. Den Projektordner in Android Studio oeffnen.
3. Android SDK Platform 36 installieren lassen, falls sie fehlt.
4. Gradle-Synchronisierung ausfuehren.
5. Ein echtes Android-Geraet mit NFC verwenden.

Die Build-Umgebung verwendet JDK 21. Der Java-Quellcode bleibt bewusst auf
Source-/Target-Kompatibilitaet 17 eingestellt. GitHub CI, GitHub Releases und
die F-Droid-Buildkonfiguration verwenden JDK 21.

Das Projekt verwendet den mitgelieferten, checksum-geprueften Gradle-Bootstrap.
Auf einem Rechner mit Internetzugang laedt dieser die in
`gradle/wrapper/gradle-wrapper.properties` konfigurierte Version.

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
versionName:   1.2.4
versionCode:   33
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

- `ci.yml` fuehrt mit JDK 21 Standalone-Codec-Test, Android-Unit-Tests, Lint
  und Debug-Build aus.
- `release.yml` verwendet JDK 21, reagiert auf Versions-Tags (`v*`), prueft,
  dass der Tag zur `versionName` passt, und baut danach eine signierte
  Release-APK.

Die Signierschluessel werden ausschliesslich ueber GitHub Secrets bereitgestellt
und gehoeren niemals ins Repository.

F-Droid-Store-Metadaten liegen unter `fastlane/metadata/android/`. Die
`fdroiddata`-Vorlage liegt unter
`fdroid/de.spoolmaker.android.yml.template` und ist fuer Version 1.2.4 /
versionCode 33 sowie JDK 21 vorbereitet.

Der aktuelle F-Droid-Merge-Request:
https://gitlab.com/fdroid/fdroiddata/-/merge_requests/47798
