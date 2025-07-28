package com.codereview.plugin.auth;

import com.codereview.plugin.constant.CommonConstant;
import com.codereview.plugin.service.MQTTService;
import com.codereview.plugin.service.ReviewService;
import com.codereview.plugin.utils.AESUtils;
import com.codereview.plugin.utils.RSAUtils;
import com.intellij.openapi.components.Service;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import com.intellij.openapi.project.Project;

/**
 * 认证服务类 
 * 用于管理用户的登录状态和认证信息
 * 与第三方认证系统对接
 */
@Service
public final class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private  static final String iamApToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6IkFHVCIsInNpZCI6MH0.Jls5ewe6aJOI2yBlXTdmWpqCeYENWFbsiM6F8T_tVzQ";

    private  static final String iamUrl = "https://iam-test.digiwincloud.com.cn";

    private  static final String loginUrl = "/api/iam/v2/identity/login";


    // 全局登录状态管理
    private static volatile boolean globalLoginState = false;
    private static volatile String globalCurrentUser = null;
    private static volatile String globalToken = null;
    private static volatile String globalUserSid = null;
    
    // 实例级别的状态（向后兼容）
    private boolean isLoggedIn = false;
    private String currentUser = null;
    private String token = null;
    private String userSid = null;

    public static final String IDENTITY_PUBLIC_KEY = "/api/iam/v2/identity/publickey";


    /**
     * 获取认证服务的实例（Project级别）
     */
    public static AuthService getInstance(Project project) {
        return project.getService(AuthService.class);
    }

    /**
     * 获取认证服务的实例（向后兼容，返回全局实例）
     */
    public static AuthService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(AuthService.class);
    }

    // 添加状态缓存，避免频繁检查
    private static volatile boolean cachedLoginState = false;
    private static volatile long lastStateCheckTime = 0;
    private static final long STATE_CACHE_DURATION = 50; // 50ms缓存时间，减少延迟
    
    /**
     * 检查用户是否已登录（带缓存优化）
     */
    public boolean isLoggedIn() {
        long currentTime = System.currentTimeMillis();
        
        // 如果缓存时间未过期，直接返回缓存状态
        if (currentTime - lastStateCheckTime < STATE_CACHE_DURATION) {
            return cachedLoginState;
        }
        
        // 更新缓存时间
        lastStateCheckTime = currentTime;
        
        // 检查实际状态（添加同步锁避免并发问题）
        boolean actualState = false;
        synchronized (this) {
            if (globalLoginState && globalToken != null) {
                actualState = true;
            } else if (isLoggedIn && token != null) {
                actualState = true;
            }
        }
        
        // 更新缓存状态
        cachedLoginState = actualState;
        return actualState;
    }



    /**
     * 登录iam
     *
     */
    public Map<String, Object> login(String username, String password) {
        String uri = iamUrl + loginUrl;
        Map<String, Object> retrunMap = new HashMap<>();

        try {
            log.info("开始登录，用户名: {}", username);
            
            // 调试：开始登录
//            javax.swing.JOptionPane.showMessageDialog(null, "步骤1: 开始登录，用户名: " + username, "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            
            //1.客户端生成公私钥
            HashMap<String, String> keyMap = getKeyPairMap();
            if (keyMap != null) {
                String clientPublicKey = keyMap.get(CommonConstant.PUBLIC_KEY);
                String privateKey = keyMap.get(CommonConstant.PRIVATE_KEY);
                
                // 调试：公私钥生成成功
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤2: 客户端公私钥生成成功", "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                
                //2.获取服务端公钥
                String serverPublicKey = getServerPublicKey();
                if (StringUtils.isEmpty(serverPublicKey)) {
                    log.error("获取服务端公钥失败");
//                    javax.swing.JOptionPane.showMessageDialog(null, "步骤3: 获取服务端公钥失败", "登录调试", javax.swing.JOptionPane.ERROR_MESSAGE);
                    return retrunMap;
                }
                
                // 调试：服务端公钥获取成功
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤3: 获取服务端公钥成功", "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                
                //3.根据服务端公钥加密客户端公钥
                String encryptPublicKey = RSAUtils.encryptByPublicKey(clientPublicKey, serverPublicKey);
                //4.获取加密后的AES的key值
                String encryptAesKey = getAesPublicKey(encryptPublicKey);
                if (StringUtils.isEmpty(encryptAesKey)) {
                    log.error("获取AES密钥失败");
//                    javax.swing.JOptionPane.showMessageDialog(null, "步骤4: 获取AES密钥失败", "登录调试", javax.swing.JOptionPane.ERROR_MESSAGE);
                    return retrunMap;
                }
                
                // 调试：AES密钥获取成功
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤4: 获取AES密钥成功", "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                
                //5.根据客户端私有解密加密的aes的key值
                String aesKey = new String(RSAUtils.decryptByPrivateKey(Base64.decodeBase64(encryptAesKey), privateKey), StandardCharsets.UTF_8);
                String passwordHash = AESUtils.aesEncryptByBase64(password, aesKey);
                //6.登录
                RestTemplate restTemplate = ReviewService.createUnsafeRestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP,iamApToken);

                Map<String, String> requestEntity = new HashMap<>(5);
                requestEntity.put(CommonConstant.IDENTITY_TYPE, CommonConstant.TOKEN);
                requestEntity.put(CommonConstant.USER_ID, username);
                requestEntity.put(CommonConstant.PASSWORD_HASH, passwordHash);
                requestEntity.put(CommonConstant.TENANT_ID, "99990000");
                requestEntity.put(CommonConstant.CLIENT_ENCRYPT_PUBLIC_KEY, encryptPublicKey);

                // 调试：准备发送登录请求
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤5: 准备发送登录请求到: " + uri, "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);

                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(requestEntity, headers);
                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, Map.class);
                Map<String, Object> resultMap = response.getBody();
                
                // 调试：收到服务器响应
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤6: 收到服务器响应: " + (resultMap != null ? resultMap.toString() : "null"), "登录调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                
                if (resultMap != null && resultMap.get(CommonConstant.TOKEN) != null) {
                    // 登录成功，更新全局状态和内部状态
                    String token = String.valueOf(resultMap.get(CommonConstant.TOKEN));
                    String userSid = String.valueOf(resultMap.get(CommonConstant.SID));
                    
                    // 更新全局状态
                    globalToken = token;
                    globalCurrentUser = username;
                    globalLoginState = true;
                    globalUserSid = userSid;
                    
                    // 更新实例状态（向后兼容）
                    this.token = token;
                    this.currentUser = username;
                    this.isLoggedIn = true;
                    this.userSid = userSid;
                    
                    retrunMap.put(CommonConstant.TOKEN, token);
                    retrunMap.put(CommonConstant.USER_SID, resultMap.get(CommonConstant.SID));
                    
                    log.info("登录成功，用户: {}, Token: {}, UserSid: {}", username, token.substring(0, Math.min(token.length(), 10)) + "...", this.userSid);
                    
                    // 登录成功后启动MQTT连接，传递userSid
                    startMqttConnection(username, this.userSid);
                    
                    return retrunMap;
                } else {
                    log.warn("登录失败，服务器响应: {}", resultMap);
                    // 调试：登录失败
//                    javax.swing.JOptionPane.showMessageDialog(null, "步骤7: 登录失败，服务器响应中没有Token", "登录调试", javax.swing.JOptionPane.ERROR_MESSAGE);
                }

