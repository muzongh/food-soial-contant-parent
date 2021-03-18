package com.mzh.diners.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.diners.utils.UserUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class SignService {

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public Integer doSign(String accessToken, String dateStr) {

        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);

        Date date = null;
        try {
            date = StrUtil.isNotBlank(dateStr) ? DateUtil.parseDate(dateStr) : new Date();
        } catch (Exception e) {
            throw new ParameterException("请传入YYYY-MM-DD的日期类型！");
        }
        int offset = DateUtil.dayOfMonth(date) - 1;
        String key = buildSignKey(signInDinerInfo.getId(), date);
        Boolean isSigned = redisTemplate.opsForValue().getBit(key, offset);
        AssertUtil.isTrue(isSigned, "今天已签到完成，无需再签");
        redisTemplate.opsForValue().setBit(key, offset, true);
        return getSignCount(signInDinerInfo.getId(), date);

    }

    /**
     * 构建签到KEY user:sign:{dinerId}:yyyyMM
     *
     * @param dinerId
     * @param date
     * @return
     */
    private String buildSignKey(Integer dinerId, Date date) {
        return String.format("user:sign:%d:%s", dinerId, DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 统计连续签到次数
     *
     * @param dinerId
     * @param date
     * @return
     */
    private Integer getSignCount(Integer dinerId, Date date) {
        int dayOfMonth = DateUtil.dayOfMonth(date);
        //构建key
        String signKey = buildSignKey(dinerId, date);
        //bitfield  user:sign:{dinerId}:yyyyMM u31 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int signCount = 0;
        long v = list.get(0) == null ? 0 : list.get(0);
        for (int i = dayOfMonth; i > 0; i--) {//i表示位移操作次数
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
