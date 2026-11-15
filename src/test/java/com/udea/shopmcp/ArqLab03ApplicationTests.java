package com.udea.shopmcp;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "SHOPMCP_GEMINI_API_KEY=test-gemini-key")
class ArqLab03ApplicationTests {

    @Autowired
    private Client geminiClient;

    @Test
    void contextLoads() {
    }

    @Test
    void resolvesGeminiApiKeyProperty() {
        assertEquals("test-gemini-key", geminiClient.apiKey());
    }

}
