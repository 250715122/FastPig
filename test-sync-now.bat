@echo off
chcp 65001 >nul
echo ========================================
echo   FastPig - 立即测试坚果云同步
echo ========================================
echo.
echo 测试步骤：
echo 1. 程序启动后会自动尝试从坚果云拉取
echo 2. 按 Alt+S 打开编辑器
echo 3. 输入测试内容（例如：test 测试同步）
echo 4. 按 Ctrl+S 保存
echo 5. 按 Ctrl+Alt+S 手动触发同步到坚果云
echo 6. 查看控制台日志，应显示"已将本地数据库同步到坚果云"
echo.
echo ========================================
echo.
pause
echo 正在启动...
echo.

java -Dnutstore.username=250715122@qq.com -Dnutstore.password=aqgd7w2f52j6g5sg -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

echo.
echo 程序已退出
echo.
echo 验证步骤：
echo 1. 登录坚果云网页版：https://www.jianguoyun.com
echo 2. 用户名：250715122@qq.com
echo 3. 密码：你的坚果云登录密码（不是应用密码）
echo 4. 查看 FastPig 目录下是否有 fastpig.db 文件
echo.
pause
