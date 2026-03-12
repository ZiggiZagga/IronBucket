#!/usr/bin/env bash
set -euo pipefail

JAVA_MAJOR="${1:-25}"

if command -v java >/dev/null 2>&1; then
  CURRENT_MAJOR="$(java -version 2>&1 | awk -F '[".]' '/version/ {print $2; exit}')"
  if [[ "$CURRENT_MAJOR" == "$JAVA_MAJOR" ]]; then
    echo "Java $JAVA_MAJOR is already available"
    java -version
    exit 0
  fi
fi

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) ARCH="x64" ;;
  aarch64|arm64) ARCH="aarch64" ;;
  *)
    echo "Unsupported architecture: $ARCH"
    exit 1
    ;;
esac

RUNNER_TEMP_DIR="${RUNNER_TEMP:-/tmp}"
JAVA_TAR="$RUNNER_TEMP_DIR/temurin-jdk-${JAVA_MAJOR}.tar.gz"
JAVA_HOME_DIR="$RUNNER_TEMP_DIR/temurin-jdk-${JAVA_MAJOR}"
DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/${JAVA_MAJOR}/ga/linux/${ARCH}/jdk/hotspot/normal/eclipse"

echo "Installing Temurin JDK ${JAVA_MAJOR} from Adoptium..."

for attempt in 1 2 3; do
  if curl -fsSL --retry 3 --retry-delay 2 "$DOWNLOAD_URL" -o "$JAVA_TAR"; then
    break
  fi

  if [[ "$attempt" == "3" ]]; then
    echo "Failed to download Java ${JAVA_MAJOR} after 3 attempts"
    exit 1
  fi

  sleep $((attempt * 5))
done

rm -rf "$JAVA_HOME_DIR"
mkdir -p "$JAVA_HOME_DIR"
tar -xzf "$JAVA_TAR" -C "$JAVA_HOME_DIR" --strip-components=1

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "JAVA_HOME=$JAVA_HOME_DIR" >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "$JAVA_HOME_DIR/bin" >> "$GITHUB_PATH"
fi

export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Java setup complete"
java -version
mvn -version
