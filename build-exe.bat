@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 1. 编译项目
echo [1/3] 正在编译项目...
set "JAVA_HOME=D:\tools\java\jdk-21.0.8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
call mvn -version
call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo ❌ 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/3] 清理旧版本...
if exist FastPig (
    rmdir /s /q FastPig
    echo 已删除旧的 FastPig 目录
)

echo.
echo [3/3] 正在生成 EXE 文件...
echo.

REM 2. 使用 Java 21 的 jpackage
set "JAVA_HOME=D:\tools\java\jdk-21.0.8"
set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"

if not exist "%JPACKAGE%" (
    echo ❌ 找不到 jpackage 工具！
    echo    期望位置: %JPACKAGE%
    echo.
    echo 请确认 Java 21 安装路径是否正确。
    pause
    exit /b 1
)

echo 使用 Java 21 jpackage
echo.

REM 3. 复制配置文件到 target 目录
if exist config.properties (
    copy config.properties target\config.properties
    echo 已复制配置文件到打包目录
)

REM 4. 生成应用程序（无控制台窗口）
REM 注意：使用 --verbose 来查看详细输出
call "%JPACKAGE%" ^
    --input target ^
    --name FastPig ^
    --main-jar FastPig-0.0.1-SNAPSHOT-jar-with-dependencies.jar ^
    --main-class com.gt.ModernSwingTest ^
    --type app-image ^
    --dest . ^
    --verbose

if errorlevel 1 (
    echo.
    echo ❌ EXE 生成失败！
    pause
    exit /b 1
)

echo.
echo [4/4] 复制配置文件...
if exist config.properties (
    copy config.properties FastPig\config.properties
    echo 已复制 config.properties 到 FastPig 目录
)
echo.
echo [5/5] 完成！
echo.
echo ========================================
echo  ✅ 成功！
echo ========================================
echo.
echo 应用程序已生成在 FastPig 目录下
echo.
echo 运行方式：
echo   1. 双击 FastPig\FastPig.exe 启动程序
echo   2. 或者复制整个 FastPig 文件夹到任意位置使用
echo.
echo 注意：FastPig 目录包含了完整的 Java 运行环境，
echo       无需安装 Java 即可运行！
echo.
pause
