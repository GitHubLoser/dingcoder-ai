package com.codereview.plugin.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;

public class MqttSubscriber {

    public static void main(String[] args) {
        String broker = "tcp://tiger-neo4j-hw-test.digiwincloud.com.cn:1883"; // MQTT Broker 地址
        String clientId = "mqtt-subscriber-client-" + System.currentTimeMillis(); // 客户端ID唯一
        String topic = "/msg/tip/591259418780224";

        String username = "admin";
        String password = "Digiwin@2024y";

        try {
            // 创建 MQTT 客户端实例
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());

            // 配置连接参数
            MqttConnectOptions options = new MqttConnectOptions();
            //是否清空session，设置为false表示服务器会保留客户端的连接记录，客户端重连之后能获取到服务器在客户端断开连接期间推送的消息
            //设置为true表示每次连接到服务端都是以新的身份
            options.setCleanSession(true);
            //断开自动重连
            options.setAutomaticReconnect(true);
            //设置连接用户名
            options.setUserName(username);
            //设置连接密码
            options.setPassword(password.toCharArray());
            //设置超时时间，单位为秒
            options.setConnectionTimeout(100);
            //设置心跳时间 单位为秒，表示服务器每隔1.5*20秒的时间向客户端发送心跳判断客户端是否在线
            options.setKeepAliveInterval(20);
            //设置遗嘱消息的话题，若客户端和服务器之间的连接意外断开，服务器将发布客户端的遗嘱信息
            options.setWill("willTopic", (clientId + "与服务器断开连接").getBytes(StandardCharsets.UTF_8), 0, false);


            // 设置回调方法
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("连接丢失，原因：" + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    System.out.println("收到消息：");
                    System.out.println("  Topic: " + topic);
                    System.out.println("  Message: " + new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 对于订阅者不涉及消息发送，不需要处理
                }
            });

            // 建立连接
            client.connect(options);
            System.out.println("已连接到 Broker: " + broker);

            // 订阅 Topic
            client.subscribe(topic, 2); // QoS 2
            System.out.println("已订阅 Topic: " + topic);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}