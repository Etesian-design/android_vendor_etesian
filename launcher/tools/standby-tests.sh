#!/bin/bash
# The standby screen's unit tests (lines, settings, presets). The launcher's own Gradle build can't compile its
# hidden-API code, so these build only the two plain files against android.jar in a throwaway JVM project.
set -euo pipefail
L="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; JAR=$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/src/main/kotlin" "$W/src/test/kotlin"
S="$L/app/src/main/java/com/bompotis/etesian/launcher/standby"
cp "$S/StandbyConfig.kt" "$S/StandbyItems.kt" "$W/src/main/kotlin/"
cp "$L/app/src/test/java/com/bompotis/etesian/launcher/standby/StandbyTest.kt" "$W/src/test/kotlin/"
cat > "$W/settings.gradle.kts" <<'G'
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "standby-tests"
G
cat > "$W/build.gradle.kts" <<G
plugins { kotlin("jvm") version "2.4.10" }
dependencies {
    compileOnly(files("$JAR"))
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
kotlin { jvmToolchain(21) }
G
"$L/gradlew" -q -p "$W" test
grep -h "<testsuite" "$W"/build/test-results/test/*.xml | grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"'
