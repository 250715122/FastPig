@echo off
chcp 65001 >nul
echo ========================================
echo FastPig 云端数据库位置诊断
echo ========================================
echo.

echo [1] 检查环境变量 NUTSTORE_DIR...
if defined NUTSTORE_DIR (
    echo     已配置: %NUTSTORE_DIR%
    echo     云端路径应为: %NUTSTORE_DIR%\FastPig\fastpig.db
    echo.
    if exist "%NUTSTORE_DIR%\FastPig\fastpig.db" (
        echo     ✅ 云端文件存在！
        dir "%NUTSTORE_DIR%\FastPig\fastpig.db"
    ) else (
        echo     ❌ 云端文件不存在
        if exist "%NUTSTORE_DIR%\FastPig" (
            echo     目录存在但文件未创建，可能未触发过同步
        ) else (
            echo     目录不存在，从未执行过同步
        )
    )
) else (
    echo     ❌ 未设置环境变量 NUTSTORE_DIR
)
echo.

echo [2] 检查常见坚果云路径...
set "COMMON_PATHS=C:\Users\%USERNAME%\Nutstore"
set "COMMON_PATHS=%COMMON_PATHS%;D:\Nutstore"
set "COMMON_PATHS=%COMMON_PATHS%;E:\Nutstore"
set "COMMON_PATHS=%COMMON_PATHS%;%USERPROFILE%\Nutstore"

for %%p in (%COMMON_PATHS%) do (
    if exist "%%p\FastPig\fastpig.db" (
        echo     ✅ 找到: %%p\FastPig\fastpig.db
        dir "%%p\FastPig\fastpig.db"
        echo.
    )
)

echo [3] 搜索所有可能的坚果云目录...
echo     提示：这可能需要一些时间...
echo.
for %%d in (C D E) do (
    if exist %%d:\ (
        echo     搜索 %%d:\ 驱动器...
        dir /S /B "%%d:\*FastPig\fastpig.db" 2>nul
    )
)

echo.
echo ========================================
echo 诊断完成
echo ========================================
echo.
echo 💡 如何配置：
echo.
echo 1. 找到你的坚果云根目录（通常是 C:\Users\你的用户名\Nutstore）
echo.
echo 2. 设置环境变量（二选一）：
echo    方法A - 系统环境变量（永久）：
echo      控制面板 → 系统 → 高级 → 环境变量
echo      新建变量：NUTSTORE_DIR = C:\Users\你的用户名\Nutstore
echo.
echo    方法B - 启动参数（临时）：
echo      java "-Dnutstore.dir=C:\Users\你的用户名\Nutstore" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
echo.
echo 3. 启动程序后，云端文件会自动创建在：
echo    <坚果云目录>\FastPig\fastpig.db
echo.
pause
