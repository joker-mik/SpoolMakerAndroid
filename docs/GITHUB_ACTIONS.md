# GitHub Actions

The workflows set up the build prerequisites instead of relying on tools that
happen to be present on the hosted runner:

- JDK 17
- Android command-line tools
- Android Platform 36
- Android Build Tools 36.0.0
- the repository's checksum-verified `./gradlew` bootstrap (Gradle 9.5.0)

CI runs on pushes to `main`, pull requests, and manual workflow dispatch. It
runs the standalone codec self-test, Android unit tests, Android Lint and the
debug APK build.

The release workflow runs on tags matching `v*`. Before signing it checks that
the tag exactly matches the `versionName` in `app/build.gradle`. It needs these
repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Do not commit the release keystore or `signing.properties`.
