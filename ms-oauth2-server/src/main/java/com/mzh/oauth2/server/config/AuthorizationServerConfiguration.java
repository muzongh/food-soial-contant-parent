package com.mzh.oauth2.server.config;

import com.mzh.commons.model.domain.SignInIdentity;
import com.mzh.oauth2.server.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import java.util.LinkedHashMap;

/**
 * 授权服务
 */
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

    //RedisTokenStore
    @Resource
    private RedisTokenStore redisTokenStore;

    //认证管理对象
    @Resource
    private AuthenticationManager authenticationManager;

    //密码编码器
    @Resource
    private PasswordEncoder passwordEncoder;

    //客户端配置类
    @Resource
    private ClientOAuth2DataConfiguration clientOAuth2DataConfiguration;

    //登陆校验
    @Resource
    private UserService userService;

    /**
     * 配置令牌端点安全约束
     * @param security
     * @throws Exception
     */
    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        //允许访问token的公钥，默认/oauth/token_key是受保护的
        security.tokenKeyAccess("permitAll()")
                //允许检查token的状态，默认/oauth/check_token是受保护的
                .checkTokenAccess("permitAll()");
    }

    /**
     * 客户端配置 - 授权模型
     * @param clients
     * @throws Exception
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory().withClient(clientOAuth2DataConfiguration.getClientId())//客户端标识ID
                .secret(passwordEncoder.encode(clientOAuth2DataConfiguration.getSecret()))//客户端安全码
                .authorizedGrantTypes(clientOAuth2DataConfiguration.getGrantTypes())//授权类型
                .accessTokenValiditySeconds(clientOAuth2DataConfiguration.getTokenValidityTime())//token 有效时间，单位秒
                .refreshTokenValiditySeconds(clientOAuth2DataConfiguration.getRefreshTokenValidityTime())//token 刷新时间，单位秒
                .scopes(clientOAuth2DataConfiguration.getScopes());//客户端访问范围
    }

    /**
     *配置授权以及令牌的访问端点和令牌服务
     * @param endpoints
     * @throws Exception
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        //认证器
        endpoints.authenticationManager(authenticationManager)
                //具体登陆方法
                .userDetailsService(userService)
                //token存储方式redis
                .tokenStore(redisTokenStore)
                //增强返回对象，增强返回结果
                .tokenEnhancer((accessToken,authentication)->{
                    //获取登陆用户的信息，然后设置
                    SignInIdentity signInIdentity= (SignInIdentity) authentication.getPrincipal();
                    LinkedHashMap<String, Object> objectObjectLinkedHashMap = new LinkedHashMap<String, Object>();
                    objectObjectLinkedHashMap.put("nickname",signInIdentity.getNickname());
                    objectObjectLinkedHashMap.put("avatarUrl",signInIdentity.getAvatarUrl());
                    DefaultOAuth2AccessToken defaultOAuth2AccessToken= (DefaultOAuth2AccessToken) accessToken;
                    defaultOAuth2AccessToken.setAdditionalInformation(objectObjectLinkedHashMap);
                    return defaultOAuth2AccessToken;
                });
    }
}
