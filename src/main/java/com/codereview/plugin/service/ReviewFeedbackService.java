package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.model.ReviewFeedbackRequest;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 代码审查反馈服务
 */
@Service
public final class ReviewFeedbackService {
    private static final Logger LOG = Logger.getInstance(ReviewFeedbackService.class);
    
    // API配置
    private static final String FEEDBACK_API_URL = "https://aide-at-test.apps.digiwincloud.com.cn/restful/standard/aide/reviewFeedback";
    
    private final AuthService authService;
    private final Gson gson;

    public ReviewFeedbackService() {
        this.authService = AuthService.getInstance();
        this.gson = new Gson();
    }

    /**
     * 获取服务实例
     */
    public static ReviewFeedbackService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(ReviewFeedbackService.class);
    }

    /**
     * 提交反馈
     * @param codeSubmtRecdNo 代码提交记录号
     * @param codeFileNo 代码文件号
     * @param codeSliceNo 代码片段号
     * @param seq 序号
     * @param userFeedbStat 用户反馈状态（2-已确认，3-误报）
     * @param userFeedbDesc 用户反馈描述
     * @param callback 结果回调函数
     */
    public void submitFeedback(String codeSubmtRecdNo, String codeFileNo, String codeSliceNo, 
                             int seq, String userFeedbStat, String userFeedbDesc, 
                             Consumer<Boolean> callback) {
        if (!authService.isLoggedIn()) {
            LOG.warn("用户未登录，无法提交反馈");
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }

        LOG.info("开始提交反馈，状态: " + userFeedbStat + ", 描述: " + userFeedbDesc);

        // 异步调用API
        new Thread(() -> {
            try {
                boolean success = callFeedbackAPI(codeSubmtRecdNo, codeFileNo, codeSliceNo, 
                                                seq, userFeedbStat, userFeedbDesc);
                if (callback != null) {
                    callback.accept(success);
                }
            } catch (Exception e) {
                LOG.error("提交反馈时发生异常", e);
                if (callback != null) {
                    callback.accept(false);
                }
            }
        }).start();
    }

    /**
     * 调用反馈API
     */
    private boolean callFeedbackAPI(String codeSubmtRecdNo, String codeFileNo, String codeSliceNo, 
                                   int seq, String userFeedbStat, String userFeedbDesc) {
        try {
            LOG.info("准备调用反馈API，URL: " + FEEDBACK_API_URL);
            
            RestTemplate restTemplate = new RestTemplate();
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 添加token
            String token = authService.getToken();
            if (token != null) {
                headers.add("token", token);
                LOG.info("已添加token到请求头");
            } else {
                LOG.warn("未获取到有效token");
                return false;
            }
            
            // 构建请求体
            ReviewFeedbackRequest.FeedbackData feedbackData = new ReviewFeedbackRequest.FeedbackData(
                codeSubmtRecdNo, codeFileNo, codeSliceNo, seq, userFeedbStat, userFeedbDesc
            );
            
            ReviewFeedbackRequest request = new ReviewFeedbackRequest(Arrays.asList(feedbackData));
            
            LOG.info("请求参数: codeSubmtRecdNo=" + codeSubmtRecdNo + ", codeFileNo=" + codeFileNo + 
                    ", codeSliceNo=" + codeSliceNo + ", seq=" + seq + ", userFeedbStat=" + userFeedbStat + 
                    ", userFeedbDesc=" + userFeedbDesc);
            
            // 创建请求实体
            HttpEntity<ReviewFeedbackRequest> requestEntity = new HttpEntity<>(request, headers);
            
            // 发送请求
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(FEEDBACK_API_URL, requestEntity, String.class);
            long endTime = System.currentTimeMillis();
            
            LOG.info("反馈API调用完成，耗时: " + (endTime - startTime) + "ms");
            LOG.info("响应状态码: " + response.getStatusCode());
            LOG.info("响应内容: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("反馈提交成功");
                return true;
            } else {
                LOG.warn("反馈API返回非200状态码: " + response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            LOG.error("调用反馈API时发生异常", e);
            return false;
        }
    }
} 