package com.mzh.points.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.model.pojo.DinerPoints;
import com.mzh.commons.model.vo.DinerPointsRankVO;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.points.mapper.DinerPointsMapper;
import com.mzh.points.utils.UserUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * 积分服务
 */
@Service
public class PointsService {

    private static Integer TOPN = 20;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private DinerPointsMapper dinerPointsMapper;

    /**
     * 添加积分
     *
     * @param dinerId 食客ID
     * @param points  积分值
     * @param types   积分类型： 0=签到，1=关注好友，2=添加feed，3=添加商户评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Integer dinerId, Integer points, Integer types) {

        AssertUtil.isTrue(dinerId == null || dinerId < 1, "食客不能为空");
        AssertUtil.isTrue(points == null || points < 1, "积分数值不能为空");
        AssertUtil.isTrue(types == null, "请选择对应的积分类型");

        //插入数据库
        DinerPoints dinerPoints = new DinerPoints();
        dinerPoints.setFkDinerId(dinerId);
        dinerPoints.setPoints(points);
        dinerPoints.setTypes(types);
        dinerPointsMapper.save(dinerPoints);

        redisTemplate.opsForZSet().incrementScore(
                RedisKeyConstant.diner_points.getKey(), dinerId, points
        );

    }

    /**
     * 查询前20积分排行榜，并显示个人排名 --redis
     *
     * @param accessToken
     * @return
     */
    public List<DinerPointsRankVO> findDinerPointRankForRedis(String accessToken) {
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        //统计积分排行榜
        Set<ZSetOperations.TypedTuple<Integer>> rangeWithScores = redisTemplate.opsForZSet()
                .reverseRangeWithScores(RedisKeyConstant.diner_points.getKey(), 0, 19);
        if (rangeWithScores == null || rangeWithScores.isEmpty()){
            return Lists.newArrayList();
        }
        //初始化食客ID集合
        List<Integer> rankDinerIds = Lists.newArrayList();
        // 根据 key：食客 ID value：积分信息 构建一个 Map
        Map<Integer, DinerPointsRankVO> ranksMap = new LinkedHashMap<>();
        Integer rank = 1;
        for (ZSetOperations.TypedTuple<Integer> rangeWithScore : rangeWithScores) {
            //食客ID
            Integer dinerId = rangeWithScore.getValue();
            //食客分数
            Integer score = rangeWithScore.getScore().intValue();
            rankDinerIds.add(dinerId);
            DinerPointsRankVO dinerPointsRankVO = new DinerPointsRankVO();
            dinerPointsRankVO.setRanks(rank);
            dinerPointsRankVO.setTotal(score);
            dinerPointsRankVO.setId(dinerId);
            ranksMap.put(dinerId,dinerPointsRankVO);
            rank++;
        }
        //完善食客头像和昵称
        List<ShortDinerInfo> shortDinerInfos = UserUtils.loadDinerMessageByIds(accessToken, StrUtil.join(",", rankDinerIds));
        for (ShortDinerInfo shortDinerInfo : shortDinerInfos) {
            DinerPointsRankVO dinerPointsRankVO = ranksMap.get(shortDinerInfo.getId());
            dinerPointsRankVO.setNickname(shortDinerInfo.getNickname());
            dinerPointsRankVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
        }

        if (ranksMap.containsKey(signInDinerInfo.getId())) {
            DinerPointsRankVO dinerPointsRankVO = ranksMap.get(signInDinerInfo.getId());
            dinerPointsRankVO.setIsMe(1);
            return Lists.newArrayList(ranksMap.values());
        }

        Long myRank = redisTemplate.opsForZSet().reverseRank(RedisKeyConstant.diner_points.getKey(), signInDinerInfo.getId());
        if (myRank != null){
            DinerPointsRankVO dinerPointsRankVO = new DinerPointsRankVO();
            BeanUtil.copyProperties(signInDinerInfo,dinerPointsRankVO);
            dinerPointsRankVO.setRanks(myRank.intValue()+1);//排名从0开始
            dinerPointsRankVO.setIsMe(1);
            Double score = redisTemplate.opsForZSet().score(RedisKeyConstant.diner_points.getKey(), signInDinerInfo.getId());
            dinerPointsRankVO.setTotal(score.intValue());
            ranksMap.put(signInDinerInfo.getId(),dinerPointsRankVO);
        }
        return Lists.newArrayList(ranksMap.values());
    }

    /**
     * 查询前20积分排行榜，并显示个人排名--关系型数据库 TODO SQL语句mysql8.0之前不可用，rank() over()
     *
     * @param accessToken
     * @return
     */
    public List<DinerPointsRankVO> findDinerPointRank(String accessToken) {

        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        List<DinerPointsRankVO> topN = dinerPointsMapper.findTopN(TOPN);

        if (topN == null || topN.isEmpty()) {
            return Lists.newArrayList();
        }

        Map<Integer, DinerPointsRankVO> rankVOMap = new LinkedHashMap<>();
        for (DinerPointsRankVO dinerPointsRankVO : topN) {
            rankVOMap.put(dinerPointsRankVO.getId(), dinerPointsRankVO);
        }
        if (rankVOMap.containsKey(signInDinerInfo.getId())) {
            DinerPointsRankVO dinerPointsRankVO = rankVOMap.get(signInDinerInfo.getId());
            dinerPointsRankVO.setIsMe(1);
            return Lists.newArrayList(rankVOMap.values());
        }
        DinerPointsRankVO dinerRank = dinerPointsMapper.findDinerRank(signInDinerInfo.getId());
        dinerRank.setIsMe(1);
        topN.add(dinerRank);
        return topN;
    }

}