//                if (resultMap.get(CommonConstant.USER_SID) == null) {
//                    Map<String, Object> userRole = getUserRole(String.valueOf(resultMap.get(CommonConstant.TOKEN)),username);
//                    retrunMap.putAll(userRole);
//                    retrunMap.put(CommonConstant.USER_SID, resultMap.get(CommonConstant.SID));
//                    retrunMap.put(CommonConstant.USER_ID, resultMap.get(CommonConstant.USER_ID));
//                    retrunMap.put(CommonConstant.USER_NAME, resultMap.get(CommonConstant.USER_NAME));
//                    retrunMap.put(CommonConstant.TOKEN, resultMap.get(CommonConstant.TOKEN));
//                    retrunMap.put(CommonConstant.TENANT_SID, resultMap.get(CommonConstant.TENANT_SID));
//
//                    retrunMap.put(CommonConstant.TENANT_ID, resultMap.get(CommonConstant.TENANT_ID));
//                    retrunMap.put(CommonConstant.TENANT_NAME, resultMap.get(CommonConstant.TENANT_NAME));
//                    retrunMap.put(CommonConstant.EMAIL, resultMap.get(CommonConstant.EMAIL));
//                    return Result.success(retrunMap);
//                }
            } else {
                // 调试：公私钥生成失败
//                javax.swing.JOptionPane.showMessageDialog(null, "步骤2: 客户端公私钥生成失败", "登录调试", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            String message = ex.getMessage();
            log.error("登录失败，异常信息: {}", message, ex);
            
            // 调试：异常信息
//            javax.swing.JOptionPane.showMessageDialog(null, "登录异常: " + message + "\n" + ex.getClass().getSimpleName(), "登录调试", javax.swing.JOptionPane.ERROR_MESSAGE);
            
            return new HashMap<>();
        }

        return retrunMap;
    }


    private static String getServerPublicKey() {
        String uri = iamUrl + IDENTITY_PUBLIC_KEY;
        try {
            // 添加调试信息
            log.info("开始获取服务端公钥，URL: {}", uri);
//            javax.swing.JOptionPane.showMessageDialog(null, "开始获取服务端公钥，URL: " + uri, "调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            
            RestTemplate restTemplate = ReviewService.createUnsafeRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP, iamApToken);
            
            // 添加调试信息
            log.info("请求头: {}", headers);
            
            HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, Map.class);
            
            // 添加调试信息
            log.info("服务端公钥响应状态码: {}", response.getStatusCode());
            log.info("服务端公钥响应体: {}", response.getBody());
            
