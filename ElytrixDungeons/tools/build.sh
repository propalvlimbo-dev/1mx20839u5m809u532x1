#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JDK_DIR="$1"
ECJ_JAR="${2:-}"
OUT=build/classes
STUBS=build/stubs
ARTIFACT="target/ElytrixDungeons-1.0.0.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS" target

find stubs -name '*.java' | sort > build/stubs.txt
if [ -n "$ECJ_JAR" ] && [ -f "$ECJ_JAR" ]; then
  "$JDK_DIR/bin/java" -jar "$ECJ_JAR" -nowarn -source 17 -target 17 \
    -encoding UTF-8 -d "$STUBS" @build/stubs.txt
else
  "$JDK_DIR/bin/javac" -nowarn --release 17 \
    -encoding UTF-8 -d "$STUBS" @build/stubs.txt
fi

find src/main/java -name '*.java' | sort > build/sources.txt
if [ -n "$ECJ_JAR" ] && [ -f "$ECJ_JAR" ]; then
  "$JDK_DIR/bin/java" -jar "$ECJ_JAR" -nowarn -source 17 -target 17 \
    -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt
else
  "$JDK_DIR/bin/javac" -nowarn --release 17 \
    -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt
fi

cp -r src/main/resources/. "$OUT"/
python3 tools/mkjar.py "$OUT" "${ARTIFACT}" src/main/resources
ls -lh "${ARTIFACT}"
