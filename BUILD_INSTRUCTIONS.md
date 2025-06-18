# 构建与安装说明

## 构建前准备

确保你的系统中已安装Java 17或更高版本。你可以通过以下命令检查当前Java版本：

```bash
java -version
```

如果版本低于17，你需要安装更高版本的JDK。

### Java版本兼容性问题解决

如果在构建过程中遇到Java兼容性错误（如需要Java 11但使用了Java 8），可以通过以下方法解决：

1. 编辑`gradle.properties`文件，指定你的JDK路径：

```properties
# 取消注释下面这行并设置为你的JDK路径
org.gradle.java.home=/path/to/your/jdk
```

2. 或者，你可以设置JAVA_HOME环境变量指向正确的JDK：

```bash
# Linux/macOS
export JAVA_HOME=/path/to/your/jdk
# Windows
set JAVA_HOME=C:\path\to\your\jdk
```

## 构建插件

```bash
# 清理并构建插件
./gradlew clean buildPlugin
```

构建成功后，插件会生成在：
```
build/distributions/ai-codereview-1.0.0.zip
```

## 安装插件到IDEA

1. 打开IntelliJ IDEA 2024.1或更高版本
2. 进入 `Settings/Preferences` -> `Plugins`
3. 点击齿轮图标，选择 `Install Plugin from Disk...`
4. 选择上面生成的zip文件
5. 重启IDEA

## 使用插件

1. 安装后，打开一个代码文件
2. 从主菜单选择 `Tools` -> `AI Code Review`，或使用快捷键 `Ctrl+Alt+R`
3. 插件会在右侧打开一个工具窗口，显示代码审查结果

## 常见问题解决

### 构建失败（Java兼容性）

如果出现如下错误：
```
> No matching variant of org.jetbrains.intellij.plugins:gradle-intellij-plugin:1.x.x was found. 
The consumer was configured to find a library for use during runtime, compatible with Java 8...
```

这表明Gradle使用了错误的Java版本。请参考上面的"Java版本兼容性问题解决"部分。

### 其他构建问题

如果构建过程中遇到其他问题，可以尝试以下步骤：

1. 确保Gradle Wrapper正确设置：
```bash
# 重新下载Gradle Wrapper JAR
curl -L -o gradle/wrapper/gradle-wrapper.jar "https://raw.githubusercontent.com/gradle/gradle/v7.6.0/gradle/wrapper/gradle-wrapper.jar"

# 赋予执行权限
chmod +x gradlew
```

2. 使用更详细的日志查看问题：
```bash
./gradlew buildPlugin --debug
```

3. 清理构建目录后重试：
```bash
./gradlew clean
./gradlew buildPlugin
``` 