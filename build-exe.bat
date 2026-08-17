@echo off
chcp 65001 >nul

echo ========================================
echo  FastPig - 生成 Windows EXE 安装包
echo ========================================
echo.

REM 0. 程序还在运行时无法删除 FastPig 目录（文件被占用），
REM    而且数据库和向量索引正被写入，此时打包会得到不一致的数据。
REM    提前拦住，避免走到一半才失败。
tasklist /FI "IMAGENAME eq FastPig.exe" 2>nul | find /I "FastPig.exe" >nul
if not errorlevel 1 (
    echo ❌ FastPig.exe 正在运行，请先退出程序再打包。
    echo    退出前记得保存当前笔记（Ctrl+S）。
    echo.
    pause
    exit /b 1
)

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
echo [3/5] 保全用户数据并清理旧版本...

REM jpackage 要求目标目录不存在，所以必须整个删掉 FastPig。
REM 但笔记、模型、数据库、向量索引都在里面，删掉就得重新下载 90MB 模型、
REM 重新拉全部笔记、并全量重建向量索引。先把它们移出去，打包完再移回。
REM 用 move 而不是 copy：同盘 move 是重命名，保留文件修改时间。
REM 一旦 mtime 变了，shouldUpload 会认为所有笔记都被改过并整批上传。
set "KEEP=_data_keep"
if exist "%KEEP%" rmdir /s /q "%KEEP%"
mkdir "%KEEP%"

if exist FastPig (
    if exist FastPig\notes        move FastPig\notes        "%KEEP%\notes"        >nul && echo   已保全 notes
    if exist FastPig\models       move FastPig\models       "%KEEP%\models"       >nul && echo   已保全 models
    if exist FastPig\vector_index move FastPig\vector_index "%KEEP%\vector_index" >nul && echo   已保全 vector_index
    if exist FastPig\fastpig.db   move FastPig\fastpig.db   "%KEEP%\fastpig.db"   >nul && echo   已保全 fastpig.db
    if exist FastPig\fastpig.db-wal move FastPig\fastpig.db-wal "%KEEP%\" >nul
    if exist FastPig\fastpig.db-shm move FastPig\fastpig.db-shm "%KEEP%\" >nul

    rmdir /s /q FastPig
    echo 已删除旧的 FastPig 目录
)

REM 删不干净说明还有文件被占用。此时 jpackage 必然失败，
REM 提前还原数据并退出，不要让笔记滞留在 %KEEP% 里。
if exist FastPig (
    echo ❌ FastPig 目录未能删除，可能仍有进程占用。
    goto :abort
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
    goto :abort
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
    goto :abort
)

echo.
echo [5/5] 复制配置文件并还原用户数据...
if exist config.properties (
    copy config.properties FastPig\config.properties
    echo 已复制 config.properties 到 FastPig 目录
)

call :restore_data
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
exit /b 0


REM ==================== 子过程 ====================

REM 把 [3/5] 保全的数据移回 FastPig 目录。
REM vector_index 里的 .schema_version 为当前版本时启动不会全量重建；
REM notes 与 fastpig.db 在位时同步只拉云端版本更高的笔记。
:restore_data
if not exist "%KEEP%" goto :eof
if not exist FastPig mkdir FastPig
if exist "%KEEP%\notes"        move "%KEEP%\notes"        FastPig\notes        >nul && echo   已还原 notes
if exist "%KEEP%\models"       move "%KEEP%\models"       FastPig\models       >nul && echo   已还原 models
if exist "%KEEP%\vector_index" move "%KEEP%\vector_index" FastPig\vector_index >nul && echo   已还原 vector_index
if exist "%KEEP%\fastpig.db"   move "%KEEP%\fastpig.db"   FastPig\fastpig.db   >nul && echo   已还原 fastpig.db
if exist "%KEEP%\fastpig.db-wal" move "%KEEP%\fastpig.db-wal" FastPig\ >nul
if exist "%KEEP%\fastpig.db-shm" move "%KEEP%\fastpig.db-shm" FastPig\ >nul
REM 异常退出残留的锁会让 Lucene 报 Lock held by another program
if exist FastPig\vector_index\write.lock del /q FastPig\vector_index\write.lock
rmdir /s /q "%KEEP%" 2>nul
echo 用户数据已还原
goto :eof

REM 打包中途失败也必须把数据还回去，否则笔记会滞留在 %KEEP% 里
:abort
echo.
echo 打包失败，正在还原用户数据...
call :restore_data
echo.
echo 已还原。若 FastPig 目录不完整，请用 FastPig-backups 下的备份恢复。
pause
exit /b 1
