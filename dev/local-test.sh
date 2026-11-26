#!/usr/bin/env bash
# Developer smoke test on a machine without ASM / network access.
# Uses the JDK-internal copy of ASM (identical API, different package) for the ASM backend.
# Requires: gradle build of core, attach, analyzer done (gradle :core:build :attach:build :analyzer:build).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
W="${TMPDIR:-/tmp}/hotspotter-localtest"
rm -rf "$W" && mkdir -p "$W/impl" "$W/sample" "$W/agent"

# 1) ASM backend compiled against the JDK-internal ASM
sed 's/org\.objectweb\.asm/jdk.internal.org.objectweb.asm/g' "$ROOT/agent-asm/src/main/java/eu/hansolo/hotspotter/agent/impl/InstrumenterImpl.java" > "$W/InstrumenterImpl.java"
javac -d "$W/impl" --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp "$ROOT/core/build/classes/java/main" "$W/InstrumenterImpl.java"

# 2) agent jar = core classes + backend
cp -r "$ROOT/core/build/classes/java/main/." "$W/agent/"
cp -r "$W/impl/." "$W/agent/"
printf 'Premain-Class: eu.hansolo.hotspotter.agent.Agent\nAgent-Class: eu.hansolo.hotspotter.agent.Agent\nCan-Retransform-Classes: true\n' > "$W/manifest.txt"
jar cfm "$W/agent.jar" "$W/manifest.txt" -C "$W/agent" .

# 3) sample app
javac --release 17 -d "$W/sample" "$ROOT/sample/src/sample/Main.java"

JVMOPTS="--add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
ANALYZER="java -jar $ROOT/analyzer/build/libs/hotspotter-analyzer-1.0.0.jar"
ATTACH="java -jar $ROOT/attach/build/libs/hotspotter-attach-1.0.0.jar"

echo "=== Test A: premain (-javaagent) ==="
java $JVMOPTS -javaagent:"$W/agent.jar"=include=sample.*,out="$W/outA",interval=1 -cp "$W/sample" sample.Main 3
$ANALYZER snapshots "$W/outA"
$ANALYZER top "$W/outA" --n 10
$ANALYZER top "$W/outA" --by class
$ANALYZER top "$W/outA" --from 0 --n 5

echo "=== Test B: attach to running JVM, then stop ==="
java $JVMOPTS -cp "$W/sample" sample.Main 8 &
APP=$!
sleep 1.5
$ATTACH "$APP" "$W/agent.jar" "include=sample.*,out=$W/outB,interval=1"
sleep 3
$ATTACH "$APP" "$W/agent.jar" stop
wait "$APP"
$ANALYZER snapshots "$W/outB"
$ANALYZER top "$W/outB" --n 10
