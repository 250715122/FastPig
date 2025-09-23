@echo off
chcp 65001 >nul
echo 全局热键测试脚本
echo ==================
echo.
echo 启动现代化代码助手...
echo 请按以下热键进行测试：
echo.
echo Alt + L: 最小化窗口
echo Alt + N: 恢复窗口（正常大小）
echo Alt + M: 最大化窗口
echo Alt + Q: 退出程序
echo.
echo 注意观察控制台输出的调试信息
echo.

java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

echo.
echo 程序已退出
pause
