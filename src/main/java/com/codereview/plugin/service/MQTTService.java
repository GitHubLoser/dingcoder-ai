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
    
    // 多topic回调映射
    private final Map<String, Consumer<String>> topicCallbacks = new ConcurrentHashMap<>();
    
    // 添加消息缓存队列
    private final java.util.Queue<String> pendingMessages = new java.util.LinkedList<>();
    private final Object messageLock = new Object();

    /**
     * 获取MQTT服务实例
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

        try {
            String clientId = "ai-code-assistant-" + userId + "-" + System.currentTimeMillis();

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
                    LOG.warn("MQTT连接丢失，原因: " + cause.getMessage());
                    isConnected = false;
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
                                // 根据功能类型找到对应的回调函数
                                Consumer<String> callback = topicCallbacks.get(functionType);
                                if (callback != null) {
                                    // 在EDT线程中调用回调
                                    LOG.info("准备在EDT线程中执行回调，功能类型: " + functionType);
                                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            LOG.info("正在执行回调函数，功能类型: " + functionType);
                                            callback.accept(parsedContent);
                                            LOG.info("回调函数执行完成");
                                        } catch (Exception e) {
                                            LOG.error("执行回调函数时出错", e);
                                        }
                                    });
                                } else {
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
    private String parseMessageContent(String jsonContent) {
        try {
            LOG.info("开始解析JSON消息: " + jsonContent);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode msgData = rootNode.get("msgData");

            if (msgData != null) {
                JsonNode textNode = msgData.get("text");
                if (textNode != null) {
                    String text = textNode.asText();
                    LOG.info("成功解析出text内容: " + text);
                    return text;
                } else {
                    LOG.warn("msgData中未找到text字段");
                }
            } else {
                LOG.warn("JSON中未找到msgData字段");
            }

            LOG.warn("未找到msgData.text字段，原始消息: " + jsonContent);
            return null;

        } catch (Exception e) {
            LOG.error("解析JSON消息失败: " + jsonContent, e);
            return null;
        }
    }

    /**
     * 设置特定功能类型的消息回调函数
     * @param functionType 功能类型 (FUNCTION_CODE_GENERATION 或 FUNCTION_CODE_REVIEW)
     * @param callback 回调函数
     */
    public void setMessageCallback(String functionType, Consumer<String> callback) {
        synchronized (messageLock) {
            topicCallbacks.put(functionType, callback);
            
            // 如果有缓存的消息，立即处理
            if (callback != null && !pendingMessages.isEmpty()) {
                LOG.info("回调函数已设置，处理 " + pendingMessages.size() + " 条缓存消息");
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
                LOG.info("已清空缓存消息队列");
            }
        } catch (Exception e) {
            LOG.error("断开MQTT连接时出错", e);
        }
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
                        mqttClient.disconnect();
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
        return isConnected && mqttClient != null && mqttClient.isConnected();
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
     * 获取代码生成回调函数（向后兼容）
     */
    public Consumer<String> getMessageCallback() {
        return getMessageCallback(FUNCTION_CODE_GENERATION);
    }
}