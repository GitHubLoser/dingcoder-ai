package com.codereview.plugin.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

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

    private MqttClient mqttClient;
    private boolean isConnected = false;
    private Consumer<String> messageCallback;
    private String currentTopic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取MQTT服务实例
     */
    public static MQTTService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(MQTTService.class);
    }

    /**
     * 连接到MQTT服务器并订阅主题
     * @param userId 用户ID
     * @param userSid 用户SID，用于构建动态主题
     * @param callback 消息回调函数，可以为null，后续可通过setMessageCallback设置
     */
    public void connectAndSubscribe(String userId, String userSid, Consumer<String> callback) {
        this.messageCallback = callback; // 可以为null

        try {
            String clientId = "ai-code-assistant-" + userId + "-" + System.currentTimeMillis();
            // 使用userSid动态构建主题
            currentTopic = TOPIC_PREFIX + userSid;

            LOG.info("开始连接MQTT Broker: " + BROKER);
            LOG.info("使用动态主题: " + currentTopic + " (userSid: " + userSid + ")");

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
            options.setWill("willTopic", (clientId + "与服务器断开连接").getBytes(), 0, false);

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
                        String content = new String(message.getPayload());
                        LOG.info("收到MQTT消息 - Topic: " + topic + ", Message: " + content);
                        LOG.info("当前回调函数状态: " + (messageCallback != null ? "已设置" : "未设置"));

                        // 解析JSON消息，提取text内容
                        String parsedContent = parseMessageContent(content);
                        LOG.info("消息解析结果: " + (parsedContent != null ? parsedContent : "解析失败"));

                        if (parsedContent != null && messageCallback != null) {
                            // 在EDT线程中调用回调
                            LOG.info("准备在EDT线程中执行回调...");
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    LOG.info("正在执行回调函数...");
                                    messageCallback.accept(parsedContent);
                                    LOG.info("回调函数执行完成");
                                } catch (Exception e) {
                                    LOG.error("执行回调函数时出错", e);
                                }
                            });
                        } else {
                            LOG.warn("无法处理消息: parsedContent=" + parsedContent + ", messageCallback=" + (messageCallback != null));
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

            // 订阅主题
            mqttClient.subscribe(currentTopic, 2); // QoS 2
            LOG.info("已订阅 Topic: " + currentTopic);

            isConnected = true;

        } catch (Exception e) {
            LOG.error("MQTT连接失败", e);
            isConnected = false;
            // 不抛出异常，避免影响登录流程
            // throw new RuntimeException("MQTT连接失败: " + e.getMessage(), e);
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
     * 断开MQTT连接
     */
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                LOG.info("MQTT连接已断开");
            }
            isConnected = false;
            currentTopic = null;
            messageCallback = null;
        } catch (Exception e) {
            LOG.error("断开MQTT连接时出错", e);
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * 设置消息回调函数
     */
    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    /**
     * 获取当前订阅的主题
     */
    public String getCurrentTopic() {
        return currentTopic;
    }

    /**
     * 获取当前消息回调函数
     */
    public Consumer<String> getMessageCallback() {
        return messageCallback;
    }
}