package com.mzh.follow.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.follow.service.FollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 关注/取关控制层
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private FollowService followService;

    @Resource
    private HttpServletRequest request;

    /**
     * 关注/取关
     *
     * @param followDinerId 关注的食客ID
     * @param isFollowed    是否关注 1=关注 0=取消
     * @param access_token  登录用户token
     * @return
     */
    @PostMapping("/{followDinerId}")
    public ResultInfo follow(@PathVariable Integer followDinerId,
                             @RequestParam int isFollowed,
                             String access_token) {
        ResultInfo resultInfo = followService.follow(followDinerId,
                isFollowed, access_token, request.getServletPath());
        return resultInfo;
    }

    /**
     * 共同关注列表
     *
     * @param dinerId
     * @param access_token
     * @return
     */
    @GetMapping("commons/{dinerId}")
    public ResultInfo findCommonsFriends(@PathVariable Integer dinerId,String access_token){

        return followService.findCommonsFriends(dinerId,access_token,request.getServletPath());

    }

    /**
     * 获取登陆用户关注列表或者粉丝列表
     *
     * @param access_token
     * @param followType 0：关注列表，1：粉丝列表
     * @return
     */
    @PostMapping("followingOrFollowerList")
    public ResultInfo followingOrFollowerList(String access_token,String followType){
        return followService.followingOrFollowerList(access_token,request.getServletPath(),followType);
    }

    /**
     * 获取粉丝ID列表
     *
     * @param dinerId
     * @return
     */
    @GetMapping("findFollowerIds/{dinerId}")
    public ResultInfo findFollowerIds(@PathVariable Integer dinerId){
        return ResultInfoUtil.buildSuccess(request.getServletPath(),followService.findFollowerIds(dinerId));
    }

}