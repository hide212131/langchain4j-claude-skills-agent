#!/usr/bin/env sh
set -e

# Resolve location of this script to support relative wrapper paths.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

JAVA_CMD=java
if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" -classpath "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
