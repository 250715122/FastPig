@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 方法1：尝试自动查找 Java 20
echo [0/3] 正在查找 jpackage...
where java >nul 2>&1
if %errorlevel% == 0 (
    for /f "tokens=*" %%i in ('where java 2^>nul') do (
        set "JAVA_PATH=%%i"
        goto :found
    )
)
:found
if defined JAVA_PATH (
    for %%i in ("%JAVA_PATH%") do set "JAVA_DIR=%%~dpi"
    set "JPACKAGE=%JAVA_DIR%jpackage.exe"
    
    if exist "%JPACKAGE%" (
        echo ✓ 找到 jpackage: %JPACKAGE%
        goto :compile
    )
)

REM 方法2：尝试几个常见路径
echo   在 PATH 中找不到，尝试常见路径...

set "JAVA_HOME=D:\tools\java\jdk-20"
if exist "%JAVA_HOME%\bin\jpackage.exe" (
    set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
    echo ✓ 找到 jpackage: %JPACKAGE%
    goto :compile
)

set "JAVA_HOME=C:\Program Files\Java\jdk-20"
if exist "%JAVA_HOME%\bin\jpackage.exe" (
    set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
    echo ✓ 找到 jpackage: %JPACKAGE%
    goto :compile
)

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if exist "%JAVA_HOME%\bin\jpackage.exe" (
    set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
    echo ✓ 找到 jpackage: %JPACKAGE%
    goto :compile
)

echo.
echo ❌ 找不到 jpackage！
echo.
echo jpackage 需要 Java 14 或更高版本。
echo 你的 Java 11 没有这个工具。
echo.
echo 请告诉我你的 Java 20 安装路径，例如：
echo   D:\tools\java\jdk-20
echo.
pause
exit /b 1

:compile
echo.
echo [1/3] 正在编译项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ❌ 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/3] 正在生成 EXE 安装包...
echo.

REM 生成 EXE
"%JPACKAGE%" ^
    --input target ^
    --name FastPig ^
    --main-jar codeReplace-0.0.1-SNAPSHOT-jar-with-dependencies.jar ^
    --main-class com.gt.ModernSwingTest ^
    --type exe ^
    --java-options "-Dnutstore.username=250715122@qq.com" ^
    --java-options "-Dnutstore.password=aqgd7w2f52j6g5sg" ^
    --dest . ^
    --win-console

if errorlevel 1 (
    echo.
    echo ❌ EXE 生成失败！
    pause
    exit /b 1
)

echo.
echo [3/3] 完成！
echo.
echo ========================================
echo  ✅ 成功！
echo ========================================
echo.
dir /b FastPig*.exe 2>nul
echo.
echo EXE 安装包已生成（见上方文件名）
echo 双击安装即可使用！
echo.
pause
