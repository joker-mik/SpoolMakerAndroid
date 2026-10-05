# Spool Maker Android 1.2.4

Complete Android Studio / Gradle project for an Android port of
DA-Osborne's **Spool-Maker**. The app reads and writes the UltiMaker-compatible
NFC spool format implemented by the upstream project on NTAG215 and NTAG216
tags.

Upstream / technical basis:
https://github.com/DA-Osborne/Spool-Maker

Current GitHub release:
https://github.com/joker-mik/SpoolMakerAndroid/releases/tag/v1.2.4

## Install

Spool Maker is available from the official F-Droid repository:

https://f-droid.org/en/packages/de.spoolmaker.android/

F-Droid is the recommended way to install the app if you want repository-based
updates. The signed APK is also available from GitHub Releases.

This project is community software and is not an official product of UltiMaker
or DA-Osborne.

## Version 1.2.4

Version 1.2.4 includes the tested status bar fix for Android 15+ (including
Motorola devices), modernizes language switching to use Android per-app
languages, and simplifies the result view when reading a tag.

On Android 13 and newer, the app uses Android's per-app language API
(`LocaleManager`). Older Android versions continue to use the compatible
fallback implementation. Available choices are
**System default / Systemkonfiguration**, **German / Deutsch**, and
**English / Englisch**. The selection is exclusive, so exactly one language
option is active at a time.

The compact result view now shows manufacturer, material, color, spool weight,
remaining weight, date, usage duration, chip UID, and material GUID.
Unknown material GUIDs are explicitly shown as **Material GUID not in
database**. Original UltiMaker tags also display their interpreted date value
directly.

Included features include:

- English and German UI localization using Android per-app languages on
  Android 13+ with a compatible fallback on older versions.
- Exclusive language selection with immediate UI language switching.
- Android 15+ status bar fix using AndroidX `ProtectionLayout`, also tested on
  Motorola hardware.
- Compact result view with manufacturer, material, color, weights, remaining
  percentage, date, usage duration, chip UID, and material GUID.
- Clear **Material GUID not in database** message for unknown material
  profiles.
- Date display for original UltiMaker tags.
- Updated F-Droid store descriptions and screenshot galleries for English and
  German.
- NFC reading and writing using Android's NFC-A reader mode.
- Explicit tag detection via `GET_VERSION`; NTAG215 and NTAG216 are supported.
- Full reading of the detected user memory: 504 bytes on NTAG215 and 888 bytes
  on NTAG216.
- Static lock bits, dynamic lock bits, and password protection for the target
  area are checked before writing.
- The write dialog remains active during writing and verification and warns the
  user not to remove the tag.
- After writing, exactly the 228 written bytes are read back and verified both
  byte-for-byte and semantically.
- If a write operation is interrupted, the app explicitly warns that the tag
  may already have been partially modified.
- Decoding and consistency checks for material, signature, and both status
  records, including CRC-8, UID/serial field, signature marker, and the
  expected four-record NDEF layout.
- Background import of Cura/UltiMaker `.xml.fdm_material` files with limits for
  file size, file count, and XML complexity, plus rejection of DTD/DOCTYPE.
- Spool weight is imported from `weight` or compatible weight fields and stored
  locally.
- The material library keeps the last valid JSON backup when saving and does
  not silently overwrite a corrupted library.
- Migration of separately stored weights from app versions 1.1.9 through
  1.1.15 (`spool_maker_material_weights_v1`).
- Material library shown as a multi-line list with icon buttons for adding,
  editing, and deleting entries.
- Visible NFC readiness and write progress indicators.
- Material library, language, info, and license are separate pages with back
  navigation.
- The license page shows origin, copyright, modification status, source code,
  and the complete GPL text.

The NFC tag format and material processing were not changed by these UI,
language, and presentation updates.

## Opening the project

1. Extract the ZIP archive or clone the repository.
2. Open the project folder in Android Studio.
3. Install Android SDK Platform 36 if it is not already available.
4. Run Gradle sync.
5. Use a real Android device with NFC.

The build environment uses JDK 21. The Java source code intentionally remains
configured for source/target compatibility 17. GitHub CI, GitHub Releases, and
the F-Droid build configuration use JDK 21.

The project uses the included checksum-verified Gradle bootstrap. On a machine
with internet access, it downloads the version configured in
`gradle/wrapper/gradle-wrapper.properties`.

### Debug APK

```bash
./gradlew assembleDebug
```

The output is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK / updating an existing installation

Android accepts an update only when it uses the same package name, a higher
`versionCode`, and the same signing certificate. This source version uses:

```text
applicationId: de.spoolmaker.android
versionName:   1.2.4
versionCode:   33
```

The `versionCode` is increased continuously across releases so Android can
recognize existing installations as upgradeable.

The private update key is **not** included in the repository. To create a
signed release build, copy `signing.properties.example` to
`signing.properties`, enter the path and passwords, and then run:

```bash
./gradlew assembleRelease
```

The official GitHub release is signed through GitHub Actions using the release
key stored there.

## Codec self-test without Android SDK

The NFC codec itself has no Android dependency:

```bash
mkdir -p out
javac -d out \
  app/src/main/java/de/spoolmaker/android/nfc/UltimakerTagCodec.java \
  tools/CodecSelfTest.java
java -cp out CodecSelfTest
```

The self-test covers both NTAG215- and NTAG216-sized memory images, record
layout, CRC, and signature markers.

## Project structure

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
  values/       # English default resources
  values-de/    # German resources
  raw/gpl_3.txt
```

## Notes on writing tags

For initial testing, use a disposable, rewritable NTAG215 or NTAG216. The
implementation supports both tag sizes, and the standalone self-test covers
both memory-image sizes. NTAG215 has not yet been tested on real hardware by
the project maintainer.

The app writes 228 bytes starting at NFC page 4. Manufacturer, lock, password,
and configuration pages are read only and are not modified.

Multi-page NFC EEPROM write operations are not atomic. If a tag is removed from
the field during writing, it may be left partially modified. The app therefore
keeps the write dialog open until read-back verification has completed and
warns about this state if an error occurs.

## Privacy and permissions

The app works locally on the device. It has no internet permission, trackers,
analytics, telemetry, advertising, or user accounts. The core functionality
uses NFC and the non-dangerous system features declared in the manifest;
dangerous Android runtime permissions are not requested.

## License

GPL-3.0-or-later. See `LICENSE` and `NOTICE.md`.

## GitHub Actions and F-Droid

This repository contains workflows under `.github/workflows/`:

- `ci.yml` uses JDK 21 and runs the standalone codec self-test, Android unit
  tests, lint, and a debug build.
- `release.yml` uses JDK 21, reacts to version tags (`v*`), verifies that the
  tag matches `versionName`, and then builds a signed release APK.

Signing keys are provided exclusively through GitHub Secrets and are never
stored in the repository.

F-Droid store metadata is located under `fastlane/metadata/android/`. The
local `fdroiddata` template is located at
`fdroid/de.spoolmaker.android.yml.template`.

Spool Maker is published in the official F-Droid repository:

https://f-droid.org/en/packages/de.spoolmaker.android/

The current F-Droid release is version 1.2.4 (versionCode 33).
