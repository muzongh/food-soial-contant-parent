package com.mzh.diners.contoller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.service.NearMeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("nearMe")
public class NearMeController {

    @Resource
    private NearMeService nearMeService;

    @Resource
    private HttpServletRequest request;

    /**
     * 更新食客坐标
     *
     * @param access_token
     * @param lon          经度
     * @param lat          纬度
     * @return
     */
    @PostMapping("updateDinerLocation")
    public ResultInfo updateDinerLocation(String access_token, @RequestParam Float lon, @RequestParam Float lat) {
        nearMeService.updateDinerLocation(access_token, lon, lat);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "更新坐标成功");
    }

    /**
     * 获取附近的人
     *
     * @param access_token
     * @param radius       半径
     * @param lon          经度
     * @param lat          纬度
     * @return
     */
    @PostMapping("findNearMe")
    public ResultInfo findNearMe(String access_token,
                                 @RequestParam(required = false) Integer radius,
                                 @RequestParam(required = false) Float lon,
                                 @RequestParam(required = false) Float lat) {

        return ResultInfoUtil.buildSuccess(request.getServletPath()
                , nearMeService.findNearMe(access_token, radius, lon, lat));
    }

    /**
     * 获取登陆用户和目标用户之间的距离
     *
     * @param access_token
     * @param targetDinerId
     * @return
     */
    @GetMapping("getDistance")
    public ResultInfo getDistance(String access_token, Integer targetDinerId) {
        return ResultInfoUtil.buildSuccess(request.getServletPath(), nearMeService.getDistance(access_token, targetDinerId));
    }

}
