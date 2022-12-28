package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证码发送
     *
     * @param phone
     * @param session
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        boolean isPhoneNumber = RegexUtils.isPhoneInvalid(phone);
        if (isPhoneNumber) {
            return Result.fail("手机号格式错误");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到redis
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //session中存入code
        //session.setAttribute("code", code);

        //发送验证码,此处模拟即可，控制台打印
        log.info("验证码发成功！==============={} " + code);
        return Result.ok(code);
    }

    /**
     * 验证用户信息，实现登录
     *
     * @param loginForm
     * @param session
     * @return
     */

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phoneNumber = loginForm.getPhone();
        boolean isPhoneNumber = RegexUtils.isPhoneInvalid(phoneNumber);
        if (isPhoneNumber) {
            return Result.fail("手机号格式错误");
        }
         /*  //校验验证码
        //不一致，报错
        Object sessionCode = session.getAttribute("code");
        String formCode = loginForm.getCode();
        */

        // TODO 从redis 获取验证码并检验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phoneNumber);
        String code = loginForm.getCode();

        if (cacheCode == null || !cacheCode.equals(code)) {
            //验证码错误
            Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户
        User user = query().eq("phone", phoneNumber).one();
        //判断用户是否存在
        //用户不存在，创建用户,保存用户信息
        if (null == user) {
            user = createUserWithPhoneNumber(phoneNumber);
        }

/*        //存在，保存用户信息到session中
       // session.setAttribute("user", user); //敏感信息不返回前端，也减小内存开销
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //TODO 保存用户到redis
        //TODO 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //TODO 将user对象转存为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //将userDTO转成map存入redis时候 dto中的id是long类型 stringRedis 的hash的和value都必须是string的形式
        //Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .ignoreNullValue()
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //或者使用json字符串直接存
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("id", userDTO.getId());
        jsonObject.set("icon", userDTO.getIcon());
        jsonObject.set("nickname", userDTO.getNickName());



        //TODO 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置key有效期 30分钟
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //TODO 返回token
        return Result.ok(token);
    }

    private User createUserWithPhoneNumber(String phoneNumber) {
        //创建用户
        User user = new User();
        user.setPhone(phoneNumber);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
