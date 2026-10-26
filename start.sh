#!/usr/bin/env bash
set -Eeuo pipefail

# Стартовый скрипт для Paper/Spigot 1.21.11; требуется Java 21 или новее.
# RAM=10G задаёт одинаковые Xms/Xmx: оставьте ОС и JVM-native память сверх этого объёма.
# Пример: RAM=8G ./start.sh

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$SCRIPT_DIR"

JAVA_BIN="${JAVA_BIN:-java}"
SERVER_JAR="${SERVER_JAR:-server.jar}"
RAM="${RAM:-10G}"
RESTART_DELAY="${RESTART_DELAY:-5}"
TIMEZONE="${TIMEZONE:-Europe/Moscow}"
ENABLE_VECTOR_API="${ENABLE_VECTOR_API:-1}"
GC_LOGGING="${GC_LOGGING:-0}"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    printf 'Ошибка: Java не найдена (JAVA_BIN=%s). Установите Java 21+ или задайте JAVA_BIN.\n' "$JAVA_BIN" >&2
    exit 127
fi

if [[ ! -r "$SERVER_JAR" ]]; then
    printf 'Ошибка: JAR-сервер не найден или недоступен: %s\n' "$SERVER_JAR" >&2
    exit 1
fi

if [[ ! "$RAM" =~ ^[1-9][0-9]*[kKmMgGtT]?$ ]]; then
    printf 'Ошибка: RAM должна быть числом с единицей, например 8G или 10240M; получено: %s\n' "$RAM" >&2
    exit 2
fi

if [[ ! "$RESTART_DELAY" =~ ^[0-9]+$ ]]; then
    printf 'Ошибка: RESTART_DELAY должен быть целым числом секунд; получено: %s\n' "$RESTART_DELAY" >&2
    exit 2
fi

if [[ "$ENABLE_VECTOR_API" != "0" && "$ENABLE_VECTOR_API" != "1" ]]; then
    printf 'Ошибка: ENABLE_VECTOR_API допускает только 0 или 1.\n' >&2
    exit 2
fi

if [[ "$GC_LOGGING" != "0" && "$GC_LOGGING" != "1" ]]; then
    printf 'Ошибка: GC_LOGGING допускает только 0 или 1.\n' >&2
    exit 2
fi

if ! JAVA_PROPERTIES="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1)"; then
    printf 'Ошибка: не удалось запустить Java: %s\n' "$JAVA_BIN" >&2
    exit 1
fi
JAVA_VERSION="$(printf '%s\n' "$JAVA_PROPERTIES" | awk -F'= ' '/java.specification.version =/ { print $2; exit }')"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ -z "$JAVA_VERSION" || ! "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || (( JAVA_MAJOR < 21 )); then
    printf 'Ошибка: для Minecraft 1.21.11 требуется Java 21 или новее; обнаружена версия: %s\n' "${JAVA_VERSION:-неизвестно}" >&2
    exit 1
fi

JVM_ARGS=(
    "-Xms${RAM}"
    "-Xmx${RAM}"
    -XX:+UseG1GC
    -XX:+ParallelRefProcEnabled
    -XX:MaxGCPauseMillis=200
    -XX:+DisableExplicitGC
    -XX:+AlwaysPreTouch
    -XX:+ExitOnOutOfMemoryError
    -Dfile.encoding=UTF-8
    "-Duser.timezone=${TIMEZONE}"
    -Dterminal.ansi=true
)

# Vector API оставлен опциональным: он полезен только если его использует ядро/плагины.
if [[ "$ENABLE_VECTOR_API" == "1" ]]; then
    JVM_ARGS+=(--add-modules=jdk.incubator.vector)
fi

# Включайте для диагностики пауз GC; журнал ротируется и занимает до ~50 MiB.
if [[ "$GC_LOGGING" == "1" ]]; then
    mkdir -p logs
    JVM_ARGS+=("-Xlog:gc*,safepoint:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M")
fi

printf 'Запуск сервера: %s | Java %s | heap %s | часовой пояс %s\n' \
    "$SERVER_JAR" "$JAVA_VERSION" "$RAM" "$TIMEZONE"
if [[ "$GC_LOGGING" == "1" ]]; then
    printf 'GC-журнал: logs/gc.log (ротация до ~50 MiB).\n'
else
    printf 'Для профилирования GC запустите: GC_LOGGING=1 ./start.sh\n'
fi

STOP_REQUESTED=0
SERVER_PID=""

request_stop() {
    STOP_REQUESTED=1
    if [[ -n "$SERVER_PID" ]]; then
        # SIGTERM даёт JVM и ядру время штатно сохранить мир и остановиться.
        kill -TERM "$SERVER_PID" 2>/dev/null || true
    else
        exit 0
    fi
}
trap request_stop INT TERM

while :; do
    printf '\nЗапуск %s…\n' "$SERVER_JAR"
    # Явный <&0 сохраняет ввод консоли у фонового процесса Bash.
    "$JAVA_BIN" "${JVM_ARGS[@]}" -jar "$SERVER_JAR" nogui <&0 &
    SERVER_PID=$!

    # wait может прерваться доставкой сигнала, пока JVM ещё завершает shutdown.
    while :; do
        if wait "$SERVER_PID"; then
            SERVER_EXIT=0
        else
            SERVER_EXIT=$?
        fi
        if (( STOP_REQUESTED )) && kill -0 "$SERVER_PID" 2>/dev/null; then
            continue
        fi
        break
    done
    SERVER_PID=""

    if (( STOP_REQUESTED )); then
        printf 'Остановлено пользователем; автоматический перезапуск отменён.\n'
        exit 0
    fi

    printf 'Сервер завершился с кодом %s; перезапуск через %s сек. Нажмите Ctrl+C, чтобы отменить.\n' \
        "$SERVER_EXIT" "$RESTART_DELAY"
    sleep "$RESTART_DELAY" || true

    if (( STOP_REQUESTED )); then
        printf 'Автоматический перезапуск отменён.\n'
        exit 0
    fi
done
