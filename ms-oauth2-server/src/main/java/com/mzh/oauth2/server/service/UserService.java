package com.mzh.oauth2.server.service;

import com.mzh.commons.model.domain.SignInIdentity;
import com.mzh.commons.model.pojo.Diners;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.oauth2.server.mapper.DinersMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class UserService implements UserDetailsService {

    @Resource
    private DinersMapper dinersMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AssertUtil.isNotEmpty(username,"请输入用户名");
        Diners diners = dinersMapper.selectByAccountInfo(username);
        if (null==diners){
            throw new UsernameNotFoundException("用户名或密码错误，请重新输入");
        }
        //初始化登陆认证对象
        SignInIdentity signInIdentity=new SignInIdentity();
        //拷贝属性
        BeanUtils.copyProperties(diners,signInIdentity);
        return signInIdentity;
    }

}
