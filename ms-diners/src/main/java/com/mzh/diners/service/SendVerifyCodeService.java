package com.mzh.diners.service;

import cn.hutool.core.util.RandomUtil;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.utils.AssertUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 发送验证码业务逻辑
 */
@Service
public class SendVerifyCodeService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     */
    public void send(String phone){
        //检查非空
        AssertUtil.isNotEmpty(phone,"手机号不能为空");
        //根据手机号是否已生成验证码，已生成直接返回
        if (checkCodeIsExpired(phone)){
            return;
        }
        //生成6位验证码
        //String code = RandomUtil.randomNumbers(6);
        String code = "000000";
        //调用短信服务发送短信服务
        //发送成功，将验证码保存至redis,失效时间60s
        String key=RedisKeyConstant.verify_code.getKey().concat(phone);
        redisTemplate.opsForValue().set(key,code,60, TimeUnit.SECONDS);

    }

    /**
     * 检查手机号是否已生成验证码
     *
     * @param phone
     * @return
     */
    private Boolean checkCodeIsExpired(String phone){
        String key = RedisKeyConstant.verify_code.getKey().concat(phone);
        String code = redisTemplate.opsForValue().get(key);
        return StringUtils.isBlank(code) ? false : true;
    }

    /**
     * 根据手机号获取验证码
     *
     * @param phone
     * @return
     */
    public String getVerifyCodeByPhone(String phone){
        String key = RedisKeyConstant.verify_code.getKey().concat(phone);
        String code = redisTemplate.opsForValue().get(key);
        return code;
    }

}
