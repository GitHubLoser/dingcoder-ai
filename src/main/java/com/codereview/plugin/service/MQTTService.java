package com.codereview.plugin.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.intellij.openapi.project.Project;
import org.apache.commons.text.StringEscapeUtils;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * MQTT服务类
 * 负责MQTT连接、订阅和消息处理
 */
@Service
public final class MQTTService {
    private static final Logger LOG = Logger.getInstance(MQTTService.class);

    // MQTT配置
    private static final String BROKER = "tcp://tiger-neo4j-hw-test.digiwincloud.com.cn:1883";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Digiwin@2024y";
    private static final String TOPIC_PREFIX = "/msg/tip/";
    
    // 功能类型定义
    public static final String FUNCTION_CODE_GENERATION = "code_generation";
    public static final String FUNCTION_CODE_REVIEW = "code_review";

    private MqttClient mqttClient;
    private boolean isConnected = false;
    private String currentUserSid;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // ✅ 新增：网络重连管理
    private static final int MAX_RECONNECT_ATTEMPTS = 3; // 最大重连次数
    private static final long INITIAL_RECONNECT_DELAY = 1000; // 初始重连延迟（1秒）
    private static final long MAX_RECONNECT_DELAY = 300000; // 最大重连延迟（5分钟）
    private volatile int reconnectAttempts = 0; // 当前重连次数
    private volatile long lastReconnectTime = 0; // 上次重连时间
    private volatile boolean isReconnecting = false; // 是否正在重连
    private volatile String lastConnectionError = null; // 最后一次连接错误
    
    // Project级别的回调映射
    private final Map<Project, Map<String, Consumer<String>>> projectCallbacks = new ConcurrentHashMap<>();
    
    // 向后兼容：全局回调映射
    private final Map<String, Consumer<String>> topicCallbacks = new ConcurrentHashMap<>();
    
    // 添加消息缓存队列
    private final java.util.Queue<String> pendingMessages = new java.util.LinkedList<>();
    private final Object messageLock = new Object();
    
    // 代码生成模块的msgMapping存储
    private Map<String, String> lastCodeGenerationMsgMappingMap = null;

    /**
     * 获取MQTT服务实例（Project级别）
     */
    public static MQTTService getInstance(Project project) {
        return project.getService(MQTTService.class);
    }

