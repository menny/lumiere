#!/usr/bin/env bash
set -e

./gradlew ${EXTRA_GRADLE_ARGS} testDebugUnitTest testDebugUnitTestCoverage
