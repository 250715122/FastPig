@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 设置 Java 路径
set "JAVA_HOME=D:\tools\java\jdk-11.0.25"
set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"

REM 检查 jpackage 是否存在
if not exist "%JPACKAGE%" (
    echo ❌ 找不到 jpackage！
    echo    期望位置: %JPACKAGE%
    echo.
    echo 提示：Java 11 没有 jpackage，需要 Java 14+
    echo 请尝试使用 Java 20: D:\tools\java\jdk-20
    pause
    exit /b 1
)

REM 1. 编译项目
echo [1/2] 正在编译项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ❌ 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/2] 正在生成 EXE 安装包...
echo.

REM 2. 生成 EXE
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
echo ========================================
echo  ✅ 成功！
echo ========================================
echo.
echo EXE 安装包已生成: FastPig-1.0.exe
echo.
echo 双击安装即可使用！
echo.
pause
