package com.cjh.agentservice.chat.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileChatMemoryRepositoryTest {

    @TempDir
    Path tempDir;

    private ConversationStore store;
    private FileChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        store = new ConversationStore(tempDir.toString(), new ObjectMapper());
        repository = new FileChatMemoryRepository(store);
    }

    @Test
    void persistsUserAndAssistantMessagesOnly() {
        repository.saveAll("m1", List.of(
                new UserMessage("你好"),
                new SystemMessage("系统提示不应入库"),
                new AssistantMessage("你好，有什么可以帮你？")));

        List<Message> loaded = repository.findByConversationId("m1");
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).getText()).isEqualTo("你好");
        assertThat(loaded.get(1).getText()).isEqualTo("你好，有什么可以帮你？");
        assertThat(store.getAnyOwner("m1").orElseThrow().messages()).hasSize(2);
    }

    @Test
    void windowedSaveKeepsFullHistory() {
        for (int i = 1; i <= 5; i += 1) {
            repository.saveAll("m2", List.of(new UserMessage("q" + i), new AssistantMessage("a" + i)));
        }
        assertThat(repository.findByConversationId("m2")).hasSize(10);

        // 模拟滑动窗口：只回传最后 3 轮
        repository.saveAll("m2", List.of(
                new UserMessage("q4"), new AssistantMessage("a4"),
                new UserMessage("q5"), new AssistantMessage("a5"),
                new UserMessage("q6"), new AssistantMessage("a6")));

        List<Message> loaded = repository.findByConversationId("m2");
        assertThat(loaded).hasSize(12);
        assertThat(loaded.get(11).getText()).isEqualTo("a6");
        assertThat(loaded.get(0).getText()).isEqualTo("q1");
    }

    @Test
    void deleteRemovesConversationFile() {
        repository.saveAll("m3", List.of(new UserMessage("你好")));
        repository.deleteByConversationId("m3");
        assertThat(repository.findByConversationId("m3")).isEmpty();
    }
}
