package com.mzh.feeds.service;

import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.Feeds;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.feeds.mapper.FeedsMapper;
import com.mzh.feeds.utils.UserUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;

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
    public void createFeed(Feeds feeds,String accessToken){

        //校验参数
        AssertUtil.isNotEmpty(feeds.getContent(),"请输入内容");
        AssertUtil.isTrue(feeds.getContent().length()>100,"输入内容过多！");
        //获取用户登陆信息
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        //feed关联用户信息
        feeds.setFkDinerId(signInDinerInfo.getId());
        //添加feed
        int save = feedsMapper.save(feeds);
        AssertUtil.isTrue(save == 0,"添加失败，请稍后再试");
        //推送到粉丝的列表里 --FIXME 后续使用异步消息队列解决性能问题
        //获取登陆用户粉丝列表
        List<Integer> followerIds = findFollowers(signInDinerInfo.getId());
        //推送feed
        Long now = System.currentTimeMillis();
        followerIds.forEach(followerId->{
            String key = RedisKeyConstant.following_feeds.getKey() + followerId;
            redisTemplate.opsForSet().add(key, feeds.getId() , now);
        });


    }

    /**
     * 删除feed
     *
     * @param id
     * @param accessToken
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFeed(Integer id,String accessToken){

        AssertUtil.isTrue(id == null || id < 1 , "请选择要删除的动态");

        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        Feeds byId = feedsMapper.findById(id);

        AssertUtil.isTrue(byId == null,"该动态已被删除!");
        AssertUtil.isTrue(byId.getFkDinerId() != signInDinerInfo.getId(),"只能删除自己的动态！");

        int delete = feedsMapper.delete(id);
        if (delete == 0){
            return;
        }
        List<Integer> followerIds = findFollowers(signInDinerInfo.getId());
        //推送feed
        Long now = System.currentTimeMillis();
        followerIds.forEach(followerId->{
            String key = RedisKeyConstant.following_feeds.getKey() + followerId;
            redisTemplate.opsForSet().remove(key, id);
        });

    }

    /**
     * 获取粉丝ID集合
     *
     * @param dinerId
     * @return
     */
    private List<Integer> findFollowers(Integer dinerId){
        String url = msFollowServer + "/follow/findFollowerIds/" + dinerId;
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, true);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            throw new ParameterException(resultInfo.getCode(),resultInfo.getMessage());
        }
        List<Integer> followerIds = (List<Integer>) resultInfo.getData();
        return followerIds;
    }

}
