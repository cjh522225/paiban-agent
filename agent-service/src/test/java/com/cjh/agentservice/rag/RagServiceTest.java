package com.cjh.agentservice.rag;

import com.cjh.agentservice.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceTest {

    private static RagService ragService;

    @BeforeAll
    static void setUp() {
        RagProperties properties = new RagProperties();
        properties.setTopK(4);
        LocalHashEmbeddingModel embeddingModel = new LocalHashEmbeddingModel();
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        ragService = new RagService(vectorStore, properties, new ObjectMapper());
        ragService.ingest();
    }

    @Test
    void ingestLoadsAllDocuments() {
        Map<String, Object> status = ragService.status();
        assertThat((int) status.get("documents")).isEqualTo(4);
        assertThat((int) status.get("chunks")).isGreaterThan(10);
        assertThat(status.get("embedding")).isEqualTo("local-hash-512");
    }

    @Test
    void searchFindsDisciplinePolicy() {
        List<Map<String, Object>> passages = ragService.search("迟到累计3次折算为1次缺岗", 3);
        assertThat(passages).isNotEmpty();
        assertThat(passages).extracting(item -> item.get("source"))
                .contains("纪律与考核办法.md");
        assertThat(passages.stream().map(item -> String.valueOf(item.get("text"))).toList())
                .anyMatch(text -> text.contains("3 次") || text.contains("3次"));
    }

    @Test
    void searchFindsLeavePolicy() {
        List<Map<String, Object>> passages = ragService.search("请假要提前几天申请", 3);
        assertThat(passages).isNotEmpty();
        assertThat(passages.get(0).get("source")).isEqualTo("请假管理办法.md");
    }

    @Test
    void chunkerKeepsSectionMetadata() {
        String markdown = "# 测试文档\n\n## 第一节\n\n内容甲。\n\n## 第二节\n\n内容乙。";
        List<Document> chunks = DocumentChunker.chunk("测试.md", markdown);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getMetadata().get("source")).isEqualTo("测试.md"));
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.getMetadata().get("section")).isEqualTo("第一节");
            assertThat(chunk.getText()).contains("内容甲");
        });
    }

    @Test
    void evalRunsAgainstGoldenSet() {
        Map<String, Object> report = ragService.evaluate();
        assertThat(report.get("total")).isEqualTo(50);
        assertThat((double) report.get("hitAt1")).isGreaterThan(50.0);
        System.out.println("RAG-EVAL: " + report.get("hitAt1") + "% hit@1, "
                + report.get("hitAt3") + "% hit@3, MRR=" + report.get("mrr"));
    }
}
