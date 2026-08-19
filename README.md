# Spool Maker Android 1.1.17

Vollstaendiges Android-Studio-/Gradle-Projekt fuer eine Android-Portierung von
DA-Osbornes **Spool-Maker**. Die App liest und schreibt das von dem Upstream-
Projekt implementierte UltiMaker-kompatible NFC-Spulenformat auf NTAG216-Tags.

Upstream / technische Grundlage:
https://github.com/DA-Osborne/Spool-Maker

Dieses Projekt ist Community-Software und kein offizielles Produkt von
UltiMaker oder DA-Osborne.

## Stand 1.1.17

Der Quellstand fasst die bisher separat gepatchten Funktionen wieder in einem
normal kompilierbaren Android-Projekt zusammen. Es gibt keine handgebauten
zusatzlichen DEX-Dateien mehr.

Enthalten sind unter anderem:

- NFC-Lesen und -Schreiben ueber den NFC-A-Reader-Mode von Android.
- Vollstaendiges Lesen des 888-Byte-Benutzerspeichers eines NTAG216.
- Dekodierung von Material-, Signatur- und beiden Statusrecords.
- Gesamtmenge, Restmenge, Nutzungsdauer, CRC-8, GUID, Batch, Station und weitere
  Felder werden ausgelesen.
- Schreibvorgang mit bytegenauer Ruecklesepruefung.
- Cura/UltiMaker-`.xml.fdm_material`-Import.
- Spulengewicht wird beim Import aus `weight` bzw. kompatiblen Gewichtsfeldern
  uebernommen und lokal gespeichert.
- Im Schreibbereich wird das Material-Spulengewicht vorbelegt; Gesamtmenge und
  Restmenge bleiben vor dem Schreiben editierbar.
- Migration der separat gespeicherten Gewichte aus den App-Versionen 1.1.9 bis
  1.1.15 (`spool_maker_material_weights_v1`).
- Materialbibliothek als mehrzeilige Liste mit Symbolbuttons fuer Hinzufuegen,
  Bearbeiten und Loeschen.
- Materialdialog mit beschrifteten Feldern fuer Hersteller, Material, Farbe,
  GUID und Spulengewicht.
- NFC-Bereitschaft wird als Dialog mit Abbrechen angezeigt statt als dauerhafte
  Statusbox.
- Seitliches Menue mit Materialeinstellungen, Info und Lizenz; die Version steht
  fest am unteren Rand.
- Info und Lizenz sind eigene Seiten mit Zurueck-Navigation statt Popups.
- Android-Zurueck-Taste/-Geste schliesst zuerst NFC-Dialog, Menue, Detailbereich
  oder Unterseite. Erst auf der Hauptseite wird die Activity beendet.
- Einheitlich blau eingefaerbte App-Leisten; grosser dunkelblauer Lesen-/Schreiben-
  Button am unteren Rand.

## Projekt oeffnen

1. ZIP entpacken.
2. Den Projektordner in Android Studio oeffnen.
3. Android SDK Platform 36 installieren lassen, falls sie fehlt.
4. Gradle-Synchronisierung ausfuehren.
5. Ein echtes Android-Geraet mit NFC verwenden.

Das Projekt ist auf Java 17 eingestellt und verwendet den mitgelieferten
Gradle-Bootstrap. Auf einem Rechner mit Internetzugang laedt dieser die in
`gradle/wrapper/gradle-wrapper.properties` konfigurierte Gradle-Version.

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
versionName:   1.1.17
versionCode:   27
```

Der private Update-Schluessel ist **nicht** im Quellarchiv enthalten.

Wenn du den bereits separat bereitgestellten PKCS12-Update-Schluessel verwenden
willst, kopiere `signing.properties.example` nach `signing.properties` und trage
dort Pfad und Kennwoerter ein. Danach:

```bash
./gradlew assembleRelease
```

Ohne `signing.properties` wird der Release-Build nicht mit diesem Update-
Schluessel signiert. Eine normal erzeugte Debug-APK kann daher nicht ueber die
bereits signierte App installiert werden.

## Codec-Selbsttest ohne Android SDK

Der reine NFC-Codec hat keine Android-Abhaengigkeit:

```bash
mkdir -p out
javac -d out \
  app/src/main/java/de/spoolmaker/android/nfc/UltimakerTagCodec.java \
  tools/CodecSelfTest.java
java -cp out CodecSelfTest
```

## Ordnerstruktur

```text
app/src/main/java/de/spoolmaker/android/
  MainActivity.java
  model/MaterialProfile.java
  nfc/NtagIo.java
  nfc/UltimakerTagCodec.java
  storage/MaterialStore.java
  util/CuraMaterialParser.java

app/src/main/res/
  layout/
  drawable/
  values/
  raw/gpl_3.txt
```

## Hinweise zum Tag-Schreiben

Zum ersten Test einen entbehrlichen, wiederbeschreibbaren NTAG216 verwenden.
Die App schreibt nur den Benutzerspeicher ab NFC-Seite 4; Hersteller-, Lock-,
Passwort- und Konfigurationsseiten werden nicht beschrieben.

## Lizenz

GPL-3.0-or-later. Siehe `LICENSE` und `NOTICE.md`.


## GitHub Actions und F-Droid

Dieses Repository enthaelt Workflows unter `.github/workflows/`:

- `ci.yml` baut und testet bei Pushes und Pull Requests eine Debug-APK.
- `release.yml` baut bei einem Git-Tag wie `v1.1.17` eine signierte Release-APK
  und legt sie als GitHub Release ab. Die Signierschluessel werden ausschliesslich
  ueber GitHub Secrets bereitgestellt und gehoeren niemals ins Repository.

F-Droid-Store-Metadaten liegen unter `fastlane/metadata/android/`. Eine Vorlage
fuers spaetere `fdroiddata`-Merge-Request liegt unter
`fdroid/de.spoolmaker.android.yml.template`.
