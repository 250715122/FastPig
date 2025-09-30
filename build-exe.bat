@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 1. 编译项目
echo [1/3] 正在编译项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ❌ 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/3] 正在生成 EXE 文件...
echo.

REM 2. 查找 jpackage（使用当前 java 所在目录）
for /f "tokens=*" %%i in ('where java') do (
    set "JAVA_PATH=%%i"
    goto :found_java
)
:found_java
for %%i in ("%JAVA_PATH%") do set "JAVA_DIR=%%~dpi"
set "JPACKAGE=%JAVA_DIR%jpackage.exe"

if not exist "%JPACKAGE%" (
    echo ❌ 找不到 jpackage 工具！
    echo    Java 路径: %JAVA_PATH%
    echo    期望位置: %JPACKAGE%
    echo.
    echo 请确认使用 Java 14 或更高版本。
    pause
    exit /b 1
)

echo 使用 jpackage: %JPACKAGE%
echo.

REM 3. 生成 EXE
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
echo [3/3] 清理临时文件...
if exist "FastPig" rmdir /s /q "FastPig"

echo.
echo ========================================
echo  ✅ 成功！
echo ========================================
echo.
echo EXE 文件已生成: FastPig-1.0.exe
echo.
echo 双击 FastPig-1.0.exe 即可安装使用！
echo.
pause
