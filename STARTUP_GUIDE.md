# FastPig 启动指南

## 🚀 启动方式（按推荐顺序）

### 方式 1: EXE 启动（最简单）✅
```bash
.\FastPig\FastPig.exe
```
**优点**: 
- 一键启动
- 包含完整 Java 运行环境
- 自定义图标

**适用场景**: 日常使用

---

### 方式 2: 快速启动（已编译）
```bash
start.bat
```
**优点**: 
- 快速启动，无需重新编译
- 适合开发调试

**前提**: 项目已编译（target 目录存在）

---

### 方式 3: 完整启动（带编译）
```bash
run.bat
```
**优点**: 
- 自动编译最新代码
- 检查环境
- 完整日志

**前提**: 
- 安装 Maven
- Java 21 环境

**步骤**:
1. 检测管理员权限
2. 设置 Java 21 环境
3. 编译项目（mvn clean compile）
4. 启动应用

---

### 方式 4: JAR 启动
```bash
java -jar target\FastPig-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

**优点**: 
- 单个 JAR 文件
- 跨平台

**前提**: 
- Java 21 环境
- 项目已打包

---

### 方式 5: 开发模式启动
```powershell
$env:JAVA_HOME="D:\tools\java\jdk-21.0.8"
$env:PATH="D:\tools\java\jdk-21.0.8\bin;$env:PATH"
java -cp "target\classes;target\dependency\*" com.gt.FastPigApplication
```

**优点**: 
- 灵活控制环境变量
- 适合调试

---

## 📋 前置要求

### 必需
- ✅ Java 21 (JDK 21.0.8)
  - 位置: `D:\tools\java\jdk-21.0.8`
- ✅ config.properties 文件
  - 包含坚果云账号配置

### 可选
- Maven 3.9+ (仅用于编译)
- 管理员权限 (全局热键需要)

---

## 🔧 配置文件

### config.properties
```properties
nutstore.username=你的邮箱@qq.com
nutstore.password=你的应用密码
nutstore.webdav.base=https://dav.jianguoyun.com/dav/
nutstore.sync.path=FastPig/fastpig.db
```

---

## ⚠️ 常见问题

### 1. run.bat 编译失败
**原因**: Java 进程占用文件
**解决**: 
```bash
taskkill /F /IM java.exe
```

### 2. 类版本错误
```
UnsupportedClassVersionError: class file version 65.0
```
**原因**: Java 版本不正确
**解决**: 确保使用 Java 21

### 3. 找不到主类
```
ClassNotFoundException: com.gt.FastPigApplication
```
**原因**: 未编译或 classpath 错误
**解决**: 先运行 `mvn clean compile`

---

## 🎯 推荐使用流程

### 日常使用
```
FastPig\FastPig.exe
```

### 开发调试
```
1. 修改代码
2. run.bat (自动编译+启动)
3. 测试
```

### 快速重启
```
1. Ctrl+C 停止
2. start.bat (快速启动)
```

---

## 📝 说明

- **run.bat**: 修复后的完整启动脚本
  - ✅ Java 21 环境
  - ✅ 英文提示（避免编码问题）
  - ✅ 完整错误检查
  
- **start.bat**: 快速启动脚本
  - 跳过编译
  - 直接启动
  
- **FastPig.exe**: 
  - 独立可执行文件
  - 包含运行环境
  - 无需配置

---

## ✅ 验证启动成功

启动成功的标志：
1. 控制台显示 "FastPig 启动中..."
2. 窗口出现并显示统一界面
3. 系统托盘出现 FastPig 图标
4. 可以使用全局热键（Alt+S 等）

---

**最后更新**: 2025-10-01

