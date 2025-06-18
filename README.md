# AI代码助手插件

这是一个IntelliJ IDEA插件，通过MQTT接收AI生成的代码，并提供一键生成Java文件的功能。

## 功能特点

- 用户登录认证
- MQTT消息订阅，实时接收AI生成的代码
- 代码展示和预览
- 单个代码确认生成Java文件
- 批量生成所有Java文件
- 自动创建项目目录结构

## 快速开始

### 安装插件

1. 在IntelliJ IDEA中，打开 Settings/Preferences → Plugins
2. 点击 "Install Plugin from Disk..."
3. 选择插件的ZIP文件
4. 重启IDE

### 使用方法

1. 从主菜单选择 Tools → AI代码助手，或使用快捷键 `Ctrl+Alt+A`
2. 首次使用会要求登录（演示版本使用 `demo/password`）
3. 登录后点击"连接MQTT"开始接收AI生成的代码
4. 查看接收到的代码，点击"生成Java文件"创建单个文件
5. 或等待所有代码接收完毕后，点击"批量生成Java文件"

## 开发说明

### 构建项目

```bash
./gradlew build
```

### 运行测试

```bash
./gradlew test
```

### 打包插件

```bash
./gradlew buildPlugin
```

打包好的插件将位于 `build/distributions` 目录。

## 开发状态

当前版本包含以下功能：

- ✅ 登录认证界面
- ✅ MQTT连接框架（当前使用模拟数据）
- ✅ 代码展示界面
- ✅ 单个文件生成功能
- ✅ 批量文件生成功能
- ✅ 自动目录结构创建

## 模拟数据

当前版本使用模拟MQTT消息进行演示：
- 连接MQTT后会自动接收4条Java代码消息
- 每条消息间隔2秒
- 接收完毕后显示"所有的代码已经生成"提示
- 可测试单个和批量文件生成功能

## 技术栈

- Java 11+
- IntelliJ Platform SDK
- MQTT（Eclipse Paho）
- Gradle 构建系统

## 许可证

[MIT 许可证](LICENSE) 