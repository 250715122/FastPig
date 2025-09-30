# 坚果云同步 - 简化版说明

## ✅ 已完成简化

现在**不需要任何配置**，数据库自动同步到项目的 `data` 目录！

## 📁 文件位置

```
FastPig/
├── fastpig.db          ← 本地数据库
└── data/
    └── fastpig.db      ← 云端数据库（自动同步）
```

## 🔄 同步方式

### 自动同步
- ✅ **启动时**：自动从 `data/fastpig.db` 拉取到本地
- ✅ **关闭时**：自动将本地上传到 `data/fastpig.db`

### 手动同步
- 🔑 按 **Ctrl+Alt+S** 立即同步到 `data` 目录
- 📊 底部状态栏显示同步状态

## 🧪 测试验证

### 刚才的测试结果：

```
[DbSync] 云端同步目录: D:\git\FastPig\data
[DbSync] 已将本地数据库同步到云端
```

✅ 文件已创建：
```
D:\git\FastPig\data\fastpig.db (24KB)
```

## 📝 接下来的步骤

### 1. 测试手动同步（Ctrl+Alt+S）

应用已启动，现在请：
1. 按 **Alt+S** 打开编辑器
2. 输入一些测试数据并保存（Ctrl+S）
3. 按 **Ctrl+Alt+S** 触发手动同步
4. 观察底部状态栏显示 "已同步到云端"
5. 检查 `data` 目录下的文件时间戳是否更新

### 2. 设置坚果云同步 `data` 目录

由于 `data` 目录在项目内，你有两个选择：

**方案A - 将整个 FastPig 项目放到坚果云**
```
C:\Users\你的用户名\Nutstore\FastPig\
├── src/
├── target/
├── fastpig.db
└── data/
    └── fastpig.db  ← 坚果云自动同步这个文件
```

**方案B - 只同步 data 目录**
1. 在坚果云客户端右键 `FastPig\data` 目录
2. 选择"添加到坚果云同步"
3. 这样只有 `data` 目录会被同步到云端

## 🌐 多设备使用

现在非常简单：
1. 将项目（或至少 `data` 目录）添加到坚果云同步
2. 在其他设备上克隆项目或同步 `data` 目录
3. 启动程序会自动拉取 `data/fastpig.db` 到本地
4. 关闭程序会自动上传到 `data/fastpig.db`

## 🎯 验证同步是否工作

### 方法1: 查看控制台输出

启动时应该看到：
```
[DbSync] 云端同步目录: D:\git\FastPig\data
[DbSync] 启动：云端较新，已覆盖本地
或
[DbSync] 启动：本地已是最新，跳过拉取
```

### 方法2: 检查文件时间戳

```bash
# PowerShell
Get-ChildItem fastpig.db, data\fastpig.db | Select-Object Name, LastWriteTime
```

两个文件的时间戳应该很接近（同步后）

### 方法3: 按 Ctrl+Alt+S 并观察

- 底部状态栏会显示："正在同步到云端…" → "已同步到云端"
- `data\fastpig.db` 的修改时间会更新

## ⚠️ 注意事项

1. **确保 data 目录在坚果云同步范围内**
2. **多设备时避免同时运行程序**，防止冲突
3. **坚果云版本历史**可以恢复误操作的数据

## 🚀 快速开始

```bash
# 1. 编译（如果还没编译）
mvn -q -DskipTests package

# 2. 启动程序
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest

# 3. 使用
Alt+S     - 打开编辑器
Ctrl+S    - 保存笔记
Ctrl+Alt+S - 同步到云端（data目录）

# 4. 检查
dir data\fastpig.db  # 查看云端文件
```

---

**现在的优势**：
- ✅ 无需配置环境变量
- ✅ 云端文件路径清晰可见（`data/fastpig.db`）
- ✅ 易于添加到坚果云同步
- ✅ 多设备协作更简单

**当前状态**：
- ✅ 代码已修改并编译
- ✅ 测试已通过
- ✅ 应用正在后台运行
- ⏳ 等待你测试 Ctrl+Alt+S 快捷键
