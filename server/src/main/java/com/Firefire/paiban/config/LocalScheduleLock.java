package com.Firefire.paiban.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程内(单 JVM)排班互斥锁。
 * 保证同一把 key 同时只有一个线程在执行（如"生成全体宿舍/办公室排班"），
 * 供单实例部署防并发写入/防连点；多实例部署时才需要配合 Redis 分布式锁。
 */
@Component
public class LocalScheduleLock {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 尝试立即拿锁；拿不到说明同 key 任务正在执行，返回 false（不排队） */
    public boolean tryLock(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        return lock.tryLock();
    }

    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
