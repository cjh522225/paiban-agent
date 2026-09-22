package com.Firefire.paiban.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnBean(RedissonClient.class)
public class SchedulingLockManager {

    private final RedissonClient redisson;

    public SchedulingLockManager(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @PostConstruct
    public void init() {
        log.info("Redis 分布式锁已启用");
    }

    public RLock acquireLock(String type, List<Long> locationIds) {
        String key = buildKey(type, locationIds);
        return redisson.getFairLock("paiban:lock:" + key);
    }

    public boolean hasConflict(String type, List<Long> locationIds) {
        RLock globalLock = redisson.getLock("paiban:lock:schedule:" + type + ":ALL");
        return globalLock.isLocked();
    }

    public boolean tryLock(String type, List<Long> locationIds, long waitSec, long holdSec) {
        RLock lock = acquireLock(type, locationIds);
        try {
            return lock.tryLock(waitSec, holdSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String type, List<Long> locationIds) {
        RLock lock = acquireLock(type, locationIds);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private String buildKey(String type, List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return "schedule:" + type + ":ALL";
        }
        return "schedule:" + type + ":location:" +
                locationIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
