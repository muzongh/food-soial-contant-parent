package com.mzh.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.dto.DinersDTO;
import com.mzh.commons.model.pojo.Diners;
import com.mzh.commons.model.vo.ShortDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.diners.config.OAuth2ClientConfiguration;
import com.mzh.diners.domain.OAuthDinerInfo;
import com.mzh.diners.mapper.DinersMapper;
import com.mzh.diners.vo.LoginDinerInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
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
    public ResultInfo register(DinersDTO dinersDTO,String path){

        //参数非空校验
        String username=dinersDTO.getUsername();
        AssertUtil.isNotEmpty(username,"请输入用户名");
        String password=dinersDTO.getPassword();
        AssertUtil.isNotEmpty(password,"请输入密码");
        String phone=dinersDTO.getPhone();
        AssertUtil.isNotEmpty(phone,"请输入手机号");
        String verifyCode=dinersDTO.getVerifyCode();
        AssertUtil.isNotEmpty(verifyCode,"请输入验证码");

        //验证码一致性校验
        String verifyCodeByPhone = sendVerifyCodeService.getVerifyCodeByPhone(phone);
        AssertUtil.isNotEmpty(verifyCodeByPhone,"验证码已过期，请重新发送");
        AssertUtil.isTrue(!dinersDTO.getVerifyCode().equals(verifyCodeByPhone),"验证码不一致，请重新输入");

        //验证用户名是否已注册
        Diners diners = dinersMapper.selectByUsername(username.trim());
        AssertUtil.isTrue(diners!=null,"该用户名已经存在，请重新输入");

        //密码加密
        dinersDTO.setPassword(DigestUtil.md5Hex(password.trim()));
        //注册
        dinersMapper.save(dinersDTO);

        //自动登陆
        return signIn(username,password,path);

    }

    /**
     * 校验手机号是否已注册
     *
     * @param phone
     */
    public void  checkPhoneIsRegistered(String phone){
        AssertUtil.isNotEmpty(phone,"手机号不能为空");
        Diners diners = dinersMapper.selectByPhone(phone);
        AssertUtil.isTrue(diners == null,"该手机号未注册");
        AssertUtil.isTrue(diners.getIsValid() == 0,"该用户已锁定，请联系管理员解决");
    }

    /**
     * 登陆
     * @param account 账号：用户名或手机号或邮箱
     * @param password 密码
     * @param path 请求路径
     * @return
     */
    public ResultInfo signIn(String account,String password,String path){
        //参数校验
        AssertUtil.isNotEmpty(account,"请输入登陆账号");
        AssertUtil.isNotEmpty(password,"请输入登陆密码");
        //构建请求头
        HttpHeaders httpHeaders=new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        //构建请求体（请求参数）
        MultiValueMap<String,Object> body=new LinkedMultiValueMap<>();
        body.add("username",account);
        body.add("password",password);
        body.setAll(BeanUtil.beanToMap(oAuth2ClientConfiguration));
        HttpEntity<MultiValueMap<String,Object>> entity=new HttpEntity<>(body,httpHeaders);
        //设置Authorization
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(oAuth2ClientConfiguration.getClientId()
                ,oAuth2ClientConfiguration.getSecret()));
        //发送请求
        ResponseEntity<ResultInfo> resultInfoResponseEntity = restTemplate.postForEntity(oauthServerName + "oauth/token", entity, ResultInfo.class);

        //处理返回结果
        AssertUtil.isTrue(resultInfoResponseEntity.getStatusCode() != HttpStatus.OK,"登陆失败 ");
        ResultInfo resultInfo = resultInfoResponseEntity.getBody();
        if (ApiConstant.SUCCESS_CODE!=resultInfo.getCode()){
            //登陆失败
            resultInfo.setData(resultInfo.getMessage());
            return resultInfo;
        }
        //Data是一个LinkedHashMap转成了域对象OAuthDinerInfo
        OAuthDinerInfo oAuthDinerInfo=BeanUtil.fillBeanWithMap((LinkedHashMap)resultInfo.getData(),
                new OAuthDinerInfo(),false);
        //根据业务需求返回视图对象
        LoginDinerInfo loginDinerInfo = new LoginDinerInfo();
        loginDinerInfo.setNickname(oAuthDinerInfo.getNickname());
        loginDinerInfo.setAvatarUrl(oAuthDinerInfo.getAvatarUrl());
        loginDinerInfo.setToken(oAuthDinerInfo.getAccessToken());
        return ResultInfoUtil.buildSuccess(path,loginDinerInfo);

    }

}
