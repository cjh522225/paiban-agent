package com.cjh.agentservice.chat;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.chat.store.ConversationStore;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史会话接口：按登录用户隔离（JWT 解析出所有者）。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationStore store;
    private final ConversationOwnerResolver ownerResolver;
    private final AgentChatService chatService;

    public ConversationController(ConversationStore store, ConversationOwnerResolver ownerResolver,
                                  AgentChatService chatService) {
        this.store = store;
        this.ownerResolver = ownerResolver;
        this.chatService = chatService;
    }

    public record RenameRequest(@NotBlank String title) {
    }

    @GetMapping
    public List<ConversationStore.ConversationSummary> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return store.list(ownerResolver.resolve(authorization));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @PathVariable String id) {
        ConversationStore.ConversationFile file = store.get(id, ownerResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessApiException(404, "会话不存在：" + id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", file.id());
        result.put("title", file.title());
        result.put("createdAt", file.createdAt());
        result.put("updatedAt", file.updatedAt());
        result.put("messages", file.messages());
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> rename(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable String id,
                                      @RequestBody @jakarta.validation.Valid RenameRequest request) {
        store.rename(id, ownerResolver.resolve(authorization), request.title());
        return Map.of("ok", true, "id", id, "title", request.title());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable String id) {
        store.delete(id, ownerResolver.resolve(authorization));
        chatService.reset(id);
        return Map.of("ok", true, "id", id);
    }
}
