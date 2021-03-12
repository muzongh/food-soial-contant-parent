package com.mzh.seckill.controller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.SeckillVouchers;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.seckill.service.SeckillService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("seckill")
@Api(tags = "秒杀接口")
public class SeckillController {

    @Resource
    private SeckillService seckillService;

    @Resource
    private HttpServletRequest httpServletRequest;

    /**
     * 抢购代金券
     *
     * @param voucherId
     * @param access_token
     * @return
     */
    @PostMapping("voucherId")
    public ResultInfo<String> doSeckill(@PathVariable Integer voucherId,String access_token){
        ResultInfo resultInfo = seckillService.doSeckill(voucherId, access_token, httpServletRequest.getServletPath());
        return resultInfo;
    }

    /**
     * 新增秒杀活动
     *
     * @param seckillVouchers
     * @return
     */
    @PostMapping("addSeckillVouchers")
    public ResultInfo addSeckillVouchers(@RequestBody SeckillVouchers seckillVouchers){
        seckillService.addSeckillVouchers(seckillVouchers);
        return ResultInfoUtil.buildSuccess(httpServletRequest.getServletPath(),"添加成功");
    }

}
