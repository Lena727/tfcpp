#!/bin/bash
# Full verification: runData, JUnit tests, game tests. Prints a short summary; full logs go to build/verify/.
# Usage: bash tools/verify.sh [--client]   (--client also starts the dev client smoke test after everything passed)
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="$(cygpath -u "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2")"
mkdir -p build/verify

CLIENT=0
if [ "$1" = "--client" ]; then CLIENT=1; fi

finish() {
  # $1 = exit code; prints the final verdict line and exits
  if [ "$1" -eq 0 ]; then echo "ALL OK"; else echo "FAILED"; fi
  exit "$1"
}

# 1. runData
./gradlew runData --offline -q > build/verify/rundata.log 2>&1
if [ $? -ne 0 ]; then
  echo "runData: FAILED"
  tail -n 15 build/verify/rundata.log
  finish 1
fi
echo "runData: ok"

# 2. JUnit tests (summary is printed even when tests fail)
rm -rf build/test-results/test
rm -f build/verify/failed_tests.txt
./gradlew test --offline -q > build/verify/test.log 2>&1
test_rc=$?

run=0; failures=0; errors=0; skipped=0
for f in build/test-results/test/*.xml; do
  [ -f "$f" ] || continue
  hdr=$(grep -m1 -o '<testsuite [^>]*>' "$f")
  n=$(echo "$hdr" | sed -n 's/.* tests="\([0-9]*\)".*/\1/p')
  fl=$(echo "$hdr" | sed -n 's/.* failures="\([0-9]*\)".*/\1/p')
  er=$(echo "$hdr" | sed -n 's/.* errors="\([0-9]*\)".*/\1/p')
  sk=$(echo "$hdr" | sed -n 's/.* skipped="\([0-9]*\)".*/\1/p')
  run=$((run + ${n:-0}))
  failures=$((failures + ${fl:-0}))
  errors=$((errors + ${er:-0}))
  skipped=$((skipped + ${sk:-0}))
  grep -B1 -E '<(failure|error)[ >]' "$f" | grep '<testcase' | while IFS= read -r line; do
    nm=$(echo "$line" | sed -n 's/.* name="\([^"]*\)".*/\1/p')
    cl=$(echo "$line" | sed -n 's/.* classname="\([^"]*\)".*/\1/p')
    echo "  $cl.$nm" >> build/verify/failed_tests.txt
  done
done
echo "junit: $run run, $failures failed, $errors errors, $skipped skipped"
if [ -s build/verify/failed_tests.txt ]; then
  head -n 40 build/verify/failed_tests.txt | cut -c1-200
fi
if [ "$test_rc" -ne 0 ] && [ "$run" -eq 0 ]; then
  echo "junit: no results written, last lines of build/verify/test.log:"
  tail -n 15 build/verify/test.log | cut -c1-200
fi
if [ "$test_rc" -ne 0 ]; then
  finish 1
fi

# 3. Game tests on a dedicated server
rm -f run/logs/latest.log
./gradlew runGameTestServer --offline -q > build/verify/gametest.log 2>&1
gt_rc=$?
okline=$(grep -h "required tests passed" build/verify/gametest.log run/logs/latest.log 2>/dev/null | tail -n 1)
if [ "$gt_rc" -eq 0 ] && [ -n "$okline" ]; then
  echo "gametest: $(echo "$okline" | sed 's/^.*\(All [0-9]* required tests passed.*\)$/\1/' | cut -c1-200)"
else
  echo "gametest: FAILED (gradle exit $gt_rc)"
  grep -h -E "required tests failed|failed!|::" build/verify/gametest.log run/logs/latest.log 2>/dev/null | head -n 40 | cut -c1-200
  finish 1
fi

# 4. Optional client smoke test, only after everything passed
if [ "$CLIENT" -eq 1 ]; then
  echo "--- smoke_client ---"
  bash tools/smoke_client.sh
  echo "--- end smoke_client ---"
fi

finish 0
