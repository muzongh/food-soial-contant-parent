package com.mzh.oauth2.server.config;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;

/**
 * Security 配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    //注入Redis连接工厂
    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    //初始化RedisTokenStore用于将token存储至Redis
    @Bean
    public RedisTokenStore redisTokenStore(){
        RedisTokenStore redisTokenStore=new RedisTokenStore(redisConnectionFactory);
        redisTokenStore.setPrefix("TOKEN:");//设置key的层级前缀，方便查询
        return redisTokenStore;
    }

    //初始化密码编码器，用MD5加密密码
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new PasswordEncoder() {
            /**
             * 加密
             * @param charSequence
             * @return
             */
            @Override
            public String encode(CharSequence charSequence) {
                return DigestUtil.md5Hex(charSequence.toString());
            }

            @Override
            public boolean matches(CharSequence charSequence, String s) {
                return DigestUtil.md5Hex(charSequence.toString()).equals(s);
            }
        };
    }

    //初始化认证管理对象
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    //放行和认证规则
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                //放行的请求
                .antMatchers("/oauth/**","/actuator/**").permitAll()
                .and()
                .authorizeRequests()
                //其他请求必须认证
                .anyRequest().authenticated();
    }
}
