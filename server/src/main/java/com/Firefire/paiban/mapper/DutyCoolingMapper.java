package com.Firefire.paiban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.Firefire.paiban.entity.DutyCooling;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DutyCoolingMapper extends BaseMapper<DutyCooling> {

    /** 删除冷却已结束的记录（值班周早于本周-1） */
    @Delete("DELETE FROM duty_cooling WHERE week_number < #{week}")
    int deleteBeforeWeek(@Param("week") int week);

    /** 回捞的人排班后把冷却周次更新为本周（重新冷却） */
    @Update("UPDATE duty_cooling SET week_number = #{week} WHERE user_id = #{userId}")
    int updateWeek(@Param("userId") Long userId, @Param("week") int week);

    /** 批量写入本周排班的人（每用户一条，重复时更新周次；分页批量插入，避免大表整批载入内存） */
    @Insert("<script>INSERT INTO duty_cooling (user_id, week_number) VALUES " +
            "<foreach collection='list' item='c' separator=','>(#{c.userId}, #{c.weekNumber})</foreach>" +
            " ON DUPLICATE KEY UPDATE week_number = VALUES(week_number)</script>")
    int batchInsert(@Param("list") List<DutyCooling> list);
}
