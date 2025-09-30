# 坚果云同步设置指南

## 当前状态

✅ **程序同步功能正常**：
- 本地数据库：`D:\git\FastPig\fastpig.db`
- 云端数据库：`D:\git\FastPig\data\fastpig.db`
- 同步日志显示成功：
  ```
  [关闭] 正在同步数据库到云端...
  [DbSync] 已将本地数据库同步到云端
  [关闭] 同步完成，退出程序
  ```

❌ **坚果云还没同步到云端**：
- `data` 目录还未被坚果云客户端同步
- 需要手动设置坚果云同步此目录

---

## 设置方法（三选一）

### 方法1：移动整个项目到坚果云目录（最简单）

1. **找到坚果云本地目录**（通常是）：
   ```
   C:\Users\你的用户名\Nutstore
   ```

2. **移动项目**：
   ```powershell
   # 假设坚果云目录是 C:\Users\admin\Nutstore
   Move-Item D:\git\FastPig C:\Users\admin\Nutstore\FastPig
   ```

3. **以后从新位置启动**：
   ```powershell
   cd C:\Users\admin\Nutstore\FastPig
   java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
   ```

4. **验证**：
   - 打开坚果云客户端，应该能看到 `FastPig` 文件夹正在同步
   - 登录坚果云网页版：https://www.jianguoyun.com
   - 查看文件列表，应该能看到 `FastPig/data/fastpig.db`

---

### 方法2：只同步 data 目录（节省空间）

#### 使用符号链接（Windows）

1. **在坚果云目录创建软链接**：
   ```powershell
   # 假设坚果云目录是 C:\Users\admin\Nutstore
   cmd /c mklink /D "C:\Users\admin\Nutstore\FastPig_Data" "D:\git\FastPig\data"
   ```

2. **验证链接**：
   ```powershell
   Get-Item C:\Users\admin\Nutstore\FastPig_Data
   ```

3. **坚果云会自动同步这个链接指向的内容**

#### 或手动复制（不推荐，需要手动同步）

1. 复制 data 目录到坚果云：
   ```powershell
   Copy-Item D:\git\FastPig\data C:\Users\admin\Nutstore\FastPig_Data -Recurse
   ```

2. 每次使用后需要手动复制回来

---

### 方法3：使用坚果云的"选择性同步"

1. **打开坚果云客户端**
2. **右键点击系统托盘的坚果云图标**
3. **选择"同步文件夹管理"**
4. **点击"添加其他文件夹"**
5. **选择** `D:\git\FastPig\data`
6. **确认同步**

---

## 快速验证方案

如果你只是想快速测试，最简单的方法：

### 快速测试步骤

```powershell
# 1. 找到你的坚果云目录（替换为实际路径）
$NUTSTORE = "C:\Users\admin\Nutstore"

# 2. 创建测试目录
New-Item -Path "$NUTSTORE\FastPig" -ItemType Directory -Force

# 3. 复制 data 目录到坚果云
Copy-Item D:\git\FastPig\data "$NUTSTORE\FastPig\" -Recurse -Force

# 4. 等待几秒钟让坚果云同步

# 5. 登录坚果云网页版查看
# https://www.jianguoyun.com
# 应该能看到 FastPig/data/fastpig.db
```

---

## 当前文件状态

```
D:\git\FastPig\fastpig.db    24KB  2025/9/30 12:01:31  ← 本地数据库
D:\git\FastPig\data\fastpig.db  24KB  2025/9/30 12:01:31  ← 程序"云端"（实际还在本地）
```

两个文件时间戳一致，说明同步功能正常工作！

---

## 推荐操作

1. **确认你的坚果云安装目录**
2. **选择上述方法之一设置同步**
3. **重新启动程序测试**
4. **登录坚果云网页版验证文件是否上传**

---

## 常见问题

### Q: 怎么找到坚果云目录？
A: 
1. 右键坚果云托盘图标
2. 点击"打开坚果云文件夹"
3. 或者通常在：`C:\Users\你的用户名\Nutstore`

### Q: 为什么不直接让程序同步到坚果云目录？
A: 已经简化了！现在程序同步到 `data` 目录，你只需要把这个目录加入坚果云同步即可。

### Q: 我想在另一台电脑上使用怎么办？
A: 
1. 在另一台电脑安装坚果云并登录同一账号
2. 等待文件同步完成
3. 启动程序会自动从 `data` 目录拉取最新数据

---

**请告诉我你的坚果云目录路径，我可以帮你生成具体的命令！**
