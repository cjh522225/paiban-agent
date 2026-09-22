-- ============================================
-- 智能排班系统 - 建表 + 初始账号
-- ============================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

DROP DATABASE IF EXISTS paiban_db;
CREATE DATABASE paiban_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE paiban_db;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 用户表
-- ============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `class_name` VARCHAR(50) COMMENT '班级',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: admin/user',
    `duty_role` VARCHAR(50) COMMENT '值班身份: 巡班/坐班/敲灯',
    `phone` VARCHAR(20) COMMENT '手机号',
    `department` VARCHAR(100) COMMENT '部门',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '性别: 男/女',
    `dormitory_id` BIGINT DEFAULT NULL COMMENT '居住宿舍楼ID',
    `status` INT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 宿舍楼表
-- ============================================
DROP TABLE IF EXISTS `dormitory`;
CREATE TABLE `dormitory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(20) NOT NULL COMMENT '宿舍楼编码',
    `name` VARCHAR(100) NOT NULL COMMENT '宿舍楼名称',
    `building` VARCHAR(50) COMMENT '所属楼栋',
    `floor` INT COMMENT '楼层数',
    `description` VARCHAR(500) COMMENT '描述',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '限制性别: 男/女',
    `status` INT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宿舍楼表';

-- ============================================
-- 办公室表
-- ============================================
DROP TABLE IF EXISTS `office`;
CREATE TABLE `office` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(20) NOT NULL COMMENT '办公室编码',
    `name` VARCHAR(100) NOT NULL COMMENT '办公室名称',
    `building` VARCHAR(50) COMMENT '所属楼栋',
    `floor` VARCHAR(20) COMMENT '楼层',
    `description` VARCHAR(500) COMMENT '描述',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '限制性别: 男/女',
    `status` INT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公室表';

-- ============================================
-- 时间段表
-- ============================================
DROP TABLE IF EXISTS `time_slot`;
CREATE TABLE `time_slot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `label` VARCHAR(50) NOT NULL COMMENT '显示标签: 1-2节/3-4节/5-6节/7-8节',
    `start_time` VARCHAR(10) COMMENT '开始时间',
    `end_time` VARCHAR(10) COMMENT '结束时间',
    `period` VARCHAR(50) COMMENT '时间段显示: 08:00-09:40',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` INT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段表';

-- ============================================
-- 请假申请表
-- ============================================
DROP TABLE IF EXISTS `leave_request`;
CREATE TABLE `leave_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `user_name` VARCHAR(50) COMMENT '用户姓名',
    `duty_type` VARCHAR(20) COMMENT '值班类型: dormitory/office',
    `leave_type` VARCHAR(20) NOT NULL COMMENT '请假类型: sick/personal/annual/compensatory',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `days` INT COMMENT '天数',
    `week_number` INT COMMENT '第几周',
    `day_of_week` INT COMMENT '星期几 1=周一..7=周日',
    `location_id` BIGINT COMMENT '值班地点ID',
    `location_name` VARCHAR(100) COMMENT '值班地点名称',
    `attachment` VARCHAR(2000) DEFAULT NULL COMMENT '附件路径(逗号分隔)',
    `reason` VARCHAR(1000) COMMENT '请假原因',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
    `approver_id` BIGINT COMMENT '审批人ID',
    `approver_name` VARCHAR(50) COMMENT '审批人姓名',
    `remark` VARCHAR(500) COMMENT '审批备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假申请表';

-- ============================================
-- 消息通知表
-- ============================================
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `type` VARCHAR(20) COMMENT '类型: duty/approval/system/urgent',
    `sender_name` VARCHAR(50) COMMENT '发送人姓名',
    `sender_id` BIGINT COMMENT '发送人ID',
    `receivers` VARCHAR(1000) COMMENT '接收人ID(逗号分隔)',
    `attachment` VARCHAR(2000) DEFAULT NULL COMMENT '附件路径(逗号分隔)',
    `status` INT DEFAULT 0 COMMENT '状态: 0-草稿 1-已发布',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- ============================================
