# 代码助手 - CodeReplace

> 一个高效的Java代码片段搜索工具，帮助开发者快速查找常用的代码模板和命令

## 📖 项目简介

代码助手是一个基于Java Swing开发的桌面应用程序，专为程序员设计。它内置了丰富的代码片段库，涵盖Java、Python、MySQL、Oracle、Hadoop、Spark、Hive、Redis、Linux等技术栈，让您能够通过关键词快速搜索和获取所需的代码模板。

### ✨ 核心特性

- 🔍 **智能搜索**：支持关键词模糊匹配和精确搜索
- ⚡ **快速响应**：按空格键即可触发搜索，无需点击按钮
- 📚 **丰富资源**：内置9大技术栈的代码片段库
- 🎨 **三版本界面**：现代化版（推荐）、简化版、完整版
- 🧠 **智能热键管理**：自动选择最佳全局热键实现方案
- 🔄 **多库支持**：JNativeHook + JIntellitype 双重保障
- 🛡️ **系统诊断**：自动检测权限、架构、兼容性
- 💡 **命令提示**：实时命令提示和自动补全
- ⌨️ **全局热键**：系统级快捷键，随时调用代码助手
- 💾 **数据持久化**：自动保存错误日志，支持自定义代码片段
- 🚀 **即开即用**：一键启动，智能权限检测

## 🏗️ 项目结构

```
codeReplace/
├── src/main/java/com/gt/
│   ├── CodeReplace.java          # 核心搜索引擎
│   ├── SimpleSwingTest.java      # GUI界面（推荐）
│   ├── SwingTest.java            # 完整版界面（含热键功能）
│   ├── GlobalHotKeyTest.java     # 全局热键测试
│   └── Test.java                 # 功能测试类
├── src/main/resources/           # 代码片段资源库
│   ├── java.txt                  # Java代码片段
│   ├── mysql.txt                 # MySQL代码片段
│   ├── oracle.txt                # Oracle代码片段
│   ├── python.txt                # Python代码片段
│   ├── hadoop.txt                # Hadoop代码片段
│   ├── spark.txt                 # Spark代码片段
│   ├── hive.txt                  # Hive代码片段
│   ├── redis.txt                 # Redis代码片段
│   └── linux.txt                 # Linux命令片段
├── pom.xml                       # Maven依赖配置
├── run.bat                       # Windows一键启动脚本
└── README.md                     # 项目文档
```

## 🛠️ 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| Java | 8+ | 推荐使用Java 11或更高版本 |
| Maven | 3.x | 用于依赖管理和项目构建 |
| 操作系统 | Windows/Linux/macOS | 当前配置针对Windows优化 |

## 🚀 快速开始

### 方法一：一键启动（推荐）

1. **下载项目**
   ```bash
   git clone <repository-url>
   cd codeReplace
   ```

2. **运行启动脚本**
   ```bash
   # Windows用户
   双击运行 run.bat
   
   # 或在命令行中执行
   run.bat
   ```

3. **选择运行模式**
   - 输入 `1`：现代化GUI界面（推荐 - 智能热键管理）
   - 输入 `2`：简化版GUI界面（基础功能）
   - 输入 `3`：完整版GUI界面（传统JIntellitype）
   - 输入 `4`：命令行测试
   - 输入 `5`：退出程序

> **推荐**：选择选项1启动现代化版本，它会自动检测系统环境并选择最佳的热键实现方案。

### 方法二：手动运行

1. **编译项目**
   ```bash
   mvn compile
   ```

2. **启动GUI界面**
   ```bash
   # 现代化版（强烈推荐 - 智能热键管理）
   java -cp "target/classes;target/dependency/*" com.gt.ModernSwingTest
   
   # 简化版（基础功能）
   java -cp "target/classes;target/dependency/*" com.gt.SimpleSwingTest
   
   # 完整版（传统JIntellitype）
   java -cp "target/classes;target/dependency/*" com.gt.SwingTest
   ```

3. **或启动命令行版本**
   ```bash
   java -cp "target/classes;target/dependency/*" com.gt.CodeReplace
   ```

## 📱 使用指南

### 版本选择

项目提供了三个GUI版本：

| 版本 | 特性 | 推荐场景 |
|------|------|----------|
| **现代化版** (`ModernSwingTest`) | 智能热键管理、多库支持、系统诊断 | **强烈推荐**，适合所有用户 |
| **简化版** (`SimpleSwingTest`) | 基础搜索功能，稳定可靠 | 日常使用，新手用户 |
| **完整版** (`SwingTest`) | 传统JIntellitype热键、命令提示 | 高级用户，传统方案 |

### 简化版GUI界面操作

1. **启动应用**：运行后会弹出"代码助手 - 简化版"窗口
2. **输入关键词**：在文本框中输入要搜索的关键词
3. **触发搜索**：按下空格键开始搜索
4. **查看结果**：搜索结果会直接显示在文本框中
5. **复制使用**：选中需要的代码片段进行复制

