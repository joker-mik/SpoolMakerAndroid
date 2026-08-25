# Changelog

## 1.2.0

- Initiale, gehaertete 1.2-Fassung.
- Lizenz-, Herkunfts- und Aenderungshinweise aus dem aktualisierten 1.1.18-Stand inhaltlich beibehalten.
- NTAG215 und NTAG216 werden vor dem Schreiben explizit per `GET_VERSION` erkannt; andere NTAG21x-Typen werden abgewiesen.
- Tagkapazitaet wird beim Lesen beruecksichtigt: 504 Byte fuer NTAG215, 888 Byte fuer NTAG216.
- Vor dem ersten Schreibbyte werden statische/dynamische Lock-Bits und Passwortschutz des Zielbereichs geprueft.
- Schreibdialog bleibt bis nach der Ruecklesepruefung aktiv; Teilabbrueche werden als potenziell teilweise geschriebener Tag gemeldet.
- Ruecklesepruefung nach dem Schreiben auf die tatsaechlich geschriebenen 228 Byte begrenzt.
- Gesamtintegritaet prueft jetzt CRC, UID/Serienfeld, Record-Anzahlen, Signaturmarker und das erwartete NDEF-Layout gemeinsam.
- Materialimport in einen Hintergrund-Executor verschoben und gegen uebergrosse/zu komplexe XML-Dateien sowie DTD/DOCTYPE gehaertet.
- Materialbibliothek speichert eine letzte gueltige JSON-Sicherung und behandelt beschaedigten Primarbestand nicht mehr still als leer.
- Sichtbare Status-/Fehlermeldungen von Textpraefix-Heuristiken entkoppelt.
- Standalone-Codec-Selbsttest aktualisiert; CI verwendet den Projekt-Bootstrap und fuehrt Unit-Tests, Lint und Build aus.
- Versions- und Formatdokumentation auf `1.2.0` / `versionCode 29` synchronisiert.
## 1.1.18

- Lizenz- und Herkunftshinweise fuer den Android-Port praezisiert.
- Upstream-Copyright von Dale Osborne und relevanter Aenderungsstand dokumentiert.
- Lizenzseite optisch ueberarbeitet: kompakte Karten fuer Herkunft, Aenderungen, Lizenz und Quellcode.
- Vollstaendiger GPL-v3-Text bleibt direkt aus der Lizenzseite erreichbar.
- Versionsangaben in App-Ressourcen, README, F-Droid-Vorlage und Release-Dokumentation auf 1.1.18 / 28 vereinheitlicht.

## 1.1.17

- Stabiler oeffentlicher Stand auf Basis des getesteten 1.1.16-fix8-UI.
- Startabsturz durch falschen ImageButton-Cast behoben.
- Systemleisten-Inset-Behandlung fuer moderne Android-Versionen.
- Android-Zurueck-Navigation aus Materialbibliothek, Info und Lizenz zum Hauptfenster.
- Ueberarbeitetes Hauptlayout mit Tab-Icons, aktiver Unterstreichung und kompakter Icon/Text-Anordnung.
- GitHub-Actions-Workflows fuer CI und signierte Releases vorbereitet.
- F-Droid/fastlane-Metadaten vorbereitet.

## 1.1.16-fix3

- Systemleisten-Abstaende fuer Status- und Navigationsleiste wieder aktiviert.
- Android-Zurueck schliesst Materialbibliothek, Info und Lizenz und kehrt zum Hauptfenster zurueck.
- Fix aus 1.1.16-fix2 fuer ImageButton-Typen bleibt erhalten.

## 1.1.16-fix2

- Fix startup `ClassCastException`: `buttonEdit` and `buttonDelete` are `ImageButton` views and are now bound as `ImageButton` in `MainActivity`.

## 1.1.16

- Consolidated the app back into a normal, full Android source project instead
  of APK/DEX overlay patches.
- Unified blue top bars for the main screen, drawer, material library, Info and
  License pages.
- Made the bottom Read/Write action 64 dp high and dark blue.
- Added reliable back navigation for Android 13+ with OnBackInvokedDispatcher,
  while retaining onBackPressed for older Android versions.
- Back now closes the NFC prompt, drawer, detail panel or secondary page before
  finishing the Activity.
- Kept the version as a fixed footer in the drawer.
- Added vector icons for drawer and material-library actions.
- Kept multi-row material library and labelled material editor.
- Kept spool-weight import, editable total/remaining amounts and separate NFC
  status values.
- Added migration from the separate 1.1.9-1.1.15 material-weight preferences.
