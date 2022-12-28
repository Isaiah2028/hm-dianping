package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @Author: IsaiahLu
 * 登录逻辑 拦截器
 * @date: 2022/12/27 16:46
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {



    /**
     * 前置拦截
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断是否需要拦截，ThreadLocal中是否有user信息
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        //有用户，放行
        return true;
    }

    /**
     * 后置拦截
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
    }

}
