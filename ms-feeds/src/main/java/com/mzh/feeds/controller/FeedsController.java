package com.mzh.feeds.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.Feeds;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.feeds.service.FeedsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/feeds")
public class FeedsController {

    @Resource
    private FeedsService feedsService;

    @Resource
    private HttpServletRequest httpServletRequest;

    /**
     * 添加feed
     *
     * @param feeds
     * @param access_token
     * @return
     */
    @PostMapping("createFeeds")
    public ResultInfo createFeeds(@RequestBody Feeds feeds,String access_token){
        feedsService.createFeed(feeds,access_token);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),"创建成功");
    }

    /**
     * 删除feed
     *
     * @param id
     * @param access_token
     * @return
     */
    @DeleteMapping("deleteFeed/{id}")
    public ResultInfo deleteFeed(@PathVariable Integer id,String access_token){
        feedsService.deleteFeed(id,access_token);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),"删除成功");
    }

    /**
     * 关注/取关操作feed
     *
     * @param followingDinerId
     * @param access_token
     * @param type 1 关注；0 取关
     * @return
     */
    @PostMapping("changeFollowingFeed/{followingDinerId}")
    public ResultInfo changeFollowingFeed(@PathVariable Integer followingDinerId, String access_token, Integer type){
        feedsService.changeFollowingFeed(followingDinerId,access_token,type);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),"操作成功");
    }

}
