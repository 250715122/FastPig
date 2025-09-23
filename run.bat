@echo off
chcp 65001 >nul
echo 代码助手项目启动器
echo ===============================

echo 检测管理员权限...
net session >nul 2>&1
if %errorLevel% == 0 (
    echo ✓ 以管理员权限运行，全局热键功能完全可用
    set ADMIN_MODE=true
) else (
    echo ⚠ 普通权限运行，全局热键可能受限
    echo 建议：右键选择"以管理员身份运行"获得完整功能
    set ADMIN_MODE=false
)

echo.
echo 设置Java和Maven环境变量...
set JAVA_HOME=D:\tools\java\jdk1.8.0_461
set PATH=D:\tools\java\jdk1.8.0_461\bin;D:\tools\maven\apache-maven-3.9.11\bin;%PATH%

echo.
echo 编译项目...
call mvn clean compile

echo.
echo 启动前，执行一次数据双向同步到坚果云...
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\git\FastPig\scripts\nutstore_bisync.ps1" -VerboseLog
if not %errorlevel%==0 (
    echo 同步失败，退出。
    exit /b 1
)

echo.
echo 启动程序...
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

echo.
echo 程序已退出，执行一次数据同步到坚果云，完成后退出...
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\git\FastPig\scripts\nutstore_bisync.ps1" -VerboseLog
if not %errorlevel%==0 (
    echo 退出前同步失败，请查看 logs 目录中的 bisync 日志。
    exit /b 1
)

pause 