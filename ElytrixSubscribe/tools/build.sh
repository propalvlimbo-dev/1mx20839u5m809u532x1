#!/usr/bin/env bash
# Сборка ElytrixSubscribe без Maven: ECJ + свой упаковщик (в песочнице нет JDK).
#   bash tools/build.sh <java-runtime> <ecj.jar>
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="$1/bin/java"
ECJ_JAR="$2"
OUT=build/classes
STUBS=build/stubs
VERSION=$(grep -m1 "version:" src/main/resources/plugin.yml | sed "s/.*'\(.*\)'.*/\1/")

rm -rf build
mkdir -p "$OUT" "$STUBS"

# 1) стабы (Bukkit/Adventure/VirtualEntityApi) — только чтобы ECJ знал API,
#    в jar они не попадают: сервер даёт настоящие классы сам.
find stubs -name '*.java' > build/stubs.txt
if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -proceedOnError -source 21 -target 21 \
  -encoding UTF-8 -d "$STUBS" @build/stubs.txt > build/stubs.log 2>&1; then
  grep -v "^----------" build/stubs.log | head -40
  echo "СБОРКА СТАБОВ НЕ УДАЛАСЬ"
  exit 1
fi

find src/main/java -name '*.java' > build/sources.txt
if ! "$JAVA_BIN" -jar "$ECJ_JAR" -nowarn -proceedOnError -source 21 -target 21 \
  -encoding UTF-8 -cp "$STUBS" -d "$OUT" @build/sources.txt > build/ecj.log 2>&1; then
  grep -v "^----------" build/ecj.log | head -60
  echo "СБОРКА НЕ УДАЛАСЬ"
  exit 1
fi
grep -E "^[0-9]+\. ERROR" build/ecj.log | head -5 || true

cp src/main/resources/plugin.yml src/main/resources/config.yml "$OUT"/
python3 tools/mkjar.py "$OUT" "ElytrixSubscribe-${VERSION}.jar" src/main/resources
ls -l "ElytrixSubscribe-${VERSION}.jar"
