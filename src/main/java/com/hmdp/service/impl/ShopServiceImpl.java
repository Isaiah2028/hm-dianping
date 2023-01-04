package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.execute.Execute;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassthrough(id);
         Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //逻辑过期，key预热方式结解决
       // Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            Result.fail("店铺不存在！");
        }
        //7返回结果
        return Result.ok(shop);
    }

    //准备线城池
    private static final ExecutorService CACHE_REBUID_EXCUTOR = Executors.newFixedThreadPool(10);

    /**
     * TODO 逻辑过期处理方法
     * @param id
     * @return
     */
/*    public Shop queryWithLogicalExpire(Long id) {
        //1,从redis中获取商户信息
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2未命中
        if (StrUtil.isBlank(shopJson)) {
            //3存在直接返回
            return null;
        }
        //4命中,把json转化为
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1未过期，直接返回店铺信息
            return shop;
        }
        //5.2已过期，尝试获取互斥锁，缓存重建
        //6缓存重建
        //6.1获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = trLock(lockKey);
        //6.2判断是否获取到锁
        if (isLock) {
            //TODO 6.4成功，开启独立线程，缓存重建
            CACHE_REBUID_EXCUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 30L);
                    //释放锁
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });

        }
        //6.3失败，返回过期的店铺信息
        //8返回结果
        return shop;
    }*/






    /**
     * 互斥锁解决缓存击穿
     *
     * @param id 商户id
     * @return shop
     */
    public Shop queryWithMutex(Long id) {
        //1,从redis中获取商户信息
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否是空值
        if (shopJson != null) {
            //返回错误信息
            return null;

        }
        //4实现缓存重建
        //4.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = trLock(lockKey);
            //4.2判断锁获取是否成功
            if (!isLock) {
                //4.3失败，则休眠并重试
                Thread.sleep(20);
                //递归
                return queryWithMutex(id);
            }
            //4不存在，根据id查询数据库
            shop = getById(id);
            log.info("查询数据库商户==============={} " + shop);

            //模拟重建延时
            Thread.sleep(200);

            //5不存在，返回错误
            if (shop == null) {
                //将空值存入redis
                //返回错误信息
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6存在写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }

        //8返回结果
        return shop;
    }

    /**
     * 穿透解决方案               
     *
     * @param id
     * @return shop
     */
    public Shop queryWithPassthrough(Long id) {
        //1,从redis中获取商户信息
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中的是否是空值
        if (shopJson != null) {
            //返回错误信息
            return null;

        }
        //4不存在，根据id查询数据库
        Shop shop = getById(id);
        log.info("查询数据库商户==============={} " + shop);
        //5不存在，返回错误
        if (shop == null) {
            //将空值存入redis
            //返回错误信息
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6存在写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7返回结果
        return shop;
    }

     /**
     * 获取锁
     *
     * @param key
     * @return
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

    public void saveShop2Redis(Long id, Long expireSeconds) {
        //从数据库查询数据
        Shop shop = getById(id);
        //封装逻辑过期时间

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新商品信息
     *
     * @param shop
     * @return
     */
    @Override
    public Result update(Shop shop) {

        Long id = shop.getId();
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        if (id == null) {
            return Result.fail("商铺id不能为空！");
        }
        //先修改数据库
        updateById(shop);
        //更新缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
