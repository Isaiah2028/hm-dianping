package com.hmdp.utils;

/**
 * TODO 分布式锁
 *
 * @Author: IsaiahLu
 * @date: 2023/1/4 22:05
 */
public interface ILock {
    /**
     * 尝试索取锁
     *
     * @param timeoutSec 过期时间
     * @return true 表示成功，false 表示失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
