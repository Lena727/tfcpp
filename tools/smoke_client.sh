#!/bin/bash
# Starts the dev client into a copy of the game test world, waits until it has joined or crashed, then closes only that client.
cd "$(dirname "$0")/.." || exit 1
mkdir -p build/verify
export JAVA_HOME="$(cygpath -u "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2")"
OUT="build/verify/smoke_gradle.log"
rm -rf run/saves/tfcpp_smoke
mkdir -p run/saves/tfcpp_smoke
cp -r run/world/. run/saves/tfcpp_smoke/
rm -f run/saves/tfcpp_smoke/session.lock
: > run/logs/latest.log
./gradlew runClient -PquickPlayWorld=tfcpp_smoke > "$OUT" 2>&1 &
for i in $(seq 1 60); do
  sleep 5
  if grep -qE "Stopping worker threads|joined the game|Crash report|Reported exception|This crash report" run/logs/latest.log 2>/dev/null; then break; fi
  grep -qE "BUILD (FAILED|SUCCESSFUL)" "$OUT" && break
done
sleep 45
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'neoforge.*client|fmlclient|forgeclient|clientdev' -and \$_.CommandLine -notmatch 'GradleDaemon' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
wait
rm -rf run/saves/tfcpp_smoke
echo FINISHED
tail -3 "$OUT"
grep -nE "TFCPP_SMOKE|Unable to load|joined the game|JEI|jei|tfcpp.*(ERROR|WARN)|Missing|missing model|FileNotFound|Exception|Crash" run/logs/latest.log | grep -iv "patchouli\|realms\|narrator" | cut -c1-200 | head -60
