package com.mzh.diners.contoller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.service.SignService;
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

}
