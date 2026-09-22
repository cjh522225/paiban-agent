package com.Firefire.paiban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.Firefire.paiban.entity.DutySchedule;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DutyScheduleMapper extends BaseMapper<DutySchedule> {

    // 值班次数统计：巡班同一人同一天只算一次（避免按宿舍楼数重复计），坐班/敲灯按实际条数计
    @Select("SELECT user_id, time_slot, " +
            "CASE WHEN time_slot = '巡班' THEN COUNT(DISTINCT duty_date) ELSE COUNT(*) END AS cnt " +
            "FROM duty_schedule WHERE type = #{type} AND duty_date < #{beforeDate} " +
            "GROUP BY user_id, time_slot")
    List<Map<String, Object>> loadDutyCounts(@Param("type") String type,
                                              @Param("beforeDate") LocalDate beforeDate);

    // 全量（含未来排班）聚合，供 staff-with-counts 使用，避免整表拉入内存
    @Select("SELECT user_id, time_slot, " +
            "CASE WHEN time_slot = '巡班' THEN COUNT(DISTINCT duty_date) ELSE COUNT(*) END AS cnt " +
            "FROM duty_schedule WHERE type = #{type} GROUP BY user_id, time_slot")
    List<Map<String, Object>> countByType(@Param("type") String type);

    @Select("SELECT user_id, MAX(duty_date) AS last_date FROM duty_schedule " +
            "WHERE type = #{type} AND duty_date < #{beforeDate} " +
            "GROUP BY user_id")
    List<Map<String, Object>> loadLastPickedDates(@Param("type") String type,
                                                    @Param("beforeDate") LocalDate beforeDate);

    @Delete("DELETE FROM duty_schedule WHERE type = #{type} AND location_id = #{locationId} " +
            "AND duty_date >= #{startDate} AND duty_date <= #{endDate}")
    int batchDeleteByLocation(@Param("type") String type,
                               @Param("locationId") String locationId,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate);

    @Insert("<script>" +
            "INSERT INTO duty_schedule (type, location_id, location_name, user_id, user_name, duty_date, time_slot) VALUES " +
            "<foreach collection='list' item='ds' separator=','>" +
            "(#{ds.type}, #{ds.locationId}, #{ds.locationName}, #{ds.userId}, #{ds.userName}, #{ds.dutyDate}, #{ds.timeSlot})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<DutySchedule> schedules);

    @Update("REPLACE INTO duty_stats (user_id, type, time_slot, duty_count) " +
            "SELECT user_id, type, time_slot, COUNT(*) FROM duty_schedule " +
            "WHERE type = #{type} GROUP BY user_id, type, time_slot")
    void rebuildStats(@Param("type") String type);
}
