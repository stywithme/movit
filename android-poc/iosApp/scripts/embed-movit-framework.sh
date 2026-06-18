#!/bin/sh
set -e

# Xcode does not load ~/.zshrc — source iosApp/.xcode.env for JAVA_HOME / ANDROID_HOME.
if [ -f "$SRCROOT/.xcode.env" ]; then
  # shellcheck disable=SC1091
  . "$SRCROOT/.xcode.env"
fi
if [ -f "$SRCROOT/.xcode.env.local" ]; then
  # shellcheck disable=SC1091
  . "$SRCROOT/.xcode.env.local"
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "error: Java 17 not found for Gradle. Install: brew install openjdk@17" >&2
  echo "  Or set JAVA_HOME in iosApp/.xcode.env.local" >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
if [ "$SDK_NAME" = "iphonesimulator" ] || [ "${SDK_NAME#iphonesimulator}" != "$SDK_NAME" ]; then
  export ARCHS="arm64"
fi
echo "Using JAVA_HOME=$JAVA_HOME"
cd "$SRCROOT/.."
./gradlew :feature:shell:embedAndSignAppleFrameworkForXcode
