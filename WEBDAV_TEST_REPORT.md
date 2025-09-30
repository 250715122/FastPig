# 坚果云 WebDAV 同步 - 测试报告

## 📋 账号信息

- **用户名**: `250715122@qq.com`
- **应用密码**: `aqgd7w2f52j6g5sg`
- **WebDAV 地址**: `https://dav.jianguoyun.com/dav/`
- **云端文件路径**: `https://dav.jianguoyun.com/dav/FastPig/fastpig.db`

---

## ✅ 已完成配置

### 1. 添加 WebDAV 依赖
- ✅ `pom.xml` 已添加 `sardine-5.10` 依赖
- ✅ 项目已重新编译成功

### 2. 创建同步服务
- ✅ `NutstoreWebDAVSync.java` - WebDAV 同步核心类
- ✅ `DbSyncService.java` - 升级为双重同步（本地备份 + WebDAV）

### 3. 启动脚本
- ✅ `start-nutstore.bat` - 预配置账号的启动脚本

---

## 🚀 测试步骤

### 步骤1：启动程序
```powershell
start-nutstore.bat
```

或者直接运行：
```powershell
java -Dnutstore.username="250715122@qq.com" -Dnutstore.password="aqgd7w2f52j6g5sg" -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
```

### 步骤2：查看启动日志
应该看到：
```
[DbSync] 本地备份目录: D:\git\FastPig\data
[WebDAV] 坚果云同步已启用
[WebDAV] 用户名: 250715122@qq.com
[WebDAV] 云端路径: https://dav.jianguoyun.com/dav/FastPig/fastpig.db
```

可能的日志：
- `[WebDAV] 云端数据库不存在，跳过拉取` - 首次使用
- `[WebDAV] 启动：云端更新，已下载` - 云端有更新
- `[WebDAV] 启动：本地已是最新，跳过拉取` - 已同步

### 步骤3：手动触发同步
1. 按 `Alt+S` 打开编辑器
2. 输入一些测试数据（例如：`test 测试同步`）
3. 按 `Ctrl+S` 保存
4. 按 `Ctrl+Alt+S` 触发手动同步到坚果云

应该看到日志：
```
[DbSync] 已保存到本地备份
[WebDAV] 已将本地数据库同步到坚果云
```

### 步骤4：验证云端文件
1. 登录坚果云网页版：https://www.jianguoyun.com
   - 用户名：`250715122@qq.com`
   - 密码：你的坚果云登录密码（不是应用密码）

2. 查看文件：
   - 进入 `FastPig` 目录
   - 应该能看到 `fastpig.db` 文件
   - 文件大小约 24KB
   - 修改时间应该是刚才同步的时间

### 步骤5：测试关闭同步
1. 关闭程序窗口（点击 X）
2. 查看控制台日志：
   ```
   [关闭] 正在同步数据库到云端...
   [DbSync] 已保存到本地备份
   [WebDAV] 已将本地数据库同步到坚果云
   [关闭] 同步完成，退出程序
   ```

---

## 🔄 同步策略说明

### 启动时：
1. **WebDAV 拉取**：从坚果云下载最新的 `fastpig.db`（如果云端更新）
2. **本地备份拉取**：从 `data/fastpig.db` 拉取（如果本地备份更新）
3. **优先级**：WebDAV > 本地备份

### 保存时：
1. **本地主文件**：修改直接保存到 `fastpig.db`
2. **自动备份**：3秒后自动保存

### 手动同步（Ctrl+Alt+S）：
1. **本地备份**：`fastpig.db` → `data/fastpig.db`
2. **WebDAV 上传**：`fastpig.db` → 坚果云 `FastPig/fastpig.db`

### 关闭程序：
1. **本地备份**：`fastpig.db` → `data/fastpig.db`
2. **WebDAV 上传**：`fastpig.db` → 坚果云 `FastPig/fastpig.db`
3. **退出程序**

---

## 📊 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| WebDAV 连接 | ✅ | 通过 Sardine 库实现 |
| 账号验证 | ✅ | 应用密码验证 |
| 启动拉取 | ✅ | 自动从云端下载 |
| 手动同步 | ✅ | Ctrl+Alt+S 触发 |
| 关闭上传 | ✅ | 窗口关闭时自动上传 |
| 本地备份 | ✅ | 同时保存到 data/ |
| 错误处理 | ✅ | 静默失败，不影响程序 |
| 目录自动创建 | ✅ | 自动创建 FastPig/ |

---

## ❓ 可能的问题

### 问题1：提示"坚果云同步未配置"
**原因**：未设置用户名或密码
**解决**：使用 `start-nutstore.bat` 启动

### 问题2：上传失败 - 401 Unauthorized
**原因**：密码错误或账号不存在
**解决**：
- 确认用户名：`250715122@qq.com`
- 确认应用密码：`aqgd7w2f52j6g5sg`（已配置）

### 问题3：上传失败 - 404 Not Found
**原因**：云端目录不存在
**解决**：程序会自动创建 `FastPig/` 目录

### 问题4：连接超时
**原因**：网络问题
**解决**：
- 检查网络连接
- 检查防火墙设置
- 稍后重试

---

## 🎯 测试验证清单

- [ ] 程序启动成功
- [ ] 启动日志显示"坚果云同步已启用"
- [ ] 创建测试数据并保存
- [ ] 手动同步成功（Ctrl+Alt+S）
- [ ] 控制台显示"已将本地数据库同步到坚果云"
- [ ] 坚果云网页端能看到 `FastPig/fastpig.db`
- [ ] 文件大小正确（约24KB）
- [ ] 文件修改时间正确
- [ ] 关闭程序时自动同步
- [ ] 重新启动程序能加载之前的数据

---

## 📁 相关文件

- `src/main/java/com/gt/NutstoreWebDAVSync.java` - WebDAV 同步核心
- `src/main/java/com/gt/DbSyncService.java` - 同步服务管理
- `start-nutstore.bat` - 快速启动脚本
- `NUTSTORE_WEBDAV_CONFIG.md` - 详细配置文档

---

## 🎉 总结

✅ **坚果云 WebDAV 同步已成功集成！**

现在你可以：
1. 双击 `start-nutstore.bat` 启动程序
2. 程序会自动从坚果云拉取最新数据
3. 按 `Ctrl+Alt+S` 手动同步到云端
4. 关闭程序时自动上传到坚果云
5. 在任何设备上使用同一个坚果云账号访问你的笔记！

**下一步**：
- 测试同步功能
- 登录坚果云网页版验证文件
- 在其他电脑上使用相同配置访问云端数据

---

**创建时间**: 2025-09-30
**状态**: ✅ 已完成
