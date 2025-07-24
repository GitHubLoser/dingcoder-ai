package com.codereview.plugin.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class MQTTServiceTest {
    @Test
    public void testParseMessageContent_standardFormat() {
        String json = "{\"msgData\":{\"userId\":\"liwlc@digiwin.com\",\"level\":\"INFO\",\"type\":null,\"text\":\"{\\\"code\\\":\\\"\\n java\\\\npackage com.digiwin.bm.common.business.validate;\\\\n\\\\nimport com.digiwin.app.resource.DWApplicationMessageResourceBundleUtils;\\\\nimport com.digiwin.bm.common.anno.Validator;\\\\nimport com.digiwin.bm.common.business.validate.ValidateContext;\\\\nimport com.digiwin.bm.common.business.validate.ValidatorBase;\\\\nimport com.digiwin.bm.common.utils.BmMessageUtils;\\\\nimport com.digiwin.bm.common.utils.math.BmMathUtils;\\\\nimport com.digiwin.bm.common.utils.datacompose.BmDataComposeUtils;\\\\nimport org.apache.commons.lang3.ObjectUtils;\\\\nimport org.springframework.stereotype.Component;\\\\nimport java.math.BigDecimal;\\\\nimport java.util.List;\\\\nimport java.util.Map;\\\\n\\\\n/**\\\\n * Author liwlc\\\\n * @Date 2025/07/24\\\\n * @Version 1.0\\\\n * @description: 汇率必须大于等于0\\\\n */\\\\n@Validator(\\\\n    validatorId = \\\\\\\"VD_pur_ctrct_00016\\\\\\\",\\\\n    bussinessModel = \\\\\\\"pur_ctrct\\\\\\\"\\\\n)\\\\n@Component\\\\npublic class VDPurCtrct00016 extends ValidatorBase<Map> {\\\\n    @Override\\\\n    public boolean execute(ValidateContext<Map> validateContext) throws Exception {\\\\n        Map<String, Object> parameter = validateContext.getParameter();\\\\n        List<Map<String, Object>> rootList = (List<Map<String, Object>>) parameter.get(\\\\\\\"pur_ctrct\\\\\\\");\\\\n        List<Map<String, Object>> erList = (List<Map<String, Object>>) BmDataComposeUtils.executor(rootList, \\\\\\\"er;\\\\\\\");\\\\n\\\\n        BigDecimal zero = BigDecimal.ZERO;\\\\n        for (Map<String, Object> item : erList) {\\\\n            BigDecimal exchangeRate = BmMathUtils.getDecimalValue(item, \\\\\\\"er\\\\\\\", zero);\\\\n\\\\n            if (exchangeRate.compareTo(zero) < 0) {\\\\n                setErrorMessage(validateContext, \\\\\\\"0014544\\\\\\\",\\\\n                    BmMessageUtils.getMessage(\\\\\\\"0014544\\\\\\\",\\\\\\\n                        DWApplicationMessageResourceBundleUtils.getString(\\\\\\\"er\\\\\\\")),\\\\n                    null, null);\\\\n            }\\\\n        }\\\\n        return !ObjectUtils.isEmpty(getErrorMessageInfo(validateContext));\\\\n    }\\\\n}\\\\n \\n\\\",\\\"msgMapping\\\":\\\"{\\\"er\\\":\\\"汇率\\\"}\\\"}\",\"todo\":null,\"category\":null},\"context\":{\"agentCode\":\"GenVerificationCode\",\"agentVersion\":\"1\",\"manualCode\":null,\"applicationCode\":\"CodeValidatorGen\",\"tenantId\":\"digiwinBmOpt\",\"manualId\":null,\"traceId\":\"bade47d0-3cc1-4f61-b3b5-d0cd41608d6c\",\"agentRunId\":\"266691705480216577\",\"id\":\"247f2dc2-96d2-4b19-84a9-af78bfa3d1ae\",\"appParamMap\":null}}";
        MQTTService service = new MQTTService();
        String code = service.parseMessageContent(json);
        //        assertEquals("System.out.println(\"Hello\");", code);
        Map<String, String> map = service.getCodeGenerationMsgMappingMap();
        assertNotNull(map);
        //        assertEquals("你好", map.get("hello"));
    }

    @Test
    public void testParseMessageContent_textIsPlainString() {
        String json = "{\"msgData\":{\"text\":\"just a plain string\"}}";
        MQTTService service = new MQTTService();
        String code = service.parseMessageContent(json);
        assertEquals("just a plain string", code);
        assertNull(service.getCodeGenerationMsgMappingMap());
    }
} 