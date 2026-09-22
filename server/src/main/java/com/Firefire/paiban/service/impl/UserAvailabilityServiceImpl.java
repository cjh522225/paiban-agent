package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.*;
import com.Firefire.paiban.mapper.*;
import com.Firefire.paiban.service.UserAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserAvailabilityServiceImpl extends ServiceImpl<UserAvailabilityMapper, UserAvailability> implements UserAvailabilityService {

    private final UserMapper userMapper;

    @Override
    public List<UserAvailability> getByUserId(Long userId) {
        LambdaQueryWrapper<UserAvailability> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAvailability::getUserId, userId);
        return list(wrapper);
    }

    @Override
    @Transactional
    public void batchSave(Long userId, List<UserAvailability> availabilities) {
        remove(new LambdaQueryWrapper<UserAvailability>().eq(UserAvailability::getUserId, userId));
        for (UserAvailability ua : availabilities) {
            ua.setUserId(userId);
            save(ua);
        }
    }

    @Override
    public List<Map<String, Object>> getAllWithUser() {
        List<UserAvailability> all = list();
        Map<Long, List<UserAvailability>> grouped = new HashMap<>();
        for (UserAvailability ua : all) {
            grouped.computeIfAbsent(ua.getUserId(), k -> new ArrayList<>()).add(ua);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<UserAvailability>> e : grouped.entrySet()) {
            User user = userMapper.selectById(e.getKey());
            if (user == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("userId", user.getId());
            m.put("realName", user.getRealName());
            m.put("username", user.getUsername());
            m.put("availabilities", e.getValue());
            result.add(m);
        }
        return result;
    }
}
