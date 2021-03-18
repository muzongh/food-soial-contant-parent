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

    @DeleteMapping("{id}")
    public ResultInfo deleteFeed(@PathVariable Integer id,String access_token){
        feedsService.deleteFeed(id,access_token);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),"删除成功");
    }

}
