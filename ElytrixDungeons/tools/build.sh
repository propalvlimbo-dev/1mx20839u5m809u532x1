#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="$1/bin/java"
ECJ_JAR="$2"
OUT=build/classes
STUBS=build/stubs
ARTIFACT="target/ElytrixDungeons-1.0.0.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS" target

find stubs -name '*.java' | sort > build/stubs.txt
"$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -source 17 -target 17 \
  -encoding UTF-8 -d "$STUBS" @build/stubs.txt

find src/main/java -name '*.java' | sort > build/sources.txt
"$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -source 17 -target 17 \
  -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt

cp -r src/main/resources/. "$OUT"/
python3 tools/mkjar.py "$OUT" "${ARTIFACT}" src/main/resources
ls -lh "${ARTIFACT}"
