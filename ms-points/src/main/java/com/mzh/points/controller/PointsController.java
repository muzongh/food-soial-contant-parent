package com.mzh.points.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.points.service.PointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 积分服务控制层代码
 */
@RestController
@RequestMapping("points")
public class PointsController {

    @Resource
    private PointsService pointsService;

    @Resource
    private HttpServletRequest request;

    /**
     * 添加积分
     *
     * @param dinerId
     * @param points
     * @param types
     * @return
     */
    @PostMapping("addPoints")
    public ResultInfo addPoints(Integer dinerId, Integer points, Integer types) {
        pointsService.addPoints(dinerId, points, types);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "添加成功");
    }

    /**
     * 获取积分榜前20名，并返回个人排行榜位置
     *
     * @param access_token
     * @return
     */
    @PostMapping("findDinerPointRankForRedis")
    public ResultInfo findDinerPointRankForRedis(String access_token) {
        return ResultInfoUtil.buildSuccess(request.getServletPath(), pointsService.findDinerPointRankForRedis(access_token));
    }


}
