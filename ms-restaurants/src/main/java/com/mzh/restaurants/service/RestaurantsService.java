package com.mzh.restaurants.service;

import cn.hutool.core.bean.BeanUtil;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.model.pojo.Restaurant;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.restaurants.mapper.RestaurantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RestaurantsService {

    @Resource
    private RestaurantMapper restaurantMapper;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 根据餐厅ID查询餐厅数据
     *
     * @param restaurantId
     * @return
     */
    public Restaurant findById(Integer restaurantId) {

        AssertUtil.isTrue(restaurantId == null, "请选择餐厅查看");
        String key = RedisKeyConstant.restaurants.getKey() + restaurantId;
        LinkedHashMap restaurantMap = (LinkedHashMap) redisTemplate.opsForHash().entries(key);
        Restaurant restaurant = null;
        if (restaurantMap == null || restaurantMap.isEmpty()) {
            log.info("缓存失效了，查询数据库：{}", restaurantId);
            //查询数据库
            restaurant = restaurantMapper.findById(restaurantId);
            if (restaurant != null){
                //更新缓存
                redisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(restaurant));
            }else {
                restaurant = new Restaurant();
                //写入一个空数据，设置失效时间，60s
                restaurant.setCnName("未知餐厅");
                restaurant.setName("Unknown restaurant");
                redisTemplate.opsForHash().putAll(key,BeanUtil.beanToMap(restaurant));
                redisTemplate.expire(key,60, TimeUnit.SECONDS);
            }
        } else {
            restaurant = BeanUtil.fillBeanWithMap(restaurantMap,
                    new Restaurant(), false);
        }
        return restaurant;

    }

}
