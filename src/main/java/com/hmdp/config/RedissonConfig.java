package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: IsaiahLu
 * @date: 2023/1/5 22:09
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //配置类
        Config config = new Config();
        //添加redis地址，这里添加了单点登录的地址，也可以使用Config.useClusterServsers()添加集群
        config.useSingleServer().setAddress("redis://121.40.160.237:6379");
        //创建客户端
        return Redisson.create(config);
    }
}
