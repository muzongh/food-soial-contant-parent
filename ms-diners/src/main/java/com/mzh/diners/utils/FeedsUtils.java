package com.mzh.diners.utils;

import com.mzh.commons.model.domain.ResultInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Configuration
public class FeedsUtils {

    private static String feedsServerName;

    @Value("${service.name.ms-feeds-server}")
    private void setOauthServerName(String feedsServerName) {
        FeedsUtils.feedsServerName = feedsServerName;
    }

    private static RestTemplate restTemplate;

    @Resource
    private void setRestTemplate(RestTemplate restTemplate){
        FeedsUtils.restTemplate = restTemplate;
    }

    /**
     * 发送请求添加或者移除关注人的Feed列表
     *
     * @param followDinerId 关注好友的ID
     * @param accessToken   当前登录用户token
     * @param type          0=取关 1=关注
     */
    public static void sendSaveOrRemoveFeed(Integer followDinerId, String accessToken, int type) {
        String feedsUpdateUrl = feedsServerName + "feeds/changeFollowingFeed/"
                + followDinerId + "?access_token=" + accessToken;
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("type", type);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(feedsUpdateUrl, entity, ResultInfo.class);
    }

}
