package com.mzh.diners.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.constant.PointsTypeConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.diners.utils.UserUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SignService {

    @Value("${service.name.ms-points-server}")
    private String pointsServerName;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public Integer doSign(String accessToken, String dateStr) {

        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        Date date = getDate(dateStr);
        int offset = DateUtil.dayOfMonth(date) - 1;
        String key = buildSignKey(signInDinerInfo.getId(), date);
        Boolean isSigned = redisTemplate.opsForValue().getBit(key, offset);
        AssertUtil.isTrue(isSigned, "今天已签到完成，无需再签");
        redisTemplate.opsForValue().setBit(key, offset, true);
        Integer continuousSignCount = getContinuousSignCount(signInDinerInfo.getId(), date);
        Integer points = 0;
        switch (continuousSignCount) {
            case 1:
                points = 10;
                break;
            case 2:
                points = 20;
                break;
            case 3:
                points = 30;
                break;
            default:
                points = 40;
        }
        Integer integer = addPoints(signInDinerInfo.getId(), points, PointsTypeConstant.SIGN.getValue());
        return integer;

    }

    /**
     * 获取用户签到天数
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public Long getSignCount(String accessToken, String dateStr) {
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        Date date = getDate(dateStr);
        String key = buildSignKey(signInDinerInfo.getId(), date);
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(key.getBytes())
        );
    }

    /**
     * 获取用户当月签到情况
     *
     * @param accessToken
     * @param dateStr     查询日期 YYYY-MM-DD
     * @return key为签到日期，value为签到状态得map
     */
    public Map<String, Boolean> getSignInfo(String accessToken, String dateStr) {
        SignInDinerInfo signInDinerInfo = UserUtils.loadSignInDinerInfo(accessToken);
        Date date = getDate(dateStr);
        String signKey = buildSignKey(signInDinerInfo.getId(), date);

        Map<String, Boolean> signInfo = new TreeMap<>();
        //获取某月得总天数
        int dayOfMonth = DateUtil.lengthOfMonth(DateUtil.month(date) + 1, DateUtil.isLeapYear(DateUtil.year(date)));
        //bitfield user:sign:5:202011 u30 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return signInfo;
        }
        long v = list.get(0) == null ? 0 : list.get(0);
        //从低位到高位进行遍历，为0表示未签到，为1表示已签到
        for (int i = dayOfMonth; i > 0; i--) {
            //yyyy-MM-dd true\false
            LocalDateTime localDateTime = LocalDateTimeUtil.of(date).withDayOfMonth(i);
            Boolean flag = v >> 1 << 1 != v;
            signInfo.put(DateUtil.format(localDateTime, "yyyy-MM-dd"), flag);
            v >>= 1;
        }
        return signInfo;
    }

    /**
     * 获取用户某月（默认当月）首次签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public String getFirstSign(String accessToken, String dateStr) {
        Map<String, Boolean> signInfo = getSignInfo(accessToken, dateStr);
        Optional<Map.Entry<String, Boolean>> first = signInfo.entrySet().stream()
                .filter(stringBooleanEntry -> stringBooleanEntry.getValue()).findFirst();
        if (!first.isPresent()) {
            return "9999-12-31";
        }
        return first.get().getKey();
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
    private Integer getContinuousSignCount(Integer dinerId, Date date) {
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

    /**
     * 添加积分
     * 积分类型： 0=签到，1=关注好友，2=添加feed，3=添加商户评论
     *
     * @param dinerId
     * @param points
     * @param types
     * @return
     */
    private Integer addPoints(Integer dinerId, Integer points, Integer types) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dinerId", dinerId);
        body.add("points", points);
        body.add("types", types);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, httpHeaders);
        ResponseEntity<ResultInfo> resultInfoResponseEntity = restTemplate.postForEntity(pointsServerName + "/points/addPoints",
                entity, ResultInfo.class);
        AssertUtil.isTrue(resultInfoResponseEntity.getStatusCode() != HttpStatus.OK, "登陆失败！");
        ResultInfo resultInfo = resultInfoResponseEntity.getBody();
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        return points;
    }

}
