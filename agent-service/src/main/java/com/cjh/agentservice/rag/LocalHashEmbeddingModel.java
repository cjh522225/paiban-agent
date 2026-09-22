package com.cjh.agentservice.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 无外部密钥时的本地兜底向量：字符 bigram + 词元哈希，L2 归一化。
 * 只用于本地开发与单测，生产请配置 SiliconFlow BGE-M3。
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embedText(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] embedText(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase().replaceAll("\\s+", "");
        for (int i = 0; i < normalized.length(); i++) {
            add(vector, String.valueOf(normalized.charAt(i)), 0.6f);
            if (i + 1 < normalized.length()) {
                add(vector, normalized.substring(i, i + 2), 1.0f);
            }
            if (i + 2 < normalized.length()) {
                add(vector, normalized.substring(i, i + 3), 0.4f);
            }
        }
        for (String word : text.toLowerCase().split("[^a-z0-9]+")) {
            if (word.length() >= 2) {
                add(vector, word, 1.2f);
            }
        }
        normalize(vector);
        return vector;
    }

    private void add(float[] vector, String token, float weight) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, DIMENSIONS);
        vector[index] += (hash & 1) == 0 ? weight : -weight;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
