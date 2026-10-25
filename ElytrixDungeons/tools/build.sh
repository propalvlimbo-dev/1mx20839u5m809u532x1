#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JDK_DIR="${1:?Usage: tools/build.sh <java-home> [ecj.jar]}"
ECJ_JAR="${2:-}"
JAVA_RELEASE="${JAVA_RELEASE:-21}"
OUT=build/classes
STUBS=build/stubs
ARTIFACT="target/ElytrixDungeons-1.0.0.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS" target

if [ -n "$ECJ_JAR" ] && [ -f "$ECJ_JAR" ]; then
  # Eclipse JDT's compiler is a Java main class even when the bundle's
  # manifest points at a different application entry point.
  compile() {
    "$JDK_DIR/bin/java" -cp "$ECJ_JAR" org.eclipse.jdt.internal.compiler.batch.Main "$@"
  }
  compile_stubs() {
    compile -nowarn -source "$JAVA_RELEASE" -target "$JAVA_RELEASE" \
      -encoding UTF-8 -d "$STUBS" @build/stubs.txt
  }
  compile_sources() {
    compile -nowarn -source "$JAVA_RELEASE" -target "$JAVA_RELEASE" \
      -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt
  }
else
  compile_stubs() {
    "$JDK_DIR/bin/javac" -nowarn --release "$JAVA_RELEASE" \
      -encoding UTF-8 -d "$STUBS" @build/stubs.txt
  }
  compile_sources() {
    "$JDK_DIR/bin/javac" -nowarn --release "$JAVA_RELEASE" \
      -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt
  }
fi

find stubs -name '*.java' | sort > build/stubs.txt
compile_stubs

find src/main/java -name '*.java' | sort > build/sources.txt
compile_sources

cp -r src/main/resources/. "$OUT"/
python3 tools/mkjar.py "$OUT" "$ARTIFACT" src/main/resources
ls -lh "$ARTIFACT"
