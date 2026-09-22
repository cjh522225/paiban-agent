package com.cjh.agentservice.config;

import com.cjh.agentservice.rag.LocalHashEmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(RagProperties properties) {
        RagProperties.Siliconflow siliconflow = properties.getSiliconflow();
        if (siliconflow.getApiKey() != null && !siliconflow.getApiKey().isBlank()) {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(siliconflow.getBaseUrl())
                    .apiKey(siliconflow.getApiKey())
                    .build();
            return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder().model(siliconflow.getEmbeddingModel()).build());
        }
        return new LocalHashEmbeddingModel();
    }

    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
