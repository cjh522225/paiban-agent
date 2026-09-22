package com.cjh.agentservice.rag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 Markdown 分块：按二级/三级标题切节，节内按段落聚合到目标长度（带重叠）。
 */
public final class DocumentChunker {

    static final int TARGET_CHARS = 480;
    static final int OVERLAP_CHARS = 80;

    private DocumentChunker() {
    }

    public static List<Document> chunk(String source, String markdown) {
        List<Document> chunks = new ArrayList<>();
        String currentSection = "概述";
        StringBuilder buffer = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                flush(source, currentSection, buffer, chunks);
                currentSection = line.replaceFirst("^#{2,3}\\s+", "").trim();
                continue;
            }
            if (line.startsWith("# ")) {
                buffer.append(line.substring(2)).append('\n');
                continue;
            }
            buffer.append(line).append('\n');
        }
        flush(source, currentSection, buffer, chunks);
        return chunks;
    }

    private static void flush(String source, String section, StringBuilder buffer, List<Document> chunks) {
        String text = buffer.toString().trim();
        buffer.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + TARGET_CHARS, text.length());
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", source);
                metadata.put("section", section);
                metadata.put("start", start);
                chunks.add(new Document(piece, metadata));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
    }
}