### 完整版GUI界面操作

1. **启动应用**：运行后会弹出"codeAssistant"窗口
2. **输入关键词**：开始输入时会自动显示命令提示下拉框
3. **选择命令**：
   - 使用 ↑↓ 键选择提示项
   - 按 Enter 键确认选择并显示完整代码
   - 按 Esc 键关闭提示框
4. **全局热键**：
   - `Alt + N`：显示/恢复窗口并聚焦输入框
   - `Alt + M`：最大化窗口
   - `Alt + L`：最小化窗口
   - `Alt + Q`：退出程序
5. **触发搜索**：按下空格键进行模糊搜索

### 现代化版GUI界面操作（推荐）

1. **启动应用**：运行后会弹出"代码助手 - 现代版"窗口
2. **系统诊断**：启动时自动检测系统环境和权限
3. **智能热键**：自动选择最佳的全局热键实现方案
4. **输入功能**：
   - 输入关键词并按空格键搜索代码片段
   - 输入时会显示智能命令提示下拉框
   - 使用 ↑↓ 键选择提示项，Enter 确认
5. **全局热键**：
   - `Alt + N`：从任何地方显示/恢复窗口
   - `Alt + M`：最大化窗口
   - `Alt + L`：最小化窗口
   - `Alt + Q`：退出程序
6. **状态显示**：界面底部显示当前使用的热键方案

### 🎯 智能热键管理详解

现代化版本提供了革命性的智能热键管理：

#### 多库支持策略
1. **JNativeHook**（优先）：现代化跨平台热键库
   - 支持Java 8+，兼容性更好
   - 跨平台支持（Windows/macOS/Linux）
   - 活跃维护，稳定可靠

2. **JIntellitype**（备选）：传统Windows热键库
   - 经典实现，部分系统上表现良好
   - 仅支持Windows系统
   - 作为备选方案保留

3. **无热键模式**：优雅降级
   - 当热键库都不可用时，程序仍能正常运行
   - 提供友好的错误提示和建议
   - 保留所有其他功能

#### 自动诊断功能
- **权限检测**：自动检查管理员权限状态
- **系统信息**：显示操作系统、架构、Java版本
- **兼容性检查**：评估全局热键支持情况
- **错误诊断**：提供详细的故障排除建议

### 搜索技巧

- **模糊搜索**：输入关键词的一部分即可匹配相关内容
- **精确搜索**：使用完整的关键词获得精确结果
- **多次搜索**：可以连续搜索不同关键词
- **错误处理**：如果搜索出错，会生成error.txt文件供排查

## 🔍 支持的关键词列表

### Java开发
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `javadwj` | 读取文件操作 | 文件IO操作代码 |
| `javadml` | 读取目录操作 | 目录遍历代码 |
| `xrwj` | 写入文件操作 | 文件写入代码 |
| `hqcxlj` | 获取程序路径 | 路径获取代码 |
| `qjrj` | 全局热键 | 热键监听代码 |
| `javabqtsk` | 自动补全提示框 | GUI组件代码 |
| `zzbds` | 正则表达式 | 正则匹配代码 |

### 数据库相关
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `mysqlrq` | MySQL日期函数 | 日期时间处理SQL |
| `mysqljb` | MySQL建表语句 | CREATE TABLE语法 |
| `mysqlzd` | MySQL字段操作 | ALTER TABLE语法 |
| `mysqlsy` | MySQL索引操作 | 索引创建和管理 |
| `mysqlcxs` | MySQL查询锁 | 锁状态查询SQL |
| `mysqlrownum` | MySQL行号 | 自增行号实现 |
| `mysqlfzpx` | MySQL分组排序 | GROUP BY和ORDER BY |
| `mysqlplcr` | MySQL批量插入 | 批量INSERT语句 |

### Oracle数据库
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `oraclerqjs` | Oracle日期计算 | 日期运算函数 |
| `oraclerqhs` | Oracle日期函数 | 日期格式化函数 |

### Python开发
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `pythonrqzsjc` | 日期转时间戳 | datetime转timestamp |
| `pytonsjczrq` | 时间戳转日期 | timestamp转datetime |
| `pytondqrqgsh` | 当前日期格式化 | 日期格式化代码 |

### 大数据技术
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `hadoopdj` | Hadoop环境搭建 | 集群配置代码 |
| `sparksjy` | Spark数据源 | 数据读取代码 |
| `hivexwb` | Hive行为表 | 表结构定义 |
| `hivebgb` | Hive曝光表 | 数据分析SQL |

### 系统运维
| 关键词 | 功能描述 | 示例 |
|--------|----------|------|
| `linuxlmysql` | Linux连接MySQL | 数据库连接命令 |
| `linuxtop` | top命令详解 | 系统监控命令 |
| `redis` | Redis相关操作 | 缓存操作命令 |

