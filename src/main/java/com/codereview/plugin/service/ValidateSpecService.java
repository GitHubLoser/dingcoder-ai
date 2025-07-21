package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.constant.CommonConstant;
import com.intellij.openapi.diagnostic.Logger;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证规格服务
 * 负责调用验证规格的API接口
 */
public class ValidateSpecService {
    private static final Logger LOG = Logger.getInstance(ValidateSpecService.class);
    
    private static final String API_URL = "https://igws-atotr-test.apps.digiwincloud.com.cn/restful/standard/iais/esp/executeEspRequest";
    
    private final AuthService authService;
    
    public ValidateSpecService() {
        this.authService = AuthService.getInstance();
    }
    
    /**
     * 调用验证规格API
     * @param text 用户输入的内容
     * @param filePath 当前选中的目录路径
     */
    public void callValidateSpecApi(String text, String filePath) {
        if (!authService.isLoggedIn()) {
            LOG.warn("用户未登录，无法调用API");
            return;
        }
        
        try {
            RestTemplate restTemplate = ReviewService.createUnsafeRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 添加用户登录后的token
            String token = authService.getToken();
            if (token != null) {
                headers.add("token" , token);
            }
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("serviceName", "api.validate.spec.entry");
            
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> context = new HashMap<>();
            context.put("agentCode", "GetVerificationSpec");
            context.put("agentVersion", "1");
            context.put("applicationCode", "CodeValidatorGen");
            context.put("tenantId", "digiwinBmOpt");
            params.put("context", context);
            
            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("text", text);
            dataItem.put("filePath", filePath);
            
            params.put("data", new Object[]{dataItem});
            requestBody.put("params", params);
            
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
            
            LOG.info("调用验证规格API，URL: " + API_URL + ", 请求体: " + requestBody);
            
            // 异步调用，不等待返回
            new Thread(() -> {
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(API_URL, HttpMethod.POST, httpEntity, Map.class);
                    LOG.info("API调用成功，响应状态: " + response.getStatusCode());
                } catch (Exception e) {
                    LOG.error("API调用失败", e);
                }
            }).start();
            
        } catch (Exception e) {
            LOG.error("调用验证规格API时出错", e);
        }
    }
} 