package com.cjh.agentservice.rag;

import com.cjh.agentservice.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final SimpleVectorStore vectorStore;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    private volatile int chunkCount;
    private volatile int documentCount;

    public RagService(SimpleVectorStore vectorStore, RagProperties properties, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void ingest() {
        if (!properties.isEnabled()) {
            log.info("RAG 已禁用");
            return;
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:rag/docs/*.md");
            List<Document> chunks = new ArrayList<>();
            for (Resource resource : resources) {
                String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                chunks.addAll(DocumentChunker.chunk(resource.getFilename(), markdown));
            }
            if (!chunks.isEmpty()) {
                vectorStore.add(chunks);
            }
            chunkCount = chunks.size();
            documentCount = resources.length;
            log.info("RAG 制度文档入库完成：{} 篇文档，{} 个片段", documentCount, chunkCount);
        } catch (Exception e) {
            log.error("RAG 文档入库失败：{}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> search(String query, Integer topK) {
        int limit = topK == null || topK <= 0 ? properties.getTopK() : Math.min(topK, 10);
        int candidates = properties.getRerank().isEnabled()
                ? Math.max(limit, properties.getRerank().getCandidates())
                : limit;
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(candidates)
                .build());
        if (documents == null) {
            return List.of();
        }
        List<Document> ordered = rerank(query, documents, limit);
        List<Map<String, Object>> result = new ArrayList<>(ordered.size());
        for (Document document : ordered) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", String.valueOf(document.getMetadata().getOrDefault("source", "未知文档")));
            item.put("section", String.valueOf(document.getMetadata().getOrDefault("section", "")));
            item.put("score", document.getScore() == null ? null : Math.round(document.getScore() * 1000) / 1000.0);
            item.put("text", document.getText());
            result.add(item);
        }
        return result;
    }

    private List<Document> rerank(String query, List<Document> documents, int limit) {
        RagProperties.Rerank rerank = properties.getRerank();
        String apiKey = properties.getSiliconflow().getApiKey();
        if (!rerank.isEnabled() || apiKey == null || apiKey.isBlank() || documents.size() <= 1) {
            return documents.stream().limit(limit).toList();
        }
        try {
            List<String> texts = documents.stream().map(Document::getText).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", rerank.getModel());
            body.put("query", query);
            body.put("documents", texts);
            body.put("top_n", limit);
            JsonNode response = RestClient.builder()
                    .baseUrl(properties.getSiliconflow().getBaseUrl())
                    .build()
                    .post()
                    .uri("/v1/rerank")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("results").isArray()) {
                return documents.stream().limit(limit).toList();
            }
            List<Document> ordered = new ArrayList<>(limit);
            for (JsonNode hit : response.path("results")) {
                int index = hit.path("index").asInt(-1);
                if (index >= 0 && index < documents.size()) {
                    ordered.add(documents.get(index));
                }
            }
            return ordered.isEmpty() ? documents.stream().limit(limit).toList() : ordered;
        } catch (Exception e) {
            log.warn("SiliconFlow 重排失败，回退向量排序：{}", e.getMessage());
            return documents.stream().limit(limit).toList();
        }
    }

    public Map<String, Object> evaluate() {
        List<Map<String, Object>> details = new ArrayList<>();
        int hit1 = 0;
        int hit3 = 0;
        double mrrSum = 0;
        int total = 0;
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource(properties.getEvalQuestions());
            JsonNode questions = objectMapper.readTree(resource.getInputStream());
            for (JsonNode question : questions) {
                total += 1;
                String text = question.path("question").asText();
                String expectedSource = question.path("expectedSource").asText();
                List<Map<String, Object>> passages = search(text, Math.max(3, properties.getTopK()));
                int rank = 0;
                String top1Source = passages.isEmpty() ? null : String.valueOf(passages.get(0).get("source"));
                for (int i = 0; i < passages.size(); i++) {
                    if (expectedSource.equals(passages.get(i).get("source"))) {
                        rank = i + 1;
                        break;
                    }
                }
                if (rank == 1) {
                    hit1 += 1;
                }
                if (rank >= 1 && rank <= 3) {
                    hit3 += 1;
                }
                if (rank >= 1) {
                    mrrSum += 1.0 / rank;
                }
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("id", question.path("id").asInt());
                detail.put("question", text);
                detail.put("expectedSource", expectedSource);
                detail.put("top1Source", top1Source);
                detail.put("rank", rank);
                details.add(detail);
            }
        } catch (Exception e) {
            throw new IllegalStateException("RAG 评测执行失败：" + e.getMessage(), e);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", total);
        report.put("hitAt1", total == 0 ? 0 : Math.round(hit1 * 1000.0 / total) / 10.0);
        report.put("hitAt3", total == 0 ? 0 : Math.round(hit3 * 1000.0 / total) / 10.0);
        report.put("mrr", total == 0 ? 0 : Math.round(mrrSum / total * 1000) / 1000.0);
        report.put("embedding", embeddingName());
        report.put("rerank", properties.getRerank().isEnabled());
        report.put("details", details);
        return report;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("documents", documentCount);
        status.put("chunks", chunkCount);
        status.put("topK", properties.getTopK());
        status.put("embedding", embeddingName());
        status.put("rerank", properties.getRerank().isEnabled() ? properties.getRerank().getModel() : false);
        return status;
    }

    private String embeddingName() {
        String apiKey = properties.getSiliconflow().getApiKey();
        return apiKey == null || apiKey.isBlank()
                ? "local-hash-512"
                : properties.getSiliconflow().getEmbeddingModel();
    }
}
