package com.mzh.oauth2.server.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.domain.SignInIdentity;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户中心
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private HttpServletRequest request;

    @Resource
    private RedisTokenStore redisTokenStore;

    /**
     * 获取当前登陆信息
     *
     * @param authentication
     * @return
     */
    @GetMapping("/me")
    public ResultInfo getCurrentUser(Authentication authentication){
        //获取登陆用户的信息，然后设置
        SignInIdentity signInIdentity= (SignInIdentity) authentication.getPrincipal();
        //转为前端可用对象
        SignInDinerInfo signInDinerInfo=new SignInDinerInfo();
        BeanUtils.copyProperties(signInIdentity,signInDinerInfo);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),signInDinerInfo);
    }

    /**
     * 安全退出
     *
     * @param access_token
     * @param authorization
     * @return
     */
    @GetMapping("/logout")
    public ResultInfo logout(String access_token,String authorization){

        //判断access_token为空，
        if (StringUtils.isBlank(access_token)){
            access_token=authorization;
        }
        if (StringUtils.isBlank(access_token)){
            return ResultInfoUtil.buildSuccess(request.getServletPath(),"退出成功");
        }
        //判断bearer token是否为空
        if (access_token.toLowerCase().contains("bearer ".toLowerCase())){
            access_token=access_token.toLowerCase().replace("bearer ","");
        }

        //清除redis token信息
        OAuth2AccessToken oAuth2AccessToken = redisTokenStore.readAccessToken(access_token);
        if (oAuth2AccessToken!=null){
            redisTokenStore.removeAccessToken(oAuth2AccessToken);
            OAuth2RefreshToken refreshToken = oAuth2AccessToken.getRefreshToken();
            redisTokenStore.removeRefreshToken(refreshToken);
        }
        return ResultInfoUtil.buildSuccess(request.getServletPath(),"退出成功");

    }



}
