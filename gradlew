#!/bin/sh
# Lightweight, checksum-verified Gradle bootstrap for this source package.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=9.5.0
GRADLE_SHA256=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
GRADLE_HOME_BASE=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_ROOT="$GRADLE_HOME_BASE/wrapper/dists/spoolmaker-gradle-$GRADLE_VERSION"
DIST_ZIP="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_BIN="$DIST_ROOT/gradle-$GRADLE_VERSION/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

verify_zip() {
    if command -v sha256sum >/dev/null 2>&1; then
        actual=$(sha256sum "$DIST_ZIP" | awk '{print $1}')
    elif command -v shasum >/dev/null 2>&1; then
        actual=$(shasum -a 256 "$DIST_ZIP" | awk '{print $1}')
    else
        echo "Neither sha256sum nor shasum is available." >&2
        exit 1
    fi
    if [ "$actual" != "$GRADLE_SHA256" ]; then
        echo "Gradle archive checksum mismatch." >&2
        rm -f "$DIST_ZIP"
        exit 1
    fi
}

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_ROOT"
    if [ ! -f "$DIST_ZIP" ]; then
        echo "Downloading Gradle $GRADLE_VERSION ..." >&2
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 3 --output "$DIST_ZIP" "$DIST_URL"
        elif command -v wget >/dev/null 2>&1; then
            wget --output-document="$DIST_ZIP" "$DIST_URL"
        else
            echo "Neither curl nor wget is available." >&2
            exit 1
        fi
    fi
    verify_zip
    if ! command -v unzip >/dev/null 2>&1; then
        echo "unzip is required to install Gradle." >&2
        exit 1
    fi
    rm -rf "$DIST_ROOT/gradle-$GRADLE_VERSION"
    unzip -q "$DIST_ZIP" -d "$DIST_ROOT"
fi

cd "$APP_HOME"
exec "$GRADLE_BIN" "$@"
