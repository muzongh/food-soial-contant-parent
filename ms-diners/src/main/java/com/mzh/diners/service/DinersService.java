package com.mzh.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.dto.DinersDTO;
import com.mzh.commons.model.pojo.Diners;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.config.OAuth2ClientConfiguration;
import com.mzh.diners.domain.OAuthDinerInfo;
import com.mzh.diners.mapper.DinersMapper;
import com.mzh.diners.utils.UserUtils;
import com.mzh.diners.vo.LoginDinerInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 食客服务业务逻辑层
 */
@Service
public class DinersService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Resource
    private DinersMapper dinersMapper;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private SendVerifyCodeService sendVerifyCodeService;

    @Resource
    private OAuth2ClientConfiguration oAuth2ClientConfiguration;

    /**
     * 根据 ids 查询食客信息
     *
     * @param ids 主键 id，多个以逗号分隔，逗号之间不用空格
     * @return
     */
    public List<ShortDinerInfo> findByIds(String ids) {
        AssertUtil.isNotEmpty(ids);
        String[] idArr = ids.split(",");
        List<ShortDinerInfo> dinerInfos = dinersMapper.findByIds(idArr);
        return dinerInfos;
    }

    /**
     * 用户注册
     *
     * @param dinersDTO
     * @param path
     * @return
     */
    public ResultInfo register(DinersDTO dinersDTO, String path) {

        //参数非空校验
        String username = dinersDTO.getUsername();
        AssertUtil.isNotEmpty(username, "请输入用户名");
        String password = dinersDTO.getPassword();
        AssertUtil.isNotEmpty(password, "请输入密码");
        String phone = dinersDTO.getPhone();
        AssertUtil.isNotEmpty(phone, "请输入手机号");
        String verifyCode = dinersDTO.getVerifyCode();
        AssertUtil.isNotEmpty(verifyCode, "请输入验证码");

        //验证码一致性校验
        String verifyCodeByPhone = sendVerifyCodeService.getVerifyCodeByPhone(phone);
        AssertUtil.isNotEmpty(verifyCodeByPhone, "验证码已过期，请重新发送");
        AssertUtil.isTrue(!dinersDTO.getVerifyCode().equals(verifyCodeByPhone), "验证码不一致，请重新输入");

        //验证用户名是否已注册
        Diners diners = dinersMapper.selectByUsername(username.trim());
        AssertUtil.isTrue(diners != null, "该用户名已经存在，请重新输入");

        //密码加密
        dinersDTO.setPassword(DigestUtil.md5Hex(password.trim()));
        //注册
        dinersMapper.save(dinersDTO);

        //自动登陆
        return signIn(username, password, path);

    }

    /**
     * 校验手机号是否已注册
     *
     * @param phone
     */
    public void checkPhoneIsRegistered(String phone) {
        AssertUtil.isNotEmpty(phone, "手机号不能为空");
        Diners diners = dinersMapper.selectByPhone(phone);
        AssertUtil.isTrue(diners == null, "该手机号未注册");
        AssertUtil.isTrue(diners.getIsValid() == 0, "该用户已锁定，请联系管理员解决");
    }

    /**
     * 登陆
     *
     * @param account  账号：用户名或手机号或邮箱
     * @param password 密码
     * @param path     请求路径
     * @return
     */
    public ResultInfo signIn(String account, String password, String path) {
        //参数校验
        AssertUtil.isNotEmpty(account, "请输入登陆账号");
        AssertUtil.isNotEmpty(password, "请输入登陆密码");
        //构建请求头
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        //构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("username", account);
        body.add("password", password);
        body.setAll(BeanUtil.beanToMap(oAuth2ClientConfiguration));
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, httpHeaders);
        //设置Authorization
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(oAuth2ClientConfiguration.getClientId()
                , oAuth2ClientConfiguration.getSecret()));
        //发送请求
        ResponseEntity<ResultInfo> resultInfoResponseEntity = restTemplate.postForEntity(oauthServerName + "oauth/token", entity, ResultInfo.class);

        //处理返回结果
        AssertUtil.isTrue(resultInfoResponseEntity.getStatusCode() != HttpStatus.OK, "登陆失败 ");
        ResultInfo resultInfo = resultInfoResponseEntity.getBody();
        if (ApiConstant.SUCCESS_CODE != resultInfo.getCode()) {
            //登陆失败
            resultInfo.setData(resultInfo.getMessage());
            return resultInfo;
        }
        //Data是一个LinkedHashMap转成了域对象OAuthDinerInfo
        OAuthDinerInfo oAuthDinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new OAuthDinerInfo(), false);
        //根据业务需求返回视图对象
        LoginDinerInfo loginDinerInfo = new LoginDinerInfo();
        loginDinerInfo.setNickname(oAuthDinerInfo.getNickname());
        loginDinerInfo.setAvatarUrl(oAuthDinerInfo.getAvatarUrl());
        loginDinerInfo.setToken(oAuthDinerInfo.getAccessToken());
        //登陆成功，在redis记录每月每日的登陆信息
        //key user:login:{dinerId}:yyyyMM value true/false
        int offset = DateUtil.dayOfMonth(new Date()) - 1;
        String loginKey = buildLoginKey(oAuthDinerInfo.getId(), new Date());
        redisTemplate.opsForValue().setBit(loginKey, offset, true);
        Long continuousLoginCount = getContinuousLoginCount(oAuthDinerInfo.getId(), new Date());
        loginDinerInfo.setContinuousLoginCount(continuousLoginCount);
        loginDinerInfo.setLoginCount(getLoginCount(oAuthDinerInfo.getAccessToken(), ""));
        return ResultInfoUtil.buildSuccess(path, loginDinerInfo);

    }

    /**
     * 获取用户某月登陆天数
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public Long getLoginCount(String accessToken, String dateStr) {
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        Date date = getDate(dateStr);
        String loginKey = buildLoginKey(signInDinerInfo.getId(), date);
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(loginKey.getBytes())
        );
    }

    /**
     * 判断并转换日期格式
     *
     * @param dateStr
     * @return
     */
    private Date getDate(String dateStr) {
        Date date = null;
        try {
            date = StrUtil.isNotBlank(dateStr) ? DateUtil.parseDate(dateStr) : new Date();
        } catch (Exception e) {
            throw new ParameterException("请传入YYYY-MM-DD的日期类型！");
        }
        return date;
    }

    /**
     * 构建登陆key user:sign:{dinerId}:yyyyMM
     *
     * @param dinerId
     * @param date
     * @return
     */
    private String buildLoginKey(Integer dinerId, Date date) {
        return String.format("user:login:%d:%s", dinerId, DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 统计连续签到次数
     *
     * @param dinerId
     * @param date
     * @return
     */
    private Long getContinuousLoginCount(Integer dinerId, Date date) {
        int dayOfMonth = DateUtil.dayOfMonth(date);
        //构建key
        String signKey = buildLoginKey(dinerId, date);
        //bitfield  user:sign:{dinerId}:yyyyMM u31 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0L;
        }
        Long signCount = 0L;
        Long v = list.get(0) == null ? 0l : list.get(0);
        //i表示位移操作次数
        for (int i = dayOfMonth; i > 0; i--) {
            //右移再左移，如果等于自己说明最低位是0，表示未签到
            if (v >> 1 << 1 == v) {
                //低位是0且非当天说明连续q签到中断了
                if (i != dayOfMonth) break;
            } else {
                signCount++;
            }
            //右移一位并赋值
            v >>= 1;
        }
        return signCount;
    }


}
