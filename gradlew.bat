@ECHO OFF
SETLOCAL

SET SCRIPT_DIR=%~dp0
SET JAVA_CMD=java

IF NOT "%JAVA_HOME%"=="" SET JAVA_CMD=%JAVA_HOME%\bin\java.exe

"%JAVA_CMD%" -classpath "%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

ENDLOCAL
