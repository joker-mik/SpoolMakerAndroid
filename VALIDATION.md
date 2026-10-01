# Validierungsbericht — Spool Maker Android 1.2.4

## Dokumentationsabgleich am 2026-10-01

- Gepruefter `main`-Stand: `b34e98581d35088016da58fa998b762964447577`.
- GitHub-Release `v1.2.4` wurde am 2026-09-30 veroeffentlicht.
- `app/build.gradle` und `fdroid/de.spoolmaker.android.yml.template` enthalten
  `versionName` 1.2.4 / `versionCode` 33.
- Die deutschen und englischen Fastlane-Releasehinweise in
  `fastlane/metadata/android/{de-DE,en-US}/changelogs/33.txt` sind vorhanden
  und bilden die Grundlage fuer den Abschnitt 1.2.4 in `CHANGELOG.md`.

Dieser Abgleich betrifft nur die Dokumentation. Fuer 1.2.4 wurden dabei keine
Builds, automatisierten Tests oder Geraetetests ausgefuehrt. Die folgenden
Pruefergebnisse beziehen sich ausschliesslich auf den historischen Stand 1.2.0.

## Historischer Validierungsbericht — Spool Maker Android 1.2.0

Datum: 2026-08-25

### Quellstand

- Paketname: `de.spoolmaker.android`
- `versionName`: `1.2.0`
- `versionCode`: `29`
- `minSdk`: 23
- `targetSdk`: 36
- `compileSdk`: 36
- Android Gradle Plugin: 9.3.0
- Gradle-Bootstrap: 9.5.0
- Java-Quellziel: 17

### Durchgefuehrte lokale Pruefungen

- Alle 35 XML-Dateien unter `app/src/main` wurden als wohlgeformtes XML geparst.
- GitHub-Workflow-YAML und F-Droid-Vorlage wurden mit einem YAML-Parser geladen.
- `gradlew` wurde mit `sh -n` auf Shell-Syntax geprueft.
- Der reine Java-NFC-Codec wurde mit `javac -Xlint:all` kompiliert und der
  aktualisierte `CodecSelfTest` erfolgreich ausgefuehrt.
- Der Codec-Selbsttest prueft das 228-Byte-Schreibimage sowie 504-Byte-
  NTAG215- und 888-Byte-NTAG216-Speicherabbilder, NDEF-Layout, CRC,
  Signaturmarker und Datumscode.
- Die 17 Testmethoden aus `UltimakerTagCodecTest` und `NtagIoTest` wurden in
  dieser Umgebung zusaetzlich mit einem kleinen lokalen JUnit-kompatiblen
  Test-Harness ausgefuehrt; alle 17 liefen erfolgreich.
- `NtagIo.java` wurde mit minimalen Android-NFC-Stubs separat kompiliert. Ein
  zusaetzlicher Harness pruefte GET_VERSION-Erkennung fuer NTAG215/216 sowie
  statische Lock-Bits, dynamische Lock-Bits und AUTH0-Passwordschutz.
- `MaterialProfile.java` wurde separat mit `javac -Xlint:all` kompiliert.
- Alle Java-Hauptquellen wurden mit `javac` auf Parser-/Syntaxfehler gescannt.
  Die erwarteten Symbolfehler wegen fehlender Android-SDK-Klassen blieben
  bestehen; es wurden keine Java-Parserfehler gefunden.
- `NOTICE.md`, `LICENSE` und `page_license.xml` sind bytegleich zum vom Nutzer
  gelieferten Ausgangsstand. Die `license_*`-Strings blieben ebenfalls
  inhaltlich unveraendert; nur der separate Versionsstring wurde auf 1.2.0
  gesetzt.

### Nicht durchgefuehrt

Ein vollstaendiger Android-Gradle-Build, Android Lint und die echten Gradle-
JUnit-Tasks konnten in dieser Laufzeit nicht ausgefuehrt werden. Der
checksum-gepruefte Gradle-Bootstrap kann `services.gradle.org` wegen fehlender
DNS-Aufloesung nicht erreichen; ein Android SDK ist lokal ebenfalls nicht
vorhanden.

Die CI-Konfiguration fuehrt fuer Repository-Builds deshalb explizit
`CodecSelfTest`, `testDebugUnitTest`, `lintDebug` und `assembleDebug` aus. Der
Release-Workflow fuehrt den Standalone-Test sowie `testDebugUnitTest`,
`lintRelease` und `assembleRelease` aus und prueft zusaetzlich, dass der Git-Tag
zum `versionName` passt.
