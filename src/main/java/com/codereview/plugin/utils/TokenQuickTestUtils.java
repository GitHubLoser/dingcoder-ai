package com.codereview.plugin.utils;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Field;

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
            
            // ✅ 新增：确保设置存储的用户名和密码，以便refreshTokenAsync能正常工作
            Field storedUsernameField = AuthService.class.getDeclaredField("storedUsername");
            storedUsernameField.setAccessible(true);
            Field storedPasswordField = AuthService.class.getDeclaredField("storedPassword");
            storedPasswordField.setAccessible(true);
            
            // 如果存储的用户名和密码为空，设置一个测试值
            String currentStoredUsername = (String) storedUsernameField.get(null);
            String currentStoredPassword = (String) storedPasswordField.get(null);
            
            if (currentStoredUsername == null || currentStoredPassword == null) {
                LOG.info("检测到storedUsername或storedPassword为空，设置测试值");
                storedUsernameField.set(null, "test_user");
                storedPasswordField.set(null, "test_password");
                LOG.info("已设置测试用户名和密码");
            } else {
                LOG.info("storedUsername和storedPassword已存在，无需设置");
            }
            
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
        LOG.info("调用forceRefreshToken()...");
        authService.forceRefreshToken();
        LOG.info("forceRefreshToken()调用完成");
        
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
    
    /**
     * 测试MQTT重连内存泄漏
     */
    public static void testMqttReconnectMemoryLeak() {
        LOG.info("=== 开始MQTT重连内存泄漏测试 ===");
        
        try {
            com.codereview.plugin.service.MQTTService mqttService = com.codereview.plugin.service.MQTTService.getInstance();
            
            // 1. 记录初始线程数
            LOG.info("1. 记录初始状态");
            int initialThreadCount = getActiveThreadCount();
            LOG.info("初始活跃线程数: " + initialThreadCount);
            
            // 2. 获取初始MQTT状态
            boolean initialConnected = mqttService.isConnected();
            LOG.info("初始MQTT连接状态: " + (initialConnected ? "已连接" : "未连接"));
            
            // 3. 模拟多次断开重连
            LOG.info("2. 开始模拟多次断开重连（10次）");
            for (int i = 1; i <= 10; i++) {
                LOG.info("--- 第" + i + "次断开重连测试 ---");
                
                // 强制断开连接
                LOG.info("强制断开MQTT连接...");
                mqttService.disconnect();
                
                // 等待断开完成
                Thread.sleep(1000);
                
                // 检查断开状态
                boolean disconnected = !mqttService.isConnected();
                LOG.info("断开状态: " + (disconnected ? "成功" : "失败"));
                
                // 触发重连
                LOG.info("触发重连...");
                mqttService.manualReconnect();
                
                // 等待重连完成
                Thread.sleep(3000);
                
                // 检查重连状态
                boolean reconnected = mqttService.isConnected();
                LOG.info("重连状态: " + (reconnected ? "成功" : "失败"));
                
                // 记录当前线程数
                int currentThreadCount = getActiveThreadCount();
                LOG.info("当前活跃线程数: " + currentThreadCount + " (变化: " + (currentThreadCount - initialThreadCount) + ")");
                
                // 如果线程数增长过多，发出警告
                if (currentThreadCount - initialThreadCount > 5) {
                    LOG.warn("⚠️ 检测到线程数异常增长！");
                }
            }
            
            // 4. 最终检查
            LOG.info("3. 最终检查");
            int finalThreadCount = getActiveThreadCount();
            LOG.info("最终活跃线程数: " + finalThreadCount);
            LOG.info("线程数变化: " + (finalThreadCount - initialThreadCount));
            
            boolean finalConnected = mqttService.isConnected();
            LOG.info("最终MQTT连接状态: " + (finalConnected ? "已连接" : "未连接"));
            
            // 5. 强制清理
            LOG.info("4. 强制清理资源");
            mqttService.forceCleanup();
            
            // 6. 清理后检查
            Thread.sleep(2000);
            int afterCleanupThreadCount = getActiveThreadCount();
            LOG.info("清理后活跃线程数: " + afterCleanupThreadCount);
            LOG.info("清理后线程数变化: " + (afterCleanupThreadCount - initialThreadCount));
            
            // 7. 结果评估
            LOG.info("5. 测试结果评估");
            if (afterCleanupThreadCount - initialThreadCount <= 2) {
                LOG.info("✅ MQTT重连内存泄漏测试通过！线程数增长正常");
            } else {
                LOG.warn("⚠️ MQTT重连可能存在内存泄漏！线程数增长异常");
            }
            
        } catch (Exception e) {
            LOG.error("MQTT重连内存泄漏测试过程中出错", e);
        }
        
        LOG.info("=== MQTT重连内存泄漏测试完成 ===");
    }
    
    /**
     * 获取活跃线程数（只统计MQTT相关线程）
     */
    private static int getActiveThreadCount() {
        Thread[] threads = new Thread[Thread.activeCount()];
        int threadCount = Thread.enumerate(threads);
        
        int mqttThreadCount = 0;
        for (int i = 0; i < threadCount; i++) {
            String threadName = threads[i].getName();
            if (threadName.contains("MQTT") || 
                threadName.contains("Reconnect") || 
                threadName.contains("TokenRefresh")) {
                mqttThreadCount++;
            }
        }
        
        return mqttThreadCount;
    }
} 