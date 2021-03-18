package com.mzh.diners.utils;

import cn.hutool.core.bean.BeanUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;

@Configuration
public class UserUtils {

    private static String oauthServerName;

    @Value("${service.name.ms-oauth-server}")
    private void setOauthServerName(String oauthServerName) {
        UserUtils.oauthServerName = oauthServerName;
    }

    private static RestTemplate restTemplate;

    @Resource
    private void setRestTemplate(RestTemplate restTemplate){
        UserUtils.restTemplate = restTemplate;
    }



    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    public static SignInDinerInfo loadSignInDinerInfo(String accessToken) {
        // 必须登录
        AssertUtil.mustLogin(accessToken);
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        if (dinerInfo == null){
            throw new ParameterException(ApiConstant.NO_LOGIN_CODE, ApiConstant.NO_LOGIN_MESSAGE);
        }
        return dinerInfo;
    }

}
