@echo off
chcp 65001 >nul
echo ========================================
echo   FastPig - 坚果云同步版
echo ========================================
echo.
echo 正在启动程序（已配置坚果云账号）...
echo.

java -Dnutstore.username=250715122@qq.com -Dnutstore.password=aqgd7w2f52j6g5sg -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

echo.
echo 程序已退出
pause
