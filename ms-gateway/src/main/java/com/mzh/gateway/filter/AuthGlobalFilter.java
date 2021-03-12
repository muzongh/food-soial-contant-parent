package com.mzh.gateway.filter;

import com.mzh.gateway.component.HandleException;
import com.mzh.gateway.config.IgnoreUrlsConfig;
import org.apache.commons.lang.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * 网关全局过滤器
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Resource
    private IgnoreUrlsConfig ignoreUrlsConfig;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private HandleException handleException;

    /**
     * 身份校验处理
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //判断请求 是否在白名单内
        AntPathMatcher  antPathMatcher=new AntPathMatcher();
        Boolean flag=false;
        String path = exchange.getRequest().getURI().getPath();
        for (String url : ignoreUrlsConfig.getUrls()) {
            if (antPathMatcher.match(url,path)){
                flag=true;
                break;
            }
        }
        //白名单放行
        if (flag){
            return chain.filter(exchange);
        }
        //获取access_token
        String access_token = exchange.getRequest().getQueryParams().getFirst("access_token");
        //判断access_token是否可用
        if (StringUtils.isBlank(access_token)){
            return handleException.writeError(exchange,"请登录");
        }
        String checkTokenUrl="http://ms-oauth2-server/oauth/check_token?token=".concat(access_token);
        try {
            //发送远程请求验证access_token
            ResponseEntity<String> forEntity = restTemplate.getForEntity(checkTokenUrl, String.class);
            //token无效的业务逻辑处理
            if (forEntity.getStatusCode() != HttpStatus.OK){
                return handleException.writeError(exchange,
                        "Token was not recognised,token:".concat(access_token));
            }
            if (StringUtils.isBlank(forEntity.getBody())){
                return handleException.writeError(exchange,
                        "Token was not invalid:".concat(access_token));
            }

        }catch (Exception e){
            return handleException.writeError(exchange,
                    "Token was not recognised,token:".concat(access_token));
        }
        //放行
        return chain.filter(exchange);
    }

    /**
     * 网关过滤排序，越小优先级越高
     *
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