-- 消息草稿表
-- ============================================
DROP TABLE IF EXISTS `message_draft`;
CREATE TABLE `message_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_id` BIGINT NOT NULL COMMENT '发送人ID',
    `title` VARCHAR(200) COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `type` VARCHAR(20) COMMENT '类型: duty/approval/system/urgent',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息草稿表';

-- ============================================
-- 值班排班表
-- ============================================
DROP TABLE IF EXISTS `duty_schedule`;
CREATE TABLE `duty_schedule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(20) NOT NULL COMMENT '类型: dormitory/office',
    `location_id` VARCHAR(50) NOT NULL COMMENT '地点ID',
    `location_name` VARCHAR(100) COMMENT '地点名称',
    `user_id` BIGINT NOT NULL COMMENT '值班人员ID',
    `user_name` VARCHAR(50) COMMENT '值班人员姓名',
    `duty_date` DATE NOT NULL COMMENT '值班日期',
    `time_slot` VARCHAR(50) COMMENT '时间段: 1-2节/3-4节/5-6节/7-8节',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班排班表';

-- ============================================
-- 人员空闲时间表
-- ============================================
DROP TABLE IF EXISTS `user_availability`;
CREATE TABLE `user_availability` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `week_parity` VARCHAR(10) NOT NULL COMMENT '周次: odd(单周)/even(双周)/both',
    `day_of_week` INT NOT NULL COMMENT '星期几 1=周一..7=周日',
    `time_slot_id` BIGINT NOT NULL COMMENT '时间段ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_week` (`user_id`, `week_parity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员空闲时间表';

-- ============================================
-- 节假日表
-- ============================================
DROP TABLE IF EXISTS `holiday`;
CREATE TABLE `holiday` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '节假日名称',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节假日表';

-- ============================================
-- 消息查看/已读记录表
-- ============================================
DROP TABLE IF EXISTS `message_read`;
CREATE TABLE `message_read` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` BIGINT NOT NULL COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `is_read` TINYINT DEFAULT 0 COMMENT '0-仅已查看 1-已读',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '用户个人删除标记（该用户不再显示此消息）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_user` (`message_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息查看/已读记录表';

-- ============================================
-- 学期配置表
-- ============================================
DROP TABLE IF EXISTS `semester_config`;
CREATE TABLE `semester_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) COMMENT '学期名称',
    `start_date` DATE NOT NULL COMMENT '学期开始日期(周一)',
    `total_weeks` INT DEFAULT 18 COMMENT '总周数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期配置表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 初始账号
-- ============================================

INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `role`, `status`) VALUES
(1, 'admin', '$2a$12$DeGitI/VRdggAHE4y9pX5eFQG5yw0qrKsB0dpxMFcKtm5f7t4Hqtm', '系统管理员', 'admin', 1),
(2, 'testuser', '$2a$12$DeGitI/VRdggAHE4y9pX5eFQG5yw0qrKsB0dpxMFcKtm5f7t4Hqtm', '测试用户', 'user', 1);

INSERT INTO `semester_config` (`id`, `name`, `start_date`, `total_weeks`) VALUES
(1, '2025-2026学年第二学期', '2026-03-09', 18);

-- ============================================
-- 多排申请 / 换班 / 纪律 扩展表
-- ============================================

CREATE TABLE IF NOT EXISTS `multi_duty_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL DEFAULT 'office',
  `week_start` INT NOT NULL,
  `week_end` INT NOT NULL,
  `note` VARCHAR(255) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `approver_id` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`), KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多排申请';

CREATE TABLE IF NOT EXISTS `swap_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `user_name` VARCHAR(50) NULL,
  `type` VARCHAR(20) NOT NULL,
  `week_number` INT NOT NULL,
  `duty_date` DATE NULL,
  `time_slot` VARCHAR(50) NULL,
  `location_name` VARCHAR(100) NULL,
  `target_day` VARCHAR(10) NULL,
  `target_slot` VARCHAR(50) NULL,
  `reason` VARCHAR(255) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/done',
  `handled_by` BIGINT NULL,
  `handled_at` DATETIME NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`), KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='换班请求';

CREATE TABLE IF NOT EXISTS `swap_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(20) NOT NULL,
  `week_number` INT NOT NULL,
  `schedule_id_a` BIGINT NOT NULL,
  `user_id_a` BIGINT NULL,
  `user_name_a` VARCHAR(50) NULL,
  `schedule_id_b` BIGINT NOT NULL,
  `user_id_b` BIGINT NULL,
  `user_name_b` VARCHAR(50) NULL,
  `operator_id` BIGINT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='换班日志';

CREATE TABLE IF NOT EXISTS `discipline_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `duty_date` DATE NULL,
  `record_type` VARCHAR(20) NOT NULL COMMENT 'late-迟到/absent-缺勤',
  `note` VARCHAR(255) NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纪律记录';

CREATE TABLE IF NOT EXISTS `discipline_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `action` VARCHAR(20) NOT NULL COMMENT '通报批评/退出发展',
  `absent_count` INT NOT NULL,
  `handled` TINYINT NOT NULL DEFAULT 0,
  `handled_by` BIGINT NULL,
  `handled_at` DATETIME NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`), KEY `idx_handled` (`handled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纪律处罚提醒';

-- 排班指针表：记录各身份池当前排到的位置（跨周持续，指针按用户表顺序循环）
CREATE TABLE IF NOT EXISTS `schedule_pointer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pool_key` VARCHAR(50) NOT NULL COMMENT '池标识：dorm_patrol/dorm_sitting_male/dorm_sitting_female/dorm_knock_male/dorm_knock_female/office',
  `current_user_id` BIGINT DEFAULT NULL COMMENT '上次排到的用户ID（下次从其后一位开始；NULL=从表头开始）',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_key` (`pool_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排班指针';

-- 调休周期配置（管理员在学期调整设置；宿舍排班覆盖[start-1,end-1]，办公室按补课映射排班）
CREATE TABLE IF NOT EXISTS `duty_adjustment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `start_date` DATE NOT NULL,
  `end_date` DATE NOT NULL,
  `note` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调休周期配置';

-- 调休补课映射（补班日=makeup_date，补的是replaced_date那天的课；空闲时间按replaced_date的星期+单双周计算）
CREATE TABLE IF NOT EXISTS `duty_adjustment_makeup` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `adjustment_id` BIGINT NOT NULL,
  `makeup_date` DATE NOT NULL COMMENT '补班日(如周六)',
  `replaced_date` DATE NOT NULL COMMENT '补哪天的课',
  PRIMARY KEY (`id`),
  KEY `idx_adjustment` (`adjustment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调休补课映射';

-- 值班冷却表：记录最近两周值过班的人，防连续两周被排（多排用户不进此表）
-- 每用户一条记录，week_number 为最近一次值班周（回捞的人更新为本周，重新冷却）
CREATE TABLE IF NOT EXISTS `duty_cooling` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '值班用户ID',
  `week_number` INT NOT NULL COMMENT '值班周次（按学期开始日推算）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_week` (`week_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班冷却表';

-- 办公室每节课人数上限配置（id 固定为 1，默认 4 人/节，可后台调整）
CREATE TABLE IF NOT EXISTS `office_schedule_config` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `slot_capacity` INT DEFAULT 4 COMMENT '每节课人数上限',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公室每节课人数上限配置';

INSERT INTO `office_schedule_config` (`id`, `slot_capacity`) VALUES (1, 4)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 宿舍值班身份可顶岗位矩阵（3×3：巡班/坐班/敲灯 可顶哪些岗位）
CREATE TABLE IF NOT EXISTS `dorm_identity_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `identity` VARCHAR(50) NOT NULL COMMENT '值班身份',
  `allow_patrol` INT DEFAULT 0 COMMENT '可顶巡班 0/1',
  `allow_duty` INT DEFAULT 0 COMMENT '可顶坐班 0/1',
  `allow_knock` INT DEFAULT 0 COMMENT '可顶敲灯 0/1',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity` (`identity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宿舍值班身份可顶岗位3x3矩阵';
