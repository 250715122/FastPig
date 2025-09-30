# 🔧 坚果云同步问题修复

## 问题原因

1. **启动命令错误**：PowerShell 中 `-D` 参数值不需要双引号包裹
2. **目录路径错误**：之前设置为 `FastPig/fastpig.db`，但你的坚果云中 `resource` 目录是在根目录，所以应该直接放在根目录

## 已修复内容

### 1. 修改 WebDAV 路径
```java
// 修改前
this.webdavUrl = NUTSTORE_WEBDAV_BASE + "FastPig/fastpig.db";

// 修改后（与 resource 同级）
this.webdavUrl = NUTSTORE_WEBDAV_BASE + "fastpig.db";
```

### 2. 修正启动命令
```bat
REM 错误的命令
java -Dnutstore.username="250715122@qq.com" ...

REM 正确的命令
java -Dnutstore.username=250715122@qq.com ...
```

### 3. 更新启动脚本
- ✅ `start-nutstore.bat` - 修复参数格式
- ✅ `test-sync-now.bat` - 新增详细测试步骤

---

## 🚀 现在测试同步

### 方法1：使用测试脚本（推荐）
```
双击：test-sync-now.bat
```

### 方法2：命令行
```powershell
java -Dnutstore.username=250715122@qq.com -Dnutstore.password=aqgd7w2f52j6g5sg -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

---

## 📝 测试步骤

1. **启动程序**
   - 双击 `test-sync-now.bat`
   - 查看日志，应该显示：
     ```
     [WebDAV] 坚果云同步已启用
     [WebDAV] 用户名: 250715122@qq.com
     [WebDAV] 云端路径: https://dav.jianguoyun.com/dav/fastpig.db
     ```

2. **创建测试数据**
   - 按 `Alt+S` 打开编辑器
   - 输入：`test 测试坚果云同步`
   - 按 `Ctrl+S` 保存

3. **手动同步**
   - 按 `Ctrl+Alt+S`
   - 查看日志：
     ```
     [DbSync] 已保存到本地备份
     [WebDAV] 已将本地数据库同步到坚果云
     ```

4. **验证云端**
   - 登录坚果云网页版：https://www.jianguoyun.com
   - 用户名：`250715122@qq.com`
   - 密码：你的坚果云登录密码
   - **应该在根目录看到 `fastpig.db` 文件（与 `resource` 目录同级）**

5. **测试关闭同步**
   - 关闭程序窗口
   - 查看日志：
     ```
     [关闭] 正在同步数据库到云端...
     [WebDAV] 已将本地数据库同步到坚果云
     [关闭] 同步完成，退出程序
     ```

---

## 🎯 预期结果

### 坚果云目录结构
```
坚果云根目录/
├── resource/          ← 已存在（你说同步成功了）
├── fastpig.db         ← 新增（现在应该能看到了）
└── ...其他文件
```

---

## ❓ 如果还是不行

### 检查1：查看详细错误日志
如果上传失败，会显示具体错误信息，例如：
- `401 Unauthorized` - 密码错误
- `403 Forbidden` - 权限不足
- `404 Not Found` - 路径不存在
- `Network error` - 网络问题

### 检查2：手动测试 WebDAV
使用浏览器或 WebDAV 客户端测试：
- 地址：`https://dav.jianguoyun.com/dav/`
- 用户名：`250715122@qq.com`
- 密码：`aqgd7w2f52j6g5sg`

### 检查3：查看本地备份
即使 WebDAV 失败，本地备份仍会成功：
```
D:\git\FastPig\data\fastpig.db
```

---

## 📱 多设备同步

修复后，你可以：
1. 在电脑A上使用程序，数据自动同步到坚果云根目录
2. 在电脑B上配置相同账号，启动时自动从坚果云拉取
3. 两台电脑的数据保持同步

---

**现在双击 `test-sync-now.bat` 测试吧！** 🎉
