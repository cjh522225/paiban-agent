-- ============================================
-- 排班系统测试数据初始化
-- ============================================
USE paiban_db;
SET NAMES utf8mb4;

-- ============================================
-- 1. 时间段（init_schema 没插入数据，需手动补充）
-- ============================================
-- 时段（先清空再插入，避免重复执行初始化脚本时产生重复数据）
DELETE FROM time_slot;
INSERT INTO `time_slot` (`label`, `start_time`, `end_time`, `period`, `sort_order`, `status`) VALUES
('1-2节', '08:00', '09:40', '08:00-09:40', 1, 1),
('3-4节', '10:00', '11:40', '10:00-11:40', 2, 1),
('5-6节', '14:00', '15:40', '14:00-15:40', 3, 1),
('7-8节', '16:00', '17:40', '16:00-17:40', 4, 1);

-- ============================================
-- 2. 办公室
-- ============================================
INSERT INTO `office` (`code`, `name`, `building`, `floor`, `description`, `gender`, `status`) VALUES
('OFF01', '教学楼办公室', '教学楼', '1楼', '教学楼一楼教师办公室', NULL, 1);


-- ============================================
-- 3. 测试用户（覆盖各种身份/性别组合）
-- ============================================
-- 巡班人员（3男2女）
INSERT INTO `user` (`username`, `password`, `real_name`, `class_name`, `role`, `duty_role`, `phone`, `department`, `gender`, `dormitory_id`, `status`) VALUES
('wangwei', '123456', '王伟', '计算机2101', 'user', '巡班', '13800001001', '计算机学院', '男', 1, 1),
('liming', '123456', '李明', '计算机2102', 'user', '巡班', '13800001002', '计算机学院', '男', 1, 1),
('zhangqiang', '123456', '张强', '软件2101', 'user', '巡班', '13800001003', '软件学院', '男', 1, 1),
('liuna', '123456', '刘娜', '计算机2101', 'user', '巡班', '13800001004', '计算机学院', '女', 2, 1),
('chenjing', '123456', '陈静', '软件2102', 'user', '巡班', '13800001005', '软件学院', '女', 2, 1);

-- 坐班人员（2男2女）
INSERT INTO `user` (`username`, `password`, `real_name`, `class_name`, `role`, `duty_role`, `phone`, `department`, `gender`, `dormitory_id`, `status`) VALUES
('zhaolei', '123456', '赵磊', '网络2101', 'user', '坐班', '13800002001', '网络学院', '男', 1, 1),
('sunpeng', '123456', '孙鹏', '网络2102', 'user', '坐班', '13800002002', '网络学院', '男', 1, 1),
('zhoulin', '123456', '周琳', '计算机2103', 'user', '坐班', '13800002003', '计算机学院', '女', 2, 1),
('wuyan', '123456', '吴燕', '软件2103', 'user', '坐班', '13800002004', '软件学院', '女', 2, 1);

-- 敲灯人员（3男3女）
INSERT INTO `user` (`username`, `password`, `real_name`, `class_name`, `role`, `duty_role`, `phone`, `department`, `gender`, `dormitory_id`, `status`) VALUES
('zhenghao', '123456', '郑浩', '计算机2104', 'user', '敲灯', '13800003001', '计算机学院', '男', 1, 1),
('huangfei', '123456', '黄飞', '软件2101', 'user', '敲灯', '13800003002', '软件学院', '男', 1, 1),
('maxiang', '123456', '马翔', '网络2103', 'user', '敲灯', '13800003003', '网络学院', '男', 1, 1),
('xumei', '123456', '徐梅', '计算机2101', 'user', '敲灯', '13800003004', '计算机学院', '女', 2, 1),
('huli', '123456', '胡丽', '软件2102', 'user', '敲灯', '13800003005', '软件学院', '女', 2, 1),
('linfang', '123456', '林芳', '网络2101', 'user', '敲灯', '13800003006', '网络学院', '女', 2, 1);

-- 坐班+巡班双身份（1男1女）
INSERT INTO `user` (`username`, `password`, `real_name`, `class_name`, `role`, `duty_role`, `phone`, `department`, `gender`, `dormitory_id`, `status`) VALUES
('dengtao', '123456', '邓涛', '计算机2102', 'user', '巡班/坐班', '13800004001', '计算机学院', '男', 1, 1),
('luoxia', '123456', '罗霞', '软件2101', 'user', '巡班/坐班', '13800004002', '软件学院', '女', 2, 1);

-- 禁用用户（测试禁用登录）
INSERT INTO `user` (`username`, `password`, `real_name`, `class_name`, `role`, `duty_role`, `phone`, `department`, `gender`, `dormitory_id`, `status`) VALUES
('disabled1', '123456', '已禁用用户', '计算机2105', 'user', '巡班', '13800009901', '计算机学院', '男', 1, 0);

-- ============================================
-- 4. 样例消息通知
-- ============================================
INSERT INTO `message` (`title`, `content`, `type`, `sender_name`, `sender_id`, `receivers`, `status`) VALUES
('本周值班安排已发布', '各位同学，本周宿舍和办公室值班表已生成，请登录系统查看个人排班。如有冲突请及时提交请假申请。', 'duty', '系统管理员', 1, '', 1),
('关于国庆节值班调整的通知', '根据学校安排，国庆节期间（10.1-10.7）暂停值班，相应排班已自动清除。祝大家节日快乐！', 'system', '系统管理员', 1, '', 1),
('请假审批提醒', '您有一条新的请假申请待审批，请及时处理。', 'approval', '系统管理员', 1, '1', 1);

-- ============================================
-- 5. 样例请假记录
-- ============================================
INSERT INTO `leave_request` (`user_id`, `user_name`, `duty_type`, `leave_type`, `start_date`, `end_date`, `days`, `week_number`, `day_of_week`, `location_id`, `location_name`, `reason`, `status`, `approver_id`, `approver_name`, `remark`) VALUES
(2, 'testuser', 'dormitory', 'sick', '2026-07-20', '2026-07-20', 1, 19, 1, 1, 'A1', '感冒发烧需要休息', 'pending', NULL, NULL, NULL),
(2, 'testuser', 'office', 'personal', '2026-07-22', '2026-07-22', 1, 19, 3, 1, '行政楼办公室', '家里有事需请假一天', 'approved', 1, 'admin', '已批准');

-- ============================================
-- 6. 样例空闲时间（testuser）
-- ============================================
INSERT INTO `user_availability` (`user_id`, `week_parity`, `day_of_week`, `time_slot_id`) VALUES
(2, 'odd', 2, 1),
(2, 'odd', 2, 2),
(2, 'both', 5, 3),
(2, 'both', 5, 4);

-- ============================================
-- 验证
-- ============================================
SELECT '=== 测试数据初始化完成 ===' AS result;
SELECT COUNT(*) AS 总用户数 FROM `user`;
SELECT COUNT(*) AS 宿舍楼数 FROM `dormitory`;
SELECT COUNT(*) AS 办公室数 FROM `office`;
SELECT COUNT(*) AS 时间段数 FROM `time_slot`;
SELECT COUNT(*) AS 消息数 FROM `message`;
SELECT COUNT(*) AS 请假记录数 FROM `leave_request`;
SELECT COUNT(*) AS 空闲时间记录数 FROM `user_availability`;
