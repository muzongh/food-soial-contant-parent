package com.mzh.feeds.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class UserUtils {

    private static String oauthServerName;

    @Value("${service.name.ms-oauth-server}")
    private void setOauthServerName(String oauthServerName) {
        UserUtils.oauthServerName = oauthServerName;
    }

    private static String dinersServerName;

    @Value("${service.name.ms-diners-server}")
    private void setDinersServerName(String dinersServerName){
        UserUtils.dinersServerName = dinersServerName;
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
        return dinerInfo;
    }

    /**
     * 根据IDS查询食客信息
     *
     * @param accessToken
     * @param ids 示例：ID1,ID2,ID3
     * @return
     */
    public static List<ShortDinerInfo> loadDinerMessageByIds(String accessToken,String ids){
        String dinersIdsUrl = dinersServerName + "/diners/findByIds?access_token={accessToken}&ids={ids}";
        ResultInfo resultInfo = restTemplate.getForObject(dinersIdsUrl,
                ResultInfo.class, accessToken, StrUtil.join(",", ids));
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            return null;
        }
        //处理结果集
        List<LinkedHashMap> dinerInfoMaps = (List<LinkedHashMap>) resultInfo.getData();
        List<ShortDinerInfo> shortDinerInfos = dinerInfoMaps.stream()
                .map(diner -> BeanUtil.fillBeanWithMap(diner, new ShortDinerInfo(), true))
                .collect(Collectors.toList());
        return shortDinerInfos;
    }

}
