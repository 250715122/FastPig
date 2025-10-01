@echo off
chcp 65001 >nul
echo ========================================
echo  FastPig 快速启动脚本
echo ========================================
echo.

echo [1/4] 检测管理员权限...
net session >nul 2>&1
if %errorLevel% == 0 (
    echo   已获得管理员权限，全局热键功能完全可用
) else (
    echo   警告: 普通权限运行，全局热键可能受限
    echo   提示: 右键选择 "以管理员身份运行" 获得完整功能
)

echo.
echo [2/4] 设置 Java 21 环境...
set JAVA_HOME=D:\tools\java\jdk-21.0.8
set PATH=%JAVA_HOME%\bin;%PATH%

REM 验证 Java 版本
java -version 2>&1 | findstr "21.0" >nul
if %errorLevel% == 0 (
    echo   Java 21 环境已就绪
) else (
    echo   错误: 未找到 Java 21，请检查 JAVA_HOME 设置
    echo   当前 JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)

echo.
echo [3/4] 编译项目...
call mvn clean compile -DskipTests -q
if not %errorLevel% == 0 (
    echo   错误: 编译失败
    pause
    exit /b 1
)
echo   编译成功

echo.
echo [4/4] 启动 FastPig 应用程序...
echo   提示: 配置文件 config.properties 需包含坚果云账号信息
echo   启动中...
echo.

java -cp "target/classes;target/dependency/*" com.gt.FastPigApplication

echo.
echo ========================================
echo  程序已退出
echo ========================================
pause 