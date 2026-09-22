package com.Firefire.paiban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 宿舍值班身份 → 可顶岗位 授权 */
@Data
@TableName("dorm_identity_permission")
public class DormIdentityPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 值班身份：巡班/坐班/敲灯 */
    private String identity;

    /** 可顶巡班岗 */
    private Integer allowPatrol;

    /** 可顶坐班岗 */
    private Integer allowDuty;

    /** 可顶敲灯岗 */
    private Integer allowKnock;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
