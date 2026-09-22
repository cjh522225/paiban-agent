package com.Firefire.paiban.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户导入任务进度（内存态，不落库）
 */
@Data
public class ImportProgress {
    /** 任务ID */
    private String taskId;
    /** 数据总行数 */
    private int total;
    /** 已处理行数 */
    private int processed;
    /** 成功条数 */
    private int success;
    /** 跳过条数（空行/账号已存在） */
    private int skip;
    /** 失败条数 */
    private int fail;
    /** 状态: running/done/error */
    private String status;
    /** 错误信息（整体失败时） */
    private String message;
    /** 失败行明细（最多保留 100 条） */
    private List<String> errors = new ArrayList<>();
    /** 开始时间戳（毫秒） */
    private long startTime;
    /** 结束时间戳（毫秒） */
    private long endTime;
}
