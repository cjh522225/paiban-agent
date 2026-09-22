package com.cjh.agentservice.chat.store;

import com.cjh.agentservice.business.BusinessApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 历史会话存储（文件版，每个会话一个 JSON 文件，原子写入）。
 * 结构：{id, owner, title, createdAt, updatedAt, messages:[{role, content, at}]}
 */
@Component
public class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int TITLE_MAX = 30;
    private static final int MAX_MESSAGES_PER_FILE = 500;

    private final Path baseDir;
    private final ObjectMapper mapper;

    public ConversationStore(@Value("${agent.chat.data-dir:data/chat}") String dataDir, ObjectMapper mapper) {
        this.baseDir = Path.of(dataDir).toAbsolutePath().normalize();
        this.mapper = mapper;
        try {
            Files.createDirectories(baseDir);
            log.info("历史会话目录：{}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建历史会话目录：" + baseDir, e);
        }
    }

    public record StoredMessage(String role, String content, String at) {
    }

    public record ConversationFile(String id, String owner, String title, String createdAt, String updatedAt,
                                   List<StoredMessage> messages) {
    }

    public record ConversationSummary(String id, String owner, String title, String createdAt, String updatedAt,
                                      int messageCount) {
    }

    /** 首次使用时登记会话所有者；已存在且所有者不同则拒绝（防止跨用户访问）。 */
    public synchronized ConversationFile ensure(String id, String owner) {
        Path path = resolve(id);
        ConversationFile existing = readOrNull(path);
        if (existing == null) {
            Instant now = Instant.now();
            ConversationFile created = new ConversationFile(id, owner, "", now.toString(), now.toString(), new ArrayList<>());
            write(path, created);
            return created;
        }
        if (!existing.owner().equals(owner)) {
            throw new BusinessApiException(403, "无权访问该会话：" + id);
        }
        return existing;
    }

    /** 覆盖式写入（供 ChatMemoryRepository 使用，内部做增量合并与上限裁剪）。 */
    public synchronized void replaceMessages(String id, List<StoredMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Path path = resolveOrAnonymous(id);
        ConversationFile file = readOrNull(path);
        if (file == null) {
            Instant now = Instant.now();
            file = new ConversationFile(id, "anonymous", "", now.toString(), now.toString(), new ArrayList<>());
        }
        List<StoredMessage> merged = merge(file.messages(), incoming);
        if (merged.size() > MAX_MESSAGES_PER_FILE) {
            merged = new ArrayList<>(merged.subList(merged.size() - MAX_MESSAGES_PER_FILE, merged.size()));
        }
        String title = file.title();
        if (title == null || title.isBlank()) {
            title = merged.stream()
                    .filter(message -> "USER".equalsIgnoreCase(message.role()))
                    .map(StoredMessage::content)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst()
                    .map(text -> text.length() > TITLE_MAX ? text.substring(0, TITLE_MAX) + "…" : text)
                    .orElse("");
        }
        ConversationFile updated = new ConversationFile(id, file.owner(), title, file.createdAt(),
                Instant.now().toString(), merged);
        write(path, updated);
    }

    public synchronized List<ConversationSummary> list(String owner) {
        List<ConversationSummary> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(baseDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                ConversationFile file = readOrNull(path);
                if (file == null || !file.owner().equals(owner)) {
                    return;
                }
                result.add(new ConversationSummary(file.id(), file.owner(), file.title(),
                        file.createdAt(), file.updatedAt(), file.messages().size()));
            });
        } catch (IOException e) {
            log.warn("列出历史会话失败：{}", e.getMessage());
        }
        result.sort(Comparator.comparing(ConversationSummary::updatedAt).reversed());
        return result;
    }

    public synchronized Optional<ConversationFile> get(String id, String owner) {
        ConversationFile file = readOrNull(resolve(id));
        if (file == null || !file.owner().equals(owner)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    public synchronized Optional<ConversationFile> getAnyOwner(String id) {
        return Optional.ofNullable(readOrNull(resolve(id)));
    }

    public synchronized void rename(String id, String owner, String title) {
        Path path = resolve(id);
        ConversationFile file = readOrNull(path);
        if (file == null || !file.owner().equals(owner)) {
            throw new BusinessApiException(404, "会话不存在：" + id);
        }
        ConversationFile updated = new ConversationFile(file.id(), file.owner(),
                title == null ? "" : title.trim(), file.createdAt(), Instant.now().toString(), file.messages());
        write(path, updated);
    }

    public synchronized void delete(String id, String owner) {
        Path path = resolve(id);
        ConversationFile file = readOrNull(path);
        if (file == null) {
            return;
        }
        if (!file.owner().equals(owner)) {
            throw new BusinessApiException(403, "无权删除该会话：" + id);
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new BusinessApiException(500, "删除会话失败：" + e.getMessage());
        }
    }

    // ==================== 内部实现 ====================

    private List<StoredMessage> merge(List<StoredMessage> stored, List<StoredMessage> incoming) {
        int maxOverlap = Math.min(stored.size(), incoming.size());
        for (int overlap = maxOverlap; overlap > 0; overlap -= 1) {
            boolean matched = true;
            for (int i = 0; i < overlap; i += 1) {
                StoredMessage left = stored.get(stored.size() - overlap + i);
                StoredMessage right = incoming.get(i);
                if (!left.role().equals(right.role()) || !java.util.Objects.equals(left.content(), right.content())) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                List<StoredMessage> merged = new ArrayList<>(stored);
                merged.addAll(incoming.subList(overlap, incoming.size()));
                return merged;
            }
        }
        List<StoredMessage> merged = new ArrayList<>(stored);
        merged.addAll(incoming);
        return merged;
    }

    private Path resolve(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new BusinessApiException(400, "会话 ID 不合法");
        }
        Path path = baseDir.resolve(id + ".json").normalize();
        if (!path.startsWith(baseDir)) {
            throw new BusinessApiException(400, "会话 ID 不合法");
        }
        return path;
    }

    private Path resolveOrAnonymous(String id) {
        return resolve(id);
    }

    private ConversationFile readOrNull(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), ConversationFile.class);
        } catch (Exception e) {
            log.warn("读取会话文件失败 {}：{}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    private void write(Path path, ConversationFile file) {
        try {
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(file), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessApiException(500, "保存会话失败：" + e.getMessage());
        }
    }
}
