package com.udea.shopmcp.controllers;

import com.udea.shopmcp.services.TaskToolsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ChatController(ChatClient.Builder builder, TaskToolsService taskTools) {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
        this.chatClient = builder
                .defaultSystem("You help users manage their tasks. Use the available tools to answer questions about tasks and to make requested changes. Never invent task data or claim a change succeeded without using a tool. Dates use yyyy-MM-dd format.")
                .defaultTools(taskTools)
                .build();
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = chatClient.prompt()
                .user(request.message().trim())
                .advisors(advisors -> advisors
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, request.conversationId().toString()))
                .call()
                .content();
        return new ChatResponse(reply);
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            @NotNull UUID conversationId
    ) {}

    public record ChatResponse(String reply) {}
}
