package com.codereview.plugin.auth;

import com.codereview.plugin.constant.CommonConstant;
import com.codereview.plugin.utils.AESUtils;
import com.codereview.plugin.utils.RSAUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.Console;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * 控制台登录测试类
 * 可以在控制台输入用户名和密码进行登录测试
 */
public class LoginConsoleTest {
    
    private static final String iamApToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6IkFHVCIsInNpZCI6MH0.Jls5ewe6aJOI2yBlXTdmWpqCeYENWFbsiM6F8T_tVzQ";
    private static final String iamUrl = "https://iam-test.digiwincloud.com.cn";
    private static final String loginUrl = "/api/iam/v2/identity/login";
    private static final String IDENTITY_PUBLIC_KEY = "/api/iam/v2/identity/publickey";

    public static void main(String[] args) {
        System.out.println("=== 登录测试程序 ===");
        System.out.println();
        
        Scanner scanner = new Scanner(System.in);
        
        try {
            // 输入用户名
            System.out.print("请输入用户名: ");
            String username = scanner.nextLine().trim();
            
            // 输入密码
            System.out.print("请输入密码: ");
            String password;
            Console console = System.console();
            if (console != null) {
                // 如果在真正的控制台中运行，隐藏密码输入
                char[] passwordArray = console.readPassword();
                password = new String(passwordArray);
            } else {
                // 在IDE中运行时，明文显示
                password = scanner.nextLine().trim();
            }
            
            if (username.isEmpty() || password.isEmpty()) {
                System.out.println("用户名和密码不能为空！");
                return;
            }
            
            System.out.println();
            System.out.println("开始登录测试...");
            System.out.println("用户名: " + username);
            System.out.println();
            
            // 执行登录
            Map<String, Object> result = performLogin(username, password);
            
            // 显示结果
            System.out.println("=== 登录结果 ===");
            if (result != null && !result.isEmpty()) {
                System.out.println("✅ 登录成功！");
                System.out.println("返回数据:");
                result.forEach((key, value) -> {
                    if ("TOKEN".equals(key) && value != null) {
                        String token = value.toString();
                        System.out.println("  " + key + ": " + token.substring(0, Math.min(token.length(), 20)) + "...");
                    } else {
                        System.out.println("  " + key + ": " + value);
                    }
                });
            } else {
                System.out.println("❌ 登录失败！");
                System.out.println("返回数据为空或null");
            }
            
        } catch (Exception e) {
            System.out.println("❌ 登录过程中发生异常:");
            System.out.println("异常类型: " + e.getClass().getSimpleName());
            System.out.println("异常信息: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
    
    /**
     * 执行登录操作（复制自AuthService的逻辑，但移除了弹框）
     */
    private static Map<String, Object> performLogin(String username, String password) {
        String uri = iamUrl + loginUrl;
        Map<String, Object> retrunMap = new HashMap<>();

        try {
            System.out.println("步骤1: 开始登录，用户名: " + username);
            
            //1.客户端生成公私钥
            HashMap<String, String> keyMap = getKeyPairMap();
            if (keyMap != null) {
                String clientPublicKey = keyMap.get(CommonConstant.PUBLIC_KEY);
                String privateKey = keyMap.get(CommonConstant.PRIVATE_KEY);
                
                System.out.println("步骤2: 客户端公私钥生成成功");
                
                //2.获取服务端公钥
                String serverPublicKey = getServerPublicKey();
                if (StringUtils.isEmpty(serverPublicKey)) {
                    System.out.println("❌ 步骤3: 获取服务端公钥失败");
                    return retrunMap;
                }
                
                System.out.println("步骤3: 获取服务端公钥成功");
                
                //3.根据服务端公钥加密客户端公钥
                String encryptPublicKey = RSAUtils.encryptByPublicKey(clientPublicKey, serverPublicKey);
                //4.获取加密后的AES的key值
                String encryptAesKey = getAesPublicKey(encryptPublicKey);
                if (StringUtils.isEmpty(encryptAesKey)) {
                    System.out.println("❌ 步骤4: 获取AES密钥失败");
                    return retrunMap;
                }
                
                System.out.println("步骤4: 获取AES密钥成功");
                
                //5.根据客户端私有解密加密的aes的key值
                String aesKey = new String(RSAUtils.decryptByPrivateKey(Base64.decodeBase64(encryptAesKey), privateKey));
                String passwordHash = AESUtils.aesEncryptByBase64(password, aesKey);
                //6.登录
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP, iamApToken);

                Map<String, String> requestEntity = new HashMap<>(5);
                requestEntity.put(CommonConstant.IDENTITY_TYPE, CommonConstant.TOKEN);
                requestEntity.put(CommonConstant.USER_ID, username);
                requestEntity.put(CommonConstant.PASSWORD_HASH, passwordHash);
                requestEntity.put(CommonConstant.TENANT_ID, "99990000");
                requestEntity.put(CommonConstant.CLIENT_ENCRYPT_PUBLIC_KEY, encryptPublicKey);

                System.out.println("步骤5: 准备发送登录请求到: " + uri);

                HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(requestEntity, headers);
                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, Map.class);
                Map<String, Object> resultMap = response.getBody();
                
                System.out.println("步骤6: 收到服务器响应");
                if (resultMap != null) {
                    System.out.println("响应内容: " + resultMap.toString());
                } else {
                    System.out.println("响应内容: null");
                }
                
                if (resultMap != null && resultMap.get(CommonConstant.TOKEN) != null) {
                    // 登录成功
                    String token = String.valueOf(resultMap.get(CommonConstant.TOKEN));
                    
                    retrunMap.put(CommonConstant.TOKEN, token);
                    retrunMap.put(CommonConstant.USER_SID, resultMap.get(CommonConstant.SID));
                    
                    System.out.println("步骤7: 登录成功！Token: " + token.substring(0, Math.min(token.length(), 10)) + "...");
                    
                } else {
                    System.out.println("❌ 步骤7: 登录失败，服务器响应中没有Token");
                }
            } else {
                System.out.println("❌ 步骤2: 客户端公私钥生成失败");
            }
        } catch (Exception ex) {
            System.out.println("❌ 登录异常: " + ex.getMessage());
            System.out.println("异常类型: " + ex.getClass().getSimpleName());
            ex.printStackTrace();
            return new HashMap<>();
        }

        return retrunMap;
    }
    
    private static String getAesPublicKey(String encryptPublicKey) {
        String uri = iamUrl + CommonConstant.AES_KEY;
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP, iamApToken);
            Map<String, String> requestEntity = new HashMap<>(1);
            requestEntity.put(CommonConstant.CLIENT_ENCRYPT_PUBLIC_KEY, encryptPublicKey);
            HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(requestEntity, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, Map.class);
            return String.valueOf(response.getBody().get(CommonConstant.ENCRYPT_AES_KEY));
        } catch (Exception e) {
            System.out.println("获取AES密钥时出错：" + e.getMessage());
        }
        return "";
    }

    private static String getServerPublicKey() {
        String uri = iamUrl + IDENTITY_PUBLIC_KEY;
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(CommonConstant.DIGI_MIDDLEWARE_AUTH_APP, iamApToken);
            HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, Map.class);
            return String.valueOf(response.getBody().get(CommonConstant.PUBLIC_KEY));
        } catch (Exception e) {
            System.out.println("获取服务端公钥时出错：" + e.getMessage());
        }
        return "";
    }

    private static HashMap<String, String> getKeyPairMap() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = new String(Base64.encodeBase64(keyPair.getPrivate().getEncoded()));
        String publicKey = new String(Base64.encodeBase64(keyPair.getPublic().getEncoded()));
        HashMap<String, String> keyMap = new HashMap<>();
        keyMap.put(CommonConstant.PRIVATE_KEY, privateKey);
        keyMap.put(CommonConstant.PUBLIC_KEY, publicKey);
        return keyMap;
    }
} 