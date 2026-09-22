package com.cjh.agentservice.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class AgentChatController {

    private final AgentChatService chatService;
    private final ObjectMapper objectMapper;

    public AgentChatController(AgentChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    public record ResetRequest(@NotBlank String conversationId) {
    }

    @PostMapping
    public ChatReply chat(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestBody @Valid ChatRequest request) {
        return chatService.chat(authorization, request.message(), request.writesAllowed(), request.conversationId());
    }

    /**
     * SSE 流式输出。每个分片先做 JSON 转义再发送（换行 -> \n 字面量），
     * 避免 SSE 协议把分片内部的换行当作事件分隔符导致 Markdown 表格被压平。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestBody @Valid ChatRequest request) {
        return chatService.stream(authorization, request.message(), request.writesAllowed(), request.conversationId())
                .map(this::toJsonChunk);
    }

    private String toJsonChunk(String chunk) {
        try {
            return objectMapper.writeValueAsString(chunk == null ? "" : chunk);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody @Valid ResetRequest request) {
        chatService.reset(request.conversationId());
        return Map.of("cleared", true, "conversationId", request.conversationId());
    }
}
