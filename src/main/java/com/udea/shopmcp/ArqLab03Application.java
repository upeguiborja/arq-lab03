package com.udea.shopmcp;

import com.udea.shopmcp.services.TaskToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ArqLab03Application {

    public static void main(String[] args) {
        SpringApplication.run(ArqLab03Application.class, args);
    }

    @Bean
    public ToolCallbackProvider taskTools(TaskToolsService tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }

}
