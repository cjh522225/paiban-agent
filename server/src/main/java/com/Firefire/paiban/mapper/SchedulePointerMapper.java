package com.Firefire.paiban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.Firefire.paiban.entity.SchedulePointer;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SchedulePointerMapper extends BaseMapper<SchedulePointer> {

    /** 有则更新、无则插入 */
    @Insert("INSERT INTO schedule_pointer (pool_key, current_user_id) VALUES (#{poolKey}, #{currentUserId}) " +
            "ON DUPLICATE KEY UPDATE current_user_id = VALUES(current_user_id)")
    int upsert(@Param("poolKey") String poolKey, @Param("currentUserId") Long currentUserId);
}
