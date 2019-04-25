#!/usr/bin/env bash
set -e

./gradlew ${EXTRA_GRADLE_ARGS} lintDebug
./gradlew ${EXTRA_GRADLE_ARGS} generateReleasePlayResources