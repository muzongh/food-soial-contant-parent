package com.mzh.points.service;

import com.mzh.commons.model.pojo.DinerPoints;
import com.mzh.commons.utils.AssertUtil;
import com.mzh.points.mapper.DinerPointsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 积分服务
 */
@Service
public class PointsService {

    @Resource
    private DinerPointsMapper dinerPointsMapper;

    /**
     * 添加积分
     *
     * @param dinerId 食客ID
     * @param points  积分值
     * @param types   积分类型： 0=签到，1=关注好友，2=添加feed，3=添加商户评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Integer dinerId, Integer points, Integer types) {

        AssertUtil.isTrue(dinerId == null || dinerId < 1, "食客不能为空");
        AssertUtil.isTrue(points == null || points < 1, "积分数值不能为空");
        AssertUtil.isTrue(types == null, "请选择对应的积分类型");

        //插入数据库
        DinerPoints dinerPoints = new DinerPoints();
        dinerPoints.setFkDinerId(dinerId);
        dinerPoints.setPoints(points);
        dinerPoints.setTypes(types);
        dinerPointsMapper.save(dinerPoints);

    }

}