## 🎯 高级功能

### 自定义代码片段

您可以通过编辑`src/main/resources/`目录下的txt文件来添加自己的代码片段：

1. **片段格式**：
   ```
   ∈关键词:描述信息∈
   代码内容行1
   代码内容行2
   ...
   ```

2. **添加步骤**：
   - 找到对应技术栈的txt文件
   - 按照格式添加新的代码片段
   - 重新编译并运行程序

### 📄 数据保存功能

项目具有以下数据保存能力：

#### 错误日志保存
- **自动保存**：当程序出现异常时，会自动生成错误日志
- **保存位置**：
  - 简化版：项目根目录下的 `error.txt`
  - 完整版：`A:\error.txt`（可根据需要修改路径）
- **内容包含**：完整的异常堆栈信息，便于问题排查

#### 代码片段持久化
- **资源文件**：所有代码片段存储在 `src/main/resources/*.txt` 文件中
- **支持编辑**：可直接修改txt文件来更新代码片段
- **格式标准**：使用 `∈关键词:描述∈` 格式标记代码片段
- **自动加载**：程序启动时自动加载所有资源文件

#### 用户数据扩展
虽然当前版本主要保存错误日志，但项目已具备文件读写能力，可扩展以下功能：
- 搜索历史记录
- 用户偏好设置
- 自定义快捷键配置
- 使用统计数据

### 命令行模式

除了GUI界面，还支持命令行模式进行测试：

```bash
java -cp "target/classes;target/dependency/*" com.gt.CodeReplace
```

## 🔧 故障排除

### 常见问题及解决方案

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 编译失败 | Java/Maven环境未配置 | 检查JAVA_HOME和PATH环境变量 |
| GUI无法启动 | 依赖缺失或版本不兼容 | 重新执行`mvn compile` |
| 搜索无结果 | 资源文件缺失 | 确认`src/main/resources`目录完整性 |
| 中文乱码 | 编码设置问题 | 确保IDE和系统编码为UTF-8 |
| 命令提示不显示 | 使用了简化版GUI | 切换到完整版：`com.gt.SwingTest` |
| 全局热键DLL加载失败 | DLL文件未正确复制到target目录 | 执行`mvn clean compile`重新构建项目 |
| 全局热键注册失败 | JIntellitype库版本兼容性问题 | 项目已优化，会显示友好错误信息并继续运行 |
| 错误日志路径问题 | 硬编码路径不存在 | 修改源码中的错误日志保存路径 |

### ⚠️ 重要修复说明

**全局热键功能修复（2024年更新）**：

1. **DLL文件问题**：修复了JIntellitype.dll文件未正确复制到编译目录的问题
   - 更新了Maven配置，自动复制DLL文件
   - 现在执行`mvn clean compile`会自动处理DLL文件

2. **异常处理改进**：完整版GUI现在包含完善的错误处理
   - 如果全局热键注册成功，会显示确认信息
   - 如果注册失败，会显示友好的错误信息并继续运行其他功能

3. **下拉列表功能修复**：
   - 修复了命令提示列表初始化为空的问题
   - 改进了动态命令匹配逻辑
   - 支持实时输入提示和键盘导航

### 调试模式

如果遇到问题，可以：

1. **查看错误日志**：程序会在根目录生成`error.txt`文件
2. **运行测试**：使用命令行模式进行功能测试
3. **检查资源**：确认所有txt文件存在且格式正确

## 🏗️ 技术架构

### 核心依赖

```xml
<dependencies>
    <!-- 日志框架 -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-core</artifactId>
        <version>2.6.1</version>
    </dependency>
    
    <!-- 全局热键支持（可选） -->
    <dependency>
        <groupId>com.melloware</groupId>
        <artifactId>jintellitype</artifactId>
        <version>1.3.9</version>
    </dependency>
    
    <!-- 本地接口支持 -->
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna-platform</artifactId>
        <version>5.4.0</version>
    </dependency>
</dependencies>
```

### 设计模式

- **单例模式**：搜索引擎核心类
- **观察者模式**：GUI事件处理
- **策略模式**：不同的搜索算法

## 🤝 贡献指南

欢迎贡献代码片段和功能改进！

1. **Fork项目**
2. **创建功能分支**：`git checkout -b feature/new-snippets`
3. **提交更改**：`git commit -am 'Add some snippets'`
4. **推送分支**：`git push origin feature/new-snippets`
5. **提交Pull Request**

### 贡献内容

- 新的代码片段
- 界面优化
- 性能改进
- 文档完善
- Bug修复

## 📄 许可证

本项目采用MIT许可证，详见[LICENSE](LICENSE)文件。

## 🙏 致谢

感谢所有为这个项目贡献代码片段和改进建议的开发者们！

---

**如果这个工具对您有帮助，请给个⭐支持一下！** 