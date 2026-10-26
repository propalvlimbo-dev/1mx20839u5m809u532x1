#!/usr/bin/env bash
# Сборка ElytrixCasino без Maven: ECJ + упаковщик.
#   bash tools/build.sh <java-runtime> <ecj.jar>
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="$1/bin/java"
ECJ_JAR="$2"
OUT=build/classes
STUBS=build/stubs
ARTIFACT="target/ElytrixCasino.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS" target

find stubs -name '*.java' > build/stubs.txt
if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -proceedOnError -source 17 -target 17 \
  -encoding UTF-8 -d "$STUBS" @build/stubs.txt > build/stubs.log 2>&1; then
  grep -v "^----------" build/stubs.log | head -50
  echo "СБОРКА СТАБОВ НЕ УДАЛАСЬ"
  exit 1
fi

find src/main/java -name '*.java' > build/sources.txt
if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -proceedOnError -source 17 -target 17 \
  -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt > build/ecj.log 2>&1; then
  grep -v "^----------" build/ecj.log | head -60
  echo "СБОРКА НЕ УДАЛАСЬ"
  exit 1
fi
grep -E "^[0-9]+\. ERROR" build/ecj.log | head -10 || true

cp -r src/main/resources/. "$OUT"/
python3 tools/mkjar.py "$OUT" "${ARTIFACT}" src/main/resources
ls -lh "${ARTIFACT}"
