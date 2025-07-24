package com.codereview.plugin.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MQTTServiceTest {


    @Test
    public void testParseMessageContentMsgMappingMap() {
        String jsonContent = "{" +
                "\"msgData\": {" +
                "\"text\": \"{\\\"code\\\":\\\"public class Test {}\\\",\\\"msgMapping\\\":\\\"{\\\\\\\"er\\\\\\\":\\\\\\\"汇率\\\\\\\",\\\\\\\"foo\\\\\\\":\\\\\\\"bar\\\\\\\"}\\\"}\"}" +
                "}" +
                "}";
        MQTTService service = new MQTTService();
        service.parseMessageContent(jsonContent);
        java.util.Map<String, String> map = service.getCodeGenerationMsgMappingMap();
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("汇率", map.get("er"));
        assertEquals("bar", map.get("foo"));
    }
} 