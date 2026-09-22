-- ============================================
-- 生成空闲时间脚本
-- 为所有启用普通用户生成空闲时间：
--   单周(odd) / 双周(even) / 期末周(exam) 各 2~5 个（星期1~5 × 时段1~4）
-- 用法：在 MySQL 中直接执行本文件即可（可重复执行）
-- 注意：会先清空 user_availability 表再重新生成
-- ============================================

-- 1. 清空现有空闲时间
DELETE FROM user_availability;

-- 2. 为每个启用普通用户生成空闲时间（每用户每类 2~5 个，均匀覆盖周一到周五各时段）
INSERT INTO user_availability (user_id, week_parity, day_of_week, time_slot_id)
SELECT u.id, w.wp, c.day_of_week, c.time_slot_id
FROM user u
CROSS JOIN (SELECT 'odd' AS wp, 0 AS seed UNION ALL SELECT 'even', 5 UNION ALL SELECT 'exam', 10) w
CROSS JOIN (
    SELECT 1 AS day_of_week, 1 AS time_slot_id, 0 AS n
    UNION ALL SELECT 1,2,1  UNION ALL SELECT 1,3,2  UNION ALL SELECT 1,4,3
    UNION ALL SELECT 2,1,4  UNION ALL SELECT 2,2,5  UNION ALL SELECT 2,3,6  UNION ALL SELECT 2,4,7
    UNION ALL SELECT 3,1,8  UNION ALL SELECT 3,2,9  UNION ALL SELECT 3,3,10 UNION ALL SELECT 3,4,11
    UNION ALL SELECT 4,1,12 UNION ALL SELECT 4,2,13 UNION ALL SELECT 4,3,14 UNION ALL SELECT 4,4,15
    UNION ALL SELECT 5,1,16 UNION ALL SELECT 5,2,17 UNION ALL SELECT 5,3,18 UNION ALL SELECT 5,4,19
) c
WHERE u.role = 'user' AND u.status = 1
  -- 每用户每类取 2~5 个；7 与 20 互质，使组合均匀分散到各天各时段，避免集中在同一天
  AND MOD(c.n * 7 + u.id + w.seed, 20) < 2 + MOD(u.id, 4);
