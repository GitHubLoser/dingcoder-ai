package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * ValidateSpecService测试类
 */
public class ValidateSpecServiceTest {
    
    private ValidateSpecService validateSpecService;
    private AuthService mockAuthService;
    
    @BeforeEach
    void setUp() {
        // 创建mock的AuthService
        mockAuthService = mock(AuthService.class);
        
        // 使用MockedStatic来mock静态方法
        try (MockedStatic<AuthService> mockedAuthService = Mockito.mockStatic(AuthService.class)) {
            mockedAuthService.when(AuthService::getInstance).thenReturn(mockAuthService);
            
            // 设置默认行为
            when(mockAuthService.isLoggedIn()).thenReturn(true);
            when(mockAuthService.getToken()).thenReturn("test-token");
            
            validateSpecService = new ValidateSpecService();
        }
    }
    
    @Test
    void testCallValidateSpecApi() {
        // 这个测试主要是验证方法能够正常调用，不抛出异常
        // 由于涉及网络调用，我们主要测试方法的结构正确性
        
        String testText = "测试API名称";
        String testFilePath = "/test/path";
        
        // 调用方法，应该不抛出异常
        validateSpecService.callValidateSpecApi(testText, testFilePath);
        
        // 由于是异步调用，我们无法直接验证结果
        // 但可以验证方法调用没有抛出异常
    }
    
    @Test
    void testCallValidateSpecApiWithEmptyText() {
        // 测试空文本的情况
        String testText = "";
        String testFilePath = "/test/path";
        
        validateSpecService.callValidateSpecApi(testText, testFilePath);
        
        // 应该不抛出异常
    }
    
    @Test
    void testCallValidateSpecApiWithNullFilePath() {
        // 测试空路径的情况
        String testText = "测试API名称";
        String testFilePath = null;
        
        validateSpecService.callValidateSpecApi(testText, testFilePath);
        
        // 应该不抛出异常
    }
    
    @Test
    void testCallValidateSpecApiWhenNotLoggedIn() {
        // 测试未登录的情况
        when(mockAuthService.isLoggedIn()).thenReturn(false);
        
        String testText = "测试API名称";
        String testFilePath = "/test/path";
        
        validateSpecService.callValidateSpecApi(testText, testFilePath);
        
        // 应该不抛出异常，但不会调用API
    }
} 