    /**
     * 获取MQTT服务实例（向后兼容，返回全局实例）
     */
    public static MQTTService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(MQTTService.class);
    }

    /**
     * 连接到MQTT服务器并订阅多个主题
     * @param userId 用户ID
     * @param userSid 用户SID，用于构建动态主题
     */
    public void connectAndSubscribe(String userId, String userSid) {
        this.currentUserSid = userSid;

        // ✅ 新增：确保只有一个活跃连接
        if (mqttClient != null && mqttClient.isConnected()) {
            LOG.info("检测到已有MQTT连接，先断开现有连接");
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (Exception e) {
                LOG.warn("断开现有MQTT连接时出错", e);
            }
            mqttClient = null;
        }

        try {
            // ✅ 优化：使用更稳定的客户端ID，避免重复连接
            String clientId = "ai-code-assistant-" + userId + "-global";

            LOG.info("开始连接MQTT Broker: " + BROKER);
            LOG.info("用户SID: " + userSid);

            // 创建MQTT客户端实例
            mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());

            // 配置连接参数
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setUserName(USERNAME);
            options.setPassword(PASSWORD.toCharArray());
            options.setConnectionTimeout(100);
            options.setKeepAliveInterval(20);
            options.setWill("willTopic", (clientId + "与服务器断开连接").getBytes(StandardCharsets.UTF_8), 0, false);

            // 设置回调方法
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOG.warn("MQTT连接丢失，原因: " + cause.getMessage(), cause);
                    isConnected = false;
                    lastConnectionError = cause.getMessage();
                    
                    // ✅ 改进：智能重连策略
                    if (!isReconnecting) {
                        handleConnectionLoss(cause);
                    } else {
                        LOG.info("[MQTT] 重连已在进行中，跳过本次重连请求");
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        String content = new String(message.getPayload(), StandardCharsets.UTF_8);
                        LOG.info("收到MQTT消息 - Topic: " + topic + ", Message: " + content);

                        // 根据topic确定功能类型
                        String functionType;
                        if (topic.endsWith("/" + FUNCTION_CODE_REVIEW)) {
                            functionType = FUNCTION_CODE_REVIEW;
                        } else {
                            // 原有的代码生成topic格式，默认为代码生成
                            functionType = FUNCTION_CODE_GENERATION;
                        }

                        // 解析JSON消息，提取text内容
                        String parsedContent = parseMessageContent(content);
                        if (parsedContent != null) {
                            LOG.info("消息解析结果 - 功能类型: " + functionType + ", 内容: " + parsedContent);
                            
                                                    synchronized (messageLock) {
                            // 广播给所有已注册的Project
                            boolean messageHandled = false;
                            
                            for (Map.Entry<Project, Map<String, Consumer<String>>> entry : projectCallbacks.entrySet()) {
                                Project project = entry.getKey();
                                Map<String, Consumer<String>> callbacks = entry.getValue();
                                
                                Consumer<String> callback = callbacks.get(functionType);
                                if (callback != null) {
                                    // 在对应Project的EDT线程中执行回调
                                    LOG.info("准备在Project " + project.getName() + " 的EDT线程中执行回调，功能类型: " + functionType);
                                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            LOG.info("正在执行回调函数，Project: " + project.getName() + ", 功能类型: " + functionType);
                                            callback.accept(parsedContent);
                                            LOG.info("回调函数执行完成");
                                        } catch (Exception e) {
                                            LOG.error("执行回调函数时出错", e);
                                        }
                                    });
                                    messageHandled = true;
                                }
                            }
                            
                            // 向后兼容：如果没有Project级别的回调，使用全局回调
                            if (!messageHandled) {
                                Consumer<String> callback = topicCallbacks.get(functionType);
                                if (callback != null) {
                                    LOG.info("使用全局回调，功能类型: " + functionType);
                                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            callback.accept(parsedContent);
                                        } catch (Exception e) {
                                            LOG.error("执行全局回调函数时出错", e);
                                        }
                                    });
                                    messageHandled = true;
                                }
                            }
                            
                            if (!messageHandled) {
                                LOG.warn("未找到功能类型 " + functionType + " 的回调函数");
                                // 如果没有找到对应的回调，缓存消息
                                pendingMessages.offer(parsedContent);
                            }
                        }
                        } else {
                            LOG.warn("无法解析消息内容");
                        }

                    } catch (Exception e) {
                        LOG.error("处理MQTT消息时出错", e);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 对于订阅者不需要处理
                }
            });

            // 建立连接
            mqttClient.connect(options);
            LOG.info("已连接到 MQTT Broker: " + BROKER);
            LOG.info("[MQTT] 连接建立成功。如果之前有断开，说明自动重连已恢复。");
            
            // ✅ 新增：连接成功后重置重连状态
            resetReconnectState();

            // 订阅代码生成主题（保持原有格式）
            String codeGenTopic = TOPIC_PREFIX + userSid;
            mqttClient.subscribe(codeGenTopic, 2); // QoS 2
            LOG.info("已订阅代码生成 Topic: " + codeGenTopic);
            
            // 订阅代码审查主题（新格式）
            String codeReviewTopic = TOPIC_PREFIX + userSid + "/" + FUNCTION_CODE_REVIEW;
            mqttClient.subscribe(codeReviewTopic, 2); // QoS 2
            LOG.info("已订阅代码审查 Topic: " + codeReviewTopic);

            isConnected = true;

        } catch (Exception e) {
            LOG.error("MQTT连接失败", e);
            isConnected = false;
        }
    }

    /**
     * 解析JSON消息，提取text内容
     * @param jsonContent JSON格式的消息内容
     * @return 解析出的text内容，如果解析失败返回null
     */
    public String parseMessageContent(String jsonContent) {
        try {
            LOG.info("开始解析JSON消息: " + jsonContent);
            // 用Jackson获取msgData.text字段的原始JSON字符串
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonContent);
            com.fasterxml.jackson.databind.JsonNode msgData = root.path("msgData");
            String text = msgData.path("text").toString(); // 带引号和所有转义
            if (text != null && text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length() - 1);
            }
            // 反转义，得到标准JSON字符串
            text = org.apache.commons.text.StringEscapeUtils.unescapeJava(text);
            LOG.info("[raw] unescape后text内容: " + text);
            // 新格式：text本身是一个JSON字符串，包含code和msgMapping
            try {
                cn.hutool.json.JSONObject textObj = cn.hutool.json.JSONUtil.parseObj(text);
                String code = textObj.getStr("code");
                String msgMapping = textObj.getStr("msgMapping");
                LOG.info("[多语言] 原始msgMapping内容: " + msgMapping);
                if (msgMapping != null) {
                    try {
                        String mappingJson = org.apache.commons.text.StringEscapeUtils.unescapeJava(msgMapping);
                        LOG.info("[多语言] unescape后msgMapping内容: " + mappingJson);
                        cn.hutool.json.JSONObject mappingObj = cn.hutool.json.JSONUtil.parseObj(mappingJson);
                        lastCodeGenerationMsgMappingMap = new java.util.HashMap<>();
                        for (String key : mappingObj.keySet()) {
                            lastCodeGenerationMsgMappingMap.put(key, mappingObj.getStr(key));
                            LOG.info("[多语言] 解析msgMapping: key=" + key + ", value=" + mappingObj.getStr(key));
                        }
                        LOG.info("[多语言] 解析后msgMapping Map: " + lastCodeGenerationMsgMappingMap);
                    } catch (Exception ex) {
                        LOG.warn("msgMapping不是标准JSON，无法转为Map", ex);
                        lastCodeGenerationMsgMappingMap = null;
                    }
                } else {
                    lastCodeGenerationMsgMappingMap = null;
                }
                return code != null ? code : text;
            } catch (Exception e) {
                LOG.warn("text字段不是标准JSON，直接返回text", e);
                lastCodeGenerationMsgMappingMap = null;
                return text;
            }
        } catch (Exception e) {
            LOG.error("解析JSON消息失败: " + jsonContent, e);
            lastCodeGenerationMsgMappingMap = null;
            return null;
        }
    }

    /**
     * 设置特定功能类型的消息回调函数（Project级别）
     * @param functionType 功能类型 (FUNCTION_CODE_GENERATION 或 FUNCTION_CODE_REVIEW)
     * @param callback 回调函数
     * @param project 项目实例
     */
    public void setMessageCallback(String functionType, Consumer<String> callback, Project project) {
        synchronized (messageLock) {
            projectCallbacks.computeIfAbsent(project, p -> new ConcurrentHashMap<>())
                           .put(functionType, callback);

            // 如果有缓存的消息，立即处理
            if (callback != null && !pendingMessages.isEmpty()) {
                LOG.info("Project级别回调函数已设置，处理 " + pendingMessages.size() + " 条缓存消息");
                List<String> messages = new ArrayList<>(pendingMessages);
                pendingMessages.clear();

                // 在EDT线程中处理所有缓存消息
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    for (String cachedMessage : messages) {
                        try {
                            LOG.info("处理缓存消息: " + cachedMessage);
                            callback.accept(cachedMessage);
                        } catch (Exception e) {
                            LOG.error("处理缓存消息时出错", e);
                        }
                    }
                });
            }
        }
    }

    /**
     * 设置特定功能类型的消息回调函数（向后兼容）
     * @param functionType 功能类型 (FUNCTION_CODE_GENERATION 或 FUNCTION_CODE_REVIEW)
     * @param callback 回调函数
     */
    public void setMessageCallback(String functionType, Consumer<String> callback) {
        synchronized (messageLock) {
            topicCallbacks.put(functionType, callback);

            // 如果有缓存的消息，立即处理
            if (callback != null && !pendingMessages.isEmpty()) {
                LOG.info("全局回调函数已设置，处理 " + pendingMessages.size() + " 条缓存消息");
                List<String> messages = new ArrayList<>(pendingMessages);
                pendingMessages.clear();

                // 在EDT线程中处理所有缓存消息
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    for (String cachedMessage : messages) {
                        try {
                            LOG.info("处理缓存消息: " + cachedMessage);
                            callback.accept(cachedMessage);
                        } catch (Exception e) {
                            LOG.error("处理缓存消息时出错", e);
                        }
                    }
                });
            }
        }
    }

    /**
     * 设置代码生成消息回调函数（向后兼容）
     */
    public void setMessageCallback(Consumer<String> callback) {
        setMessageCallback(FUNCTION_CODE_GENERATION, callback);
    }

    /**
     * 断开MQTT连接
     */
    public void disconnect() {
        try {
            synchronized (messageLock) {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    LOG.info("MQTT连接已断开");
                }
                
                // 关闭MQTT客户端
                if (mqttClient != null) {
                    try {
                        mqttClient.close();
                        LOG.info("MQTT客户端已关闭");
                    } catch (Exception e) {
                        LOG.error("关闭MQTT客户端时出错", e);
                    }
                    mqttClient = null;
                }
                
                isConnected = false;
                currentUserSid = null;
                topicCallbacks.clear();
                // 清空缓存消息
                pendingMessages.clear();
                // 清空代码生成msgMapping
                lastCodeGenerationMsgMappingMap = null;
                LOG.info("已清空缓存消息队列和msgMapping");
            }
        } catch (Exception e) {
            LOG.error("断开MQTT连接时出错", e);
        }
    }
    
    /**
     * 处理连接丢失事件
     */
    private void handleConnectionLoss(Throwable cause) {
        if (isReconnecting) {
            LOG.info("[MQTT] 重连已在进行中，跳过本次重连");
            return;
        }
        
        // 检查重连次数限制
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOG.error("[MQTT] 已达到最大重连次数(" + MAX_RECONNECT_ATTEMPTS + ")，停止重连。请检查网络连接或手动重连。");
            showNetworkErrorNotification("MQTT连接失败", 
                "已达到最大重连次数(" + MAX_RECONNECT_ATTEMPTS + ")，请检查网络连接或重新登录。");
            return;
        }
        
        // 计算指数退避延迟
        long delay = calculateReconnectDelay();
        reconnectAttempts++;
        
        LOG.info("[MQTT] 开始第" + reconnectAttempts + "次重连，延迟" + delay + "毫秒");
        
        // 异步重连
        new Thread(() -> {
            try {
                isReconnecting = true;
                lastReconnectTime = System.currentTimeMillis();
                
                Thread.sleep(delay);
                
                if (currentUserSid != null) {
                    LOG.info("[MQTT] 执行重连操作...");
                    connectAndSubscribe("reconnect-" + reconnectAttempts, currentUserSid);
                } else {
                    LOG.warn("[MQTT] 无法重连：用户SID为空");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("[MQTT] 重连被中断");
            } catch (Exception e) {
                LOG.error("[MQTT] 重连过程中发生错误", e);
            } finally {
                isReconnecting = false;
            }
        }, "MQTT-Reconnect-" + reconnectAttempts).start();
    }
    
    /**
     * 计算重连延迟时间（指数退避）
     */
    private long calculateReconnectDelay() {
        if (reconnectAttempts == 0) {
            return INITIAL_RECONNECT_DELAY;
        }
        
        // 指数退避：1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s
        long delay = Math.min(INITIAL_RECONNECT_DELAY * (long)Math.pow(2, reconnectAttempts - 1), MAX_RECONNECT_DELAY);
        
        // 添加随机抖动，避免多个客户端同时重连
        long jitter = (long)(Math.random() * 1000);
        return delay + jitter;
    }
    
    /**
     * 显示网络错误通知
     */
    private void showNetworkErrorNotification(String title, String message) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            try {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    message,
                    title,
                    javax.swing.JOptionPane.WARNING_MESSAGE
                );
            } catch (Exception e) {
                LOG.error("显示网络错误通知时出错", e);
            }
        });
    }
    
    /**
     * 重置重连状态（连接成功时调用）
     */
    private void resetReconnectState() {
        reconnectAttempts = 0;
        lastReconnectTime = 0;
        isReconnecting = false;
        lastConnectionError = null;
        LOG.info("[MQTT] 重连状态已重置");
    }
    
    /**
     * 手动重连（供用户主动触发）
     */
    public void manualReconnect() {
        if (currentUserSid == null) {
            LOG.warn("[MQTT] 无法手动重连：用户SID为空");
            return;
        }
        
        LOG.info("[MQTT] 用户手动触发重连");
        resetReconnectState(); // 重置重连状态
        handleConnectionLoss(new Exception("Manual reconnect"));
    }
    
    /**
     * 获取连接状态信息
     */
    public String getConnectionStatusInfo() {
        StringBuilder info = new StringBuilder();
        info.append("MQTT连接状态: ").append(isConnected() ? "已连接" : "未连接").append("\n");
        info.append("重连次数: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");
        info.append("是否正在重连: ").append(isReconnecting).append("\n");
        
        if (lastConnectionError != null) {
            info.append("最后错误: ").append(lastConnectionError).append("\n");
        }
        
        if (lastReconnectTime > 0) {
            long timeSinceLastReconnect = System.currentTimeMillis() - lastReconnectTime;
            info.append("距离上次重连: ").append(timeSinceLastReconnect / 1000).append("秒\n");
        }
        
        return info.toString();
    }
    
    /**
     * 强制清理所有资源（用于插件卸载时）
     */
    public void forceCleanup() {
        LOG.info("开始强制清理MQTT服务资源");
        try {
            synchronized (messageLock) {
                // 断开连接
                if (mqttClient != null && mqttClient.isConnected()) {
                    try {
                        // 设置较短的超时时间，避免长时间阻塞
                        mqttClient.disconnect(1000); // 1秒超时
                        LOG.info("强制断开MQTT连接");
                    } catch (Exception e) {
                        LOG.error("强制断开MQTT连接时出错", e);
                    }
                }
                
                // 关闭客户端
                if (mqttClient != null) {
                    try {
                        mqttClient.close();
                        LOG.info("强制关闭MQTT客户端");
                    } catch (Exception e) {
                        LOG.error("强制关闭MQTT客户端时出错", e);
                    }
                    mqttClient = null;
                }
                
                // 清理所有状态
                isConnected = false;
                currentUserSid = null;
                topicCallbacks.clear();
                pendingMessages.clear();
                lastCodeGenerationMsgMappingMap = null;
                
                LOG.info("MQTT服务资源清理完成");
            }
        } catch (Exception e) {
            LOG.error("强制清理MQTT服务资源时出错", e);
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        boolean connected = this.isConnected && mqttClient != null && mqttClient.isConnected();
        
        // ✅ 改进：如果检测到连接断开，使用智能重连策略
        if (!connected && currentUserSid != null && !isReconnecting) {
            LOG.warn("检测到MQTT连接断开，尝试重新连接...");
            try {
                // 使用智能重连策略，而不是简单的延迟重连
                handleConnectionLoss(new Exception("Connection check detected disconnect"));
            } catch (Exception e) {
                LOG.error("MQTT重连失败", e);
            }
        }
        
        return connected;
    }

    /**
     * 获取当前订阅的主题列表
     */
    public List<String> getSubscribedTopics() {
        List<String> topics = new ArrayList<>();
        if (currentUserSid != null) {
            topics.add(TOPIC_PREFIX + currentUserSid); // 代码生成原有格式
            topics.add(TOPIC_PREFIX + currentUserSid + "/" + FUNCTION_CODE_REVIEW); // 代码审查新格式
        }
        return topics;
    }

    /**
     * 获取当前用户SID
     */
    public String getCurrentUserSid() {
        return currentUserSid;
    }

    /**
     * 获取指定功能类型的回调函数
     */
    public Consumer<String> getMessageCallback(String functionType) {
        return topicCallbacks.get(functionType);
    }
    
    /**
     * 获取指定Project和功能类型的回调函数
     */
    public Consumer<String> getMessageCallback(String functionType, Project project) {
        Map<String, Consumer<String>> callbacks = projectCallbacks.get(project);
        return callbacks != null ? callbacks.get(functionType) : null;
    }

    /**
     * 获取代码生成回调函数（向后兼容）
     */
    public Consumer<String> getMessageCallback() {
        return getMessageCallback(FUNCTION_CODE_GENERATION);
    }

    /**
     * 从text中提取code内容
     * 格式：{"code":"代码内容","msgMapping":"..."}
     */
    private String extractCodeFromText(String text) {
        try {
            // 找到 "code":" 的开始位置
            String codePrefix = "\"code\":\"";
            int codeStart = text.indexOf(codePrefix);
            if (codeStart == -1) {
                return null;
            }
            codeStart += codePrefix.length();
            
            // 找到 ","msgMapping": 的位置作为code内容的结束
            String msgMappingPrefix = "\",\"msgMapping\":";
            int codeEnd = text.indexOf(msgMappingPrefix, codeStart);
            if (codeEnd == -1) {
                return null;
            }
            
            // 提取code内容
            return text.substring(codeStart, codeEnd);
        } catch (Exception e) {
            LOG.error("提取code内容失败", e);
            return null;
        }
    }
    
    /**
     * 从text中提取msgMapping内容
     * 格式：{"code":"...","msgMapping":"{"key":"value"}"}
     */
    private String extractMsgMappingFromText(String text) {
        try {
            // 找到 "msgMapping":" 的开始位置
            String msgMappingPrefix = "\"msgMapping\":\"";
            int mappingStart = text.indexOf(msgMappingPrefix);
            if (mappingStart == -1) {
                return null;
            }
            mappingStart += msgMappingPrefix.length();
            
            // 从后往前找，找到倒数第二个 " 的位置（最后一个"是整个JSON的结束）
            int lastQuote = text.lastIndexOf("\"");
            int secondLastQuote = text.lastIndexOf("\"", lastQuote - 1);
            
            if (secondLastQuote == -1 || secondLastQuote <= mappingStart) {
                return null;
            }
            
            // 提取msgMapping内容
            return text.substring(mappingStart, secondLastQuote);
        } catch (Exception e) {
            LOG.error("提取msgMapping内容失败", e);
            return null;
        }
    }

    /**
     * 获取代码生成的最后一次msgMapping（Map结构）
     * @return msgMapping的Map，如果没有则返回null
     */
    public Map<String, String> getCodeGenerationMsgMappingMap() {
        return lastCodeGenerationMsgMappingMap;
    }

    /**
     * 清除代码生成的msgMapping
     */
    public void clearCodeGenerationMsgMapping() {
        this.lastCodeGenerationMsgMappingMap = null;
    }


}