package com.mzh.follow.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.Follow;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.follow.mapper.FollowMapper;
import com.mzh.follow.utils.FeedsUtils;
import com.mzh.follow.utils.UserUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注/取关业务逻辑层
 */
@Service
public class FollowService {

    @Resource
    private FollowMapper followMapper;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 获取粉丝ID列表
     *
     * @param dinerId
     * @return
     */
    public Set<Integer> findFollowerIds(Integer dinerId){

        AssertUtil.isTrue(dinerId== null,"请选择要查看的用户");
        Set<Integer> members = redisTemplate.opsForSet().members(RedisKeyConstant.followers.getKey() + dinerId);
        return members;
    }

    /**
     * 共同关注列表
     *
     * @param dinerId
     * @param accessToken
     * @param path
     * @return
     */
    public ResultInfo findCommonsFriends(Integer dinerId,String accessToken,String path){

        AssertUtil.isTrue(dinerId == null || dinerId < 1,
                "请选择要查看的人");

        //获取用户登陆信息
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        //获取登陆用户关注信息
        String loginDinerKey=RedisKeyConstant.following.getKey() + signInDinerInfo.getId();

        //获取登陆用户查看用户的关注信息
        String dinerKey = RedisKeyConstant.following.getKey() + dinerId;

        //计算交集
        Set<Integer> intersect = redisTemplate.opsForSet().intersect(loginDinerKey, dinerKey);

        if (intersect == null || intersect.isEmpty()){
            return  ResultInfoUtil.buildSuccess(path,new ArrayList<ShortDinerInfo>());
        }
        List<ShortDinerInfo> shortDinerInfos = UserUtils.loadDinerMessageByIds(accessToken, StrUtil.join(",", intersect));

        return ResultInfoUtil.buildSuccess(path,shortDinerInfos);

    }

    /**
     * 关注/取关
     *
     * @param followDinerId 关注的食客ID
     * @param isFollowed    是否关注 1=关注 0=取关
     * @param accessToken   登录用户token
     * @param path          访问地址
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultInfo follow(Integer followDinerId, int isFollowed,
                             String accessToken, String path) {
        // 是否选择了关注对象
        AssertUtil.isTrue(followDinerId == null || followDinerId < 1,
                "请选择要关注的人");
        // 获取登录用户信息 (封装方法)
        SignInDinerInfo dinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        // 获取当前登录用户与需要关注用户的关注信息
        Follow follow = followMapper.selectFollow(dinerInfo.getId(), followDinerId);

        // 如果没有关注信息，且要进行关注操作 -- 添加关注
        if (follow == null && isFollowed == 1) {
            // 添加关注信息
            int count = followMapper.save(dinerInfo.getId(), followDinerId);
            // 添加关注列表到 Redis
            if (count == 1) {
                addToRedisSet(dinerInfo.getId(), followDinerId);
            }
            FeedsUtils.sendSaveOrRemoveFeed(followDinerId,accessToken,1);
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,
                    "关注成功", path, "关注成功");
        }

        // 如果有关注信息，且目前处于关注状态，且要进行取关操作 -- 取关关注
        if (follow != null && follow.getIsValid() == 1 && isFollowed == 0) {
            // 取关
            int count = followMapper.update(follow.getId(), isFollowed);
            // 移除 Redis 关注列表
            if (count == 1) {
                removeFromRedisSet(dinerInfo.getId(), followDinerId);
            }
            FeedsUtils.sendSaveOrRemoveFeed(followDinerId,accessToken,0);
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,
                    "成功取关", path, "成功取关");
        }

        // 如果有关注信息，且目前处于取关状态，且要进行关注操作 -- 重新关注
        if (follow != null && follow.getIsValid() == 0 && isFollowed == 1) {
            // 重新关注
            int count = followMapper.update(follow.getId(), isFollowed);
            // 添加关注列表到 Redis
            if (count == 1) {
                addToRedisSet(dinerInfo.getId(), followDinerId);
            }
            FeedsUtils.sendSaveOrRemoveFeed(followDinerId,accessToken,1);
            return ResultInfoUtil.build(ApiConstant.SUCCESS_CODE,
                    "关注成功", path, "关注成功");
        }

        return ResultInfoUtil.buildSuccess(path, "操作成功");
    }

    /**
     * 关注列表
     *
     * @param accessToken
     * @param path
     * @param followType
     * @return
     */
    public ResultInfo followingOrFollowerList(String accessToken,String path,String followType){

        AssertUtil.isNotEmpty(followType,"请选择要查询的列表类别");
        AssertUtil.isTrue(!followType.equals("1") && !followType.equals("0"),"请选择正确的列表类型");
        //获取当前用户登陆信息
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        String dinerKey= ("0".equals(followType)
                ? RedisKeyConstant.following.getKey()
                : RedisKeyConstant.followers.getKey() )
                + signInDinerInfo.getId();
        Set<Integer> members = redisTemplate.opsForSet().members(dinerKey);
        List<ShortDinerInfo> shortDinerInfos = UserUtils.loadDinerMessageByIds(accessToken, StrUtil.join(",", members));
        return ResultInfoUtil.buildSuccess(path,shortDinerInfos);

    }

    /**
     * 添加关注列表到 Redis
     *
     * @param dinerId
     * @param followDinerId
     */
    private void addToRedisSet(Integer dinerId, Integer followDinerId) {
        redisTemplate.opsForSet().add(RedisKeyConstant.following.getKey() + dinerId, followDinerId);
        redisTemplate.opsForSet().add(RedisKeyConstant.followers.getKey() + followDinerId, dinerId);
    }

    /**
     * 移除 Redis 关注列表
     *
     * @param dinerId
     * @param followDinerId
     */
    private void removeFromRedisSet(Integer dinerId, Integer followDinerId) {
        redisTemplate.opsForSet().remove(RedisKeyConstant.following.getKey() + dinerId, followDinerId);
        redisTemplate.opsForSet().remove(RedisKeyConstant.followers.getKey() + followDinerId, dinerId);
    }

}
