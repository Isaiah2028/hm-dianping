package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Override
    public Result queryById(Long id) {
        //1,从redis中获取商户信息
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //命中的是否是空值
        if (shopJson != null) {
            //返回错误信息
            return Result.fail("店铺不存在！");

        }
        //4不存在，根据id查询数据库
        Shop shop = getById(id);
        log.info("查询数据库商户==============={} " + shop);
        //5不存在，返回错误
        if (shop == null) {
            //将空值存入redis
            //返回错误信息
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商户不存在");
        }
        //6存在写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7返回结果
        return Result.ok(shop);
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
