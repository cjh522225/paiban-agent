package com.cjh.agentservice.chat.store;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationStoreTest {

    @TempDir
    Path tempDir;

    private ConversationStore store;

    @BeforeEach
    void setUp() {
        store = new ConversationStore(tempDir.toString(), new ObjectMapper());
    }

    @Test
    void ensureCreatesConversationAndRejectsOtherOwner() {
        store.ensure("c1", "u1");
        assertThat(store.list("u1")).hasSize(1);
        assertThatThrownBy(() -> store.ensure("c1", "u2"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void replaceMessagesMergesWindowedSaveWithoutLosingHistory() {
        List<ConversationStore.StoredMessage> full = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new ConversationStore.StoredMessage(i % 2 == 1 ? "USER" : "ASSISTANT", "m" + i, "t"))
                .toList();
        store.replaceMessages("c2", full);

        List<ConversationStore.StoredMessage> window = new ArrayList<>(full.subList(10, 30));
        window.add(new ConversationStore.StoredMessage("ASSISTANT", "m31", "t"));
        store.replaceMessages("c2", window);

        ConversationStore.ConversationFile file = store.getAnyOwner("c2").orElseThrow();
        assertThat(file.messages()).hasSize(31);
        assertThat(file.messages().get(0).content()).isEqualTo("m1");
        assertThat(file.messages().get(30).content()).isEqualTo("m31");
    }

    @Test
    void titleDerivedFromFirstUserMessage() {
        store.replaceMessages("c3", List.of(
                new ConversationStore.StoredMessage("USER", "帮我查一下下周的排班情况好吗", "t")));
        assertThat(store.getAnyOwner("c3").orElseThrow().title()).startsWith("帮我查一下下周的排班情况");
    }

    @Test
    void invalidConversationIdRejected() {
        assertThatThrownBy(() -> store.get("../secret", "u1"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("会话 ID 不合法");
    }

    @Test
    void deleteOnlyAllowedByOwner() {
        store.ensure("c4", "u1");
        assertThatThrownBy(() -> store.delete("c4", "u2"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("无权删除");
        store.delete("c4", "u1");
        assertThat(store.list("u1")).isEmpty();
    }

    @Test
    void listSortsByUpdatedAtDescending() throws InterruptedException {
        store.ensure("c5", "u1");
        Thread.sleep(10);
        store.ensure("c6", "u1");
        store.replaceMessages("c6", List.of(new ConversationStore.StoredMessage("USER", "新的会话", "t")));
        List<ConversationStore.ConversationSummary> list = store.list("u1");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).id()).isEqualTo("c6");
        assertThat(list.get(1).id()).isEqualTo("c5");
    }
}
