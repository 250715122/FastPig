# FastPig 坚果云同步功能说明

## 功能概述

FastPig 已集成坚果云数据库同步功能，实现以下自动化流程：

### 1. 启动时自动拉取
- 程序启动时自动检查坚果云目录
- 如果云端存在 `fastpig.db` 且比本地新（或本地不存在），则自动下载到本地
- 控制台输出同步日志

### 2. 关闭时自动上传
- 程序正常关闭时（点击关闭按钮或 Alt+Q）自动将本地数据库上传到坚果云
- 静默同步，不弹窗打扰

### 3. 手动同步快捷键
- **Ctrl+Alt+S**：手动触发同步，立即将本地数据库上传到坚果云
- 底部状态栏显示同步状态："正在同步到云端…" → "已同步到云端" / "同步失败"

## 配置方法

### 方式一：环境变量（推荐）

在系统中设置环境变量 `NUTSTORE_DIR` 指向坚果云本地同步目录：

```bash
# Windows 示例
set NUTSTORE_DIR=C:\Users\YourName\Nutstore

# 或在系统环境变量中永久设置
# 控制面板 → 系统 → 高级系统设置 → 环境变量
```

### 方式二：启动参数

启动程序时通过 JVM 参数指定：

```bash
java -Dnutstore.dir=C:\Users\YourName\Nutstore -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

## 目录结构

假设坚果云目录为 `C:\Users\YourName\Nutstore`，则：

- 本地数据库：`<工作目录>/fastpig.db`（例如：`D:\git\FastPig\fastpig.db`）
- 云端数据库：`C:\Users\YourName\Nutstore\FastPig\fastpig.db`

程序会自动创建 `FastPig` 子目录。

## 测试验证

### 方式一：运行测试脚本

```bash
test-sync.bat
```

该脚本会：
1. 创建模拟坚果云目录 `test_nutstore`
2. 备份当前数据库
3. 创建测试数据到云端
4. 启动程序验证拉取功能
5. 清理并恢复

### 方式二：手动验证

1. **验证启动拉取**：
   - 在坚果云目录创建 `FastPig/fastpig.db` 文件
   - 删除本地 `fastpig.db`
   - 设置环境变量并启动程序
   - 观察控制台输出 `[DbSync] 启动：已从云端拉取数据库到本地`

2. **验证手动同步**：
   - 启动程序并打开编辑器（Alt+S）
   - 按 **Ctrl+Alt+S**
   - 观察底部状态栏显示 "已同步到云端"
   - 检查坚果云目录下文件时间戳是否更新

3. **验证关闭同步**：
   - 修改一些数据并保存
   - 正常关闭程序
   - 观察控制台输出 `[DbSync] 已将本地数据库同步到云端`
   - 检查云端文件时间戳

## 实现细节

### 文件列表
- `src/main/java/com/gt/DbSyncService.java`：同步服务单例类
- `src/main/java/com/gt/ModernSwingTest.java`：启动入口，集成启动拉取和关闭上传
- `src/main/java/com/gt/UnifiedNoteAppFrame.java`：编辑界面，绑定 Ctrl+Alt+S 快捷键

### 同步策略
- **拉取时机**：程序启动时，比较云端和本地时间戳，云端更新则覆盖本地
- **上传时机**：
  - 手动：Ctrl+Alt+S 触发
  - 自动：程序正常退出时
- **冲突处理**：简单策略，后同步者覆盖（坚果云自身也有版本历史功能）

### 未配置时行为
如果未设置 `NUTSTORE_DIR` 或 `nutstore.dir`，程序会：
- 控制台输出：`[DbSync] 未配置 NUTSTORE_DIR/nutstore.dir，跳过云端同步`
- 正常运行，所有同步操作静默跳过
- 不影响本地单机使用

## 注意事项

1. **坚果云安装位置**：确保 `NUTSTORE_DIR` 指向坚果云客户端的同步根目录
2. **多设备使用**：建议在切换设备前先关闭程序触发自动上传，避免冲突
3. **数据安全**：坚果云自带版本历史功能，可恢复误覆盖的数据库
4. **权限问题**：确保程序对坚果云目录有读写权限

## 快捷键汇总

| 快捷键 | 功能 | 作用域 |
|--------|------|--------|
| Ctrl+S | 保存当前笔记 | 应用内 |
| **Ctrl+Alt+S** | 同步数据库到坚果云 | 应用内 |
| Alt+S | 打开编辑器 | 全局 |
| Alt+P | 预览/关闭预览 | 应用内 |
| Alt+D | 软删除当前笔记 | 应用内 |
| Alt+Z | 撤销删除（1分钟内） | 应用内 |
| Alt+M/N/L | 最大化/恢复/最小化窗口 | 全局 |
| Alt+Q | 退出程序（触发自动同步） | 全局 |
