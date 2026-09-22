package com.cjh.agentservice.chat.store;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件版会话记忆仓库：把 ChatMemory 的读写落到 ConversationStore，
 * 使多轮上下文在服务重启后仍然存在；展示用的历史消息也复用同一份文件。
 */
public class FileChatMemoryRepository implements ChatMemoryRepository {

    private final ConversationStore store;

    public FileChatMemoryRepository(ConversationStore store) {
        this.store = store;
    }

    @Override
    public List<String> findConversationIds() {
        return store.list("anonymous").stream().map(ConversationStore.ConversationSummary::id).toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return store.getAnyOwner(conversationId)
                .map(file -> {
                    List<Message> messages = new ArrayList<>();
                    for (ConversationStore.StoredMessage stored : file.messages()) {
                        if (stored.content() == null || stored.content().isBlank()) {
                            continue;
                        }
                        switch (stored.role() == null ? "" : stored.role().toUpperCase()) {
                            case "USER" -> messages.add(new UserMessage(stored.content()));
                            case "ASSISTANT" -> messages.add(new AssistantMessage(stored.content()));
                            case "SYSTEM" -> messages.add(new SystemMessage(stored.content()));
                            default -> {
                                // 工具消息不回放，避免污染上下文
                            }
                        }
                    }
                    return messages;
                })
                .orElseGet(List::of);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<ConversationStore.StoredMessage> incoming = new ArrayList<>();
        for (Message message : messages) {
            if (message == null || message.getText() == null || message.getText().isBlank()) {
                continue;
            }
            String role = message.getMessageType() == null ? "ASSISTANT" : message.getMessageType().name();
            // 只落库用户与助手消息：系统提示与工具消息不写入历史（避免回放污染上下文/界面）
            if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
                continue;
            }
            incoming.add(new ConversationStore.StoredMessage(role, message.getText(), Instant.now().toString()));
        }
        if (!incoming.isEmpty()) {
            store.replaceMessages(conversationId, incoming);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        store.getAnyOwner(conversationId).ifPresent(file -> store.delete(file.id(), file.owner()));
    }
}
