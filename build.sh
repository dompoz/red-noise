#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME=/usr/lib/jvm/java-17-temurin ./gradlew assembleDebug bundleRelease
