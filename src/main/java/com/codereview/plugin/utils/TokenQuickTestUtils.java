package com.codereview.plugin.utils;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Token快速测试工具类
 * 提供即时测试Token自动刷新功能的方法，无需等待长时间
 */
public class TokenQuickTestUtils {
    
    private static final Logger LOG = Logger.getInstance(TokenQuickTestUtils.class);
    
    /**
     * 快速测试Token自动刷新功能（模拟即将过期）
     */
    public static void quickTestWithSimulation() {
        LOG.info("=== 开始Token自动刷新快速测试（模拟即将过期） ===");
        
        AuthService authService = AuthService.getInstance();
        
        // 1. 检查当前登录状态
        LOG.info("1. 检查当前登录状态");
        boolean isLoggedIn = authService.isLoggedIn();
        LOG.info("当前登录状态: " + (isLoggedIn ? "已登录" : "未登录"));
        
        if (!isLoggedIn) {
            LOG.warn("用户未登录，无法进行Token刷新测试");
            return;
        }
        
        // 2. 获取当前Token状态
        LOG.info("2. 获取当前Token状态");
        double originalRemainingMinutes = authService.getTokenRemainingMinutes();
        LOG.info("原始Token剩余时间: " + String.format("%.2f", originalRemainingMinutes) + " 分钟");
        
        // 3. 模拟Token即将过期（通过反射修改过期时间）
        LOG.info("3. 模拟Token即将过期");
        simulateTokenExpiringSoon();
        
        // 4. 检查模拟后的状态
        double simulatedRemainingMinutes = authService.getTokenRemainingMinutes();
        boolean isExpiringSoon = authService.isTokenExpiringSoon();
        LOG.info("模拟后Token剩余时间: " + String.format("%.2f", simulatedRemainingMinutes) + " 分钟");
        LOG.info("是否即将过期: " + (isExpiringSoon ? "是" : "否"));
        
        // 5. 触发手动检查
        LOG.info("4. 触发手动检查");
        authService.manualCheckToken();
        
        // 6. 等待一段时间让刷新完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            LOG.error("等待刷新时被中断", e);
        }
        
        // 7. 检查刷新结果
        double newRemainingMinutes = authService.getTokenRemainingMinutes();
        LOG.info("5. 检查刷新结果");
        LOG.info("刷新后Token剩余时间: " + String.format("%.2f", newRemainingMinutes) + " 分钟");
        
        if (newRemainingMinutes > simulatedRemainingMinutes) {
            LOG.info("✅ Token自动刷新测试成功！");
        } else {
            LOG.warn("⚠️ Token自动刷新可能失败");
        }
        
        // 8. 恢复原始状态
        LOG.info("6. 恢复原始状态");
        restoreOriginalTokenState();
        
