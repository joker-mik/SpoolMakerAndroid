# GitHub Actions

The CI workflow deliberately sets up all build prerequisites instead of assuming
that they are present on the hosted runner:

- JDK 17
- Android command-line tools
- Android Platform 36
- Android Build Tools 36.0.0
- Gradle 9.5.0

CI runs on pushes to `main`, pull requests, and manual workflow dispatch.

The release workflow runs on tags matching `v*`. It needs these repository
secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Do not commit the release keystore or `signing.properties`.
