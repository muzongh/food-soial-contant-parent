package com.mzh.seckill.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mzh.commons.constant.ApiConstant;
import com.mzh.commons.constant.RedisKeyConstant;
import com.mzh.commons.exception.ParameterException;
import com.mzh.commons.model.domain.ResultInfo;
import com.mzh.commons.model.pojo.SeckillVouchers;
import com.mzh.commons.model.pojo.VoucherOrders;
import com.mzh.commons.model.vo.SignInDinerInfo;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.commons.utils.ResultInfoUtil;
import com.mzh.seckill.mapper.SeckillVouchersMapper;
import com.mzh.seckill.mapper.VoucherOrdersMapper;
import com.mzh.seckill.model.RedisLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀业务逻辑层
 *
 */
@Service
public class SeckillService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Resource
    private SeckillVouchersMapper seckillVouchersMapper;

    @Resource
    private VoucherOrdersMapper voucherOrdersMapper;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private DefaultRedisScript defaultRedisScript;

    @Resource
    private RedisLock redisLock;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 抢购代金券
     *
     * @param voucherId 代金券ID
     * @param accessToken 登陆token
     * @param path 访问路径
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultInfo doSeckill(Integer voucherId,String accessToken,String path){
        //基本参数校验
        AssertUtil.isTrue(voucherId == null  || voucherId<0,"请选择需要抢购的代金券");
        AssertUtil.isNotEmpty(accessToken,"请登录");
        //判断代金券是否加入抢购
        //注释原始数据库代码
        /*SeckillVouchers seckillVouchers = seckillVouchersMapper.selectVoucher(voucherId);
        AssertUtil.isTrue(seckillVouchers==null,"该代金券并未有抢购活动");
        //判断是否有效
        AssertUtil.isTrue(seckillVouchers.getIsValid()==0,"该活动已结束");*/
        //采用redis
        String key = RedisKeyConstant.seckill_vouchers.getKey().concat(voucherId.toString());
        Map<String,Object> entries = redisTemplate.opsForHash().entries(key);
        SeckillVouchers seckillVouchers = BeanUtil.mapToBean(entries, SeckillVouchers.class, true, null);

        //判断是否开始、结束
        Date now=new Date();
        AssertUtil.isTrue(now.before(seckillVouchers.getStartTime()),"该抢购还未开始");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()),"该抢购已结束");
        //判断是否买完
        AssertUtil.isTrue(seckillVouchers.getAmount()<1,"该券已经被抢购完");
        //获取用户登陆信息
        String url = oauthServerName.concat("user/me?access_token={accessToken}");
        ResultInfo resultInfo = restTemplate.getForEntity(url, ResultInfo.class, accessToken).getBody();
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE){
            resultInfo.setPath(path);
            return resultInfo;
        }
        SignInDinerInfo signInDinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        //判断登陆用户是否已抢到
        VoucherOrders dinerOrder = voucherOrdersMapper.findDinerOrder(signInDinerInfo.getId(),
                seckillVouchers.getFkVoucherId());
        AssertUtil.isTrue(dinerOrder!=null,"您已有此优惠券，无需再次抢购");

        //扣库存
        //原始关系型数据库
       /* int count=seckillVouchersMapper.stockDecrease(seckillVouchers.getId());
        AssertUtil.isTrue(count == 0 ,"该券已被抢购完");*/

        //采用redis
        //Long count = redisTemplate.opsForHash().increment(key, "amount", -1);
        //AssertUtil.isTrue(count < 0 ,"该券已被抢购完");

        String lockName=RedisKeyConstant.lock_key.getKey() +
                signInDinerInfo.getId() + ":" +voucherId;
        Long expireTime  = seckillVouchers.getEndTime().getTime()  - now.getTime();
        //使用redis 锁一个账号只能买一次
        //使用自定义redis锁
//        String lockKey = redisLock.tryLock(lockName, expireTime);
        //redis分布式锁
        RLock lock = redissonClient.getLock(lockName);
        try {
            //不为空意味着拿到锁了
            //自定义redis锁
//            if (StrUtil.isNotBlank(lockKey)){
            //Redisson 分布式锁
            boolean isLocked = lock.tryLock(expireTime, TimeUnit.MILLISECONDS);
            if (isLocked){
                //下单
                VoucherOrders voucherOrders = new VoucherOrders();
                voucherOrders.setFkDinerId(signInDinerInfo.getId());
                //redis不需要维护外键信息
                //voucherOrders.setFkSeckillId(seckillVouchers.getId());
                voucherOrders.setFkVoucherId(seckillVouchers.getFkVoucherId());
                String orderNo = IdUtil.getSnowflake(1, 1).nextIdStr();
                voucherOrders.setOrderNo(orderNo);
                voucherOrders.setOrderType(1);
                voucherOrders.setStatus(0);
                int save = voucherOrdersMapper.save(voucherOrders);
                AssertUtil.isTrue(save==0,"抢购失败，请稍后重试");

                //采用redis+lua
                List<String> keys=new ArrayList<>();
                keys.add(key);
                keys.add("amount");
                Long amount = (Long) redisTemplate.execute(defaultRedisScript, keys);
                AssertUtil.isTrue(amount==null || amount<1,"该券已被抢购完");
            }
        } catch (Exception e) {
            //手动回滚事务
            //TODO 报错org.springframework.transaction.NoTransactionException: No transaction aspect-managed TransactionStatus in scope未解决
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            //解锁
//            redisLock.unlock(lockName,lockKey);
            lock.unlock();
            if (e instanceof ParameterException){
                return ResultInfoUtil.buildError(0,"该券已经卖完了",path);
            }
        }

        return ResultInfoUtil.buildSuccess(path,"抢购成功");
    }

    /**
     * 添加需要抢购的代金券
     *
     * @param seckillVouchers
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVouchers(SeckillVouchers seckillVouchers){

        //非空校验
        AssertUtil.isTrue(seckillVouchers.getFkVoucherId() == null,"请选择需要抢购的代金券");
        AssertUtil.isTrue(seckillVouchers.getAmount() == 0,"请输入抢购总数量");
        Date now = new Date();
        AssertUtil.isNotNull(seckillVouchers.getStartTime(),"请输入开始时间");
        //AssertUtil.isTrue(now.after(seckillVouchers.getStartTime()),"开始时间不能早于当前时间");
        AssertUtil.isNotNull(seckillVouchers.getEndTime(),"请输入结束时间");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()),"结束时间不能早于当前时间");
        AssertUtil.isTrue(seckillVouchers.getStartTime().after(seckillVouchers.getEndTime()),"开始时间不能晚于结束时间");
        //原始走数据库得流程
        /*SeckillVouchers seckillVouchersFromDb = seckillVouchersMapper.selectVoucher(seckillVouchers.getFkVoucherId());
        AssertUtil.isTrue(seckillVouchersFromDb!=null,"该券已经拥有了抢购活动");
        seckillVouchersMapper.save(seckillVouchers);*/

        //采用redis实现
        String key = RedisKeyConstant.seckill_vouchers.getKey().concat(seckillVouchers.getFkVoucherId().toString());
        Map<String,Object> entries = redisTemplate.opsForHash().entries(key);
        AssertUtil.isTrue(!entries.isEmpty() && (Integer)entries.get("amount") > 0 ,"该券已经存在抢购活动");
        seckillVouchers.setIsValid(1);
        seckillVouchers.setCreateDate(now);
        seckillVouchers.setUpdateDate(now);
        redisTemplate.opsForHash().putAll(key,BeanUtil.beanToMap(seckillVouchers));

    }

}
