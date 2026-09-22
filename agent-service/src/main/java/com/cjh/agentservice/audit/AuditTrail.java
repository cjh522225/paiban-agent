package com.cjh.agentservice.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 工具调用审计（内存环形缓冲，供管理端/演示查看，后续可落库）。
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);
    private static final int MAX_ENTRIES = 1000;
    private static final int MAX_ARGS_LENGTH = 400;

    private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();

    public record Entry(String at, String tool, String args, long durationMs, boolean ok, String error) {
    }

    public void record(String tool, String args, long durationMs, boolean ok, String error) {
        String safeArgs = args == null ? "" : (args.length() > MAX_ARGS_LENGTH ? args.substring(0, MAX_ARGS_LENGTH) + "…" : args);
        entries.addFirst(new Entry(Instant.now().toString(), tool, safeArgs, durationMs, ok, error));
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
        if (ok) {
            log.info("tool={} duration={}ms args={}", tool, durationMs, safeArgs);
        } else {
            log.warn("tool={} duration={}ms failed={} args={}", tool, durationMs, error, safeArgs);
        }
    }

    public List<Entry> recent(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_ENTRIES));
        List<Entry> result = new ArrayList<>(size);
        for (Entry entry : entries) {
            if (result.size() >= size) {
                break;
            }
            result.add(entry);
        }
        return result;
    }
}
