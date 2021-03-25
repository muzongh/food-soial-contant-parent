package com.mzh.diners.service;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.model.vo.NearMeDinerVO;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.diners.utils.UserUtils;
import lombok.NonNull;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.ReactiveGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class NearMeService {

    @Resource
    private DinersService dinersService;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 更新食客坐标
     *
     * @param accessToken
     * @param lon         经度
     * @param lat         纬度
     */
    public void updateDinerLocation(String accessToken, Float lon, Float lat) {

        AssertUtil.isTrue(lon == null, "获取经度失败");
        AssertUtil.isTrue(lat == null, "获取纬度失败");
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        String locationKey = RedisKeyConstant.diner_location.getKey();

        RedisGeoCommands.GeoLocation geoLocation = new RedisGeoCommands
                .GeoLocation(signInDinerInfo.getId(), new Point(lon, lat));
        redisTemplate.opsForGeo().add(locationKey, geoLocation);

    }

    /**
     * 获取附近的人
     *
     * @param accessToken
     * @param radius      半径
     * @param lon         经度
     * @param lat         纬度
     * @return
     */
    public List<NearMeDinerVO> findNearMe(String accessToken, Integer radius, Float lon, Float lat) {

        if (radius == null || radius == 0) {
            radius = 1000;
        }
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        String key = RedisKeyConstant.diner_location.getKey();
        Point point = null;
        if (lon == null || lat == null) {
            List<Point> points = redisTemplate.opsForGeo().position(key, signInDinerInfo.getId());
            AssertUtil.isTrue(points == null || points.isEmpty(), "获取用户经纬度失败");
            point = points.get(0);
        }else {
            point = new Point(lon, lat);
        }
        Distance distance = new Distance(radius, RedisGeoCommands.DistanceUnit.METERS);
        //初始化GEI命令参数对象
        RedisGeoCommands.GeoRadiusCommandArgs commandArgs =
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs();
        //附近的人限制20，包含距离，由近及远
        commandArgs.limit(20).includeDistance().sortAscending();
        //以用户经纬度为圆心，范围 radius
        Circle circle = new Circle(point, distance);
        //获取附近的人的GEO location信息
        GeoResults<RedisGeoCommands.GeoLocation> geoLocationGeoResults =
                redisTemplate.opsForGeo().radius(key, circle, commandArgs);
        //初始化dinerId列表
        List<Integer> dinerIds = Lists.newArrayList();
        //完善用户头像昵称信息
        Map<Integer, NearMeDinerVO> nearMeDinerVOMap = Maps.newLinkedHashMap();
        for (GeoResult<RedisGeoCommands.GeoLocation> geoLocationGeoResult : geoLocationGeoResults) {
            RedisGeoCommands.GeoLocation<Integer> content = geoLocationGeoResult.getContent();
            double resultDistance = geoLocationGeoResult.getDistance().getValue();
            //初始化VO
            NearMeDinerVO nearMeDinerVO = new NearMeDinerVO();
            nearMeDinerVO.setDistance(NumberUtil.round(resultDistance, 1) + "m");
            nearMeDinerVO.setId(content.getName());
            dinerIds.add(content.getName());
            nearMeDinerVOMap.put(content.getName(), nearMeDinerVO);
        }
        //获取附近的人的信息
        List<ShortDinerInfo> shortDinerInfos =
                dinersService.findByIds(StrUtil.join(",", dinerIds));
        shortDinerInfos.forEach(shortDinerInfo -> {
            NearMeDinerVO nearMeDinerVO = nearMeDinerVOMap.get(shortDinerInfo.getId());
            nearMeDinerVO.setNickname(shortDinerInfo.getNickname());
            nearMeDinerVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
        });
        return Lists.newArrayList(nearMeDinerVOMap.values());
    }

    /**
     * 获取登陆用户和目标用户之间的距离
     *
     * @param accessToken
     * @param targetDinerId
     * @return
     */
    public String getDistance(String accessToken,Integer targetDinerId){
        AssertUtil.isTrue( targetDinerId == null || targetDinerId < 1,"请选择目标用户");
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        String key = RedisKeyConstant.diner_location.getKey();
        Distance distance = redisTemplate.opsForGeo().distance(key, signInDinerInfo.getId(), targetDinerId);
        AssertUtil.isTrue(distance == null ,"未找到目标用户位置，可能目标位置未开启定位！");
        return NumberUtil.round(distance.getValue(),1)+"m";
    }

}
