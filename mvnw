#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper script for Unix
# ----------------------------------------------------------------------------
BASEDIR=$(cd "$(dirname "$0")"; pwd -P)
MAVEN_PROJECTBASEDIR=$BASEDIR
export MAVEN_PROJECTBASEDIR

MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  echo "Missing Maven wrapper jar at $MAVEN_WRAPPER_JAR"
  exit 1
fi

JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if [ ! -x "$JAVA_CMD" ]; then
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" -jar "$MAVEN_WRAPPER_JAR" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" -Dmaven.home="$MAVEN_PROJECTBASEDIR/.mvn/wrapper" -Dmaven.wrapper.properties="$MAVEN_WRAPPER_PROPERTIES" "$@"
