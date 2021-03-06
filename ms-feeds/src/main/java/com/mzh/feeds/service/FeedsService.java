package com.mzh.feeds.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.Feeds;
import com.mzh.commons.model.vo.FeedsVO;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.feeds.mapper.FeedsMapper;
import com.mzh.feeds.utils.UserUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeedsService {

    @Value("${service.name.ms-follow-server}")
    private String msFollowServer;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private FeedsMapper feedsMapper;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 创建feed
     *
     * @param feeds
     * @param accessToken
     */
    @Transactional(rollbackFor = Exception.class)
    public void createFeed(Feeds feeds, String accessToken) {

        //校验参数
        AssertUtil.isNotEmpty(feeds.getContent(), "请输入内容");
        AssertUtil.isTrue(feeds.getContent().length() > 100, "输入内容过多！");
        //获取用户登陆信息
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        //feed关联用户信息
        feeds.setFkDinerId(signInDinerInfo.getId());
        //添加feed
        int save = feedsMapper.save(feeds);
        AssertUtil.isTrue(save == 0, "添加失败，请稍后再试");
        //推送到粉丝的列表里 --FIXME 后续使用异步消息队列解决性能问题
        //获取登陆用户粉丝列表
        List<Integer> followerIds = findFollowers(signInDinerInfo.getId());
        //推送feed
        Long now = System.currentTimeMillis();
        followerIds.forEach(followerId -> {
            String key = RedisKeyConstant.following_feeds.getKey() + followerId;
            redisTemplate.opsForZSet().add(key, feeds.getId(), now);
        });


    }

    /**
     * 删除feed
     *
     * @param id
     * @param accessToken
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFeed(Integer id, String accessToken) {

        AssertUtil.isTrue(id == null || id < 1, "请选择要删除的动态");

        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        Feeds byId = feedsMapper.findById(id);

        AssertUtil.isTrue(byId == null, "该动态已被删除!");
        AssertUtil.isTrue(byId.getFkDinerId() != signInDinerInfo.getId(), "只能删除自己的动态！");

        int delete = feedsMapper.delete(id);
        if (delete == 0) {
            return;
        }
        List<Integer> followerIds = findFollowers(signInDinerInfo.getId());
        //推送feed
        followerIds.forEach(followerId -> {
            String key = RedisKeyConstant.following_feeds.getKey() + followerId;
            redisTemplate.opsForZSet().remove(key, id);
        });

    }

    /**
     * 变更feed流
     *
     * @param followingDinerId 取关/关注用户ID
     * @param accessToken      登陆用户Token
     * @param type             1 关注；0 取关
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeFollowingFeed(Integer followingDinerId, String accessToken, Integer type) {
        AssertUtil.isTrue(followingDinerId == null || followingDinerId < 1, "请选择需要操作的用户！");
        AssertUtil.isTrue(type != 0 && type != 1, "请选择正确的操作类型！");
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        List<Feeds> byDinerIdFeeds = feedsMapper.findByDinerId(followingDinerId);

        String key = RedisKeyConstant.following_feeds.getKey() + signInDinerInfo.getId();
        if (type == 0) {
            //取关
            List<Integer> feedIds = byDinerIdFeeds.stream().map(feed -> feed.getId()).collect(Collectors.toList());
            redisTemplate.opsForZSet().remove(key, feedIds.toArray(new Integer[]{}));
        } else {
            //关注
            Set<ZSetOperations.TypedTuple> typedTuples = byDinerIdFeeds.stream()
                    .map(feed -> new DefaultTypedTuple<>(feed.getId(), (double) feed.getUpdateDate().getTime()))
                    .collect(Collectors.toSet());
            redisTemplate.opsForZSet().add(key, typedTuples);
        }

    }

    /**
     * 根据时间由远及近，每次查询20条
     *
     * @param page
     * @param accessToken
     * @return
     */
    public List<FeedsVO> selectForPage(Integer page, String accessToken) {
        if (page == null || page < 1) {
            page = 1;
        }
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        String key = RedisKeyConstant.following_feeds.getKey() + signInDinerInfo.getId();

        long start = (page - 1) * ApiConstant.PAGE_SIZE;
        long end = page * ApiConstant.PAGE_SIZE - 1;
        Set<Integer> feedIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (feedIds == null || feedIds.isEmpty()) {
            return Lists.newArrayList();
        }
        List<Feeds> feedsByIds = feedsMapper.findFeedsByIds(feedIds);
        List<Integer> followingIds = new ArrayList<>();
        List<FeedsVO> feedsVOS = feedsByIds.stream().map(feed -> {
            FeedsVO feedsVO = new FeedsVO();
            BeanUtil.copyProperties(feed, feedsVO);
            followingIds.add(feed.getFkDinerId());
            return feedsVO;
        }).collect(Collectors.toList());
        List<ShortDinerInfo> shortDinerInfos = UserUtils.loadDinerMessageByIds(accessToken, StrUtil.join(",", followingIds));
        //stream处理成Map<Integer, ShortDinerInfo>
        Map<Integer, ShortDinerInfo> shortDinerInfoMap = shortDinerInfos.stream().collect(Collectors.toMap(
                //KEY
                shortDinerInfo -> shortDinerInfo.getId(),
                //VALUE
                shortDinerInfo -> shortDinerInfo
        ));
        feedsVOS.forEach(feedsVO -> {
            feedsVO.setDinerInfo(shortDinerInfoMap.get(feedsVO.getFkDinerId()));
        });
        return feedsVOS;
    }

    /**
     * 获取粉丝ID集合
     *
     * @param dinerId
     * @return
     */
    private List<Integer> findFollowers(Integer dinerId) {
        String url = msFollowServer + "/follow/findFollowerIds/" + dinerId;
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, true);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        List<Integer> followerIds = (List<Integer>) resultInfo.getData();
        return followerIds;
    }

}
