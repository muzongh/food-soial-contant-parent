package com.mzh.restaurants.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.restaurants.service.RestaurantsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("restaurants")
public class RestaurantsController {

    @Resource
    private HttpServletRequest request;

    @Resource
    private RestaurantsService restaurantsService;

    /**
     * 根据餐厅ID查询餐厅数据
     *
     * @param restaurantId
     * @return
     */
    @GetMapping("findById")
    public ResultInfo findById(Integer restaurantId) {
        return ResultInfoUtil.buildSuccess(request.getServletPath(), restaurantsService.findById(restaurantId));
    }

}
