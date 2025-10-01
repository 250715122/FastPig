@echo off
echo ========================================
echo  FastPig Startup Script
echo ========================================
echo.

echo [1/4] Checking admin privileges...
net session >nul 2>&1
if %errorLevel% == 0 (
    echo   Admin privileges: YES
) else (
    echo   Admin privileges: NO
    echo   Note: Global hotkeys may be limited
)

echo.
echo [2/4] Setting Java 21 environment...
set "JAVA_HOME=D:\tools\java\jdk-21.0.8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo   JAVA_HOME: %JAVA_HOME%
echo   Verifying Java version...
"%JAVA_HOME%\bin\java" -version
if not %errorLevel% == 0 (
    echo   ERROR: Java not found at %JAVA_HOME%
    pause
    exit /b 1
)
echo   Java 21 ready

echo.
echo [3/4] Compiling project...
call mvn clean compile -DskipTests -q
if not %errorLevel% == 0 (
    echo   ERROR: Compilation failed
    pause
    exit /b 1
)
echo   Compilation successful

echo.
echo [4/4] Starting FastPig application...
echo   Note: config.properties must contain Nutstore credentials
echo   Starting...
echo.

"%JAVA_HOME%\bin\java" -cp "target\classes;target\dependency\*" com.gt.FastPigApplication

echo.
echo ========================================
echo  Program exited
echo ========================================
pause