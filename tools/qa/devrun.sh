#!/usr/bin/env bash
# Start or stop the OmniVault dev server and QA client under Xvfb with software rendering.
#   tools/qa/devrun.sh setup-server        # writes runs/server/server.properties for QA (flat, creative, RCON)
#   tools/qa/devrun.sh start-server
#   tools/qa/devrun.sh start-client         # needs Xvfb on $DISPLAY (default :1)
#   tools/qa/devrun.sh stop-client | stop-server
# Logs go to $QA_LOGS (default /tmp/omnivault-qa).
set -u
LOGS=${QA_LOGS:-/tmp/omnivault-qa}
mkdir -p "$LOGS"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WS="$ROOT/1.21.1"
export JAVA_HOME=${QA_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}}
case "$1" in
  setup-server)
    mkdir -p "$WS/neoforge/runs/server"
    cat > "$WS/neoforge/runs/server/server.properties" <<PROPS
online-mode=false
enable-rcon=true
rcon.password=qa
rcon.port=25575
broadcast-rcon-to-ops=false
level-name=qa
level-type=minecraft\:flat
gamemode=creative
difficulty=peaceful
spawn-protection=0
spawn-monsters=false
spawn-animals=false
allow-flight=true
view-distance=8
simulation-distance=6
motd=OmniVault QA
PROPS
    # The QA server is a local development server for testing this mod.
    echo "eula=true" > "$WS/neoforge/runs/server/eula.txt"
    ;;
  start-server)
    cd "$WS" && nohup bash ./gradlew :neoforge:runServer --no-daemon > "$LOGS/server.log" 2>&1 &
    ;;
  start-client)
    export DISPLAY=${DISPLAY:-:1} LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
    cd "$WS" && nohup bash ./gradlew :neoforge:runQaClient --no-daemon > "$LOGS/client.log" 2>&1 &
    ;;
  stop-client|stop-server)
    # Only java processes started from this workspace are considered.
    want=$([ "$1" = stop-client ] && echo 'runQaClient|quickPlayMultiplayer' || echo 'runServer|neoforgeserver|--nogui')
    for pid in $(pgrep -x java); do
      cmd=$(tr '\0' ' ' < "/proc/$pid/cmdline")
      cwd=$(readlink "/proc/$pid/cwd")
      if echo "$cmd" | grep -qE "$want" && echo "$cwd $cmd" | grep -q "$WS"; then kill "$pid"; fi
    done
    ;;
esac
