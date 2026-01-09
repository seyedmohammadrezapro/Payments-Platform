@ECHO OFF
SETLOCAL

SET "BASEDIR=%~dp0"
for %%I in ("%BASEDIR%.") do set "MAVEN_PROJECTBASEDIR=%%~fI"

SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Missing Maven wrapper jar at %WRAPPER_JAR%
  EXIT /B 1
)

IF NOT DEFINED JAVA_HOME (
  SET "JAVA_EXE=java"
) ELSE (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*

ENDLOCAL
