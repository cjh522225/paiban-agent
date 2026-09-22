package com.Firefire.paiban.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.Firefire.paiban.entity.UserAvailability;
import java.util.List;
import java.util.Map;

public interface UserAvailabilityService extends IService<UserAvailability> {
    List<UserAvailability> getByUserId(Long userId);
    List<Map<String, Object>> getAllWithUser();
    void batchSave(Long userId, List<UserAvailability> availabilities);
}
