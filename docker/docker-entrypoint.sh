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

if [ "${SPOTIFY_ENABLED:-false}" = "true" ]; then
    SPOTIFY_CONFIG_DIR="${SPOTIFY_CONFIG_DIR:-$DATA_DIR/spotify}"
    SPOTIFY_PIPE_PATH="${SPOTIFY_PIPE_PATH:-$DATA_DIR/spotify.pipe}"
    SPOTIFY_API_PORT="${SPOTIFY_API_PORT:-3678}"
    SPOTIFY_API_URL="${SPOTIFY_API_URL:-http://127.0.0.1:$SPOTIFY_API_PORT}"
    SPOTIFY_FFMPEG_PATH="${SPOTIFY_FFMPEG_PATH:-ffmpeg}"
    SPOTIFY_CALLBACK_PORT="${SPOTIFY_CALLBACK_PORT:-0}"
    SPOTIFY_DEVICE_NAME="${SPOTIFY_DEVICE_NAME:-JMusicBot Spotify}"
    SPOTIFY_VOLUME_STEPS="${SPOTIFY_VOLUME_STEPS:-150}"
    SPOTIFY_INITIAL_VOLUME="${SPOTIFY_INITIAL_VOLUME:-100}"
    SPOTIFY_BITRATE="${SPOTIFY_BITRATE:-320}"
    SPOTIFY_CREDENTIALS_TYPE="${SPOTIFY_CREDENTIALS_TYPE:-interactive}"
    SPOTIFY_STATE_CONFIG="$SPOTIFY_CONFIG_DIR/config.yml"

    mkdir -p "$SPOTIFY_CONFIG_DIR"
    rm -f "$SPOTIFY_PIPE_PATH"
    mkfifo "$SPOTIFY_PIPE_PATH"

    if [ ! -f "$SPOTIFY_STATE_CONFIG" ]; then
        cat > "$SPOTIFY_STATE_CONFIG" <<EOF
log_level: info
device_name: "$SPOTIFY_DEVICE_NAME"
device_type: speaker
audio_backend: pipe
audio_output_pipe: "$SPOTIFY_PIPE_PATH"
audio_output_pipe_format: s16le
bitrate: $SPOTIFY_BITRATE
volume_steps: $SPOTIFY_VOLUME_STEPS
initial_volume: $SPOTIFY_INITIAL_VOLUME
external_volume: true
disable_autoplay: false
zeroconf_enabled: false
credentials:
  type: $SPOTIFY_CREDENTIALS_TYPE
  interactive:
    callback_port: $SPOTIFY_CALLBACK_PORT
server:
  enabled: true
  address: 127.0.0.1
  port: $SPOTIFY_API_PORT
EOF
    fi

    export SPOTIFY_API_URL
    export SPOTIFY_PIPE_PATH
    export SPOTIFY_FFMPEG_PATH

    /usr/local/bin/go-librespot --config_dir "$SPOTIFY_CONFIG_DIR" &
fi

exec java ${JAVA_OPTS:-} \
    -Dnogui=true \
    -Dconfig.file="$CONFIG_FILE" \
    -Djmusicbot.home="$DATA_DIR" \
    -jar /app/JMusicBot.jar
