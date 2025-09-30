@echo off
chcp 65001 >nul
echo ========================================
echo FastPig 数据库同步功能测试
echo ========================================
echo.

REM 1. 创建模拟坚果云目录
echo [1/5] 创建模拟坚果云目录...
set TEST_NUTSTORE=D:\git\FastPig\test_nutstore
if not exist "%TEST_NUTSTORE%" mkdir "%TEST_NUTSTORE%"
if not exist "%TEST_NUTSTORE%\FastPig" mkdir "%TEST_NUTSTORE%\FastPig"
echo     已创建: %TEST_NUTSTORE%\FastPig
echo.

REM 2. 备份当前数据库（如果存在）
echo [2/5] 备份当前本地数据库...
if exist "fastpig.db" (
    copy /Y "fastpig.db" "fastpig.db.backup" >nul
    echo     已备份: fastpig.db -^> fastpig.db.backup
) else (
    echo     本地数据库不存在，跳过备份
)
echo.

REM 3. 创建测试数据库到云端
echo [3/5] 创建测试数据库到云端...
echo TEST_DB > "%TEST_NUTSTORE%\FastPig\fastpig.db"
echo     已创建测试文件: %TEST_NUTSTORE%\FastPig\fastpig.db
echo.

REM 4. 测试启动时拉取（模拟首次启动）
echo [4/5] 测试启动时从云端拉取...
if exist "fastpig.db" del /F /Q "fastpig.db"
echo     已删除本地数据库，模拟首次启动
echo     设置环境变量 NUTSTORE_DIR=%TEST_NUTSTORE%
echo     启动程序测试（请手动观察控制台输出）...
echo.
set NUTSTORE_DIR=%TEST_NUTSTORE%
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
echo.

REM 5. 清理
echo [5/5] 测试完成，清理临时文件...
if exist "fastpig.db.backup" (
    move /Y "fastpig.db.backup" "fastpig.db" >nul
    echo     已恢复备份数据库
)
echo.

echo ========================================
echo 测试完成！
echo ========================================
echo.
echo 验证要点：
echo 1. 启动时控制台输出 "[DbSync] 启动：已从云端拉取数据库到本地"
echo 2. 在应用中按 Ctrl+Alt+S，底部状态栏显示 "已同步到云端"
echo 3. 关闭应用时控制台输出 "[DbSync] 已将本地数据库同步到云端"
echo.
echo 测试目录: %TEST_NUTSTORE%\FastPig
echo 可手动检查该目录下的 fastpig.db 文件时间戳
echo.
pause
