package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Token拦截器
 * 在API调用前检查token状态，自动刷新即将过期的token
 */
public class TokenInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(TokenInterceptor.class);
    
    private final AuthService authService;
    
    public TokenInterceptor(AuthService authService) {
        this.authService = authService;
    }
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // 检查是否需要添加token
        if (request.getHeaders().containsKey("token")) {
            // 获取当前token
            String currentToken = authService.getToken();
            
            // 如果token为null（已过期），等待刷新完成
            if (currentToken == null) {
                LOG.warn("Token已过期，等待刷新完成...");
                
                // 等待token刷新完成（最多等待10秒）
                int maxWaitTime = 10000; // 10秒
                int waitTime = 0;
                int checkInterval = 500; // 每500ms检查一次
                
                while (currentToken == null && waitTime < maxWaitTime) {
                    try {
                        Thread.sleep(checkInterval);
                        waitTime += checkInterval;
                        currentToken = authService.getToken();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                if (currentToken == null) {
                    LOG.error("Token刷新超时，API调用可能失败");
                } else {
                    LOG.info("Token刷新完成，继续API调用");
                }
            }
            
            // 更新请求头中的token
            if (currentToken != null) {
                request.getHeaders().set("token", currentToken);
                LOG.debug("已更新请求头中的token");
            }
        }
        
        // 执行原始请求
        return execution.execute(request, body);
    }
} 