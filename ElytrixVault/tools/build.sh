#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="$1/bin/java"
ECJ_JAR="$2"
OUT=build/classes
STUBS=build/stubs
ARTIFACT="target/ElytrixVault-1.0.0.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS" target

find stubs -name '*.java' > build/stubs.txt
if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -source 17 -target 17   -encoding UTF-8 -d "$STUBS" @build/stubs.txt > build/stubs.log 2>&1; then
  grep -v "^----------" build/stubs.log | head -60
  echo "СБОРКА СТАБОВ НЕ УДАЛАСЬ"
  exit 1
fi

if [ -d src/main/java ] && [ -n "$(find src/main/java -name '*.java' -print -quit)" ]; then
  find src/main/java -name '*.java' > build/sources.txt
  if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -source 17 -target 17     -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt > build/ecj.log 2>&1; then
    grep -v "^----------" build/ecj.log | head -80
    echo "СБОРКА НЕ УДАЛАСЬ"
    exit 1
  fi
  cp -r src/main/resources/. "$OUT"/
  python3 tools/mkjar.py "$OUT" "${ARTIFACT}" src/main/resources
  ls -lh "${ARTIFACT}"
fi
