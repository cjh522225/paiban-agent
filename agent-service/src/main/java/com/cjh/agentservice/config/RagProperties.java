package com.cjh.agentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.rag")
public class RagProperties {

    private boolean enabled = true;

    private int topK = 4;

    private String evalQuestions = "classpath:rag/eval/questions.json";

    private final Rerank rerank = new Rerank();

    private final Siliconflow siliconflow = new Siliconflow();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getEvalQuestions() {
        return evalQuestions;
    }

    public void setEvalQuestions(String evalQuestions) {
        this.evalQuestions = evalQuestions;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public Siliconflow getSiliconflow() {
        return siliconflow;
    }

    public static class Rerank {

        private boolean enabled;

        private String model = "BAAI/bge-reranker-v2-m3";

        private int candidates = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getCandidates() {
            return candidates;
        }

        public void setCandidates(int candidates) {
            this.candidates = candidates;
        }
    }

    public static class Siliconflow {

        private String baseUrl = "https://api.siliconflow.cn";

        private String apiKey = "";

        private String embeddingModel = "BAAI/bge-m3";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }
}
