package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Author: IsaiahLu
 * @date: 2023/1/4 22:08
 */
public class SimpleRedisLock implements ILock {

    /**
     * 业务名称，锁名称
     */
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 锁前缀
     */
    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID(true) + "-";

    /**
     * 尝试索取锁
     *
     * @param timeoutSec 过期时间
     * @return true 表示成功，false 表示失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String key = KEY_PREFIX + name;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId + "", timeoutSec, TimeUnit.SECONDS);
        //解决success拆箱引起空指针
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        //获取线程表示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取redis锁中的线程标识
        String key = KEY_PREFIX + name;
        String threadIdFromRedis = stringRedisTemplate.opsForValue().get(key);
        if (threadId.equals(threadIdFromRedis)) {
            stringRedisTemplate.delete(key);
        }

    }
}
