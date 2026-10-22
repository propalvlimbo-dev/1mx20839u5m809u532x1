#!/usr/bin/env bash
# Сборка ElytrixDuelsBridge без Gradle/Maven.
# Использование: bash build.sh <JAVA_HOME> <ecj.jar>
#
# В архиве: plugin.yml, config.yml и классы плагина (стабы не попадают).
set -euo pipefail
JAVA_HOME="${1:?укажи каталог JRE/JDK}"
ECJ="${2:?укажи путь к ecj.jar}"
cd "$(dirname "$0")"
PY="${PYTHON:-python3}"

rm -rf build/classes build/out
mkdir -p build/classes build/out

# 1. стабы Bukkit — только чтобы скомпилировать, в jar не попадают
"$JAVA_HOME/bin/java" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -17 -nowarn -encoding UTF-8 -proc:none -d build/classes \
  $(find stubs -name '*.java')

# 2. сам плагин
"$JAVA_HOME/bin/java" -cp "$ECJ:build/classes" org.eclipse.jdt.internal.compiler.batch.Main \
  -17 -nowarn -encoding UTF-8 -proc:none -cp build/classes -d build/out \
  $(find src -name '*.java')

# 3. упаковка: jar из JDK, иначе python-скрипт
if [ -x "$JAVA_HOME/bin/jar" ]; then
  "$JAVA_HOME/bin/jar" --create --file ElytrixDuelsBridge-1.12.3.jar \
    -C resources . -C build/out .
  echo "готово: $(pwd)/ElytrixDuelsBridge-1.12.3.jar"
else
  "$PY" tools/mkjar.py build/out ElytrixDuelsBridge-1.12.3.jar resources
fi
