package com.mzh.commons.constant;

import lombok.Getter;

@Getter
public enum PointsTypeConstant {

    //积分类型： 0=签到，1=关注好友，2=添加feed，3=添加商户评论
    SIGN(0,"签到积分"),
    FOLLOW_FRIEND(0,"关注好友积分"),
    ADD_FEED(0,"添加feed积分"),
    ADD_MERCHANT_REVIEWS(0,"添加商户评论积分");

    private Integer value;
    private String desc;

    PointsTypeConstant(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

}