        LOG.info("=== Token自动刷新快速测试完成 ===");
    }
    
    /**
     * 模拟Token即将过期
     */
    private static void simulateTokenExpiringSoon() {
        try {
            // 使用反射访问私有字段
            Field tokenExpireTimeField = AuthService.class.getDeclaredField("tokenExpireTime");
            tokenExpireTimeField.setAccessible(true);
            
            // 设置过期时间为1分钟后
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (1 * 60 * 1000); // 1分钟后过期
            tokenExpireTimeField.set(null, expireTime);
            
            LOG.info("已模拟Token在1分钟后过期");
            
        } catch (Exception e) {
            LOG.error("模拟Token过期失败", e);
        }
    }
    
    /**
     * 恢复原始Token状态
     */
    private static void restoreOriginalTokenState() {
        try {
            // 使用反射访问私有字段
            Field tokenExpireTimeField = AuthService.class.getDeclaredField("tokenExpireTime");
            tokenExpireTimeField.setAccessible(true);
            
            // 设置过期时间为55分钟后
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (55 * 60 * 1000); // 55分钟后过期
            tokenExpireTimeField.set(null, expireTime);
            
            LOG.info("已恢复Token状态为55分钟后过期");
            
        } catch (Exception e) {
            LOG.error("恢复Token状态失败", e);
        }
    }
    
    /**
     * 测试强制刷新功能
     */
    public static void testForceRefresh() {
        LOG.info("=== 开始强制刷新测试 ===");
        
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            LOG.warn("用户未登录，无法测试强制刷新");
            return;
        }
        
        // 记录刷新前状态
        double beforeRemainingMinutes = authService.getTokenRemainingMinutes();
        LOG.info("刷新前Token剩余时间: " + String.format("%.2f", beforeRemainingMinutes) + " 分钟");
        
        // 执行强制刷新
        LOG.info("执行强制刷新...");
        authService.forceRefreshToken();
        
        // 等待刷新完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            LOG.error("等待刷新时被中断", e);
        }
        
        // 检查刷新结果
        double afterRemainingMinutes = authService.getTokenRemainingMinutes();
        LOG.info("刷新后Token剩余时间: " + String.format("%.2f", afterRemainingMinutes) + " 分钟");
        
        if (afterRemainingMinutes > beforeRemainingMinutes) {
            LOG.info("✅ 强制刷新成功");
        } else {
            LOG.warn("⚠️ 强制刷新可能失败");
        }
        
        LOG.info("=== 强制刷新测试完成 ===");
    }
    
    /**
     * 测试登录功能
     */
    public static void testLogin() {
        LOG.info("=== 开始登录测试 ===");
        
        AuthService authService = AuthService.getInstance();
        
        // 使用测试账号登录
        String testUsername = "test_user";
        String testPassword = "test_password";
        
        LOG.info("尝试登录，用户名: " + testUsername);
        
        Map<String, Object> loginResult = authService.login(testUsername, testPassword);
        
        if (loginResult != null && loginResult.get("token") != null) {
            LOG.info("✅ 登录成功");
            String token = String.valueOf(loginResult.get("token"));
            LOG.info("Token: " + token.substring(0, Math.min(token.length(), 10)) + "...");
            LOG.info("UserSid: " + loginResult.get("userSid"));
            
            // 检查登录状态
            boolean isLoggedIn = authService.isLoggedIn();
            LOG.info("登录状态验证: " + (isLoggedIn ? "✅ 已登录" : "❌ 未登录"));
            
            // 获取Token状态
            double remainingMinutes = authService.getTokenRemainingMinutes();
            LOG.info("Token剩余时间: " + String.format("%.2f", remainingMinutes) + " 分钟");
            
        } else {
            LOG.error("❌ 登录失败");
        }
        
        LOG.info("=== 登录测试完成 ===");
    }
    
    /**
     * 获取完整的测试报告
     */
    public static String getQuickTestReport() {
        AuthService authService = AuthService.getInstance();
        
        StringBuilder report = new StringBuilder();
        report.append("=== Token快速测试报告 ===\n");
        report.append("登录状态: ").append(authService.isLoggedIn() ? "已登录" : "未登录").append("\n");
        report.append("剩余时间: ").append(String.format("%.2f", authService.getTokenRemainingMinutes())).append(" 分钟\n");
        report.append("即将过期: ").append(authService.isTokenExpiringSoon() ? "是" : "否").append("\n");
        report.append("已过期: ").append(authService.isTokenExpired() ? "是" : "否").append("\n");
        report.append("测试模式: ").append(AuthService.isTestMode() ? "是" : "否").append("\n");
        report.append("详细状态: ").append(authService.getTokenStatusInfo()).append("\n");
        report.append("=====================");
        
        return report.toString();
    }
    
    /**
     * 测试Token刷新和MQTT重连
     */
    public static void testTokenRefreshAndMqttReconnect() {
        LOG.info("=== 开始Token刷新和MQTT重连测试 ===");
        
        AuthService authService = AuthService.getInstance();
        
        // 1. 检查当前状态
        LOG.info("1. 检查当前状态");
        boolean isLoggedIn = authService.isLoggedIn();
        LOG.info("登录状态: " + (isLoggedIn ? "已登录" : "未登录"));
        
        if (!isLoggedIn) {
            LOG.warn("用户未登录，无法进行测试");
            return;
        }
        
        // 2. 获取MQTT服务状态
        LOG.info("2. 获取MQTT服务状态");
        try {
            com.codereview.plugin.service.MQTTService mqttService = com.codereview.plugin.service.MQTTService.getInstance();
            boolean mqttConnected = mqttService.isConnected();
            LOG.info("MQTT连接状态: " + (mqttConnected ? "已连接" : "未连接"));
            
            if (mqttConnected) {
                String mqttStatus = mqttService.getConnectionStatusInfo();
                LOG.info("MQTT状态详情:\n" + mqttStatus);
            }
        } catch (Exception e) {
            LOG.error("获取MQTT状态失败", e);
        }
        
        // 3. 模拟Token即将过期
        LOG.info("3. 模拟Token即将过期");
        simulateTokenExpiringSoon();
        
        // 4. 触发Token刷新
        LOG.info("4. 触发Token刷新");
        authService.forceRefreshToken();
        
        // 5. 等待刷新完成
        LOG.info("5. 等待刷新完成（10秒）");
        try {
            Thread.sleep(10000); // 等待10秒，确保刷新和重连完成
        } catch (InterruptedException e) {
            LOG.error("等待刷新时被中断", e);
        }
        
        // 6. 检查刷新结果
        LOG.info("6. 检查刷新结果");
        double newRemainingMinutes = authService.getTokenRemainingMinutes();
        LOG.info("刷新后Token剩余时间: " + String.format("%.2f", newRemainingMinutes) + " 分钟");
        
        // 7. 检查MQTT重连结果
        LOG.info("7. 检查MQTT重连结果");
        try {
            com.codereview.plugin.service.MQTTService mqttService = com.codereview.plugin.service.MQTTService.getInstance();
            boolean mqttConnected = mqttService.isConnected();
            LOG.info("刷新后MQTT连接状态: " + (mqttConnected ? "已连接" : "未连接"));
            
            if (mqttConnected) {
                String mqttStatus = mqttService.getConnectionStatusInfo();
                LOG.info("刷新后MQTT状态详情:\n" + mqttStatus);
            }
        } catch (Exception e) {
            LOG.error("检查MQTT状态失败", e);
        }
        
        // 8. 恢复原始状态
        LOG.info("8. 恢复原始状态");
        restoreOriginalTokenState();
        
        LOG.info("=== Token刷新和MQTT重连测试完成 ===");
    }
} 