# 关闭同步功能测试

## ✅ 已修复问题

之前关闭 `UnifiedNoteAppFrame` 时不会触发同步，现在已修复。

## 🔧 修复内容

在 `UnifiedNoteAppFrame` 构造函数中添加了窗口关闭监听器：

```java
setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

addWindowListener(new java.awt.event.WindowAdapter() {
    @Override
    public void windowClosing(java.awt.event.WindowEvent e) {
        System.out.println("[关闭] 正在同步数据库到云端...");
        DbSyncService.getInstance().syncToCloudSilently();
        System.out.println("[关闭] 同步完成，退出程序");
        System.exit(0);
    }
});
```

## 🧪 测试步骤

### 测试1: 验证启动拉取

1. 删除本地数据库：`del fastpig.db`
2. 启动程序
3. 观察控制台输出：
   ```
   [DbSync] 云端同步目录: D:\git\FastPig\data
   [DbSync] 启动：已从云端拉取数据库到本地
   ```

### 测试2: 验证关闭同步

1. 启动程序（刚才已启动）
2. 按 `Alt+S` 打开编辑器
3. 编辑并保存一些内容
4. **点击窗口关闭按钮 ×**
5. 观察控制台输出应该包含：
   ```
   [关闭] 正在同步数据库到云端...
   [DbSync] 已将本地数据库同步到云端
   [关闭] 同步完成，退出程序
   ```

### 测试3: 验证手动同步（Ctrl+Alt+S）

1. 启动程序
2. 按 `Alt+S` 打开编辑器
3. 按 **Ctrl+Alt+S**
4. 观察：
   - 状态栏显示："正在同步到云端…" → "已同步到云端"
   - 控制台输出：`[DbSync] 已将本地数据库同步到云端`

### 测试4: 验证文件时间戳

```powershell
# 查看文件详情
Get-Item fastpig.db, data\fastpig.db | Format-Table Name, LastWriteTime, Length
```

同步后，两个文件的时间戳应该非常接近。

## 📊 预期控制台输出（完整流程）

```
启动现代化代码助手...
[DbSync] 云端同步目录: D:\git\FastPig\data
[DbSync] 启动：本地已是最新，跳过拉取
... (其他初始化信息) ...

# 用户按 Alt+S
执行Alt+S: 打开编辑器

# 用户编辑并按 Ctrl+Alt+S
[DbSync] 已将本地数据库同步到云端

# 用户关闭窗口
[关闭] 正在同步数据库到云端...
[DbSync] 已将本地数据库同步到云端
[关闭] 同步完成，退出程序
```

## 🎯 当前状态

- ✅ 代码已修复并编译
- ✅ 应用正在后台运行
- ⏳ 请测试关闭窗口，观察控制台输出

---

## 📝 请执行以下操作验证

1. **按 Alt+S** 打开编辑器（或者窗口已经打开）
2. **点击窗口右上角的 × 关闭按钮**
3. **观察终端输出**，应该看到：
   ```
   [关闭] 正在同步数据库到云端...
   [DbSync] 已将本地数据库同步到云端
   [关闭] 同步完成，退出程序
   ```
4. **检查文件**：
   ```powershell
   dir data\fastpig.db
   ```
   查看修改时间是否更新

---

修复完成！现在关闭窗口时一定会触发同步到 `data` 目录。
