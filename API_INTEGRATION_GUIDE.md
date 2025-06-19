# API集成功能指南

## 新增功能概述

本次更新为鼎码智辅插件添加了验证规格API集成功能，实现了以下特性：

### 1. API调用功能
- **接口地址**: `https://igws-atotr-test.apps.digiwincloud.com.cn/restful/standard/iais/esp/executeEspRequest`
- **调用方式**: 异步调用，不等待返回结果
- **认证方式**: 使用用户登录后的token进行认证

### 2. 请求格式
```json
{
    "serviceName": "api.validate.spec.entry",
    "params": {
        "context": {
            "agentCode": "GetVerificationSpec",
            "agentVersion": "1",
            "applicationCode": "CodeValidatorGen",
            "tenantId": "digiwinBmOpt"
        },
        "data": [
            {
                "text": "用户输入的内容",
                "filePath": "当前选中的目录路径"
            }
        ]
    }
}
```

### 3. 用户交互流程

1. **发送消息**: 用户点击发送按钮或按回车键
2. **API调用**: 系统自动调用验证规格API
3. **按钮禁用**: 发送按钮变为"生成中..."状态并禁用
4. **等待消息**: 系统等待MQTT消息
5. **恢复按钮**: 收到"所有代码均已生成"消息后恢复发送按钮

### 4. 技术实现

#### 新增类
- `ValidateSpecService`: 负责API调用的服务类
- 使用Spring RestTemplate进行HTTP请求
- 异步调用，避免阻塞UI线程

#### 修改的类
- `ChatToolWindowPanel`: 添加了发送按钮状态管理
- 集成了API调用逻辑
- 增强了MQTT消息处理

### 5. 状态管理

#### 发送按钮状态
- **正常状态**: "发送" - 可点击
- **等待状态**: "生成中..." - 禁用
- **恢复条件**: 收到"所有代码均已生成"消息

#### 等待状态标志
- `isWaitingForGeneration`: 布尔标志，控制发送按钮状态
- 在退出登录时自动重置

### 6. 错误处理

- API调用失败不会影响UI状态
- 网络异常通过日志记录
- 用户未登录时阻止API调用

### 7. 兼容性

- 不影响原有功能
- 保持向后兼容
- 新增功能可独立开关

## 使用说明

1. 确保用户已登录
2. 在项目视图中选择目标目录（可选）
3. 在输入框中输入API名称或校验器名称
4. 点击发送按钮
5. 等待代码生成完成
6. 查看生成的代码并选择是否生成Java文件

## 注意事项

- API调用是异步的，不会阻塞UI
- 发送按钮在等待期间会被禁用
- 只有收到特定消息才会恢复发送按钮
- 退出登录会重置所有状态 