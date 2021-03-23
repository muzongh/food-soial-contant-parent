package com.mzh.diners.contoller;

import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.dto.DinersDTO;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.service.DinersService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@Api(tags = "食客相关接口")
@RequestMapping("/diners")
public class DinersController {

    @Resource
    private DinersService dinersService;

    @Resource
    private HttpServletRequest request;

    /**
     * 根据 ids 查询食客信息
     *
     * @param ids
     * @return
     */
    @GetMapping("findByIds")
    public ResultInfo<List<ShortDinerInfo>> findByIds(String ids) {
        List<ShortDinerInfo> dinerInfos = dinersService.findByIds(ids);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), dinerInfos);
    }

    /**
     * 用户注册
     *
     * @param dinersDTO
     * @return
     */
    @PostMapping("register")
    public ResultInfo register(@RequestBody DinersDTO dinersDTO) {
        return dinersService.register(dinersDTO, request.getServletPath());
    }

    /**
     * 校验手机号是否已注册
     *
     * @param phone
     * @return
     */
    @GetMapping("checkPhone")
    public ResultInfo checkPhone(String phone) {
        dinersService.checkPhoneIsRegistered(phone);
        return ResultInfoUtil.buildSuccess(request.getServletPath());
    }

    /**
     * 登陆
     *
     * @param account
     * @param password
     * @return
     */
    @GetMapping("/signIn")
    public ResultInfo signIn(String account, String password) {
        try {
            return dinersService.signIn(account, password, request.getServletPath());
        } catch (Exception e) {
            return ResultInfoUtil.buildError(0, e.getMessage(), request.getServletPath());
        }
    }

    /**
     * 获取用户某月登陆天数（默认当月）
     *
     * @param access_token
     * @param dateStr
     * @return
     */
    @GetMapping("getLoginCount")
    public ResultInfo getLoginCount(String access_token, String dateStr) {
        return ResultInfoUtil.buildSuccess(request.getServletPath(), dinersService.getLoginCount(access_token, dateStr));
    }

}
