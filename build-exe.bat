@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 1. 编译项目
echo [1/5] 正在编译项目...
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
echo [2/5] 清理 target 目录中不需要的文件...
REM 删除依赖目录（已经打进 shaded jar）
if exist target\dependency (
    rmdir /s /q target\dependency
    echo 已删除 dependency 目录
)
REM 删除编译类文件目录
if exist target\classes (
    rmdir /s /q target\classes
    echo 已删除 classes 目录
)
REM 删除其他构建产物
if exist target\generated-sources (
    rmdir /s /q target\generated-sources
    echo 已删除 generated-sources 目录
)
if exist target\maven-status (
    rmdir /s /q target\maven-status
    echo 已删除 maven-status 目录
)
if exist target\archive-tmp (
    rmdir /s /q target\archive-tmp
    echo 已删除 archive-tmp 目录
)
if exist target\maven-archiver (
    rmdir /s /q target\maven-archiver
    echo 已删除 maven-archiver 目录
)
REM 删除原始 jar（只保留 shaded jar）
if exist target\FastPig-0.0.1-SNAPSHOT.jar (
    del /q target\FastPig-0.0.1-SNAPSHOT.jar
    echo 已删除原始 jar
)
if exist target\original-FastPig-0.0.1-SNAPSHOT.jar (
    del /q target\original-FastPig-0.0.1-SNAPSHOT.jar
    echo 已删除 original jar
)

echo.
echo [3/5] 清理旧版本...
if exist FastPig (
    rmdir /s /q FastPig
    echo 已删除旧的 FastPig 目录
)

echo.
echo [4/5] 正在生成 EXE 文件...
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

REM 3. 复制配置文件和图标到 target 目录
if exist config.properties (
    copy config.properties target\config.properties
    echo 已复制配置文件到打包目录
)

REM 复制图标文件到 target 目录
if exist src\main\resources\icons\FastPig.ico (
    copy src\main\resources\icons\FastPig.ico target\FastPig.ico
    echo 已复制图标文件到打包目录
) else (
    echo 警告：未找到图标文件，使用默认图标
)

REM 4. 生成应用程序（无控制台窗口，带自定义图标）
REM 注意：使用 --verbose 来查看详细输出
REM 使用 jlink 裁剪 JRE，只包含必要的 Java 模块，大幅减小打包体积
call "%JPACKAGE%" ^
    --input target ^
    --name FastPig ^
    --main-jar FastPig-0.0.1-SNAPSHOT-jar-with-dependencies.jar ^
    --main-class com.gt.FastPigApplication ^
    --type app-image ^
    --dest . ^
    --icon target\FastPig.ico ^
    --add-modules java.base,java.desktop,java.sql,java.logging,java.naming,java.xml,java.datatransfer,java.prefs,java.management,jdk.unsupported ^
    --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=2" ^
    --verbose

if errorlevel 1 (
    echo.
    echo ❌ EXE 生成失败！
    pause
    exit /b 1
)

echo.
echo [5/5] 复制配置文件...
if exist config.properties (
    copy config.properties FastPig\config.properties
    echo 已复制 config.properties 到 FastPig 目录
)
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
