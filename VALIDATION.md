# Validierungsbericht — Spool Maker Android 1.1.16

Datum: 2026-08-19

## Quellstand

- Paketname: `de.spoolmaker.android`
- `versionName`: `1.1.16`
- `versionCode`: `18`
- `minSdk`: 23
- `targetSdk`: 36
- `compileSdk`: 36
- Android Gradle Plugin: 9.3.0
- Gradle: 9.5.0
- Java-Quellziel: 17

## Durchgefuehrte lokale Pruefungen

- Alle XML-Dateien unter `app/src/main` sind wohlgeformt.
- Java-Quellen wurden mit `javac` auf Syntaxfehler gescannt. Erwartete Fehler
  wegen der in dieser Laufzeit fehlenden Android-SDK-Klassen wurden ignoriert;
  es wurden keine Java-Syntaxfehler gefunden.
- Verwendete eigene `R.id`, `R.string`, `R.color` und `R.drawable`-Referenzen
  wurden gegen die Projektressourcen abgeglichen.
- Der reine Java-NFC-Codec wurde kompiliert und `CodecSelfTest` erfolgreich
  ausgefuehrt.
- Codec-Selbsttest: 228 Byte Schreibimage, 888 Byte gelesener
  NTAG216-Benutzerspeicher, vier NDEF-Records, CRC-/Duplikatpruefungen und
  getrennte Gesamt-/Restmenge erfolgreich.
- Die Materialbibliothek liest weiterhin das bisherige JSON-Format und kann
  zusaetzlich die separat gespeicherten Gewichte aus
  `spool_maker_material_weights_v1` uebernehmen.

## Nicht durchgefuehrt

Ein vollstaendiger Android-Gradle-Build konnte in dieser Laufzeit nicht
abgeschlossen werden, weil keine Android-SDK-Installation vorhanden ist und der
Gradle-Bootstrap wegen fehlender DNS/Netzwerkauflosung die Distribution nicht
herunterladen konnte.

Der Projektstand ist deshalb fuer Android Studio vorbereitet, aber die erste
lokale Kompilierung auf dem eigenen Rechner bleibt ein wichtiger letzter Test.
