package com.udea.shopmcp.controllers;

import com.udea.shopmcp.services.TaskToolsService;
import com.udea.shopmcp.services.InventoryToolsService;
import com.udea.shopmcp.services.DeliveryToolsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ChatController(ChatClient.Builder builder, TaskToolsService taskTools, DeliveryToolsService deliveries, InventoryToolsService inventory) {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(taskTools, deliveries, inventory)
                .build();
    }

    private static final String SYSTEM_PROMPT = """
                        Responde en español de Colombia. Ayuda a gestionar tareas, hacer pedidos de supermercado
                        a domicilio y consultar el estado de cada producto. Usa las herramientas para consultar
                        datos y hacer cambios. Solo confirma un cambio cuando la herramienta indique que se realizó.
                        Consulta el inventario para confirmar el nombre del catálogo y las existencias.
                        Si faltan datos del pedido, pídelos. Al registrar un pedido, entrega el código de seguimiento.
                        Expresa los estados de entrega como recibido, en preparación, en camino o entregado.
                        Usa el formato aaaa-mm-dd para las fechas.
                        """;

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
            @NotBlank(message = "Escribe un mensaje.")
            @Size(max = 4000, message = "El mensaje puede tener hasta 4000 caracteres.") String message,
            @NotNull(message = "Ingresa el identificador de la conversación.") UUID conversationId
    ) {}

    public record ChatResponse(String reply) {}

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String messageId = UUID.randomUUID().toString();
        String textId = UUID.randomUUID().toString();
        AtomicBoolean textStarted = new AtomicBoolean();
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        emitter.onCompletion(() -> {
            Disposable active = subscription.get();
            if (active != null) active.dispose();
        });

        ToolCallingManager delegate = ToolCallingManager.builder().build();
        ToolCallingManager observingManager = new ToolCallingManager() {
            @Override
            public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
                return delegate.resolveToolDefinitions(options);
            }

            @Override
            public ToolExecutionResult executeToolCalls(Prompt prompt, org.springframework.ai.chat.model.ChatResponse response) {
                var calls = response.getResults().stream()
                        .flatMap(result -> result.getOutput().getToolCalls().stream())
                        .map(call -> Map.entry(call, call.id().isBlank() ? UUID.randomUUID().toString() : call.id()))
                        .toList();
                for (var call : calls) {
                    send(emitter, Map.of("type", "tool-input-available", "toolCallId", call.getValue(),
                            "toolName", call.getKey().name(), "input", Map.of("arguments", call.getKey().arguments()),
                            "dynamic", true, "providerExecuted", true));
                }
                ToolExecutionResult result = delegate.executeToolCalls(prompt, response);
                var replies = result.conversationHistory().getLast() instanceof ToolResponseMessage message
                        ? message.getResponses() : List.<ToolResponseMessage.ToolResponse>of();
                for (var call : calls) {
                    var reply = replies.stream().filter(item -> item.name().equals(call.getKey().name())
                            && item.id().equals(call.getKey().id())).findFirst();
                    send(emitter, Map.of("type", "tool-output-available", "toolCallId", call.getValue(),
                            "output", reply.map(ToolResponseMessage.ToolResponse::responseData).orElse("")));
                }
                return result;
            }
        };
        ToolCallingAdvisor toolAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(observingManager)
                .build();

        send(emitter, Map.of("type", "start", "messageId", messageId));
        subscription.set(chatClient.prompt()
                .system(SYSTEM_PROMPT + "La interfaz muestra los resultados de inventario y seguimiento como tarjetas. Resume la respuesta sin repetir toda la lista.")
                .user(request.message().trim())
                .advisors(advisors -> advisors.advisors(memoryAdvisor, toolAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, request.conversationId().toString()))
                .stream()
                .content()
                .subscribe(delta -> {
                    if (delta == null || delta.isEmpty()) return;
                    if (textStarted.compareAndSet(false, true)) {
                        send(emitter, Map.of("type", "text-start", "id", textId));
                    }
                    send(emitter, Map.of("type", "text-delta", "id", textId, "delta", delta));
                }, error -> {
                    send(emitter, Map.of("type", "error", "errorText", "El asistente no pudo responder. Intenta de nuevo."));
                    emitter.complete();
                }, () -> {
                    if (textStarted.get()) send(emitter, Map.of("type", "text-end", "id", textId));
                    send(emitter, Map.of("type", "finish"));
                    emitter.complete();
                }));
        return ResponseEntity.ok()
                .header("x-vercel-ai-ui-message-stream", "v1")
                .body(emitter);
    }

    private static void send(SseEmitter emitter, Map<String, ?> event) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(event));
            }
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }
}