//            javax.swing.JOptionPane.showMessageDialog(null,
//                "服务端公钥响应:\n" +
//                "状态码: " + response.getStatusCode() + "\n" +
//                "响应体: " + response.getBody(),
//                "调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            
            if (response.getBody() != null && response.getBody().get(CommonConstant.PUBLIC_KEY) != null) {
                String publicKey = String.valueOf(response.getBody().get(CommonConstant.PUBLIC_KEY));
                log.info("成功获取服务端公钥，长度: {}", publicKey.length());
                return publicKey;
            } else {
                log.error("服务端公钥响应中没有publicKey字段");
//                javax.swing.JOptionPane.showMessageDialog(null, "服务端公钥响应中没有publicKey字段", "调试", javax.swing.JOptionPane.ERROR_MESSAGE);
                return "";
            }
        } catch (Exception e) {
            log.error("获取服务端公钥时出错：{}", e.getMessage(), e);
//            javax.swing.JOptionPane.showMessageDialog(null,
//                "获取服务端公钥失败！\n" +
//                "URL: " + uri + "\n" +
//                "异常类型: " + e.getClass().getSimpleName() + "\n" +
//                "异常信息: " + e.getMessage(),
//                "调试", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        return "";
    }
    private static String getAesPublicKey(String encryptPublicKey) {
        String uri = iamUrl + CommonConstant.AES_KEY;
        try {
//            javax.swing.JOptionPane.showMessageDialog(null, "开始获取AES密钥，URL: " + uri, "调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            
            RestTemplate restTemplate = ReviewService.createUnsafeRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP, iamApToken);
            Map<String, String> requestEntity = new HashMap<>(1);
            requestEntity.put(CommonConstant.CLIENT_ENCRYPT_PUBLIC_KEY, encryptPublicKey);
            HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(requestEntity, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, Map.class);
            
//            javax.swing.JOptionPane.showMessageDialog(null, "AES密钥响应: " + response.getStatusCode() + ", Body: " + response.getBody(), "调试", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            
            return String.valueOf(response.getBody().get(CommonConstant.ENCRYPT_AES_KEY));
        } catch (Exception e) {
            log.error("获取AES密钥失败：{}", e.getMessage(), e);
            
//            javax.swing.JOptionPane.showMessageDialog(null,
//                "获取AES密钥失败！\n" +
//                "URL: " + uri + "\n" +
//                "异常类型: " + e.getClass().getSimpleName() + "\n" +
//                "异常信息: " + e.getMessage(),
//                "调试", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        return "";
    }

    public static HashMap<String, String> getKeyPairMap() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = new String(Base64.encodeBase64(keyPair.getPrivate().getEncoded()), StandardCharsets.UTF_8);
        String publicKey = new String(Base64.encodeBase64(keyPair.getPublic().getEncoded()), StandardCharsets.UTF_8);
        HashMap<String, String> keyMap = new HashMap<>();
        keyMap.put(CommonConstant.PRIVATE_KEY, privateKey);
        keyMap.put(CommonConstant.PUBLIC_KEY, publicKey);
        return keyMap;
    }
    /**
     * 退出登录过程
     */
    public void logout() {
        log.info("用户 {} 开始退出登录", currentUser);
        
        // 清空面板内容并更新所有窗口UI状态
        try {
            // 通过ChatToolWindowFactory获取当前面板实例并清空内容
            com.codereview.plugin.ui.ChatToolWindowFactory.clearCurrentPanelContent();
            // 强制更新所有窗口的UI状态
            com.codereview.plugin.ui.ChatToolWindowFactory.updateCurrentPanelStatus();
        } catch (Exception e) {
            log.error("清空面板内容时发生错误", e);
        }
        
        // 断开MQTT连接
        try {
            MQTTService mqttService = MQTTService.getInstance();
            if (mqttService.isConnected()) {
                mqttService.disconnect();
                log.info("MQTT连接已断开");
            }
        } catch (Exception e) {
            log.error("断开MQTT连接时发生错误", e);
        }
        
        // 清除全局状态
        globalLoginState = false;
        globalCurrentUser = null;
        globalToken = null;
        globalUserSid = null;
        
        // 清除实例状态
        this.isLoggedIn = false;
        this.currentUser = null;
        this.token = null;
        this.userSid = null;
        
        // 清除缓存状态
        cachedLoginState = false;
        lastStateCheckTime = 0;
        
        log.info("用户退出登录完成");
    }

    /**
     * 获取当前用户名
     */
    public String getCurrentUser() {
        // 优先使用全局状态
        if (globalCurrentUser != null) {
            return globalCurrentUser;
        }
        return currentUser;
    }

    /**
     * 获取认证令牌
     */
    public String getToken() {
        // 优先使用全局状态
        if (globalToken != null) {
            return globalToken;
        }
        return token;
    }

    /**
     * 启动MQTT连接
     */
    private void startMqttConnection(String username, String userSid) {
        // 在后台线程异步启动MQTT连接，避免阻塞登录过程
        Thread mqttThread = new Thread(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            boolean connected = false;
            
            while (!connected && retryCount < maxRetries) {
                try {
                    log.info("开始为用户 {} (userSid: {}) 启动MQTT连接，尝试次数: {}", username, userSid, retryCount + 1);
                    MQTTService mqttService = MQTTService.getInstance();
                    
                    // 保存当前的回调函数
                    Consumer<String> existingCodeGenCallback = mqttService.getMessageCallback(MQTTService.FUNCTION_CODE_GENERATION);
                    Consumer<String> existingCodeReviewCallback = mqttService.getMessageCallback(MQTTService.FUNCTION_CODE_REVIEW);
                    
                    // 如果已经连接，先断开
                    if (mqttService.isConnected()) {
                        log.info("检测到已存在的MQTT连接，先断开");
                        mqttService.disconnect();
                    }
                    
                    // 重新连接并订阅多个topic
                    mqttService.connectAndSubscribe(username, userSid);
                    
                    // 恢复回调函数
                    if (existingCodeGenCallback != null) {
                        mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_GENERATION, existingCodeGenCallback);
                    }
                    if (existingCodeReviewCallback != null) {
                        mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_REVIEW, existingCodeReviewCallback);
                    }
                    
                    // 等待确认连接成功
                    Thread.sleep(1000); // 等待1秒确认连接状态
                    if (mqttService.isConnected()) {
                        log.info("MQTT连接启动成功");
                        connected = true;
                        
                        // 通知UI线程重新设置所有MQTT回调
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                com.codereview.plugin.ui.CodeReviewPanel codeReviewPanel = com.codereview.plugin.ui.CodeReviewPanel.getInstance();
                                if (codeReviewPanel != null) {
                                    log.info("通知CodeReviewPanel重新设置MQTT回调");
                                    codeReviewPanel.ensureCodeReviewMqttCallback();
                                }
                                // 新增：通知ChatToolWindowPanel重新设置code_generation回调
                                com.codereview.plugin.ui.ChatToolWindowPanel chatPanel = com.codereview.plugin.ui.ChatToolWindowPanel.getInstance();
                                if (chatPanel != null) {
                                    log.info("通知ChatToolWindowPanel重新设置MQTT回调");
                                    chatPanel.ensureCodeGenerationMqttCallback();
                                }
                            } catch (Exception e) {
                                log.error("通知UI设置回调时出错", e);
                            }
                        });
                    } else {
                        log.warn("MQTT连接未成功建立，将重试");
                        retryCount++;
                    }
                } catch (Exception e) {
                    log.error("启动MQTT连接失败: {}", e.getMessage(), e);
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(1000 * (1 << retryCount));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (!connected) {
                log.error("MQTT连接在{}次尝试后仍然失败", maxRetries);
                // 通知UI线程显示错误消息
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    MQTTService mqttService = MQTTService.getInstance();
                    Consumer<String> callback = mqttService.getMessageCallback();
                    if (callback != null) {
                        callback.accept("MQTT连接失败，请尝试重新登录");
                    }
                });
            }
        });
        
        // 设置为守护线程，确保IDE关闭时线程能正确退出
        mqttThread.setDaemon(true);
        mqttThread.start();
    }

    /**
     * 获取用户SID
     */
    public String getUserSid() {
        return userSid;
    }
} 