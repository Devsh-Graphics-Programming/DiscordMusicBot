#!/bin/sh
set -eu

DATA_DIR="${JMUSICBOT_HOME:-/data}"
CONFIG_FILE="${JMUSICBOT_CONFIG_FILE:-$DATA_DIR/config.txt}"
CONFIG_TEMPLATE="$DATA_DIR/config.template.txt"
SETTINGS_FILE="$DATA_DIR/serversettings.json"
SETTINGS_TEMPLATE="$DATA_DIR/serversettings.template.json"

mkdir -p "$DATA_DIR" "$DATA_DIR/Playlists"

if [ ! -f "$CONFIG_FILE" ] && [ -f "$CONFIG_TEMPLATE" ]; then
    cp "$CONFIG_TEMPLATE" "$CONFIG_FILE"
fi

if [ ! -f "$SETTINGS_FILE" ]; then
    if [ -f "$SETTINGS_TEMPLATE" ]; then
        cp "$SETTINGS_TEMPLATE" "$SETTINGS_FILE"
    else
        printf '{}\n' > "$SETTINGS_FILE"
    fi
fi

exec java ${JAVA_OPTS:-} \
    -Dnogui=true \
    -Dconfig.file="$CONFIG_FILE" \
    -Djmusicbot.home="$DATA_DIR" \
    -jar /app/JMusicBot.jar
