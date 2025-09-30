# 坚果云 WebDAV 同步配置指南

## 📌 重要提示

你用的是**坚果云网页端**，没有本地客户端。因此我们使用**坚果云 WebDAV 接口**来实现真正的云同步。

---

## 🔑 第一步：获取坚果云应用密码

### 1. 登录坚果云网页版
访问：https://www.jianguoyun.com

### 2. 进入账户设置
- 点击右上角头像
- 选择"账户信息"或"安全设置"

### 3. 生成应用密码
- 找到"第三方应用管理"或"添加应用密码"
- 应用名称填写：`FastPig`
- 点击"生成密码"
- **复制生成的密码**（类似：`xxxx-xxxx-xxxx-xxxx`）

⚠️ **注意**：
- **应用密码 ≠ 登录密码**
- 应用密码只显示一次，请立即复制保存
- 如果忘记了可以重新生成

---

## ⚙️ 第二步：配置程序

### 方法1：使用启动参数（推荐）

```powershell
java -Dnutstore.username="你的坚果云邮箱" -Dnutstore.password="应用密码" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

**示例**：
```powershell
java -Dnutstore.username="zhangsan@example.com" -Dnutstore.password="abcd-1234-efgh-5678" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

### 方法2：使用环境变量

#### PowerShell:
```powershell
$env:NUTSTORE_USERNAME = "你的坚果云邮箱"
$env:NUTSTORE_PASSWORD = "应用密码"
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

#### CMD:
```cmd
set NUTSTORE_USERNAME=你的坚果云邮箱
set NUTSTORE_PASSWORD=应用密码
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

---

## 📂 第三步：确认坚果云目录

1. **登录坚果云网页版**：https://www.jianguoyun.com
2. **确认存在 `FastPig` 目录**（你已经有了）
3. 程序会自动上传到：`FastPig/fastpig.db`

---

## 🚀 第四步：测试同步

### 1. 重新编译（添加了 WebDAV 依赖）

```powershell
mvn clean package -DskipTests
```

### 2. 启动程序（配置账号）

```powershell
# 替换为你的真实账号和应用密码
java -Dnutstore.username="你的邮箱" -Dnutstore.password="应用密码" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

### 3. 检查启动日志

应该看到：
```
[WebDAV] 坚果云同步已启用
[WebDAV] 用户名: 你的邮箱
[WebDAV] 云端路径: https://dav.jianguoyun.com/dav/FastPig/fastpig.db
[WebDAV] 启动：云端更新，已下载
```

或者：
```
[WebDAV] 云端数据库不存在，跳过拉取
```

### 4. 手动触发上传

- 按 `Ctrl+Alt+S` 触发手动同步
- 应该看到日志：`[WebDAV] 已将本地数据库同步到坚果云`

### 5. 验证云端文件

1. 登录坚果云网页版：https://www.jianguoyun.com
2. 进入 `FastPig` 目录
3. 应该能看到 `fastpig.db` 文件（24KB）
4. 查看文件修改时间，应该是刚刚上传的时间

---

## 🔄 同步策略

### 启动时：
1. 从坚果云 WebDAV 拉取最新数据库（如果配置了）
2. 同时从本地 `data/` 目录拉取备份

### Ctrl+Alt+S 或关闭程序时：
1. 先保存到本地 `data/fastpig.db`（本地备份）
2. 再上传到坚果云 WebDAV（云端）

---

## 🛠️ 快速启动脚本

创建 `start-with-nutstore.bat`：

```bat
@echo off
chcp 65001 >nul
echo 正在启动 FastPig（坚果云同步版）...
echo.
echo 请确保已设置环境变量：
echo   NUTSTORE_USERNAME=你的邮箱
echo   NUTSTORE_PASSWORD=应用密码
echo.
java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
pause
```

或者直接在脚本里硬编码（不推荐，安全性低）：

```bat
@echo off
chcp 65001 >nul
echo 正在启动 FastPig（坚果云同步版）...
java -Dnutstore.username="你的邮箱" -Dnutstore.password="应用密码" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
pause
```

---

## ❓ 常见问题

### Q1: 提示"坚果云同步未配置"
A: 需要设置 `nutstore.username` 和 `nutstore.password`

### Q2: 上传失败，提示"401 Unauthorized"
A: 
- 检查用户名（邮箱）是否正确
- 检查是否使用了**应用密码**而非登录密码
- 重新生成应用密码

### Q3: 上传失败，提示"404 Not Found"
A: 
- 登录坚果云网页版
- 手动创建 `FastPig` 目录
- 或者程序会自动创建

### Q4: 想临时禁用 WebDAV 同步
A: 不设置用户名和密码，程序会自动跳过 WebDAV 同步，只进行本地备份

### Q5: 在另一台电脑上使用
A:
1. 在新电脑上 clone 项目
2. 配置同样的坚果云账号和应用密码
3. 启动程序，会自动从云端下载最新数据库

---

## 📝 配置模板

### 环境变量配置（推荐）

在 Windows 系统环境变量中添加：

```
变量名: NUTSTORE_USERNAME
变量值: 你的坚果云邮箱

变量名: NUTSTORE_PASSWORD
变量值: 应用密码（从坚果云生成）
```

### 临时环境变量（当前 PowerShell 会话）

```powershell
$env:NUTSTORE_USERNAME = "your-email@example.com"
$env:NUTSTORE_PASSWORD = "abcd-1234-efgh-5678"
```

---

## ✅ 验证清单

- [ ] 已登录坚果云网页版
- [ ] 已生成应用密码并保存
- [ ] 确认坚果云中有 `FastPig` 目录
- [ ] 已配置 `nutstore.username`
- [ ] 已配置 `nutstore.password`
- [ ] 已重新编译项目（`mvn clean package -DskipTests`）
- [ ] 启动日志显示"坚果云同步已启用"
- [ ] 手动同步成功（`Ctrl+Alt+S`）
- [ ] 坚果云网页端能看到 `fastpig.db` 文件

---

**现在就去坚果云生成应用密码，然后配置启动参数吧！** 🎉
