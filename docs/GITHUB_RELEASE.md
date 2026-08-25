# GitHub-Release einrichten

## 1. Einmalig: privaten Release-Schluessel lokal erzeugen

Der private Schluessel darf niemals in Git eingecheckt werden.

Beispiel mit dem JDK-Tool `keytool`:

```bash
keytool -genkeypair \
  -keystore spoolmaker-release.p12 \
  -storetype PKCS12 \
  -alias spoolmaker-release \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

Die Datei `spoolmaker-release.p12` sicher offline sichern. Bei Verlust kann eine
mit diesem Schluessel installierte App nicht mehr mit demselben Paketnamen
aktualisiert werden.

## 2. Keystore als Base64 fuer GitHub Secrets vorbereiten

Linux/macOS:

```bash
base64 < spoolmaker-release.p12 | tr -d '\n'
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("spoolmaker-release.p12"))
```

## 3. GitHub Repository Secrets anlegen

Repository -> Settings -> Secrets and variables -> Actions -> New repository secret

Diese vier Secrets werden von `.github/workflows/release.yml` erwartet:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## 4. Release bauen

Die Version in `app/build.gradle` muss zum Tag passen. Fuer diesen Stand:

- `versionName '1.2.0'`
- `versionCode 29`
- Tag `v1.2.0`

Danach:

```bash
git tag v1.2.0
git push origin v1.2.0
```

Der Workflow baut die signierte APK und legt automatisch einen GitHub Release
mit APK und SHA-256-Datei an.
