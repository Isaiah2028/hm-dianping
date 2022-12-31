package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * TODO redis工具类
 *
 * @Author: IsaiahLu
 * @date: 2022/12/29 18:12
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * TODO 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间命
     *
     * @param key    key
     * @param object 要存入的对象
     * @param time   设置的逻辑过期时间长度
     * @param unit   时间处理单元工具
     */
    public void set(String key, Object object, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object), time, unit);
    }

    /**
     * TODO 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param key    key
     * @param object 要存得对象
     * @param time   过期时间
     * @param unit   时间处理单元工具
     */
    public void setWithLogicalExpire(String key, Object object, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * TODO 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix  key前缀
     * @param id         商户id
     * @param type       实体类型
     * @param dbFallBack 数据库查询结果
     * @param time       过期时间
     * @param unit       时间单元
     * @param <R>        实体类泛型
     * @param <ID>       泛型id
     * @return r       返回对象
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        //1,从redis中获取商户信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断json存在
        if (StringUtils.isNotBlank(json)) {
            //存在
            return JSONUtil.toBean(json, type);
        }
        //判断命中是否为空值
        if (json != null) {
            //this is error message json result
            return null;
        }
        //从数据库查询数据
        R r = dbFallBack.apply(id);
        //not exit in database
        if (r == null) {
            //set null data into redis;
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // return error message null
            return null;
        }
        //数据库存在,写入redis
        this.set(key, r, time, unit);
        return r;
    }

    //准备线城池
    private static final ExecutorService CACHE_REBUID_EXCUTOR = Executors.newFixedThreadPool(10);

    /**
     * TODO 根据指定的key查询redis 解决缓存穿透的问题
     *
     * @param id id
     * @return r
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, Long id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unite) {

        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        //判断json是否为空
        if (StringUtils.isBlank(json)) {
            //是空直接返回
            return null;
        }
        //不为空，判断是否null值
        //解析json
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接过期
            log.info("未过期！！！！！ddd");
        }
        if (expireTime.isBefore(LocalDateTime.now())) {
            log.info("过期了过期了！！！！");
        }
        //过期， 需要重建缓存
        //6缓存重建
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = trLock(lockKey);
        if (isLock) {
            //获取互斥锁成功，开启独立线程，实现缓存重建
            CACHE_REBUID_EXCUTOR.submit(() -> {
                try {
                    //从数据库查询数据
                    R r1 = dbFallback.apply((ID) id);
                    //写入redis
                    this.setWithLogicalExpire(key, r1, time, unite);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(key);
                }
            });
        }
        return r;
    }

    /**
     * 获取锁
     *
     * @param key key
     * @return flag 是否获取锁
     */
    private boolean trLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
