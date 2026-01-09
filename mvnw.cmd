@ECHO OFF
SETLOCAL

SET BASEDIR=%~dp0
SET MAVEN_PROJECTBASEDIR=%BASEDIR%

SET WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar
SET WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Missing Maven wrapper jar at %WRAPPER_JAR%
  EXIT /B 1
)

IF NOT DEFINED JAVA_HOME (
  SET JAVA_EXE=java
) ELSE (
  SET JAVA_EXE="%JAVA_HOME%\bin\java"
)

%JAVA_EXE% -jar "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -Dmaven.home="%MAVEN_PROJECTBASEDIR%.mvn\wrapper" -Dmaven.wrapper.properties="%WRAPPER_PROPERTIES%" %*

ENDLOCAL
