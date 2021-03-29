package com.mzh.restaurants.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.restaurants.service.ReviewsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("reviews")
public class ReviewsController {

    @Resource
    private ReviewsService reviewsService;

    @Resource
    private HttpServletRequest request;

    /**
     * 添加餐厅评论
     *
     * @param restaurantId
     * @param access_token
     * @param content
     * @param likeIt
     * @return
     */
    @PostMapping("addReview")
    public ResultInfo addReview(Integer restaurantId, String access_token,
                                String content, Integer likeIt) {
        reviewsService.addReview(restaurantId, access_token, content, likeIt);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "评论成功");
    }

    /**
     * 获取餐厅最新评论
     *
     * @param restaurantId
     * @param access_token
     * @return
     */
    @PostMapping("findNewReviews")
    public ResultInfo findNewReviews(Integer restaurantId, String access_token){
        return ResultInfoUtil.buildSuccess(request.getServletPath(),reviewsService.findNewReviews(restaurantId,access_token));
    }

}
