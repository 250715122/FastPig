@echo off
REM FastPig Quick Start (No compilation)
echo ========================================
echo  FastPig Quick Start
echo ========================================
echo.

set "JAVA_HOME=D:\tools\java\jdk-21.0.8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting FastPig...
echo.

"%JAVA_HOME%\bin\java" -cp "target\classes;target\dependency\*" com.gt.FastPigApplication

pause

