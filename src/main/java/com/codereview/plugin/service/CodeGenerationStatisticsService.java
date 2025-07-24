package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.diagnostic.Logger;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

public class CodeGenerationStatisticsService {
    private static final Logger LOG = Logger.getInstance(CodeGenerationStatisticsService.class);


    private static final String API_URL = "https://igws-atotr-test.apps.digiwincloud.com.cn/restful/standard/iais/esp/executeEspRequest";
    private final AuthService authService = AuthService.getInstance();
    private static final CodeGenerationStatisticsService INSTANCE = new CodeGenerationStatisticsService();
    public static CodeGenerationStatisticsService getInstance() { return INSTANCE; }

    public void sendStatistics(String codeId, String userName, String className, String type, String feedbackType, String feedbackContent) {
        try {
            LOG.info("[统计] sendStatistics参数: codeId=" + codeId + ", userName=" + userName + ", className=" + className + ", type=" + type + ", feedbackType=" + feedbackType + ", feedbackContent=" + feedbackContent);
            RestTemplate restTemplate = ReviewService.createUnsafeRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String token = authService.getToken();
            if (token != null) headers.add("token", token);

            // 构建请求体，结构与api.validate.spec.entry一致
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("serviceName", "api.code.generator.statistics");

            Map<String, Object> params = new HashMap<>();
            Map<String, Object> context = new HashMap<>();
            context.put("agentCode", "GetVerificationSpec");
            context.put("agentVersion", "1");
            context.put("applicationCode", "CodeValidatorGen");
            context.put("tenantId", "digiwinBmOpt");
            params.put("context", context);

            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("codeId", codeId);
            dataItem.put("userName", userName);
            dataItem.put("className", className);
            dataItem.put("type", type);
            dataItem.put("feedbackType", feedbackType);
            dataItem.put("feedbackContent", feedbackContent);
            params.put("data", new Object[]{dataItem});
            requestBody.put("params", params);

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
            LOG.info("[统计] sendStatistics请求体: " + requestBody);

            // 异步调用，不等待返回
            new Thread(() -> {
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(API_URL, HttpMethod.POST, httpEntity, Map.class);
                    LOG.info("[统计] sendStatistics调用成功，响应状态: " + response.getStatusCode());
                } catch (Exception e) {
                    LOG.error("[统计] sendStatistics调用失败", e);
                }
            }).start();

        } catch (Exception e) {
            LOG.error("[统计] sendStatistics调用失败", e);
        }
    }
} 