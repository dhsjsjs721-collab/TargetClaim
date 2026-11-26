#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/main build/test
javac --release 21 -d build/main src/main/java/pvp/{AimModel,CombatAI,CombatEngine,CombatSettings,CombatTrace,ClientPreferences,GuiState,TargetFilter,SupportPlan,CombatReplay,ReplayAnalysis}.java
javac --release 21 -cp build/main -d build/test $(find src/test/java -name '*.java')
java -cp build/main:build/test pvp.CoreTest
java -cp build/main:build/test pvp.CombatTest
java -cp build/main:build/test pvp.AimBenchmark
jar --create --file build/pvp-core-0.2.jar -C build/main .
