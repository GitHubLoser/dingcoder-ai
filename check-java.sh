#!/bin/bash

# 获取Java版本
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "当前Java版本: $JAVA_VERSION"
    
    # 提取主版本号
    JAVA_MAJOR_VERSION=$(echo $JAVA_VERSION | sed -E 's/^([0-9]+).*$/\1/')
    
    # 对于旧版本Java，版本号是1.x格式
    if [ "$JAVA_MAJOR_VERSION" == "1" ]; then
        JAVA_MAJOR_VERSION=$(echo $JAVA_VERSION | sed -E 's/^1\.([0-9]+).*$/\1/')
    fi
    
    echo "Java主版本号: $JAVA_MAJOR_VERSION"
    
    # 检查是否满足要求
    if [ "$JAVA_MAJOR_VERSION" -lt 17 ]; then
        echo "警告: 当前Java版本低于要求的17版本"
        echo "请安装JDK 17或更高版本"
    else
        echo "Java版本符合要求"
    fi
else
    echo "错误: 找不到Java。请安装JDK 17或更高版本"
fi

# 检查JAVA_HOME是否设置
if [ -z "$JAVA_HOME" ]; then
    echo "警告: JAVA_HOME环境变量未设置"
else
    echo "JAVA_HOME: $JAVA_HOME"
fi

# 创建或更新gradle.properties
if [ ! -f "gradle.properties" ]; then
    echo "创建gradle.properties文件..."
    cat > gradle.properties << EOF
# 使用JVM参数指定Java版本兼容性
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -XX:+UseParallelGC

# 如果你有特定的JDK路径，可以取消注释下面这行并指定路径
# org.gradle.java.home=/path/to/your/jdk

# 构建性能优化
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
EOF
    echo "gradle.properties文件已创建"
fi

echo "检查完成。如果需要指定JDK路径，请编辑gradle.properties文件" 