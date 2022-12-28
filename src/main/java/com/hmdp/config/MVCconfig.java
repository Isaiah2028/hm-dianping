package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RereshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Author: IsaiahLu
 * @date: 2022/12/27 17:04
 */
@Configuration
public class MVCconfig  implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 注册拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //拦截部分
        registry.addInterceptor( new LoginInterceptor())
                .excludePathPatterns("/user/code",
                                      "/user/login",
                                      "/blog/hot",
                                      "/shop/**",
                                      "/shop-type/**",
                                      "/upload/**",
                                      "/voucher/**").order(1);
        //拦截所有
        registry.addInterceptor(new RereshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

}
