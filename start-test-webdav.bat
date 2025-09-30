@echo off
chcp 65001 >nul
echo ========================================
echo   FastPig - 坚果云 WebDAV 同步测试
echo ========================================
echo.
echo 请先配置你的坚果云账号：
echo.
echo 方法1：修改此脚本，取消下面两行的注释
echo   set NUTSTORE_USERNAME=你的邮箱
echo   set NUTSTORE_PASSWORD=应用密码
echo.
echo 方法2：使用启动参数
echo   java -Dnutstore.username="邮箱" -Dnutstore.password="密码" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
echo.
echo ========================================
echo.
pause
echo 正在启动...
echo.

REM 取消下面两行的注释并填入你的坚果云账号信息
REM set NUTSTORE_USERNAME=your-email@example.com
REM set NUTSTORE_PASSWORD=abcd-1234-efgh-5678

java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

echo.
echo 程序已退出
pause
