#!/usr/bin/env bash

./gradlew ${EXTRA_GRADLE_ARGS} lintDebug
./gradlew ${EXTRA_GRADLE_ARGS} generateReleasePlayResources