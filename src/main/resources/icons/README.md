# FastPig 图标文件

## 📁 目录结构

```
icons/
├── FastPig.ico          # Windows ICO 图标（用于 EXE）
├── FastPig.png          # 主图标 PNG 格式（256x256）
├── fastpig-16.png       # 16x16 小图标
├── fastpig-32.png       # 32x32 图标（窗口标题栏）
├── fastpig-48.png       # 48x48 图标
├── fastpig-64.png       # 64x64 图标
├── fastpig-128.png      # 128x128 图标
├── fastpig-256.png      # 256x256 大图标
└── README.md            # 本文件

## 🎨 图标设计

### 设计元素
- **背景**: 蓝色渐变圆角矩形（从 #2980b9 到 #34495e）
- **主元素**: 白色字母 "F"（代表 FastPig）
- **装饰**: 黄色小闪电图标（表示"快速"）
- **效果**: 顶部光泽效果，字母阴影

### 设计理念
1. **简洁明了**: 一眼就能识别
2. **专业感**: 渐变和光泽效果
3. **品牌识别**: F + 闪电 = Fast（快）

## 🔧 使用方式

### 1. Windows EXE 图标
```batch
# build-exe.bat 中使用
jpackage --icon target\FastPig.ico ...
```

图标文件：`FastPig.ico`

### 2. 窗口标题栏图标
```java
// UnifiedNoteAppFrame.java 中加载
InputStream iconStream = getClass().getResourceAsStream("/icons/fastpig-32.png");
BufferedImage iconImage = ImageIO.read(iconStream);
setIconImage(iconImage);
```

图标文件：`fastpig-32.png`

### 3. 系统托盘图标
```java
// 使用 16x16 图标
Image trayImage = createTrayIcon(); // 或从 fastpig-16.png 加载
TrayIcon trayIcon = new TrayIcon(trayImage, "FastPig");
```

图标文件：`fastpig-16.png` 或动态生成

### 4. macOS/Linux 图标
- macOS: 需要 `.icns` 格式（可从 PNG 转换）
- Linux: 直接使用 PNG 格式

## 🔄 重新生成图标

如果需要修改图标设计，运行以下命令：

```bash
# 1. 生成 PNG 图标
javac -d target/classes src/main/java/com/gt/IconGenerator.java
java -cp target/classes com.gt.IconGenerator

# 2. 转换为 ICO
javac -d target/classes src/main/java/com/gt/PngToIcoConverter.java
java -cp target/classes com.gt.PngToIcoConverter
```

或者使用在线工具：
- PNG 转 ICO: https://convertio.co/zh/png-ico/
- ICO 编辑器: https://www.xiconeditor.com/

## 📏 图标尺寸说明

| 尺寸 | 用途 |
|------|------|
| 16x16 | 系统托盘、收藏夹图标 |
| 32x32 | 窗口标题栏、小图标 |
| 48x48 | 文件管理器列表视图 |
| 64x64 | 文件管理器大图标视图 |
| 128x128 | 高分辨率显示 |
| 256x256 | macOS、高清显示 |

## 🎯 最佳实践

1. **保持一致性**: 所有尺寸使用相同的设计元素
2. **可识别性**: 小尺寸（16x16）也要清晰可辨
3. **背景透明**: PNG 使用透明背景（ICO 无需）
4. **高质量**: 使用抗锯齿，避免锯齿边缘

## 📝 修改记录

- 2025-10-01: 初始版本，蓝色渐变 + 白色 F + 黄色闪电设计

