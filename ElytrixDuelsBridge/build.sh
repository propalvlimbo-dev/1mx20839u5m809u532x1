#!/usr/bin/env bash
# Сборка ElytrixDuelsBridge без Gradle/Maven.
# Использование: bash build.sh <JAVA_HOME> [ecj.jar]
# ECJ необязателен: если он не указан, используется javac из JDK.
# В архиве: plugin.yml, config.yml и классы плагина (стабы не попадают).
set -euo pipefail
JAVA_HOME="${1:?укажи каталог JDK}"
ECJ="${2:-}"
cd "$(dirname "$0")"
PY="${PYTHON:-python3}"

rm -rf build/classes build/out
mkdir -p build/classes build/out

mapfile -d '' STUBS < <(find stubs -name '*.java' -print0)
mapfile -d '' SOURCES < <(find src -name '*.java' -print0)

if [[ -n "$ECJ" ]]; then
  # 1. compile-only Bukkit/Adventure stubs
  "$JAVA_HOME/bin/java" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -17 -nowarn -encoding UTF-8 -proc:none -d build/classes "${STUBS[@]}"

  # 2. plugin sources
  "$JAVA_HOME/bin/java" -cp "$ECJ:build/classes" org.eclipse.jdt.internal.compiler.batch.Main \
    -17 -nowarn -encoding UTF-8 -proc:none -cp build/classes -d build/out "${SOURCES[@]}"
else
  # The project is self-contained against its compile-only stubs, so plain JDK works.
  "$JAVA_HOME/bin/javac" --release 17 -encoding UTF-8 -proc:none \
    -d build/classes "${STUBS[@]}"
  "$JAVA_HOME/bin/javac" --release 17 -encoding UTF-8 -proc:none \
    -cp build/classes -d build/out "${SOURCES[@]}"
fi

# 3. package the plugin resources and compiled classes; stubs are not included
if [ -x "$JAVA_HOME/bin/jar" ]; then
  "$JAVA_HOME/bin/jar" --create --file ElytrixDuelsBridge-1.12.5.jar \
    -C resources . -C build/out .
  echo "готово: $(pwd)/ElytrixDuelsBridge-1.12.5.jar"
else
  "$PY" tools/mkjar.py build/out ElytrixDuelsBridge-1.12.5.jar resources
fi
