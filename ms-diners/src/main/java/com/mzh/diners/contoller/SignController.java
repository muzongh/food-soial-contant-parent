package com.mzh.diners.contoller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.service.SignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("sign")
public class SignController {

    @Resource
    private SignService signService;

    @Resource
    private HttpServletRequest httpServletRequest;

    /**
     * 签到
     *
     * @param access_token
     * @param dateStr yyyyMMdd
     * @return 连续签到天数
     */
    @PostMapping("doSign")
    public ResultInfo doSign(String access_token,String dateStr){
        Integer integer = signService.doSign(access_token, dateStr);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),integer);
    }

    /**
     * 获取用户签到天数
     *
     * @param access_token
     * @param dateStr
     * @return
     */
    @GetMapping("getSignCount")
    public ResultInfo getSignCount(String access_token,String dateStr){
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),signService.getSignCount(access_token,dateStr));
    }

    /**
     * 获取用户当月签到情况
     *
     * @param access_token
     * @param dateStr
     * @return
     */
    @GetMapping("getSignInfo")
    public ResultInfo getSignInfo(String access_token,String dateStr){
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),signService.getSignInfo(access_token,dateStr));
    }

}
