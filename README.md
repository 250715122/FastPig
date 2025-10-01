# FastPig 🐷⚡

> 一个现代化的快捷命令笔记管理工具，支持 Markdown、LaTeX 公式渲染和云端同步

## 📖 项目简介

FastPig 是一个基于 Java Swing 开发的桌面笔记应用，专为程序员和知识工作者设计。它将快捷命令、Markdown 编辑、LaTeX 公式渲染和云端同步完美结合，让您的笔记管理更加高效。

### ✨ 核心特性

- 🔍 **快捷命令搜索**：通过快捷键快速唤起，输入关键词即时搜索笔记
- ⚡ **实时智能提示**：输入时自动显示匹配的命令和描述
- 📝 **Markdown 支持**：完整的 Markdown 语法支持，实时预览
- 🧮 **LaTeX 公式渲染**：支持行内和独立公式，实时渲染为图片
- ☁️ **云端同步**：支持坚果云 WebDAV 同步，多设备无缝切换
- ⌨️ **全局热键**：系统级快捷键，随时随地调用
- 💾 **SQLite 存储**：本地数据库存储，快速可靠
- 🎨 **现代化界面**：简洁直观的用户界面
- 🔄 **自动保存**：3秒防抖自动保存，无需手动操作
- 🗑️ **软删除 + 撤销**：删除后60秒内可撤销

## 🚀 快速开始

### 方式一：直接运行 EXE（推荐）

1. **运行打包脚本**
   ```bash
   .\build-exe.bat
   ```

2. **启动应用**
   ```
   双击 FastPig\FastPig.exe
   ```

3. **配置云同步（可选）**
   - 复制 `config.properties.example` 为 `config.properties`
   - 填入您的坚果云账号信息

### 方式二：源码运行

1. **环境要求**
   - Java 21+
   - Maven 3.x

2. **编译运行**
   ```bash
   mvn clean package -DskipTests
   java -jar target/FastPig-0.0.1-SNAPSHOT-jar-with-dependencies.jar
   ```

## 📱 使用指南

### 快捷键

#### 全局热键（系统级，任意位置可用）

| 快捷键 | 功能 | 说明 |
|--------|------|------|
| `Alt + S` | 同步数据库到云端 | 手动触发云端同步 |
| `Alt + N` | 显示/恢复窗口 | 将窗口恢复到正常大小 |
| `Alt + M` | 最大化窗口 | 最大化应用窗口 |
| `Alt + L` | 最小化窗口 | 最小化应用窗口 |
| `Alt + Q` | 退出程序 | 同步数据并退出 |

#### 应用内快捷键（需要应用在前台）

| 快捷键 | 功能 | 说明 |
|--------|------|------|
| `Ctrl + S` | 保存当前笔记 | 手动保存（已有3秒自动保存） |
| `Alt + P` | 切换预览模式 | 开启/关闭 Markdown 预览 |
| `Alt + D` | 软删除当前笔记 | 删除后60秒内可撤销 |
| `Alt + Z` | 撤销删除 | 恢复最近一次删除的笔记 |
| `Ctrl + Home` | 跳到第一行 | 光标移到文档开头 |
| `Ctrl + L` | 选择第一行 | 选中快捷命令行 |
| `Ctrl + ` ` | 插入代码块 | 插入 Markdown 代码块标记 |
| `Ctrl + Shift + M` | 插入行内公式 | 插入 LaTeX 行内公式标记 |
| `Ctrl + Shift + L` | 插入块级公式 | 插入 LaTeX 块级公式标记 |
| `Esc` | 关闭建议列表 | 关闭自动补全弹窗 |

### 基本操作

1. **创建笔记**
   - 第一行格式：`快捷命令 描述`
   - 例如：`java 快速排序算法实现`
   - 从第二行开始编写内容
   - 💾 **自动保存**：编辑停止 3 秒后自动保存

2. **搜索笔记**
   - 在第一行输入关键词
   - 自动显示匹配的命令列表
   - 使用方向键选择，Enter 确认

3. **Markdown 编辑**
   - 支持标准 Markdown 语法
   - `Alt + P` 开启分屏预览
   - 左侧编辑，右侧实时渲染

4. **LaTeX 公式**
   - 行内公式：`$E=mc^2$`
   - 独立公式：`$$\int_0^\infty e^{-x^2}dx=\frac{\sqrt{\pi}}{2}$$`
   - 预览时自动渲染为图片

5. **系统托盘**
   - 点击窗口关闭按钮（X）→ 最小化到托盘
   - 双击托盘图标 → 恢复窗口
   - 右键托盘图标：
     - `Show Window` - 显示窗口
     - `Exit` - 退出程序

## ⚙️ 配置说明

### 坚果云同步配置

创建 `config.properties` 文件：

```properties
# 坚果云账号（邮箱）
nutstore.username=your-email@example.com

# 坚果云应用密码（不是登录密码！）
nutstore.password=your-app-password

# WebDAV 地址（一般不需要修改）
nutstore.webdav.base=https://dav.jianguoyun.com/dav/

# 同步路径（一般不需要修改）
nutstore.sync.path=FastPig/fastpig.db
```

**获取应用密码**：
1. 登录坚果云网页版
2. 进入 账户信息 → 安全选项
3. 添加应用 → 生成密码

## 🏗️ 项目结构

```
FastPig/
├── src/main/java/com/gt/
│   ├── FastPigApplication.java       # 主程序入口
│   ├── UnifiedNoteAppFrame.java      # 统一笔记界面
│   ├── NoteRepository.java           # 数据库操作
│   ├── DbSyncService.java            # 数据库同步服务
│   ├── NutstoreWebDAVSync.java       # 坚果云 WebDAV 同步
│   ├── HotKeyManager.java            # 全局热键管理
│   └── ...
├── src/main/resources/
│   ├── log4j2.xml                    # 日志配置
│   └── *.txt                         # 初始数据资源
├── FastPig/                          # 打包后的可执行文件目录
│   ├── FastPig.exe                   # Windows 可执行文件
│   ├── app/                          # 应用程序文件
│   ├── runtime/                      # 内置 Java 运行环境
│   ├── logs/                         # 日志目录
│   ├── config.properties             # 配置文件
│   └── fastpig.db                    # SQLite 数据库
├── build-exe.bat                     # Windows EXE 打包脚本
├── config.properties.example         # 配置文件模板
└── pom.xml                           # Maven 配置
```

## 🔧 技术栈

- **Java 21**：核心语言
- **Swing**：GUI 框架
- **SQLite**：本地数据库
- **Flexmark**：Markdown 渲染
- **JLaTeXMath**：LaTeX 公式渲染
- **Sardine**：WebDAV 客户端
- **Log4j2**：日志框架
- **JNativeHook**：全局热键支持

## 📝 日志说明

日志文件位置：`FastPig.exe 所在目录\logs\`

- `fastpig.log` - 所有日志（INFO 及以上）
- `fastpig-error.log` - 仅错误日志
- 自动按日期和大小滚动
- 最多保留 30 个历史日志文件

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'Add amazing feature'`
4. 推送分支：`git push origin feature/amazing-feature`
5. 提交 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者！

---

**如果这个工具对您有帮助，请给个 ⭐ 支持一下！**
