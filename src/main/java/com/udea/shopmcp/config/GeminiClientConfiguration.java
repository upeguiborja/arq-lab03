package com.udea.shopmcp.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class GeminiClientConfiguration {
    @Bean
    public Client googleGenAiClient(Environment environment) {
        String apiKey = environment.getRequiredProperty("spring.ai.google.genai.api-key");
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(45_000)
                        .retryOptions(HttpRetryOptions.builder().attempts(1))
                        .build())
                .build();
    }
}
