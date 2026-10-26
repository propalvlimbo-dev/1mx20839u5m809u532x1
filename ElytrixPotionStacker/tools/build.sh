#!/usr/bin/env bash
# Сборка ElytrixPotionStacker без Maven: ECJ + упаковщик (в песочнице нет JDK).
#   bash tools/build.sh <java-runtime> <ecj.jar>
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="$1/bin/java"
ECJ_JAR="$2"
OUT=build/classes
STUBS=build/stubs
VERSION=$(awk '/^version:/{gsub(/[^0-9.]/,"",$2); print $2; exit}' src/main/resources/plugin.yml)
ARTIFACT="ElytrixPotionStacker-${VERSION}.jar"

rm -rf build
mkdir -p "$OUT" "$STUBS"

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

cp -r src/main/resources/. "$OUT"/
python3 tools/mkjar.py "$OUT" "${ARTIFACT}" src/main/resources
if [ -d schem ]; then
  python3 - "$PWD/${ARTIFACT}" "$PWD/schem" <<'PYEOF'
import os, sys, zipfile
jar, folder = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar, "a", zipfile.ZIP_DEFLATED) as z:
    for name in sorted(os.listdir(folder)):
        path = os.path.join(folder, name)
        if os.path.isfile(path):
            z.write(path, "schem/" + name)
PYEOF
fi
ls -l "${ARTIFACT}"
